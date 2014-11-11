/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.value.refiner;

import static com.google.common.collect.FluentIterable.from;

import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.types.Type;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.MutableARGPath;
import org.sosy_lab.cpachecker.cpa.conditions.path.AssignmentsInPathCondition.UniqueAssignmentsInPathConditionState;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState.MemoryLocation;
import org.sosy_lab.cpachecker.cpa.value.refiner.utils.ErrorPathClassifier;
import org.sosy_lab.cpachecker.cpa.value.refiner.utils.ErrorPathClassifier.ErrorPathPrefixPreference;
import org.sosy_lab.cpachecker.cpa.value.refiner.utils.ValueAnalysisFeasibilityChecker;
import org.sosy_lab.cpachecker.cpa.value.refiner.utils.ValueAnalysisEdgeInterpolator;
import org.sosy_lab.cpachecker.cpa.value.type.Value;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException.Reason;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

@Options(prefix="cpa.value.refiner")
public class ValueAnalysisPathInterpolator implements Statistics {
  /**
   * whether or not to do lazy-abstraction, i.e., when true, the re-starting node
   * for the re-exploration of the ARG will be the node closest to the root
   * where new information is made available through the current refinement
   */
  @Option(secure=true, description="whether or not to do lazy-abstraction")
  private boolean doLazyAbstraction = true;

  @Option(secure=true, description="which prefix of an actual counterexample trace should be used for interpolation")
  private ErrorPathPrefixPreference prefixPreference = ErrorPathPrefixPreference.DOMAIN_BEST_SHALLOW;

  /**
   * the offset in the path from where to cut-off the subtree, and restart the analysis
   */
  private int interpolationOffset = -1;

  /**
   * a reference to the assignment-counting state, to make the precision increment aware of thresholds
   */
  private UniqueAssignmentsInPathConditionState assignments = null;

  // statistics
  private StatCounter totalInterpolations   = new StatCounter("Number of interpolations");
  private StatInt totalInterpolationQueries = new StatInt(StatKind.SUM, "Number of interpolation queries");
  private StatInt sizeOfInterpolant         = new StatInt(StatKind.AVG, "Size of interpolant");
  private StatTimer timerInterpolation      = new StatTimer("Time for interpolation");

  private final CFA cfa;
  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final Configuration config;

  private final ValueAnalysisEdgeInterpolator interpolator;

  public ValueAnalysisPathInterpolator(Configuration pConfig,
      final LogManager pLogger, final ShutdownNotifier pShutdownNotifier,
      final CFA pCfa)
      throws InvalidConfigurationException {
    pConfig.inject(this);
    config = pConfig;

    logger           = pLogger;
    cfa              = pCfa;
    shutdownNotifier = pShutdownNotifier;
    interpolator     = new ValueAnalysisEdgeInterpolator(pConfig, logger, shutdownNotifier, cfa);
  }

  protected Map<ARGState, ValueAnalysisInterpolant> performInterpolation(MutableARGPath errorPath,
      ValueAnalysisInterpolant interpolant) throws CPAException, InterruptedException {
    totalInterpolations.inc();
    timerInterpolation.start();

    interpolationOffset = -1;

    List<CFAEdge> errorTrace = obtainErrorTrace(obtainErrorPathPrefix(errorPath, interpolant));

    // obtain use-def relation, containing variables relevant to the "failing" assumption
    Set<MemoryLocation> useDefRelation = new HashSet<>();
    /* TODO: does not work as long as AssumptionUseDefinitionCollector is incomplete (e.g., does not take structs into account)
    if (prefixPreference != ErrorPathPrefixPreference.DEFAULT) {
      AssumptionUseDefinitionCollector useDefinitionCollector = new InitialAssumptionUseDefinitionCollector();
      useDefRelation = from(useDefinitionCollector.obtainUseDefInformation(errorTrace)).
          transform(MemoryLocation.FROM_STRING_TO_MEMORYLOCATION).toSet();
    }*/

    Map<ARGState, ValueAnalysisInterpolant> pathInterpolants = new LinkedHashMap<>(errorPath.size());
    for (int i = 0; i < errorPath.size() - 1; i++) {
      shutdownNotifier.shutdownIfNecessary();

      if (!interpolant.isFalse()) {
        interpolant = interpolator.deriveInterpolant(errorTrace, i, interpolant, useDefRelation);
      }

      totalInterpolationQueries.setNextValue(interpolator.getNumberOfInterpolationQueries());

      if (!interpolant.isTrivial() && interpolationOffset == -1) {
        interpolationOffset = i + 1;
      }

      sizeOfInterpolant.setNextValue(interpolant.isTrivial() ? 0 : interpolant.assignment.size());

      pathInterpolants.put(errorPath.get(i + 1).getFirst(), interpolant);

      assert ((i != errorTrace.size() - 1) || interpolant.isFalse()) : "final interpolant is not false";
    }

    timerInterpolation.stop();
    return pathInterpolants;
  }

