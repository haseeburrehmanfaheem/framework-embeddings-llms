package com.aacr.helpers.detectors.app.componentgraph.models;

import com.aacr.helpers.accesscontrol.app.Component;
import com.aacr.helpers.detectors.app.componentgraph.models.info.ComponentInfo;

public class Node {
    public final Component appComponent;
    public final ComponentInfo miscInfo;

    public Node(Component appComponent, ComponentInfo miscInfo) {
        this.appComponent = appComponent;
        this.miscInfo = miscInfo;
    }

    @Override
    public String toString() {
        return "Node{" +
                "appComponent=" + appComponent +
                ", miscInfo=" + miscInfo +
                '}';
    }
}
