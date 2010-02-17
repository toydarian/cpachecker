/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker. 
 *
 *  Copyright (C) 2007-2008  Dirk Beyer and Erkan Keremoglu.
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
 *    http://www.cs.sfu.ca/~dbeyer/CPAchecker/
 */
package cfa;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import org.eclipse.cdt.core.dom.ast.IASTBinaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTExpressionList;
import org.eclipse.cdt.core.dom.ast.IASTFunctionCallExpression;

import cfa.objectmodel.CFAEdge;
import cfa.objectmodel.CFAFunctionDefinitionNode;
import cfa.objectmodel.CFANode;
import cfa.objectmodel.c.CallToReturnEdge;
import cfa.objectmodel.c.FunctionCallEdge;
import cfa.objectmodel.c.ReturnEdge;
import cfa.objectmodel.c.StatementEdge;

/**
 * Used for post processing the CFA. This class provides methods which are used
 * to modify the CFAs for each function according to user's needs. For example
 * to handle function calls, call, return and summary edges can be inserted to
 * the CFA using methods in this class.
 * @author erkan
 *
 */
public class CPASecondPassBuilder {

  private final Map<String, CFAFunctionDefinitionNode> cfas;
  private final boolean createCallEdgesForExternalCalls;

  /**
   * Class constructor.
   * @param map List of all CFA's in the program.
   */
  public CPASecondPassBuilder(Map<String, CFAFunctionDefinitionNode> cfas,
      boolean noCallEdgesForExternalCalls) {
    this.cfas = cfas;
    createCallEdgesForExternalCalls = !noCallEdgesForExternalCalls;
  }


  /**
   * Traverses a CFA with the specified function name and insert call edges
   * and return edges from the call site and to the return site of the function
   * call. Each node carries information about the function they are in and
   * this information is also set by this method, see {@link CFANode#setFunctionName(String)}.
   * @param funcName name of the function to be processed.
   */
  public void insertCallEdges(String funcName){
    // we use a worklist algorithm
    Deque<CFANode> workList = new ArrayDeque<CFANode> ();
    Deque<CFANode> processedList = new ArrayDeque<CFANode> ();

    CFANode initialNode = cfas.get(funcName);

    workList.addLast (initialNode);
    processedList.addLast (initialNode);

    while (!workList.isEmpty ())
    {
      CFANode node = workList.pollFirst ();
      int numLeavingEdges = node.getNumLeavingEdges ();

      for (int edgeIdx = 0; edgeIdx < numLeavingEdges; edgeIdx++)
      {
        CFAEdge edge = node.getLeavingEdge (edgeIdx);
        CFANode successorNode = edge.getSuccessor();
        if(edge instanceof StatementEdge)
        {
          IASTExpression expr = ((StatementEdge)edge).getExpression();

          // if expression is a binary expression
          if(expr instanceof IASTBinaryExpression){
            IASTExpression operand2 = ((IASTBinaryExpression)expr).getOperand2();
            // if statement is of the form x = call(a,b);
            if(operand2 instanceof IASTFunctionCallExpression &&
                shouldCreateCallEdges((IASTFunctionCallExpression)operand2)){
              createCallAndReturnEdges(node, successorNode, edge, expr, (IASTFunctionCallExpression)operand2);
            }
            // if this is not a function call just set the function name
            else{
              successorNode.setFunctionName(node.getFunctionName());
            }
          }

          // if expression is function call, e.g. call(a,b);
          else if(expr instanceof IASTFunctionCallExpression &&
              shouldCreateCallEdges((IASTFunctionCallExpression)expr)){
            IASTFunctionCallExpression functionCall = (IASTFunctionCallExpression)expr;
            createCallAndReturnEdges(node, successorNode, edge, expr, functionCall);
          }

          else{
            successorNode.setFunctionName(node.getFunctionName());
          }
        }

        else if(!((edge instanceof FunctionCallEdge) ||
            (edge instanceof ReturnEdge))){
          successorNode.setFunctionName(node.getFunctionName());
        }

        // if the node is not already processed and if successor node is not
        // on a different CFA, add successor node to the worklist
        if(!processedList.contains(successorNode) &&
            node.getFunctionName().equals(successorNode.getFunctionName())){
          workList.add(successorNode);
        }
      }
      // node is processed
      processedList.add(node);
    }
  }

