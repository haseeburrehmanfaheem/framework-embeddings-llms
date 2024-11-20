package com.aacr.helpers.accesscontrol.framework;

import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import java.util.Collections;
import java.util.HashSet;

public class PotentialMethodNameAccessControl extends AccessControlWrapper{
    public PotentialMethodNameAccessControl(MethodReference callingMethod,
                                            TypeReference callingMethodClass,
                                            SSAAbstractInvokeInstruction instruction, String paramCheck) {
        super(-1, callingMethod, callingMethodClass, instruction,
                new HashSet<>(Collections.singletonList(paramCheck)));
    }
}
