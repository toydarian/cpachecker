/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.exceptions;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sosy_lab.cpachecker.cfa.ast.IASTFileLocation;
import org.sosy_lab.cpachecker.cfa.ast.IASTNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;

/**
 * Exception thrown when a CPA cannot handle some C code attached to a CFAEdge.
 */
public class UnrecognizedCCodeException extends CPATransferException {

  private static final long serialVersionUID = -8319167530363457020L;

  protected UnrecognizedCCodeException(String msg1, String msg2, IASTFileLocation loc, String rawSignature, CFAEdge pEdge) {
    super(msg1
        + (msg2 != null ? " (" + msg2 + ") " : " ")
        + "in line " + loc.getStartingLineNumber()
        + ": " + rawSignature);
  }

  public UnrecognizedCCodeException(String msg, CFAEdge edge, IASTNode astNode) {
    this("Unrecognized C code", msg, astNode.getFileLocation(), astNode.getRawSignature(), edge);
  }

  public UnrecognizedCCodeException(CFAEdge edge, IASTNode astNode) {
    this(null, edge, astNode);
  }

  public UnrecognizedCCodeException(String msg, CFAEdge edge) {
    this(msg, edge, edge.getRawAST());
  }

  /**
   * Deprecated because this exception should always contain the relevant edge.
   */
  @Deprecated
  public UnrecognizedCCodeException(String msg, IASTNode astNode) {
    this(msg, null, astNode);
  }

  /**
   * Create an UnrecognizedCCodeException only with a message.
   * Deprecated because such an exception should always contain the relevant source code.
   */
  @Deprecated
  public UnrecognizedCCodeException(String msg) {
    super("Unrecognized C code (" + checkNotNull(msg) + ")");
  }
}
