package com.aacr.helpers.detectors.framework;

public class AcmNode {
    public NodeTypes nodeType; // AC, Resource, Conditional, Entry
    public String methodName;
    public String className;

    public enum NodeTypes{
        AccessControl,
        AccessControlPotential,
        InvokeResource,
        FieldAccessResource,
        EntryPoint,
    }

    public AcmNode(NodeTypes nodeType, String methodName, String className){
        this.nodeType = nodeType;
        this.methodName = methodName;
        this.className = className;
    }


}
