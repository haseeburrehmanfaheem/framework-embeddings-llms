package com.aacr.helpers.utils;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.TypeReference;
import com.aacr.helpers.custom.ConditionalInfo;
import com.aacr.helpers.dataflow.DefUseChain;

import java.util.*;

/**
 * This class contains utility methods for working with SSAInstructions.
 */
public class InstructionUtils {

    /**
     * Adjusts parameter index based on whether the method is static.
     * Note: getParameterType() already normalizes the parameter index. So this method should
     * not be used before calling getParameterType().
     * @param i SSAAbstractInvokeInstruction.
     * @param paramNumber Parameter index (starting at 1).
     * @return Normalized Parameter index.
     */
    public static int getParameterIndex(SSAAbstractInvokeInstruction i, int paramNumber) {
        if(!i.isStatic()) {
            paramNumber++;
        }
        return paramNumber;
    }

    /**
     * Returns definition corresponding to a use number.
     * @param useNumber Use number.
     * @param icfg Interprocedural control flow graph.
     * @param bb Basic block of instruction from which the useNumber was retrieved.
     * @param st SymbolTable.
     * @param du DefUse object.
     * @return ArrayList of possible definitions. These can include permission strings, constants, and instructions.
     * @throws Exception
     */
    public static ArrayList<Object> getDefinition(int useNumber, InterproceduralCFG icfg, BasicBlockInContext<ISSABasicBlock> bb, SymbolTable st, DefUse du) throws Exception {
        ArrayList returnList = new ArrayList();
        if (st.isParameter(useNumber)) {
            try {
                returnList = DefUseChain.chain(useNumber, bb, icfg, new ArrayList<Object>());
            } catch (Exception e) {
                //@ToDO: FIX THIS...
                //returnList = DefUseChain.chain(useNumber, bb, icfg, new ArrayList<Object>());
            }

            Set<Object> returnSet = new HashSet<>(returnList);
            returnList.clear();
            returnList.addAll(returnSet);
            return returnList;
        } else if (st.isConstant(useNumber)) {
            returnList.add(st.getConstantValue(useNumber));
            return returnList;
        }
        returnList.add(du.getDef(useNumber));
        return returnList;
    }

    /**
     * Gets definition for a return statement.
     * @param i SSAReturnInstruction.
     * @param icfg Interprocedural control flow graph.
     * @param bb Basic block of instruction from which the useNumber was retrieved.
     * @param st SymbolTable.
     * @param du DefUse object.
     * @return ArrayList of possible definitions. These can include constants, permission strings, and instructions.
     * @throws Exception
     */
    public static ArrayList<Object> getReturnDefinition(SSAReturnInstruction i, InterproceduralCFG icfg, BasicBlockInContext<ISSABasicBlock> bb, SymbolTable st, DefUse du) throws Exception {
        int valueNumber = i.getUse(0);
        if(valueNumber == -1) {
            return null;
        } else {
            return getDefinition(valueNumber, icfg, bb, st, du);
        }
    }

    /**
     * Get inner instructions from an SSAPhiInstruction.
     * @param i SSAPhiInstruction.
     * @param du DefUse object.
     * @return ArrayList of SSAInstructions from the SSAPhiInstruction.
     */
    public static ArrayList<Object> getInnerInstructionsFromPhi(SSAPhiInstruction i, DefUse du) {
        ArrayList<Object> innerInstructions = new ArrayList<>();
        for(int n = 0; n < i.getNumberOfUses(); n++) {
            try {
                Object phiInstr = du.getDef(i.getUse(n));
                if(phiInstr != null) {
                    innerInstructions.add(phiInstr);
                }
            } catch (Exception e) {
                //
            }
        }
        return innerInstructions;
    }

