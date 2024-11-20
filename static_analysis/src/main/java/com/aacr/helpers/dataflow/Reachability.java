package com.aacr.helpers.dataflow;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.aacr.helpers.extension.ExtendedMethodReference;
import com.aacr.helpers.utils.FactoryUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Stack;

public class Reachability {

    public static HashSet<ExtendedMethodReference> detectReachableCallers(DefaultEntrypoint ep, CallGraph cg, AnalysisScope scope, CGNode startNode) {
        HashSet<ExtendedMethodReference> reachableMethods = new HashSet<>();
        HashSet<CGNode> visited = new HashSet<>();

        Stack<CGNode> analysisStack = new Stack<>();
        analysisStack.add(startNode);

        while(!analysisStack.isEmpty()) {
            CGNode nodeToAnalyze = analysisStack.pop();
            visited.add(nodeToAnalyze);
            addToReachableMethods(scope, nodeToAnalyze, startNode, reachableMethods);

            Iterator<CGNode> callerNodesIterator = cg.getPredNodes(nodeToAnalyze);
            while(callerNodesIterator.hasNext()) {
                CGNode addNode = callerNodesIterator.next();
                if(addNode != null && !visited.contains(addNode)) {
                    analysisStack.add(addNode);
                }
            }
        }
        return reachableMethods;
    }

    public static void addToReachableMethods(AnalysisScope scope, CGNode analyzedNode, CGNode startNode, HashSet<ExtendedMethodReference> reachableMethods) {
        //System.out.println("111: " + analyzedNode.getMethod().toString());
        //System.out.println("VS 222: " + startNode.getMethod().toString());
        //System.out.println("EQUALITY: " + analyzedNode.getMethod().toString().equals(startNode.getMethod().toString()));

        //if((!analyzedNode.getMethod().toString().equals(startNode.getMethod().toString())) && (scope.isApplicationLoader(analyzedNode.getMethod().getDeclaringClass().getClassLoader()))) {
        if(!analyzedNode.getMethod().toString().equals(startNode.getMethod().toString())) {
            reachableMethods.add(FactoryUtils.makeExtendedMethodReference(analyzedNode));
        }
    }

    public static ArrayList<BasicBlockInContext<ISSABasicBlock>> getPredNodes(BasicBlockInContext<ISSABasicBlock> startingBlock, IMethod calleeMethod, InterproceduralCFG icfg, ArrayList<BasicBlockInContext<ISSABasicBlock>> finalList, int depth) {
        Iterator<CGNode> roots = icfg.getCallGraph().getPredNodes(startingBlock.getNode());
        while(roots.hasNext()) {
            CGNode root = roots.next();
            boolean foundInstruction = false;
            if (root != null) {
                if (root.getMethod().getDeclaringClass().getClassLoader().getName().toString().toLowerCase().contains("primordial")) {
                    continue;
                }

                Iterator<SSAInstruction> rootInstructions = root.getIR().iterateAllInstructions();
                while (rootInstructions.hasNext()) {
                    SSAInstruction rootInstruction = rootInstructions.next();
                    if (rootInstruction != null && rootInstruction instanceof SSAAbstractInvokeInstruction
                            && ((SSAAbstractInvokeInstruction) rootInstruction).getCallSite().getDeclaredTarget().equals(calleeMethod.getReference())) {
                        finalList.add(new BasicBlockInContext<>(root, root.getIR().getBasicBlockForInstruction(rootInstruction)));
                        foundInstruction = true;
                        break;
                    }
                }
            }
            if(!foundInstruction){
                int newDepth = depth + 1;
                getPredNodes(new BasicBlockInContext<>(root, icfg.getCFG(root).getNode(0)), calleeMethod, icfg, finalList, newDepth);
            }
        }
        return finalList;
    }
}

