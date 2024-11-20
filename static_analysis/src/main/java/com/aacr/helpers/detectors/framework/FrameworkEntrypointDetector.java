package com.aacr.helpers.detectors.framework;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
/**
 * Detects framework entrypoints
 */
public class FrameworkEntrypointDetector {

    /** Goes through all system service classes and retrieves entrypoints.
     * @param cha ClassHierarchy.
     * @param systemServices HashSet of IClass for each system service.
     * @return Hashset of DefaultEntrypoints, each representing a framework entrypoint.
     */
    public static Pair<HashSet<DefaultEntrypoint>, HashMap<DefaultEntrypoint, String>> getFrameworkEntrypoints(AnalysisScope scope, ClassHierarchy cha, HashSet<IClass> systemServices, HashMap<IClass, String> classLookup) {
        HashSet<DefaultEntrypoint> frameworkEntrypoints = new HashSet<>();
        HashMap<DefaultEntrypoint, String> _map = new HashMap<>();
        for(IClass serviceClass : systemServices) {
            ArrayList<DefaultEntrypoint> newEntrypoints = listServiceEntrypoints(cha, serviceClass);

            if(newEntrypoints != null) {
                frameworkEntrypoints.addAll(newEntrypoints);
                for(DefaultEntrypoint e: newEntrypoints){
//                    _map.put(e, classLookup.get(serviceClass));
                    _map.put(e, serviceClass.getName().toString());
                }
            }
        }
        return Pair.make(frameworkEntrypoints, _map);
    }

    public static boolean foundInSuper(String s, ArrayList<String> superList) {
        for (String ep : superList) {
            if (ep.equals(s)) {
                return true;
            }
        }
        return false;
    }


    /** Finds a system service class's exposed entrypoints and returns them. - But returns the Interface
     * @param cha ClassHierarchy.
     * @param c System service class.
     * @return ArrayList of DefaultEntrypoints, each representing an entrypoint to the framework.
     */
    public static ArrayList<DefaultEntrypoint> listServiceEntrypoints(ClassHierarchy cha, IClass c) {

//        if(c == null) return null;
//
//        IClass superClass = c.getSuperclass();
//        if (superClass.getName().toString().contains("$Stub")) {
//            superClass = cha.lookupClass(TypeReference.find(ClassLoaderReference.Application, superClass.getName().toString().
//                    replace("$Stub", "")));
//        }
//        ArrayList<DefaultEntrypoint> frameworkEntrypoints = new ArrayList<>();
//
//        ArrayList<String> publicInterfaces = new ArrayList<>();
//        for (IMethod sm : superClass.getDeclaredMethods()) {
//            if (sm.isPublic() && !IClassUtils.exclusionClasses.contains(sm.getDeclaringClass().getName().toString())) {
//                frameworkEntrypoints.add(new DefaultEntrypoint(sm, cha));
//                publicInterfaces.add(sm.getSelector().toString());
//            }
//        }
//
//        for (IMethod cm : c.getDeclaredMethods()) {
//            if (cm.isPublic() && foundInSuper(cm.getSelector().toString(), publicInterfaces)) {
//                frameworkEntrypoints.add(new DefaultEntrypoint(cm, cha));
//            }
//        }
//        return frameworkEntrypoints;

        if(c == null) return null;
        ArrayList<DefaultEntrypoint> frameworkEntrypoints = new ArrayList<>();

        IClass publicInt = getServicePublicInterface(c, cha);
        if (publicInt != null) {
            for (IMethod m : publicInt.getDeclaredMethods()){
                if(m.isPublic()){
                    frameworkEntrypoints.add(new DefaultEntrypoint(m, cha));
                }
            }
        }

        ArrayList<DefaultEntrypoint> imps = getImplementationsFromIClass(cha, c);
        ArrayList<DefaultEntrypoint> ans = getCommonDefaultEntryPoints(frameworkEntrypoints, imps);
        return ans;
    }

    public static ArrayList<DefaultEntrypoint> getImplementationsFromIClass(ClassHierarchy cha, IClass c){
        if(c == null) return null;

        ArrayList<DefaultEntrypoint> frameworkEntrypoints = new ArrayList<>();

        for (IMethod m : c.getDeclaredMethods()){
            if(m.isPublic()){
                frameworkEntrypoints.add(new DefaultEntrypoint(m, cha));
            }
        }

        return frameworkEntrypoints;
    }

    public static ArrayList<DefaultEntrypoint> getCommonDefaultEntryPoints(ArrayList<DefaultEntrypoint> interfaces, ArrayList<DefaultEntrypoint> imps){
        ArrayList<DefaultEntrypoint> ans = new ArrayList<>();

        for (DefaultEntrypoint ep: interfaces){
            String name = ep.getMethod().getName().toString();
            String returnType = ep.getMethod().getReturnType().getName().toString();
            String params = Integer.toString(ep.getMethod().getNumberOfParameters());
            name = name + "_" + returnType + "_" + params;

            for(DefaultEntrypoint i: imps){
                String name2 = i.getMethod().getName().toString();
                String returnType2 = i.getMethod().getReturnType().getName().toString();
                String params2 = Integer.toString(i.getMethod().getNumberOfParameters());
                name2 = name2 + "_" + returnType2 + "_" + params2;

                if(name2.equals(name)){
                    ans.add(i);
                }
            }

        }
        return ans;
    }

    private static IClass getServicePublicInterface(IClass c, ClassHierarchy cha) {
        for (IClass sup : c.getAllImplementedInterfaces()) {
            if (sup.getAllImplementedInterfaces().size() == 1
                    && sup.getAllImplementedInterfaces().contains((cha.lookupClass(TypeReference
                    .find(ClassLoaderReference.Application, "Landroid/os/IInterface")))))
                return sup;
        }

        return null;
    }
}
