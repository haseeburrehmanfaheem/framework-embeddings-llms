package com.aacr.helpers.detectors.app.ui;

import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.aacr.helpers.accesscontrol.app.Component;
import com.aacr.helpers.detectors.app.componentgraph.models.info.ComponentInfo;

import java.util.HashMap;

// TODO: Get this done
public class AlertDialogDetector {
    private final ClassHierarchy appCha;
    private final AnalysisScope appScope;
    private final Component comp;
    private final ComponentInfo compInfo;
    private final CallGraph compCg;

    public AlertDialogDetector(ClassHierarchy appCha, AnalysisScope appScope,
                               Component comp, ComponentInfo compInfo) {
        this.appCha = appCha;
        this.appScope = appScope;
        this.comp = comp;
        this.compInfo = compInfo;
        this.compCg = compInfo.getCg();
    }

    public HashMap<Component, ComponentInfo> extractEps() {
        return null;
    }
}
