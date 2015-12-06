package org.sosy_lab.cpachecker.core.algorithm;

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
public class SpecInferenceAlgorithm implements Algorithm {

  private final CPAAlgorithm cpaAlg;

  @Options(prefix = "specInference")
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
  public AlgorithmStatus run(ReachedSet reachedSet) throws CPAException, InterruptedException {

    AlgorithmStatus retVal = cpaAlg.run(reachedSet);

    while (reachedSet.iterator().hasNext()) {




      //System.out.println(state.toString());

    }

    return retVal;
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
}
