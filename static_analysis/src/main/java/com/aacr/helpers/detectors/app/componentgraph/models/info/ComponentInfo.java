package com.aacr.helpers.detectors.app.componentgraph.models.info;

import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.aacr.helpers.accesscontrol.app.Protection;
import com.aacr.helpers.detectors.app.AppEntrypoint;
import com.aacr.helpers.utils.CallGraphUtils;

import java.util.HashMap;

public class ComponentInfo {
    public final HashMap<AppEntrypoint, Protection> epMap;
    private CallGraph componentCg = null;
    private final AnalysisScope scope;
    private final ClassHierarchy cha;
    private boolean epsChanged = false;

    public ComponentInfo(AnalysisScope scope, ClassHierarchy cha, HashMap<AppEntrypoint, Protection> epMap) {
        this.epMap = epMap;
        this.scope = scope;
        this.cha = cha;
    }

    public void addEps(HashMap<AppEntrypoint, Protection> epMap) {
        this.epMap.putAll(epMap);
        epsChanged = true;
    }

    public CallGraph getCg() {
        if (componentCg == null || epsChanged)
            initCg();
        epsChanged = false;
        return componentCg;
    }

    private void initCg() {
        if (epMap != null && !epMap.isEmpty()) {
            componentCg = CallGraphUtils.createComponentCg(scope, cha, epMap.keySet());
        }
    }
}