  public Multimap<CFANode, MemoryLocation> determinePrecisionIncrement(MutableARGPath errorPath)
      throws CPAException, InterruptedException {

    assignments = AbstractStates.extractStateByType(errorPath.getLast().getFirst(),
        UniqueAssignmentsInPathConditionState.class);

    Multimap<CFANode, MemoryLocation> increment = HashMultimap.create();

    Map<ARGState, ValueAnalysisInterpolant> itps = performInterpolation(errorPath, ValueAnalysisInterpolant.createInitial());

    for (Map.Entry<ARGState, ValueAnalysisInterpolant> itp : itps.entrySet()) {
      addToPrecisionIncrement(increment, AbstractStates.extractLocation(itp.getKey()), itp.getValue());
    }

    return increment;
  }

  /**
   * This method adds the given variable at the given location to the increment.
   *
   * @param increment the current increment
   * @param currentNode the current node for which to add a new variable
   * @param memoryLocation the name of the variable to add to the increment at the given edge
   */
  private void addToPrecisionIncrement(Multimap<CFANode, MemoryLocation> increment,
      CFANode currentNode,
      ValueAnalysisInterpolant itp) {
    for (MemoryLocation memoryLocation : itp.getMemoryLocations()) {
      if (assignments == null || !assignments.exceedsHardThreshold(memoryLocation)) {
        increment.put(currentNode, memoryLocation);
      }
    }
  }

  /**
   * This method determines the new refinement root.
   *
   * @param errorPath the error path from where to determine the refinement root
   * @param increment the current precision increment
   * @param isRepeatedRefinement the flag to determine whether or not this is a repeated refinement
   * @return the new refinement root
   * @throws RefinementFailedException if no refinement root can be determined
   */
  public Pair<ARGState, CFAEdge> determineRefinementRoot(MutableARGPath errorPath, Multimap<CFANode, MemoryLocation> increment,
      boolean isRepeatedRefinement) throws RefinementFailedException {

    if (interpolationOffset == -1) {
      throw new RefinementFailedException(Reason.InterpolationFailed, errorPath.immutableCopy());
    }

    // if doing lazy abstraction, use the node closest to the root node where new information is present
    if (doLazyAbstraction) {
      return errorPath.get(interpolationOffset);
    }

    // otherwise, just use the successor of the root node
    else {
      return errorPath.get(1);
    }
  }

  /**
   * This method obtains, from the error path, the list of CFA edges to be interpolated.
   *
   * @param errorPath the error path
   * @return the list of CFA edges to be interpolated
   */
  private List<CFAEdge> obtainErrorTrace(MutableARGPath errorPath) {
    return from(errorPath).transform(Pair.<CFAEdge>getProjectionToSecond()).toList();
  }

