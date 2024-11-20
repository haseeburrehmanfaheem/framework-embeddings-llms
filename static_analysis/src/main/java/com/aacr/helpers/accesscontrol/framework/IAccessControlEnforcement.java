package com.aacr.helpers.accesscontrol.framework;

import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

/**
 * This interface specifies the requirements for the AccessControlEnforcement class and all sub-classes.
 */
interface IAccessControlEnforcement {
        public void setIndex(int index);
        public void setInstruction(SSAInstruction instruction);
        public int getIndex();
        public SSAInstruction getInstruction();
        public Object getEnforcement();
        public MethodReference getCallingMethod();
        public TypeReference getCallingMethodClass();
        public SSAAbstractInvokeInstruction getAccessControlInstruction();
}
