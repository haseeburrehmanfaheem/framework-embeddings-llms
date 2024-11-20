package com.aacr.helpers.accesscontrol.framework;

import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;


public class ComparativeAccessControlCheck extends AccessControlEnforcement{

    public enum ACType {
        Getting,
        Checking
    }

    private ACType type;
    private MethodReference callingMethod;
    private TypeReference callingMethodClass;
    private SSAAbstractInvokeInstruction accessControlCheck;
    private String operator;
    private Object otherValue;

    /**
     * ComparativeAccessControlCheck constructor.
     * @param index Instruction index.
     * @param type Type of instruction target.
     * @param callingMethod Method that invokes the comparative access control check.
     * @param instruction SSAInstruction of interest.
     * @param accessControlCheck Instruction of predicate with the access control check.
     */
    public ComparativeAccessControlCheck(int index, ACType type, MethodReference callingMethod, TypeReference callingMethodClass, SSAInstruction instruction,  SSAAbstractInvokeInstruction accessControlCheck, String operator, Object otherValue) {
        super.setUpEnforcement(index, instruction);
        this.type = type;
        this.callingMethod = callingMethod;
        this.callingMethodClass = callingMethodClass;
        this.accessControlCheck = accessControlCheck;
        this.operator = operator;
        this.otherValue = otherValue;
    }

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

    /**
     * @return The type of the ComparativeAccessControlCheck.
     */
    public ACType getType() {
        return this.type;
    }


    public Object getOtherValue() { return this.otherValue; }

    public String getOperator() { return this.operator; }


    /**
     * @return The instruction corresponding to the predicate with the access control check.
     */
    public SSAAbstractInvokeInstruction getAccessControlInstruction() {
        return this.accessControlCheck;
    }

    /**
     * This method returns the inner instruction that has information on the access control check being conducted.
     * @return An SSAAbstractInvokeInstruction corresponding to one of the SSAConditionalBranchInstruction's predicates.
     */
    public Object getEnforcement() {
        return this.accessControlCheck;
    }

    @Override
    public String toString() {
        String returnString = "";
        returnString += accessControlCheck.toString();
        if(otherValue != null) {
            returnString += " " + this.operator + " " + otherValue.toString();
        }
        return returnString;
    }
}
