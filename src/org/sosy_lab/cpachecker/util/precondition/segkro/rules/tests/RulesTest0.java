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
package org.sosy_lab.cpachecker.util.precondition.segkro.rules.tests;

import org.junit.Before;
import org.junit.Test;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.util.predicates.FormulaManagerFactory.Solvers;
import org.sosy_lab.cpachecker.util.predicates.Solver;
import org.sosy_lab.cpachecker.util.predicates.interfaces.ArrayFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType.NumeralType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.IntegerFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.z3.matching.Z3AstMatcher;
import org.sosy_lab.cpachecker.util.test.SolverBasedTest0;


public class RulesTest0 extends SolverBasedTest0 {

  @Override
  protected Solvers solverToUse() {
    return Solvers.Z3;
  }

  private Solver solver;
  private FormulaManagerView mgrv;
  private Z3AstMatcher matcher;

  @Before
  public void setup() throws InvalidConfigurationException {
    mgrv = new FormulaManagerView(mgr, config, logger);
    matcher = new Z3AstMatcher(logger, mgr, mgrv);
    solver = new Solver(mgrv, factory);
  }

  @Test
  public void testSubstitution1() {
    IntegerFormula _x = mgrv.makeVariable(NumeralType.IntegerType, "x");
    IntegerFormula _e = mgrv.makeVariable(NumeralType.IntegerType, "e");
    IntegerFormula _0 = imgr.makeNumber(0);

    // Formulas for the premise
    BooleanFormula _x_EQ_e = imgr.equal(_x, _e);
    ArrayFormula<IntegerFormula, IntegerFormula> _a = amgr.makeArray("a", NumeralType.IntegerType, NumeralType.IntegerType);
    ArrayFormula<IntegerFormula, IntegerFormula> _a_store_0_at_x = amgr.store(_a, _x, _0);

    // The conclusion
    BooleanFormula _a_at_e_EQ_0 = imgr.equal(amgr.select(_a, _e), _0);

    // Check

  }

}