  private boolean shouldCreateCallEdges(IASTFunctionCallExpression f) {
    if (createCallEdgesForExternalCalls) return true;
    String name = f.getFunctionNameExpression().getRawSignature();
    CFAFunctionDefinitionNode fDefNode = cfas.get(name);
    return fDefNode != null;
  }

  /**
   * inserts call, return and summary edges from a node to its successor node.
   * @param node The node which is the call site.
   * @param successorNode The first node of the called function.
   * @param edge The function call edge.
   * @param expr The function call expression.
   * @param functionCall If the call was an assignment from the function call
   * this keeps only the function call expression, e.g. if statement is a = call(b);
   * then expr is a = call(b); and functionCall is call(b).
   */
  private void createCallAndReturnEdges(CFANode node, CFANode successorNode, CFAEdge edge, IASTExpression expr, IASTFunctionCallExpression functionCall) {
    String functionName = functionCall.getFunctionNameExpression().getRawSignature();
    CFAFunctionDefinitionNode fDefNode = cfas.get(functionName);

    IASTExpression parameters = functionCall.getParameterExpression();
    FunctionCallEdge callEdge = new FunctionCallEdge(functionCall.getRawSignature(), parameters);

    // if the successor node is null, then this is an external call
    if(fDefNode == null){
      assert(createCallEdgesForExternalCalls); // AG

      callEdge.setExternalCall();
      callEdge.initialize (node, edge.getSuccessor());
      callEdge.getSuccessor().setFunctionName(node.getFunctionName());
      CallToReturnEdge calltoReturnEdge = new CallToReturnEdge("External Call", expr);
      calltoReturnEdge.initializeSummaryEdge(node, edge.getSuccessor());
      node.removeLeavingEdge(edge);
      successorNode.removeEnteringEdge(edge);
      return;
    }

    callEdge.initialize (node, fDefNode);
    // set name of the function
    fDefNode.setFunctionName(functionName);
    // set return edge from exit node of the function
    ReturnEdge returnEdge = new ReturnEdge("Return Edge to " + successorNode.getNodeNumber());
    returnEdge.initialize(cfas.get(functionName).getExitNode(), successorNode);
    returnEdge.getSuccessor().setFunctionName(node.getFunctionName());

    CallToReturnEdge calltoReturnEdge = new CallToReturnEdge(expr.getRawSignature(), expr);
    calltoReturnEdge.initializeSummaryEdge(node, successorNode);

    node.removeLeavingEdge(edge);
    successorNode.removeEnteringEdge(edge);

    // set function parameters
//  if(parameters instanceof IASTIdExpression){
//  IASTIdExpression variableParam = (IASTIdExpression)parameters;
//  IASTExpression[] expressionList = new IASTExpression[1];
//  expressionList[0] = variableParam;
//  callEdge.setArguments(expressionList);
//  }
//  else if(parameters instanceof IASTExpressionList){
//  IASTExpressionList paramList = (IASTExpressionList)parameters;
//  IASTExpression[] expressionList = paramList.getExpressions();
//  callEdge.setArguments(expressionList);
//  }
    // AG - the above is not exhaustive, there are some cases that
    // are not handled (e.g. f(5))
    if (parameters instanceof IASTExpressionList) {
      IASTExpressionList paramList = (IASTExpressionList)parameters;
      IASTExpression[] expressionList = paramList.getExpressions();
      callEdge.setArguments(expressionList);
    } else if (parameters != null) {
      callEdge.setArguments(new IASTExpression[]{parameters});
    }
  }
}
