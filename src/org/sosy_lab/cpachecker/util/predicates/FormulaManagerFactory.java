/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.predicates;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.InterpolatingProverEnvironment;
import org.sosy_lab.cpachecker.util.predicates.interfaces.ProverEnvironment;
import org.sosy_lab.cpachecker.util.predicates.mathsat5.Mathsat5FormulaManager;
import org.sosy_lab.cpachecker.util.predicates.mathsat5.Mathsat5InterpolatingProver;
import org.sosy_lab.cpachecker.util.predicates.mathsat5.Mathsat5TheoremProver;
import org.sosy_lab.cpachecker.util.predicates.smtInterpol.SmtInterpolFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.smtInterpol.SmtInterpolInterpolatingProver;
import org.sosy_lab.cpachecker.util.predicates.smtInterpol.SmtInterpolTheoremProver;
import org.sosy_lab.cpachecker.util.predicates.z3.Z3FormulaManager;
import org.sosy_lab.cpachecker.util.predicates.z3.Z3TheoremProver;

@Options(prefix="cpa.predicate")
public class FormulaManagerFactory {

  private static final String MATHSAT5 = "MATHSAT5";
  private static final String SMTINTERPOL = "SMTINTERPOL";
  private static final String Z3 = "Z3";

  @Option(name="solver.useIntegers",
      description="Encode program variables as INTEGER variables, instead of "
      + "using REALs. Not all solvers might support this.")
  private boolean useIntegers = false;

  @Option(values={MATHSAT5, SMTINTERPOL, Z3}, toUppercase=true,
      description="Whether to use MathSAT 5, SmtInterpol or Z3 as SMT solver")
  private String solver = MATHSAT5;

  private final FormulaManager fmgr;

  public FormulaManagerFactory(Configuration config, LogManager logger) throws InvalidConfigurationException {
    config.inject(this);

    FormulaManager lFmgr;
    switch (solver) {
    case SMTINTERPOL:
      lFmgr = SmtInterpolFormulaManager.create(config, logger, useIntegers);
      break;

    case MATHSAT5:
      try {
        assert solver.equals(MATHSAT5);

        lFmgr = Mathsat5FormulaManager.create(logger, config);
        if (useIntegers) { throw new InvalidConfigurationException(
            "Using integers for program variables is currently not implementted when MathSAT is used."); }

      } catch (UnsatisfiedLinkError e) {
        throw new InvalidConfigurationException("The SMT solver " + solver
            + " is not available on this machine."
            + " You may experiment with SMTInterpol by setting cpa.predicate.solver=SMTInterpol.", e);
      }
      break;

    case Z3:
      lFmgr = Z3FormulaManager.create(logger, config, useIntegers);
      break;

    default:
      throw new AssertionError("no solver selected");
    }

    fmgr = lFmgr;
  }

  public FormulaManager getFormulaManager() {
    return fmgr;
  }

  public ProverEnvironment newProverEnvironment(boolean generateModels) {
    switch(solver){
    case SMTINTERPOL:
      return new SmtInterpolTheoremProver((SmtInterpolFormulaManager)fmgr);
    case MATHSAT5:
      return new Mathsat5TheoremProver((Mathsat5FormulaManager)fmgr, generateModels);
    case Z3:
      return new Z3TheoremProver((Z3FormulaManager)fmgr);
    default:
      throw new AssertionError("no solver selected");
    }
  }

  public InterpolatingProverEnvironment<?> newProverEnvironmentWithInterpolation(boolean shared) {
    if (solver.equals(SMTINTERPOL)) {
      return new SmtInterpolInterpolatingProver((SmtInterpolFormulaManager) fmgr);
    } else {
      assert solver.equals(MATHSAT5);
      return new Mathsat5InterpolatingProver((Mathsat5FormulaManager) fmgr, shared);
    }
  }
}
