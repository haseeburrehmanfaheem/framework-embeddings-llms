package com.aacr.helpers.extension;

import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.aacr.helpers.utils.ComparisonUtils;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class ExtendedMethodReference {
    private TypeReference typeReference;
    private MethodReference methodReference;

    public ExtendedMethodReference(TypeReference typeReference, MethodReference methodReference) {
        this.typeReference = typeReference;
        this.methodReference = methodReference;
    }

    public MethodReference getMethodReference() {
        return this.methodReference;
    }

    public TypeReference getClassReference() {
        return this.typeReference;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 41).
                append(typeReference).
                append(methodReference).
                toHashCode();
    }

    @Override
    public boolean equals(Object object) {
        if(!(object instanceof ExtendedMethodReference)) return false;
        if(!ComparisonUtils.compareTypeReferences(((ExtendedMethodReference) object).getClassReference(), this.typeReference)) return false;
        if(!ComparisonUtils.compareMethodReferences(((ExtendedMethodReference) object).getMethodReference(), this.methodReference)) return false;
        return true;
    }

    @Override
    public String toString() {
        return this.typeReference + "." + this.methodReference;
    }
}
