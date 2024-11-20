package com.aacr.helpers.detectors.framework;

import com.ibm.wala.ssa.SSAInstruction;
import com.aacr.helpers.accesscontrol.framework.AccessControlEnforcement;
import com.aacr.helpers.accesscontrol.framework.ComparativeAccessControlCheck;
import com.aacr.helpers.accesscontrol.framework.PermissionCheck;
import com.aacr.helpers.accesscontrol.framework.PotentialMethodNameAccessControl;
import com.aacr.wala.workshop.analyzers.FrameworkAnalyzer;

import java.util.HashSet;

public class AccessControlNode extends AcmNode{
    public AccessControlEnforcement accessControl;
    public SSAInstruction instruction;
    public int accessLevel;
    public String accessLevelString;
    public String accessControlString;

    public AccessControlNode(SSAInstruction instruction, NodeTypes nodeType, String methodName, String className, AccessControlEnforcement accessControl) {
        super(nodeType, methodName, className);
        this.instruction = instruction;
        this.accessControl = accessControl;

        if(accessControl instanceof PotentialMethodNameAccessControl){
            String permLevel = ((PotentialMethodNameAccessControl) accessControl).getAccessControlInstruction().getDeclaredTarget().toString();
            accessLevel = getPermissionLevelInt(permLevel);
            accessLevelString = getPermissionLevel(permLevel);
            accessControlString = "[PotentialMethodNameAccessControl] " + accessControl.getAccessControlInstruction().getDeclaredTarget().toString() + " " + ((PotentialMethodNameAccessControl) accessControl).getCheckingFor().toString() + " " + accessLevelString;
        }else if(accessControl instanceof ComparativeAccessControlCheck){
            String permLevel = ((ComparativeAccessControlCheck) accessControl).getAccessControlInstruction().getDeclaredTarget().toString();
            accessLevel = getPermissionLevelInt(permLevel);
            accessLevelString = getPermissionLevel(permLevel);
            accessControlString = "[ComparativeAccessControlCheck] " + accessControl.getAccessControlInstruction().getDeclaredTarget().toString() + " " + ((ComparativeAccessControlCheck) accessControl).getAccessControlInstruction().getDeclaredTarget().toString() + " " + accessLevelString;
        }else if(accessControl instanceof PermissionCheck){
            String permLevel = ((PermissionCheck) accessControl).getSingleEnforcement();
            HashSet<String> temp = new HashSet<>();
            temp.add(permLevel);

            permLevel = temp.toString();

            accessLevel = getPermissionLevelInt(permLevel);
            accessLevelString = getPermissionLevel(permLevel);
            accessControlString = "[PermissionCheck] " + accessControl.getAccessControlInstruction().getDeclaredTarget().toString() + " " + ((PermissionCheck) accessControl).getSingleEnforcement() + " " + accessLevelString;
        }else{
            accessLevel = -1;
            accessLevelString = "null";
            accessControlString = "null";
        }

    }

    public String getAccessControlString(){
        return accessControlString;
    }

    public int getPermissionLevelInt(String s){
        if(s.toLowerCase().contains("permission")){
            String[] last = s.split("[.]", 0);
            String _s = last[last.length - 1];
            _s = _s.substring(0, _s.length()-1);
            if(FrameworkAnalyzer.permissionMap.containsKey(_s)){
                return FrameworkAnalyzer.permissionMap.get(_s).getLevel();
            }else{
                return -1;
            }
        }else if(s.toLowerCase().contains("uid")
                || s.toLowerCase().contains("system")
                || s.toLowerCase().contains("package") || s.toLowerCase().contains("process")) {
            return 3;
        }else if(s.toLowerCase().contains("app") || s.toLowerCase().contains("user")){
            // treat app id as Normal? Lets see what happens
            return 3;
        }else{
            return -1;
        }
    }

    public String getPermissionLevel(String s){
        if(s.toLowerCase().contains("permission")){
            String[] last = s.split("[.]", 0);
            String _s = last[last.length - 1];
            _s = _s.substring(0, _s.length()-1);
            if(FrameworkAnalyzer.permissionMap.containsKey(_s)){
                return FrameworkAnalyzer.permissionMap.get(_s).getString();
            }else{
                return _s + " - KEY NOT FOUND";
            }
        }else if(s.toLowerCase().contains("uid")){
            return "UID CHECK";
        }else if(s.toLowerCase().contains("system")){
            return "SYSTEM CHECK";
        }else if(s.toLowerCase().contains("package")){
            return "PACKAGE CHECK";
        }else if(s.toLowerCase().contains("process")){
            return "PROCESS CHECK";
        }else if(s.toLowerCase().contains("appid")){
            return "APP ID CHECK";
        }else if(s.toLowerCase().contains("pid")){
            return "PID CHECK";
        }else if(s.toLowerCase().contains("user")){
            return "USER CHECK";
        }else{
            return "NONE";
        }
    }

}
