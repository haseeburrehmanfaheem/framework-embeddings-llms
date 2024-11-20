package com.aacr.helpers.custom;

import com.ibm.wala.shrike.shrikeBT.IConditionalBranchInstruction;

import java.util.ArrayList;

public class ConditionalInfo {
    private ArrayList<Object> predicateList1;
    private IConditionalBranchInstruction.IOperator operator;
    private ArrayList<Object> predicateList2;

    public ConditionalInfo(ArrayList<Object> predicateList1, IConditionalBranchInstruction.IOperator operator, ArrayList<Object> predicateList2) {
        this.predicateList1 = predicateList1;
        this.operator = operator;
        this.predicateList2 = predicateList2;
    }

    public ArrayList<Object> getFirstPredicateList() {
        return this.predicateList1;
    }

    public IConditionalBranchInstruction.IOperator getOperator() {
        return this.operator;
    }

    public ArrayList<Object> getSecondPredicateList() {
        return this.predicateList2;
    }

    @Override
    public String toString() {
        return "First Predicate: " + this.predicateList1.toString()
                + "\nSecond Predicate: " + this.predicateList2.toString()
                + "\n Operator: " + this.operator.toString();
    }
}
