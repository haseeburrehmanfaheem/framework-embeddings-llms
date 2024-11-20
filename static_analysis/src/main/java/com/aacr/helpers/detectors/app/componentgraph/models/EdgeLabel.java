package com.aacr.helpers.detectors.app.componentgraph.models;

import com.aacr.helpers.accesscontrol.app.Protection;

public class EdgeLabel {
    public final Protection protection;

    public EdgeLabel(Protection protection) {
        this.protection = protection;
    }

    @Override
    public String toString() {
        return "ComponentEdgeLabel{" +
                "relatedProtection=" + protection +
                '}';
    }
}
