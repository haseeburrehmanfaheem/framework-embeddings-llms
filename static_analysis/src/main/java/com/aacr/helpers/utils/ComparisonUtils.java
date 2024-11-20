package com.aacr.helpers.utils;

import com.ibm.wala.dalvik.dex.instructions.PutField;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

/**
 * This class contains utility methods for comparisons between instructions and various reference types.
 */
public class ComparisonUtils {

    /**
     * Returns boolean representing whether the PutField and FieldReference
     * (retrieved from an SSAInstruction) are equivalent.
     * @param putFieldInstruction PutField instruction.
     * @param fieldReference FieldReference from an SSAInstruction.
     * @return Boolean indicating equivalence.
     */
    public static boolean comparePutFieldAndFieldReference(PutField putFieldInstruction, FieldReference fieldReference) {
        if(!putFieldInstruction.fieldName.equals(fieldReference.getName().toString())) return false;
        if(!putFieldInstruction.fieldType.equals(fieldReference.getFieldType().getName().toString())) return false;
        if(!putFieldInstruction.clazzName.equals(fieldReference.getDeclaringClass().getName().toString())) return false;
        return true;
    }

    /**
     * Returns boolean representing whether the fieldReferences are equivalent.
     * @param fieldReference1 FieldReference.
     * @param fieldReference2 FieldReference.
     * @return Boolean indicating equivalence.
     */
    public static boolean compareFieldReferences(FieldReference fieldReference1, FieldReference fieldReference2) {
        if(!fieldReference1.getName().toString().equals(fieldReference2.getName().toString())) return false;
        if(!fieldReference1.getFieldType().toString().equals(fieldReference2.getFieldType().toString())) return false;
        if(!fieldReference1.getDeclaringClass().toString().equals(fieldReference2.getDeclaringClass().toString())) return false;
        return true;
    }

    /**
     * Returns boolean representing whether the methodReferences are equivalent.
     * @param methodReference1 MethodReference.
     * @param methodReference2 MethodReference.
     * @return Boolean indicating equivalence.
     */
    public static boolean compareMethodReferences(MethodReference methodReference1, MethodReference methodReference2) {
        if(!methodReference1.getName().toString().equals(methodReference2.getName().toString())) return false;
        if(!methodReference1.getDescriptor().toString().equals(methodReference2.getDescriptor().toString())) return false;
        if(!methodReference1.getReturnType().toString().equals(methodReference2.getReturnType().toString())) return false;
        return true;
    }

    /**
     * Returns boolean representing whether the typeReferences are equivalent.
     * @param typeReference1 TypeReference.
     * @param typeReference2 TypeReference.
     * @return Boolean indicating equivalence.
     */
    public static boolean compareTypeReferences(TypeReference typeReference1, TypeReference typeReference2) {
        if(!typeReference1.getName().toString().equals(typeReference2.getName().toString())) return false;
        if(!typeReference1.getClassLoader().toString().equals(typeReference2.getClassLoader().toString())) return false;
        return true;
    }
}