    /**
     * Get inner definitions (instructions or constants) from an SSAPhiInstruction.
     * @param i SSAPhiInstruction.
     * @param du DefUse object.
     * @param st SymboleTable object.
     * @return ArrayList of SSAInstructions from the SSAPhiInstruction.
     */
    public static ArrayList<Object> getInnerDefinitionsFromPhi(InterproceduralCFG icfg, BasicBlockInContext<ISSABasicBlock> bb, SSAPhiInstruction i, DefUse du, SymbolTable st) {
        ArrayList<Object> innerDefinitions = new ArrayList<>();
        for(int n = 0; n < i.getNumberOfUses(); n++) {
            try {
                InstructionUtils.getDefinition(i.getUse(n),icfg, bb, st, du);
                Object phiInstr = du.getDef(i.getUse(n));
                if(phiInstr != null) {
                    innerDefinitions.add(phiInstr);
                }
            } catch (Exception e) {
                //
            }
        }
        return innerDefinitions;
    }

    /**
     * Get number corresponding to basic block enclosing the inputted SSAInstruction.
     * @param icfg Interprocedural control flow graph.
     * @param i SSAInstruction.
     * @return Integer corresponding to basic block.
     */
    public static int getBBNumberForInstruction(InterproceduralCFG icfg, SSAInstruction i) {
        for(BasicBlockInContext<ISSABasicBlock> bb: icfg) {
            ISSABasicBlock ebb = bb.getDelegate();

            Iterator<SSAInstruction> instructions = ebb.iterator();
            while (instructions.hasNext()) {
                SSAInstruction currentInstruction = instructions.next();
                if (currentInstruction.equals(i)) {
                    return bb.getGraphNodeId();
                }
            }
        }
        return -1;
    }

    public static BasicBlockInContext<ISSABasicBlock> getBBForInstruction(InterproceduralCFG icfg, SSAInstruction i) {
        for(BasicBlockInContext<ISSABasicBlock> bb: icfg) {
            ISSABasicBlock ebb = bb.getDelegate();

            Iterator<SSAInstruction> instructions = ebb.iterator();
            while (instructions.hasNext()) {
                SSAInstruction currentInstruction = instructions.next();
                if (currentInstruction.equals(i)) {
                    return bb;
                }
            }
        }
        return null;
    }

    /**
     * Get index corresponding to the inputted SSAInstruction.
     * @param instruction SSAInstruction.
     * @param nd CGNode.
     * @return Index corresponding to SSAInstruction.
     */
    //TODO: 1/21/22 Verify this functionality.
    public static int getIndexForInstruction(SSAInstruction instruction, CGNode nd) {
        Iterator<SSAInstruction> it = nd.getIR().iterateAllInstructions();
        int index = 0;
        while (it.hasNext()) {
            SSAInstruction s = it.next();
            if (s.equals(instruction)) {
                return index;
            }
            index++;
        }
        return index;
    }

    /**
     * Get SSAInstruction corresponding to the inputted index.
     * @param instrIterator SSAInstruction iterator.
     * @param index Index.
     * @return SSAInstruction corresponding to the inputted index.
     */
    //TODO: 1/21/22 Verify this functionality.
    public static SSAInstruction mapIndexToInstruction(Iterator<SSAInstruction> instrIterator, int index) {
        int currIndex = 0;
        SSAInstruction returnInstruction = null;

        while(instrIterator.hasNext() && currIndex <= index) {
            returnInstruction = instrIterator.next();
            currIndex++;
        }
        return returnInstruction;
    }

