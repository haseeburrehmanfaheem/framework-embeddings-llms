package com.aacr.helpers.accesscontrol.framework;

import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import java.util.HashSet;

public class AccessControlWrapper extends AccessControlEnforcement {
    private MethodReference callingMethod;
    private TypeReference callingMethodClass;
    private SSAAbstractInvokeInstruction accessControlCheck;
    private HashSet<String> checkingFor;

    public AccessControlWrapper(int index, MethodReference callingMethod,
                                TypeReference callingMethodClass, SSAAbstractInvokeInstruction instruction, HashSet<String> checkingFor) {
        super.setUpEnforcement(index, instruction);
        this.callingMethod = callingMethod;
        this.callingMethodClass = callingMethodClass;
        this.accessControlCheck = instruction;
        this.checkingFor = checkingFor;
    }

    public HashSet<String> getCheckingFor() { return this.checkingFor; }

    /**
     * @return The calling method.
     */
    public MethodReference getCallingMethod() {
        return this.callingMethod;
    }

    /**
     * @return The calling method class.
     */
    public TypeReference getCallingMethodClass() {
        return this.callingMethodClass;
    }

    public SSAAbstractInvokeInstruction getAccessControlInstruction() {
        return this.accessControlCheck;
    }

    /**
     * This method returns the inner instruction that has information on the access control check
     * being conducted.
     *
     * @return An SSAAbstractInvokeInstruction corresponding to one of the
     * SSAConditionalBranchInstruction's predicates.
     */
    public Object getEnforcement() {
        return this.accessControlCheck;
    }

    @Override
    public String toString() {
//        String returnString = this.accessControlCheck.toString();
        String returnString = "";
        for(String s : this.getCheckingFor()) {
            returnString += ", " + s;
        }
        return returnString;
    }
}
