package com.aacr.helpers.detectors.app;

import com.ibm.wala.classLoader.BytecodeClass;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.aacr.helpers.accesscontrol.app.Component;
import com.aacr.helpers.accesscontrol.app.Protection;
import com.aacr.helpers.detectors.app.componentgraph.models.info.ComponentInfo;

import java.util.HashMap;

public class BinderEntrypointDetector {
    public static HashMap<Component, ComponentInfo> getEps(HashMap<String, Component> serviceEpClasses,
                                                           ClassHierarchy appCha, AnalysisScope appScope) {
        HashMap<Component, ComponentInfo> compMap = new HashMap<>();
        for (IClass c : appCha) {
            //Only consider classes in CHA that extend IBinder
            String superClssName = "";
            try {
                superClssName = c.getSuperclass().getName().toString();
            } catch (Exception e) {
                //ignore
            }
            if (superClssName.isEmpty()) {
                try {
                    superClssName = ((BytecodeClass)c).getSuperName().toString();
                } catch (Exception e) {
                    //ignore
                }
            }
            if (c != null && appScope.isApplicationLoader(c.getClassLoader())
                    && superClssName.contains("Landroid/os/IBinder")) {
                //Is the class being considered, an inner class of a known service EP?
                String serviceClassStr = c.getName().toString();
                serviceClassStr = serviceClassStr.substring(0, serviceClassStr.lastIndexOf('$'));
                Component serviceComp = serviceEpClasses.get(serviceClassStr);
                if (serviceComp != null) {
                    //If yes, then add all its exposed methods to the list of EPs
                    HashMap<AppEntrypoint, Protection> binderEps = new HashMap<>();
                    for (IMethod m : c.getAllMethods()) {
                        if (m != null && !m.isPrivate() && !m.isProtected()) {
                            binderEps.put(new AppEntrypoint(m, appCha, c.getReference()), serviceComp.getProtection());
                        }
                    }
                    compMap.put(serviceComp, new ComponentInfo(appScope, appCha, binderEps));
                }
            }
        }
        return compMap;
    }
}