    /**
     * Get typeReference for SSAInstruction. The process differs based on instruction type.
     * @param i SSAInstruction.
     * @param du DefUse.
     * @return List of TypeReferences corresponding to the inputted SSAInstruction.
     */
    public static ArrayList<TypeReference> getTargetTypeReference(SSAInstruction i, DefUse du) {
        ArrayList<TypeReference> typeRefs = new ArrayList<>();

        if(i instanceof SSAAbstractInvokeInstruction) {
             typeRefs.add(((SSAAbstractInvokeInstruction) i).getCallSite().getDeclaredTarget().getDeclaringClass());
        } else if(i instanceof SSANewInstruction) {
            typeRefs.add(((SSANewInstruction) i).getConcreteType());
        } else if(i instanceof SSAGetInstruction) {
            typeRefs.add(((SSAGetInstruction) i).getDeclaredFieldType());
        } else if(i instanceof SSACheckCastInstruction) {
            SSAInstruction inner = du.getDef(i.getUse(0));
            typeRefs.addAll(getTargetTypeReference(inner, du));
        } else if(i instanceof SSAPhiInstruction) {
            ArrayList<Object> innerInstructions = getInnerInstructionsFromPhi(((SSAPhiInstruction) i), du);
            for(Object innerInstruction : innerInstructions) {
                if(innerInstruction instanceof SSAInstruction && !(innerInstruction instanceof SSAPhiInstruction)) {
                    typeRefs.addAll(getTargetTypeReference(((SSAInstruction) innerInstruction), du));
                }
            }
        }
        return typeRefs;
    }

