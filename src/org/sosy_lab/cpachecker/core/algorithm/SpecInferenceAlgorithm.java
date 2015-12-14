package org.sosy_lab.cpachecker.core.algorithm;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AlgorithmIterationListener;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.composite.CompositeState;
import org.sosy_lab.cpachecker.cpa.specinference.SpecInferenceState;
import org.sosy_lab.cpachecker.exceptions.CPAException;

/**
 * This is a wrapper around the {@link CPAAlgorithm}.
 */
@Options(prefix = "specInference")
public class SpecInferenceAlgorithm implements Algorithm {

  private static final String PREFIX = "State_";
  private final CPAAlgorithm cpaAlg;
  private final LogManager logger;

  @Options
  public static class SpecInferenceAlgorithmFactory {

    private final ConfigurableProgramAnalysis cpa;
    private final LogManager logger;
    private final ShutdownNotifier shutdownNotifier;
    private final AlgorithmIterationListener iterationListener;
    private final Configuration config;

    public SpecInferenceAlgorithmFactory(ConfigurableProgramAnalysis cpa,
                                         LogManager logger,
                                         Configuration config,
                                         ShutdownNotifier pShutdownNotifier,
                                         @Nullable AlgorithmIterationListener pIterationListener)
            throws InvalidConfigurationException {

      config.inject(this);
      this.cpa = cpa;
      this.logger = logger;
      this.shutdownNotifier = pShutdownNotifier;
      this.iterationListener = pIterationListener;
      this.config = config;
    }

    public SpecInferenceAlgorithm newInstance() throws InvalidConfigurationException {
      return new SpecInferenceAlgorithm(cpa, logger, config, shutdownNotifier, iterationListener);
    }
  }

  private SpecInferenceAlgorithm(ConfigurableProgramAnalysis cpa,
                                 LogManager logger,
                                 Configuration config,
                                 ShutdownNotifier pShutdownNotifier,
                                 AlgorithmIterationListener iterationListener) throws InvalidConfigurationException {

    this.cpaAlg = CPAAlgorithm.create(cpa, logger, config, pShutdownNotifier, iterationListener);
    this.logger = logger;
  }

  public static SpecInferenceAlgorithm create(ConfigurableProgramAnalysis cpa,
                                              LogManager logger,
                                              Configuration config,
                                              ShutdownNotifier pShutdownNotifier,
                                              AlgorithmIterationListener pIterationListener)
          throws InvalidConfigurationException {
    return new SpecInferenceAlgorithmFactory(cpa, logger, config, pShutdownNotifier, pIterationListener).newInstance();
  }

  public static SpecInferenceAlgorithm create(ConfigurableProgramAnalysis cpa,
                                              LogManager logger,
                                              Configuration config,
                                              ShutdownNotifier pShutdownNotifier)
          throws InvalidConfigurationException {
    return new SpecInferenceAlgorithmFactory(cpa, logger, config, pShutdownNotifier, null).newInstance();
  }

  @Override
  public AlgorithmStatus run(ReachedSet reachedSet)
      throws CPAException, InterruptedException {

    AlgorithmStatus retVal = cpaAlg.run(reachedSet);

    if (!(reachedSet.getFirstState() instanceof ARGState)) {
      throw new IllegalArgumentException(
          "SpecInferenceAlgorithm needs ARGStates in the reachedSet!");
    }

    ARGState first = (ARGState)reachedSet.getFirstState();

    // TODO write the automaton to file
    System.out.println(assembleAutomatonString(assembleAutomaton(first), getNextRelevant(first).getStateId()));

    return retVal;
  }

