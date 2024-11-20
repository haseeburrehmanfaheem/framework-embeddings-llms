package com.aacr.helpers.dataflow;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ssa.*;
import com.ibm.wala.util.collections.Pair;
import com.aacr.helpers.utils.InstructionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

/**
 * This class provides inter-procedural def-use chain analysis functionality.
 */
public class DefUseChain {
    /**
     * This method is the entrypoint to the def-use chain analysis. A value number corresponding to a parameter is passed in.
     * It is then normalized based on whether its enclosing method is static or special.
     * @see com.aacr.helpers.utils.InstructionUtils#findParameterIndex(IMethod, int, SymbolTable).
     * Finally, we go up the call chain to find the definition.
     * @param  valueNumber Non-normalized value number corresponding to a parameter.
     * @param startingBlock Basic Block containing the instruction from which the valueNumber was retrieved.
     * @param icfg The interprocedural control flow graph containing the startingBlock.
     * @param returnList Empty list which will be populated with any discovered definition for valueNumber.
     * @return ArrayList holding all discovered definitions (permission strings, constants, or instructions).
     * @throws Exception
     */
    public static ArrayList<Object> chain(int valueNumber, BasicBlockInContext<ISSABasicBlock> startingBlock, InterproceduralCFG icfg, ArrayList<Object> returnList) throws Exception {
        return getParameterDef(InstructionUtils.findParameterIndex(startingBlock.getMethod(), valueNumber, startingBlock.getNode().getIR().getSymbolTable()), startingBlock, icfg, returnList, 0, false);
    }

    public static ArrayList<Object> chainWithNodes(int valueNumber, BasicBlockInContext<ISSABasicBlock> startingBlock, InterproceduralCFG icfg, ArrayList<Object> returnList) throws Exception {
        return getParameterDef(InstructionUtils.findParameterIndex(startingBlock.getMethod(), valueNumber, startingBlock.getNode().getIR().getSymbolTable()), startingBlock, icfg, returnList, 0, true);
    }

    /**
     * This recursive method controls the flow of the def-use chain analysis.
     * A value number representing a formal parameter is passed in. All callers are examined to find
     * an invocation instruction corresponding to the method in which the formal parameter is defined.
     * Then the value number of the actual parameter used in the invocation instruction is retrieved. From there,
     * getParameterDef(..) determines if the new value number corresponds to a definition or another parameter.
     * If the latter, then getParameterDef(..) is called again with a new value number and starting block.
     * @param valueNumber Normalized value number.
     * @param startingBlock Basic Block containing the instruction from which the valueNumber was retrieved.
     * @param icfg The interprocedural control flow graph containing the startingBlock.
     * @param returnList List which will be populated with any discovered definition for valueNumber.
     * @param depth Tracks recursion depth.
     * @return ArrayList holding all discovered definitions (permission strings, constants, or instructions).
     */
    public static ArrayList<Object>  getParameterDef(int valueNumber, BasicBlockInContext<ISSABasicBlock> startingBlock, InterproceduralCFG icfg, ArrayList<Object> returnList, int depth, boolean shouldIncludeNodes) {

        int normalizedValueNumber = valueNumber;

        /**
         * Determine all possible calling nodes. Note that if there are no calling nodes
         * and a definition has not yet been found, an empty or null list can be returned.
         */
        ArrayList<BasicBlockInContext<ISSABasicBlock>> callingBlocks = getPredNodes(startingBlock, startingBlock.getMethod(), icfg, new HashSet<CGNode>(), new ArrayList<BasicBlockInContext<ISSABasicBlock>>(), 0);
        for (BasicBlockInContext<ISSABasicBlock> callingBlock : callingBlocks) {
            SymbolTable st = callingBlock.getNode().getIR().getSymbolTable();
            ISSABasicBlock ibb = callingBlock.getDelegate();
            Iterator<SSAInstruction> ibbInstructions = ibb.iterator();

            /**
             * For each calling block, we attempt to find an invoke instruction corresponding to the startingBlock.
             */
            SSAAbstractInvokeInstruction invokeInstruction = null;
            while (ibbInstructions.hasNext()) {
                SSAInstruction ibbCurrentInstruction = ibbInstructions.next();
                if (ibbCurrentInstruction instanceof SSAAbstractInvokeInstruction) {
                    invokeInstruction = (SSAAbstractInvokeInstruction) ibbCurrentInstruction;
                    break;
                }
            }

            /**
             * If we find an invoke instruction, then we can find all associated value numbers.
             * These will either point us to the definition or to another parameter, meaning
             * that we have to go up the call chain once more.
             */
            if (invokeInstruction != null) {
                int useNumber = InstructionUtils.findParameterUseNumber(invokeInstruction, valueNumber);
                if (useNumber == -1 || invokeInstruction.getNumberOfUses() <= useNumber) {
                    valueNumber = useNumber;
                    continue;
                }
                int newValueNumber = invokeInstruction.getUse(useNumber);
                ArrayList<Integer> newValueNumbers = new ArrayList<>();


                /**
                 * If the new value number points to a Phi Instruction,
                 * we need to extract all possible associated value numbers.
                 */
                DefUse du = callingBlock.getNode().getDU();
                SSAInstruction possiblePhiInstruction = du.getDef(newValueNumber);
                if(possiblePhiInstruction instanceof SSAPhiInstruction) {
                    newValueNumbers.addAll(getValueNumbersFromPhiInstruction((SSAPhiInstruction) possiblePhiInstruction, du));
                } else {
                    newValueNumbers.add(newValueNumber);
                }

                /**
                 * For each new value number, we test to see if we can retrieve a definition.
                 * If so, the definition is added to returnList. Otherwise, we call
                 * getParameterDef(..) again, using the new value number and the new starting block.
                 */
                for(Integer testValueNumber : newValueNumbers) {
                    boolean done = addAvailableDefinition(callingBlock.getNode(), returnList, testValueNumber, st, du, shouldIncludeNodes);
                    if(!done && depth < 20) {
                        getParameterDef(InstructionUtils.findParameterIndex(callingBlock.getMethod(), testValueNumber, callingBlock.getNode().getIR().getSymbolTable()), callingBlock, icfg, returnList, depth+1, shouldIncludeNodes);
                    }
                }
            }
        }
        return returnList;
    }