    /**
     * Returns a boolean value representing whether an instruction's target type reference is a primordial type.
     * This is necessary because even types such as Ljava/lang/String might be considered to fall under Application, rather than Primordial
     * when examining target type references.
     * @param i SSAInstruction of interest.
     * @param du DefUse for the node containing the SSAInstruction.
     * @return Boolean value denoting whether the target type is primordial.
     */
    public static boolean targetTypeReferenceIsPrimordial(SSAInstruction i, DefUse du) {
        ArrayList<TypeReference> typeRefs = getTargetTypeReference(i, du);
        for(TypeReference typeRef : typeRefs) {
            if(typeRef.toString().contains("Ljava")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the instructions corresponding to the two conditional predicates (left-hand side and right-hand side).
     * @param icfg Interprocedural control flow graph.
     * @param bb Basic block.
     * @param st Symbol table.
     * @param du DefUse object.
     * @param i SSAConditionalBranchInstruction.
     * @return List of lists, with each subList representing the possible definitions for one of the two conditional predicates.
     * @throws Exception
     */
    public static ArrayList<ArrayList<Object>> getInnerInstructionsFromConditional(InterproceduralCFG icfg, BasicBlockInContext<ISSABasicBlock> bb, SymbolTable st, DefUse du, SSAConditionalBranchInstruction i)
        throws Exception {
        ArrayList<ArrayList<Object>> conditionalDefinitionsList = new ArrayList<>();
        int firstPredicateValueNumber = i.getUse(0);
        int secondPredicateValueNumber = i.getUse(1);
        conditionalDefinitionsList.add(getDefinition(firstPredicateValueNumber, icfg, bb, st, du));
        conditionalDefinitionsList.add(getDefinition(secondPredicateValueNumber, icfg, bb, st, du));
        return conditionalDefinitionsList;
    }

    /**
     * Return a representation of the conditional branch instruction with
     * a.) a list of definitions for the first predicate,
     * b.) operator information,
     * and c.) a list of definitions for the second predicate.
     * @param icfg Interprocedural control flow graph.
     * @param bb Basic block.
     * @param st Symbol table.
     * @param du DefUse object.
     * @param i SSAConditionalBranchInstruction.
     * @return Information on predicates and operator within the SSAConditionalBranchInstruction.
     * @throws Exception
     */
    public static ConditionalInfo getConditionalInformation(InterproceduralCFG icfg, BasicBlockInContext<ISSABasicBlock> bb, SymbolTable st, DefUse du, SSAConditionalBranchInstruction i)
        throws Exception {
        int firstPredicateValueNumber = i.getUse(0);
        int secondPredicateValueNumber = i.getUse(1);
        ConditionalInfo conditionalInfo = new ConditionalInfo(getDefinition(firstPredicateValueNumber, icfg, bb, st, du),
            i.getOperator(),
            getDefinition(secondPredicateValueNumber, icfg, bb, st, du));
        return conditionalInfo;
    }

    /**
     * Return textual representation of the SSAInstruction type.
     * @param i SSAInstruction.
     * @return String representing the SSAInstruction type.
     */
    public static String getInstructionType(SSAInstruction i) {
        if (i instanceof SSAAbstractInvokeInstruction) {
            return SSAAbstractInvokeInstruction.class.getName();

        } else if (i instanceof SSAAbstractThrowInstruction) {
            return SSAAbstractThrowInstruction.class.getName();

        } else if (i instanceof SSAAbstractUnaryInstruction) {
            return SSAAbstractThrowInstruction.class.getName();

        } else if (i instanceof SSAAddressOfInstruction) {
            return SSAAddressOfInstruction.class.getName();

        } else if (i instanceof SSAArrayLengthInstruction) {
            return SSAArrayLengthInstruction.class.getName();

        } else if (i instanceof SSAArrayReferenceInstruction) {
            return SSAArrayReferenceInstruction.class.getName();

        } else if (i instanceof SSABinaryOpInstruction) {
            return SSABinaryOpInstruction.class.getName();

        } else if (i instanceof SSACheckCastInstruction) {
            return SSACheckCastInstruction.class.getName();

        } else if (i instanceof SSAComparisonInstruction) {
            return SSAComparisonInstruction.class.getName();

        } else if (i instanceof SSAConditionalBranchInstruction) {
            return SSAConditionalBranchInstruction.class.getName();

        } else if (i instanceof SSAConversionInstruction) {
            return SSAConversionInstruction.class.getName();

        } else if (i instanceof SSAFieldAccessInstruction) {
            return SSAFieldAccessInstruction.class.getName();

        } else if (i instanceof SSAGetCaughtExceptionInstruction) {
            return SSAGetCaughtExceptionInstruction.class.getName();

        } else if (i instanceof SSAGotoInstruction) {
            return SSAGotoInstruction.class.getName();

        } else if (i instanceof SSAInstanceofInstruction) {
            return SSAInstanceofInstruction.class.getName();

        } else if (i instanceof SSALoadMetadataInstruction) {
            return SSALoadMetadataInstruction.class.getName();

        } else if (i instanceof SSAMonitorInstruction) {
            return SSAMonitorInstruction.class.getName();

        } else if (i instanceof SSANewInstruction) {
            return SSANewInstruction.class.getName();

        } else if (i instanceof SSAPhiInstruction) {
            return SSAPhiInstruction.class.getName();

        } else if (i instanceof SSAReturnInstruction) {
            return SSAReturnInstruction.class.getName();

        } else if (i instanceof SSAStoreIndirectInstruction) {
            return SSAStoreIndirectInstruction.class.getName();

        } else if (i instanceof SSASwitchInstruction) {
            return SSASwitchInstruction.class.getName();

        } else {
            return SSAInstruction.class.getName();
        }
    }

    public static int findParameterIndex(IMethod m, int valueNumber, SymbolTable st) {
        int newValueNumber = valueNumber;
        if(!m.isStatic() && st.isParameter(valueNumber)) {
            newValueNumber -= 1;
        }
        return newValueNumber;
    }

    public static int findParameterUseNumber(SSAAbstractInvokeInstruction i, int normalizedValueNumber) {
        int parameterUseNumber = normalizedValueNumber;
        if(i.isStatic()) {
            parameterUseNumber -= 1;
        }
        return parameterUseNumber;
    }

    public static int getNumberOfParameters(SSAAbstractInvokeInstruction i) {
        int numParams = i.getNumberOfPositionalParameters();
        if (!i.isStatic()) {
            numParams -= 1;
        }
        return numParams;
    }

    public static int getNumberOfParameters(IMethod m) {
        int numParams = m.getNumberOfParameters();
        if (!m.isStatic()) {
            numParams -= 1;
        }
        return numParams;
    }
}