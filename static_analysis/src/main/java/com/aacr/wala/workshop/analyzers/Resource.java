package com.aacr.wala.workshop.analyzers;

public class Resource {
    public String _name;
    public String _type;
    public String _method;
    public String _class;
    public String _accessString;
    public Integer _callGraphNodeId;
    public Integer _controlFlowGraphNodeId;
    public Integer _parentHash;

    public Resource(String name, String type, String method, String className, String accessString, Integer callGraphNodeId, Integer controlFlowGraphNodeId, Integer parentHash) {
        _name = name;
        _type = type;
        _method = method;
        _parentHash = parentHash;
        _class = className;
        _accessString = accessString;
        _callGraphNodeId = callGraphNodeId;
        _controlFlowGraphNodeId = controlFlowGraphNodeId;

    }

    public String toString(){
        String s = "[TYPE]:" + _type + " [NAME]: " + _name + " [PARENT]:" + _method + " [PARENT_HASH]:" + Integer.toString(_parentHash) + " [CLASS]:" + _class + " [CGNode_ID]:" + Integer.toString(_callGraphNodeId) + " [CFGNode_ID]:" + Integer.toString(_controlFlowGraphNodeId) + " [PROTECTION]:" + _accessString;
        return s;
    }
}
