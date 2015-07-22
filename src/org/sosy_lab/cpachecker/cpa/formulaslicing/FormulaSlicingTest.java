/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.formulaslicing;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import org.junit.Before;
import org.junit.Test;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.CFACreator;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.core.AnalysisDirection;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.predicates.FormulaManagerFactory;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormulaManager.Tactic;
import org.sosy_lab.cpachecker.util.predicates.interfaces.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.UnsafeFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManagerImpl;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.test.TestDataTools;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * TODO: Class Description
 */
public class FormulaSlicingTest {
  private CFACreator creator;
  private LogManager logger;
  private PathFormulaManager pfmgr;
  private FormulaManagerView fmgr;
  private BooleanFormulaManager bfmgr;
  private UnsafeFormulaManager ufmgr;
  
  @Before public void setUp() throws Exception {
    Configuration config = TestDataTools.configurationForTest().setOptions(
        ImmutableMap.of(
            "cpa.predicate.solver", "Z3",
            "log.consoleLevel", "FINE",

            // todo: just for easier debugging now.
            "cpa.predicate.handlePointerAliasing", "false"
        )
    ).build();
    ShutdownNotifier notifier = ShutdownNotifier.create();
    logger = new BasicLogManager(config,
        new StreamHandler(System.out, new SimpleFormatter()));
    creator = new CFACreator(config, logger, notifier);
    FormulaManagerFactory factory = new FormulaManagerFactory(config, logger, notifier);
    fmgr = new FormulaManagerView(factory, config, logger);
    // todo: non-deprecated constructor.
    pfmgr = new PathFormulaManagerImpl(fmgr, config, logger, notifier,
        MachineModel.LINUX32, AnalysisDirection.FORWARD);
    bfmgr = fmgr.getBooleanFormulaManager();
    ufmgr = factory.getFormulaManager().getUnsafeFormulaManager();
  }

  @Test public void blah() throws Exception {
    // todo: read in from the file instead.
    CFA cfa = toCFA(
        "int x = 5;",
        "int y = 10;",
        "int p;",
        "int nondet;",
        "int nondet2;",
        "if (nondet) {",  "y = 100;", "p = 1", "}",
        "} else {", "p = 2;", "}"
    );
    logger.log(Level.INFO, "CFA = ", cfa);
    PathFormula pf = toPathFormula(cfa);
    logger.log(Level.INFO, "PathFormula = ", pf);
    BooleanFormula bf = pf.getFormula();

    BooleanFormula negated = bfmgr.not(bf);
    BooleanFormula negatedNNF = bfmgr.applyTactic(negated, Tactic.NNF);

    logger.log(Level.INFO, "Negated in NNF: ", negatedNNF);
    logger.flush();
  }

  private PathFormula toPathFormula(CFA cfa) throws Exception {
    // todo: extract to some utility func.
    Map<CFANode, PathFormula> mapping = new HashMap<>(cfa.getAllNodes().size());
    CFANode start = cfa.getMainFunction();

    mapping.put(start, pfmgr.makeEmptyPathFormula());
    Deque<CFANode> queue = new LinkedList<>();
    queue.add(start);

    while (!queue.isEmpty()) {
      CFANode node = queue.removeLast();
      Preconditions.checkState(!node.isLoopStart(),
          "Can only work on loop-free fragments");
      PathFormula path = mapping.get(node);

      for (CFAEdge e : CFAUtils.leavingEdges(node)) {
        CFANode toNode = e.getSuccessor();
        PathFormula old = mapping.get(toNode);
        PathFormula n = pfmgr.makeAnd(path, e);
        PathFormula out;
        if (old == null) {
          out = n;
        } else {
          out = pfmgr.makeOr(old, n);
          out = out.updateFormula(fmgr.simplify(out.getFormula()));
        }
        mapping.put(toNode, out);
        queue.add(toNode);
      }
    }

    return mapping.get(cfa.getMainFunction().getExitNode());
  }

  private CFA toCFA(String... parts) throws Exception {
    return creator.parseFileAndCreateCFA(getProgram(parts));
  }

  private String getProgram(String... parts) {
    return "int main() {" +  Joiner.on('\n').join(parts) + "}";
  }
}