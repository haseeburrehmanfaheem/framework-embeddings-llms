package com.aacr.helpers.accesscontrol.framework;

import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import java.util.ArrayList;
import java.util.Collection;


public class PermissionCheck extends AccessControlEnforcement {

    private MethodReference callingMethod;
    private TypeReference callingMethodClass;
    private ArrayList<String> permissionList;

    /**
     * PermissionCheck constructor.
     * @param index Instruction index.
     * @param callingMethod Method that invokes the permission check.
     * @param instruction SSAInstruction of interest.
     * @param permission String representing a particular Android permission.
     */
    public PermissionCheck(int index, SSAInstruction instruction, MethodReference callingMethod, TypeReference callingMethodClass, String permission) {
        super.setUpEnforcement(index, instruction);
        this.callingMethod = callingMethod;
        this.callingMethodClass = callingMethodClass;
        this.permissionList = new ArrayList<>();
        this.permissionList.add(permission);
    }

    /**
     * PermissionCheck constructor.
     * @param index Instruction index.
     * @param instruction SSAInstruction of interest.
     * @param callingMethod Method that invokes the permission check.
     * @param permissionList Collection of Strings, each representing a particular Android permission.
     */
    public PermissionCheck(int index, SSAInstruction instruction,  MethodReference callingMethod, TypeReference callingMethodClass, Collection<Object> permissionList) {
        super.setUpEnforcement(index, instruction);
        this.callingMethod = callingMethod;
        this.callingMethodClass = callingMethodClass;
        this.permissionList = new ArrayList<>();
        for(Object permissionString : permissionList) {
            this.permissionList.add((String) permissionString);
        }
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
     * @return The list of permissions that could be passed into this permission check.
     */
    public Object getEnforcement() {
        return this.permissionList;
    }

    /**
     * @return The first permission in the list of permissions that could be passed into this permission check.
     */
    public String getSingleEnforcement() {
        if(this.permissionList.size() > 0) {
            return this.permissionList.get(0);
        }
        return "";
    }

    /**
     * @return The invoke instruction.
     */
    public SSAAbstractInvokeInstruction getAccessControlInstruction() {
        return (SSAAbstractInvokeInstruction) super.getInstruction();
    }

    /**
     * @return A textual representation of the PermissionCheck.
     */
    @Override
    public String toString() {
        return this.permissionList.toString();
    }
}
