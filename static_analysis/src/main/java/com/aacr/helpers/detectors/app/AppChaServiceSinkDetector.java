package com.aacr.helpers.detectors.app;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.classLoader.DexIMethod;
import com.ibm.wala.dalvik.dex.instructions.Instruction;
import com.ibm.wala.dalvik.dex.instructions.Invoke;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.aacr.helpers.accesscontrol.framework.AccessControlEnforcement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class AppChaServiceSinkDetector {
    private final ClassHierarchy appCha;
    private final AnalysisScope appScope;
    private final HashMap<String, ArrayList<AccessControlEnforcement>> targetSinks;
    private final HashMap<String, ArrayList<AccessControlEnforcement>> allAppSinks = new HashMap<>();
//    private final HashMap<String, HashSet<AccessControlEnforcement>> allAppInvokers;

    public AppChaServiceSinkDetector(ClassHierarchy appCha, AnalysisScope appScope,
                                     HashMap<String, ArrayList<AccessControlEnforcement>> targetSinks) {
        this.appCha = appCha;
        this.appScope = appScope;
        this.targetSinks = targetSinks;
        extractAllAppSinks();

//        ManagerDetector detector = new ManagerDetector(appScope, appCha, targetSinks);
//        allAppInvokers = detector.getNewManagerEpProtections();
    }

    private void extractAllAppSinks() {
        for (IClass c : appCha) {
            if (AppClassUtils.shouldNotAnalyze(c, appScope))
                continue;

            for (IMethod m : c.getAllMethods()) {
                if (m instanceof DexIMethod) {
                    Instruction[] instrs = null;
                    try {
                        instrs = ((DexIMethod)m).getInstructions();
                    } catch (Exception e) {
                        continue;
                    }
                    if (instrs == null)
                        continue;
                    for (Instruction ins : instrs) {
                        if (ins instanceof Invoke) {
                            Invoke invoke = (Invoke) ins;
                            if (invoke.clazzName == null
                                    || invoke.methodName == null
                                    || invoke.descriptor == null)
                                continue;
                                StringBuilder invokedMethod = new StringBuilder(invoke.clazzName.replace('/', '.'));
                                if (invokedMethod.indexOf("L") == 0)
                                    invokedMethod.deleteCharAt(0);
                                invokedMethod.append(".")
                                        .append(invoke.methodName)
                                        .append(invoke.descriptor);
                                String invokeStr = invokedMethod.toString();
                                if (targetSinks.containsKey(invokeStr))
                                    allAppSinks.put(invokeStr, targetSinks.get(invokeStr));
                        }
                    }
                }
            }
        }
    }

    public void printSinks() {
        for (Map.Entry<String, ArrayList<AccessControlEnforcement>> sink : allAppSinks.entrySet())
            System.out.println(sink.getKey() + "&" + getProtectionStr(sink.getValue()));

//        for (Map.Entry<String, HashSet<AccessControlEnforcement>> sink : allAppInvokers.entrySet())
//            System.out.println(sink.getKey() + "&" + getProtectionStr(sink.getValue()));
    }

    private String getProtectionStr(ArrayList<AccessControlEnforcement> protections) {
        if (protections == null || protections.isEmpty()) return "";
        StringBuilder ret = new StringBuilder();
        for (AccessControlEnforcement p : protections) {
            ret.append("::").append(p);
        }
        return ret.toString();
    }

    private String getProtectionStr(HashSet<AccessControlEnforcement> protections) {
        if (protections == null || protections.isEmpty()) return "";
        StringBuilder ret = new StringBuilder();
        for (AccessControlEnforcement p : protections) {
            ret.append("::").append(p);
        }
        return ret.toString();
    }
}