  private static List<String> assembleAutomaton(ARGState root) {

    List<String> retList = new LinkedList<>();

    StringBuilder sb;
    SpecInferenceState curState = getSpecInfState(root);
    ARGState current = curState.isEmpty() ? getNextRelevant(root) : root;

    // to save memory and execution time, we do not do recursion if there is only one child
    while (current.getChildren().size() == 1) {
      sb = new StringBuilder();

      ARGState next = getNextRelevant(current);
      curState = getSpecInfState(current);
      int currSink = curState.getAutomaton().getSink();

      // generate state in output automaton
      sb.append("STATE USEFIRST ");
      sb.append(PREFIX);
      sb.append(current.getStateId());
      sb.append(" :\n");

      // generate transition in output automaton
      sb.append("  ");
      sb.append(curState.getAutomaton().getEdge(currSink - 1).getStatement());
      sb.append(" GOTO ");
      sb.append(PREFIX);
      sb.append(next.getStateId());
      sb.append(" ;\n\n");

      retList.add(sb.toString());
      current = next;
    }

    sb = new StringBuilder();

    if (current.getChildren().size() == 0) {

      // this state is covered
      if (current.isCovered()) {

        sb.append("DUMMY \n");
        sb.append("  MATCH{$?} -> GOTO ");
        sb.append(PREFIX);
        // MAGIC - I assume that a node where the ARG splits has only one parent
        sb.append(current.getCoveringState().getParents().iterator().next().getStateId());
        sb.append(" ;\n\n");

      }

      // at this edge the ARG ends
      else {
        // generate accepting dummy state
        sb.append("STATE USEFIRST ");
        sb.append(PREFIX);
        sb.append(current.getStateId());
        sb.append(" :\n");
        sb.append("  MATCH{$?} -> GOTO ");
        sb.append(PREFIX);
        sb.append(current.getStateId());
        sb.append(" ;\n");
        sb.append("  // ACCEPT\n\n");
      }

      retList.add(sb.toString());

    } else if (current.getChildren().size() > 1) {

      retList.add("MARKER");
      int marker = retList.size() - 1;

      sb.append("STATE USEALL ");
      sb.append(PREFIX);
      sb.append(current.getStateId());
      sb.append(" :\n");

      for (ARGState s : current.getChildren()) {
        List<String> l = assembleAutomaton(s);
        String assume = l.remove(0);

        sb.append(assume.substring(assume.indexOf("\n") + 1));
        sb.deleteCharAt(sb.length() - 1);

        retList.addAll(l);

      }

      retList.set(marker, sb.toString());

    } else {
      // OH FUCK! Negative number of items in a Collection, will never happen
      throw new Error("Everything just dropped on you...");
    }

    return retList;
  }

  private static ARGState getNextRelevant(ARGState s) {

    ARGState current = s;
    SpecInferenceState currentState = getSpecInfState(current);
    ARGState next = current.getChildren().iterator().next();
    int sink;
    boolean takeFirstNotEmpty = false;

    while (true) {

      // If there is no automaton yet, continue
      if (currentState.isEmpty()) {
        current = next;
        next = current.getChildren().iterator().next();
        currentState = getSpecInfState(current);
        takeFirstNotEmpty = true;
        continue;
      } else {

        if (takeFirstNotEmpty) {
          return current;
        }

        sink = getSpecInfState(current).getAutomaton().getSink();
      }

      if (current.getChildren().size() != 1) {
        return current;
      } else if (next.getChildren().size() != 1) {
        return next;
      } else if (sink != getSpecInfState(next).getAutomaton().getSink()) {
        return next;
      } else {
        current = next;
        next = current.getChildren().iterator().next();
        currentState = getSpecInfState(current);
      }
    }

  }

  private static SpecInferenceState getSpecInfState(AbstractState s) {

    CompositeState compState = (CompositeState) ((ARGState) s).getWrappedState();
    for (AbstractState abstractState : compState.getWrappedStates()) {
      if (abstractState instanceof SpecInferenceState) {
        return (SpecInferenceState) abstractState;
      }
    }

    throw new IllegalArgumentException("Did not find a SpecInferenceState!");
  }

  private static String assembleAutomatonString(List<String> aut, int initState) {

    StringBuilder sb = new StringBuilder();

    sb.append("OBSERVER AUTOMATON AutomatonName\n\n");
    sb.append("INITIAL STATE ");
    sb.append(PREFIX);
    sb.append(initState);
    sb.append(" ;\n\n");

    for (String s : aut) {

      sb.append(s);
      if (!s.contains("// ACCEPT")) {
        sb.append("  MATCH EXIT -> ERROR;\n\n");
      }
    }

    sb.append("END AUTOMATON");

    return sb.toString();
  }
}