  /**
   * This path obtains a (sub)path of the error path which is given to the interpolation procedure.
   *
   * @param errorPath the original error path
   * @param interpolant the initial interpolant, i.e. the initial state, with which to check the error path.
   * @return a (sub)path of the error path which is given to the interpolation procedure
   * @throws CPAException
   * @throws InterruptedException
   */
  private MutableARGPath obtainErrorPathPrefix(MutableARGPath errorPath, ValueAnalysisInterpolant interpolant)
          throws CPAException, InterruptedException {

    try {
      ValueAnalysisFeasibilityChecker checker = new ValueAnalysisFeasibilityChecker(logger, cfa, config);
      List<MutableARGPath> prefixes                  = checker.getInfeasilbePrefixes(errorPath, interpolant.createValueAnalysisState());

      ErrorPathClassifier classifier          = new ErrorPathClassifier(cfa.getVarClassification(), cfa.getLoopStructure());
      errorPath                               = classifier.obtainPrefix(prefixPreference, errorPath, prefixes);

    } catch (InvalidConfigurationException e) {
      throw new CPAException("Configuring ValueAnalysisFeasibilityChecker failed: " + e.getMessage(), e);
    }

    return errorPath;
  }

  @Override
  public String getName() {
    return "ValueAnalysisInterpolationBasedRefiner";
  }

  @Override
  public void printStatistics(PrintStream out, Result result, ReachedSet reached) {
    StatisticsWriter writer = StatisticsWriter.writingStatisticsTo(out).beginLevel();
    writer.put(totalInterpolations);
    writer.put(totalInterpolationQueries);
    writer.put(sizeOfInterpolant);
    writer.put(timerInterpolation);
  }

  public int getInterpolationOffset() {
    return interpolationOffset;
  }

  /**
   * This class represents a Value-Analysis interpolant, itself, just a mere wrapper around a map
   * from memory locations to values, representing a variable assignment.
   */
  public static class ValueAnalysisInterpolant {
    /**
     * the variable assignment of the interpolant
     */
    private @Nullable final Map<MemoryLocation, Value> assignment;
    private @Nullable final Map<MemoryLocation, Type> assignmentTypes;

    /**
     * the interpolant representing "true"
     */
    public static final ValueAnalysisInterpolant TRUE  = new ValueAnalysisInterpolant();

    /**
     * the interpolant representing "false"
     */
    public static final ValueAnalysisInterpolant FALSE = new ValueAnalysisInterpolant((Map<MemoryLocation, Value>)null,(Map<MemoryLocation, Type>)null);

    /**
     * Constructor for a new, empty interpolant, i.e. the interpolant representing "true"
     */
    private ValueAnalysisInterpolant() {
      assignment = new HashMap<>();
      assignmentTypes = new HashMap<>();
    }

    /**
     * Constructor for a new interpolant representing the given variable assignment
     *
     * @param pAssignment the variable assignment to be represented by the interpolant
     */
    public ValueAnalysisInterpolant(Map<MemoryLocation, Value> pAssignment, Map<MemoryLocation, Type> pAssignmentToType) {
      assignment = pAssignment;
      assignmentTypes = pAssignmentToType;
    }

    /**
     * This method serves as factory method for an initial, i.e. an interpolant representing "true"
     *
     * @return
     */
    public static ValueAnalysisInterpolant createInitial() {
      return new ValueAnalysisInterpolant();
    }

    public Set<MemoryLocation> getMemoryLocations() {
      return isFalse()
          ? Collections.<MemoryLocation>emptySet()
          : Collections.unmodifiableSet(assignment.keySet());
    }

    /**
     * This method joins to value-analysis interpolants. If the underlying map contains different values for a key
     * contained in both maps, the behaviour is undefined.
     *
     * @param other the value-analysis interpolant to join with this one
     * @return a new value-analysis interpolant containing the joined mapping of this and the other value-analysis
     * interpolant
     */
    public ValueAnalysisInterpolant join(ValueAnalysisInterpolant other) {

      if (assignment == null || other.assignment == null) {
        return ValueAnalysisInterpolant.FALSE;
      }

      Map<MemoryLocation, Value> newAssignment = new HashMap<>(assignment);

      // add other itp mapping - one by one for now, to check for correctness
      // newAssignment.putAll(other.assignment);
      for (Map.Entry<MemoryLocation, Value> entry : other.assignment.entrySet()) {
        if (newAssignment.containsKey(entry.getKey())) {
          assert (entry.getValue().equals(other.assignment.get(entry.getKey()))) : "interpolants mismatch in " + entry.getKey();
        }

        newAssignment.put(entry.getKey(), entry.getValue());
      }

      Map<MemoryLocation, Type> newAssignmentTypes = new HashMap<>();
      for (MemoryLocation loc : newAssignment.keySet()) {
        if (assignmentTypes.containsKey(loc)) {
          newAssignmentTypes.put(loc, assignmentTypes.get(loc));
        } else {
          newAssignmentTypes.put(loc, other.assignmentTypes.get(loc));
        }
      }


      return new ValueAnalysisInterpolant(newAssignment, newAssignmentTypes);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(assignment) + Objects.hashCode(assignmentTypes);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }

