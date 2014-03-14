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
package org.sosy_lab.cpachecker.core.algorithm.testgen;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;

public class TestGenStatistics implements Statistics {

  private final Timer totalTimer = new Timer();
  private final Timer cpaAlogorithmTimer = new Timer();
  private final Timer pathCheckTimer = new Timer();
  private final Timer pathGenerationTimer = new Timer();


  private volatile int cpaAlgorithmCount = 0;
  private int countPathChecks = 0;
  private boolean printPathGenerationStats;


  public TestGenStatistics(boolean pPrintPathGenerationStats) {
    printPathGenerationStats = pPrintPathGenerationStats;

  }

  @Override
  public String getName() {
    return "TestGen algorithm";
  }

  @Override
  public void printStatistics(PrintStream out, Result pResult,
      ReachedSet pReached) {

    out.println("Number of CPA algorithm runs:         " + cpaAlgorithmCount);

    if (cpaAlgorithmCount > 0) {

      out.println("Number of PathChecker querys:         " + countPathChecks);
      //out.println("Max. size of reached set before ref.: " + maxReachedSizeBeforeRefinement);
      //out.println("Max. size of reached set after ref.:  " + maxReachedSizeAfterRefinement);
      //out.println("Avg. size of reached set before ref.: " + div(totalReachedSizeBeforeRefinement, countRefinements));
      //out.println("Avg. size of reached set after ref.:  " + div(totalReachedSizeAfterRefinement, countSuccessfulRefinements));
      out.println("");
      out.println("Average time CPA algorithm runs:      " + cpaAlogorithmTimer.getAvgTime().formatAs(TimeUnit.SECONDS));
      out.println("Max time for CPA algorithm runs:      " + cpaAlogorithmTimer.getMaxTime().formatAs(TimeUnit.SECONDS));
      out.println("Time for CPA Algorithm runs:          " + cpaAlogorithmTimer);
      out.println("Time for PathChecker querys:          " + pathCheckTimer);
      out.println("Total time for TestGen algorithm:     " + totalTimer);
      if (printPathGenerationStats) {
        out.println("Total time for path generation:       " + pathGenerationTimer);
        out.println("Avg.time for Path generation:         " + pathGenerationTimer.getAvgTime().formatAs(TimeUnit.SECONDS));
        out.println("Max. time for Path generation:        " + pathGenerationTimer.getMaxTime().formatAs(TimeUnit.SECONDS));
      }
    }
  }


  public void beforePathGeneration() {
    pathGenerationTimer.start();
  }

  public void afterPathGeneration() {
    pathGenerationTimer.stop();
  }

  public void beforePathCheck() {
    countPathChecks++;
    pathCheckTimer.start();

  }

  public void afterPathCheck() {
    pathCheckTimer.stop();

  }

  public void beforeCpaAlgortihm() {
    cpaAlgorithmCount++;
    cpaAlogorithmTimer.start();
  }


  public void afterCpaAlgortihm() {
    cpaAlogorithmTimer.stop();
  }

  Timer getTotalTimer() {
    return totalTimer;
  }
}