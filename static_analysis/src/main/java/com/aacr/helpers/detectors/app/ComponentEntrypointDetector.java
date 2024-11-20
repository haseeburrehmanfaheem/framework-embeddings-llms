package com.aacr.helpers.detectors.app;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.aacr.helpers.accesscontrol.app.Component;
import com.aacr.helpers.accesscontrol.app.Protection;
import com.aacr.helpers.detectors.app.componentgraph.models.info.ComponentInfo;
import com.aacr.helpers.utils.IClassUtils;

import java.util.HashMap;

public class ComponentEntrypointDetector {
    public static HashMap<Component, ComponentInfo> getEps(HashMap<String, Component> epProtectionMap,
                                                           ClassHierarchy appCha, AnalysisScope appScope) {

        HashMap<Component, ComponentInfo> compMap = new HashMap<>();

        //Get all entry points from known callbacks of detected application components
        for (Component comp : epProtectionMap.values()) {
            IClass c = IClassUtils.getClass(appCha, comp.getClassStr());
            try {
                //Class must be there in the set of exposed components
                if (c != null) {
                    HashMap<AppEntrypoint, Protection> eps = new HashMap<>();
                    for (IMethod m : c.getAllMethods()) {
                        if (m == null) continue;
                        //Method must be a known EP of the component
                        if (comp.getType().knownEpMethods.contains(m.getName().toString())) {
                            eps.put(new AppEntrypoint(m, appCha, c.getReference()), comp.getProtection());
                        }
                    }
                    compMap.put(comp, new ComponentInfo(appScope, appCha, eps));
                } else {
                    //System.out.println();
                }
            } catch (Exception e) {
                //ignore
            }
        }

        //Get all entry points from known callbacks of detected application components
//        for (IClass c : appCha) {
//            //Best effort - ignore errors
//            try {
//                //Class must be there in the set of exposed components
//                if (c != null && appScope.isApplicationLoader(c.getClassLoader())) {
//                    Component component = epProtectionMap.get(c.getName().toString());
//                    if (component != null) {
//                        HashMap<AppEntrypoint, Protection> eps = new HashMap<>();
//                        for (IMethod m : c.getAllMethods()) {
//                            if (m == null) continue;
//                            //Method must be a known EP of the component
//                            if (component.getType().knownEpMethods.contains(m.getName().toString())) {
//                                eps.put(new AppEntrypoint(m, appCha), component.getProtection());
//                            }
//                        }
//                        compMap.put(component, new ComponentInfo(appScope, appCha, eps));
//                    }
//                }
//            } catch (Exception e) {
//                //ignore
//            }
//        }

        return compMap;
    }
}