      if (obj == null) {
        return false;
      }

      if (getClass() != obj.getClass()) {
        return false;
      }

      ValueAnalysisInterpolant other = (ValueAnalysisInterpolant) obj;
      return Objects.equals(assignment, other.assignment) && Objects.equals(assignmentTypes, other.assignmentTypes);
    }

    /**
     * The method checks for trueness of the interpolant.
     *
     * @return true, if the interpolant represents "true", else false
     */
    public boolean isTrue() {
      return !isFalse() && assignment.isEmpty();
    }

    /**
     * The method checks for falseness of the interpolant.
     *
     * @return true, if the interpolant represents "false", else true
     */
    public boolean isFalse() {
      return assignment == null;
    }

    /**
     * The method checks if the interpolant is a trivial one, i.e. if it represents either true or false
     *
     * @return true, if the interpolant is trivial, else false
     */
    public boolean isTrivial() {
      return isFalse() || isTrue();
    }

    /**
     * This method serves as factory method to create a value-analysis state from the interpolant
     *
     * @return a value-analysis state that represents the same variable assignment as the interpolant
     */
    public ValueAnalysisState createValueAnalysisState() {
      return new ValueAnalysisState(PathCopyingPersistentTreeMap.copyOf(assignment), PathCopyingPersistentTreeMap.copyOf(assignmentTypes));
    }

    @Override
    public String toString() {
      if (isFalse()) {
        return "FALSE";
      }

      if (isTrue()) {
        return "TRUE";
      }

      return assignment.toString();
    }

    public boolean strengthen(ValueAnalysisState valueState, ARGState argState) {
      if (isTrivial()) {
        return false;
      }

      boolean strengthened = false;

      for (Map.Entry<MemoryLocation, Value> itp : assignment.entrySet()) {
        if (!valueState.contains(itp.getKey())) {
          valueState.assignConstant(itp.getKey(), itp.getValue(), assignmentTypes.get(itp.getKey()));
          strengthened = true;

        } else if(valueState.contains(itp.getKey()) && valueState.getValueFor(itp.getKey()).asNumericValue().longValue() != itp.getValue().asNumericValue().longValue()) {
          assert false : "state and interpolant do not match in value for variable " + itp.getKey() + "[state = " + valueState.getValueFor(itp.getKey()).asNumericValue().longValue() + " != " + itp.getValue() + " = itp] for state " + argState.getStateId();
        }
      }

      return strengthened;
    }

    /**
     * This method weakens the interpolant to the given set of memory location identifiers.
     *
     * As the information on what to retain is derived in a static syntactical analysis, the set to retain is a
     * collection of memory location identifiers, instead of {@link MemoryLocation}s, as offsets cannot be provided.
     *
     * @param toRetain the set of memory location identifiers to retain in the interpolant.
     * @return the weakened interpolant
     */
    public ValueAnalysisInterpolant weaken(Set<String> toRetain) {
      if (isTrivial()) {
        return this;
      }

      ValueAnalysisInterpolant weakenedItp = new ValueAnalysisInterpolant(new HashMap<>(assignment), new HashMap<>(assignmentTypes));

      for (Iterator<MemoryLocation> it = weakenedItp.assignment.keySet().iterator(); it.hasNext(); ) {
        MemoryLocation current = it.next();

        if (!toRetain.contains(current.getAsSimpleString())) {
          it.remove();
        }
      }

      return weakenedItp;
    }
  }
}