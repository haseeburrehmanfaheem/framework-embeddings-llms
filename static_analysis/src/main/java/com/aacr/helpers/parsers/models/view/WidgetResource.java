package com.aacr.helpers.parsers.models.view;

import java.util.HashMap;

public class WidgetResource {
    public final String id;
    public final String name;
    public final HashMap<String, String> stringProps;
    private LayoutResource parent = null;

    public WidgetResource(String id, String name, HashMap<String, String> stringProps) {
        this.id = id;
        this.name = name;
        this.stringProps = stringProps;
    }

    public WidgetResource(String id, String name, HashMap<String, String> stringProps, LayoutResource parent) {
        this.id = id;
        this.name = name;
        this.stringProps = stringProps;
        this.parent = parent;
    }

    public void setParent(LayoutResource parent) {
        this.parent = parent;
    }

    @Override
    public String toString() {
        return "WidgetResource{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", stringProps=" + stringProps +
                ", parent=" + parent +
                '}';
    }
}