    /**
     * Recursive method that finds all caller blocks that invoke the starting block.
     * After retrieving all caller blocks, it skips any previously visited blocks or blocks corresponding to primordial methods.
     * For remaining caller blocks, the method analyzes their instructions to find invocation instructions
     * targeting the starting block. If the proper invocation instruction is found, then the block is one of those returned.
     * If no invoking caller was found, getPredNodes(..) calls itself to find an invoking caller higher up in the call chain.
     * @param startingBlock Starting basic block.
     * @param calleeMethod Starting basic block's enclosing IMethod.
     * @param icfg The interprocedural control flow graph containing the startingBlock.
     * @param visited Tracks already-visited blocks.
     * @param finalList List which will be populated with any caller blocks that invoke the starting block.
     * @param depth Tracks recursion depth.
     * @return Arraylist of caller blocks that invoke the starting block.
     */
    public static ArrayList<BasicBlockInContext<ISSABasicBlock>> getPredNodes(BasicBlockInContext<ISSABasicBlock> startingBlock, IMethod calleeMethod, InterproceduralCFG icfg, HashSet<CGNode> visited, ArrayList<BasicBlockInContext<ISSABasicBlock>> finalList, int depth) {
        Iterator<CGNode> roots = icfg.getCallGraph().getPredNodes(startingBlock.getNode());
        while(roots.hasNext()) {
            CGNode root = roots.next();
            boolean foundInstruction = false;
            if (root != null) {
                if (visited.contains(root) || root.getMethod().getDeclaringClass().getClassLoader().getName().toString().toLowerCase().contains("primordial")) {
                    continue;
                }

                Iterator<SSAInstruction> rootInstructions = root.getIR().iterateAllInstructions();
                while (rootInstructions.hasNext()) {
                    SSAInstruction rootInstruction = rootInstructions.next();
                    if (rootInstruction != null && rootInstruction instanceof SSAAbstractInvokeInstruction && ((SSAAbstractInvokeInstruction) rootInstruction).getCallSite().getDeclaredTarget().equals(calleeMethod.getReference())) {
                        finalList.add(new BasicBlockInContext<>(root, root.getIR().getBasicBlockForInstruction(rootInstruction)));
                        foundInstruction = true;
                        break;
                    }
                }
            }
            HashSet<CGNode> newVisited = new HashSet(visited);
            newVisited.add(root);
            if(!foundInstruction){
                int newDepth = depth + 1;
                getPredNodes(new BasicBlockInContext<>(root, icfg.getCFG(root).getNode(0)), calleeMethod, icfg, newVisited, finalList, newDepth);
            }
        }
        return finalList;
    }

    /**
     * Finds all value numbers associated with an SSAPhiInstruction.
     * @param phiInstruction SSAPhiInstruction of interest.
     * @param du DefUse object for the node containing phiInstruction.
     * @return ArrayList of all value numbers associated with the SSAPhiInstruction.
     */
    public static ArrayList<Integer> getValueNumbersFromPhiInstruction(SSAPhiInstruction phiInstruction, DefUse du) {
        ArrayList<Integer> newValueNumbers = new ArrayList<>();

        for(int i = 0; i < phiInstruction.getNumberOfUses(); i++) {
            SSAInstruction nextInstr = du.getDef(((SSAPhiInstruction) phiInstruction).getUse(i));
            if(!(nextInstr instanceof SSAPhiInstruction)) {
                newValueNumbers.add(phiInstruction.getUse(i));
            }
        }
        return newValueNumbers;
    }

    /**
     * Takes in a value number. If it is determined to have an instruction or constant definition, then the
     * definition is added to the inputted returnList.
     *
     * @param node
     * @param returnList         List to which a definition will be added, if found.
     * @param valueNumber        Value number.
     * @param st                 SymbolTable object for the node from which valueNumber was retrieved.
     * @param du                 DefUse object for the node from which valueNumber was retrieved.
     * @param shouldIncludeNodes
     * @return Boolean representing whether a definition was found.
     */
    public static boolean addAvailableDefinition(CGNode node, ArrayList<Object> returnList, int valueNumber, SymbolTable st, DefUse du, boolean shouldIncludeNodes) {
        boolean notParam = !st.isParameter(valueNumber);
        if (notParam) {
            if(st.isConstant(valueNumber)) {
                returnList.add(st.getConstantValue(valueNumber));
            } else {
                SSAInstruction def = du.getDef(valueNumber);
                if(def != null) {
                    if (shouldIncludeNodes)
                        returnList.add(Pair.make(node, def));
                    else
                        returnList.add(def);
                }
            }
        }
        return notParam;
    }
}

