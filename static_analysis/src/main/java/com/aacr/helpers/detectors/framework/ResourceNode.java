package com.aacr.helpers.detectors.framework;

import com.ibm.wala.ssa.SSAInstruction;

import java.util.HashMap;

public class ResourceNode extends AcmNode {

    public String resourceName;
    public SSAInstruction instruction;

    public HashMap<String, Boolean> booleanFeatures;
    public HashMap<String, String> stringFeatures;

    public ResourceNode(SSAInstruction instruction, NodeTypes nodeType, String methodName, String className, String resourceName, HashMap<String, Boolean> booleanFeatures, HashMap<String, String> stringFeatures){
        super(nodeType,methodName,className);
        this.instruction = instruction;
        this.resourceName = resourceName;
        this.booleanFeatures = booleanFeatures;
        this.stringFeatures = stringFeatures;
    }

    public String createKey(){
        return this.nodeType.toString() + " " + this.resourceName;
    }

    public String createFeatures(){
        return "BooleanFeatures: " + this.booleanFeatures.toString() + "\nStringFeatures: " + this.stringFeatures.toString();
    }


}
