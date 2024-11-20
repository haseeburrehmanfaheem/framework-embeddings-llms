package com.aacr.helpers.detectors.app.componentgraph.models.info;

import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.aacr.helpers.accesscontrol.app.Protection;
import com.aacr.helpers.detectors.app.AppEntrypoint;
import com.aacr.helpers.parsers.models.view.LayoutResource;

import java.util.HashMap;
import java.util.List;

public class UiInfo extends ComponentInfo {
    public final List<LayoutResource> layouts;

    public UiInfo(AnalysisScope scope, ClassHierarchy cha,
                  HashMap<AppEntrypoint, Protection> epMap,
                  List<LayoutResource> layouts) {
        super(scope, cha, epMap);
        this.layouts = layouts;
    }

    @Override
    public String toString() {
        return "UiInfo{" +
                "layouts=" + layouts +
                '}';
    }
}
