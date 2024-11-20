package com.aacr.helpers.parsers.models.view;

import java.util.ArrayList;
import java.util.HashMap;

public class LayoutResource {
    public static final String name = "layout";

    private final String layoutId;
    private final ArrayList<String> allStrings;
    private final HashMap<String, WidgetResource> widgets;
    private final ArrayList<String> innerLayouts;

    public LayoutResource(String layoutId, ArrayList<String> allStrings,
                          HashMap<String, WidgetResource> widgets,
                          ArrayList<String> innerLayouts) {
        this.layoutId = layoutId;
        this.allStrings = allStrings;
        this.widgets = widgets;
        this.innerLayouts = innerLayouts;
    }

    public void parseInnerLayouts(HashMap<String, LayoutResource> layoutResMap) {

        if (innerLayouts.isEmpty()) return;

        for (String innL : innerLayouts) {
            LayoutResource inn = layoutResMap.get(innL);
            if (inn != null) {
                allStrings.addAll(inn.allStrings);
                widgets.putAll(inn.widgets);
            }
        }

        innerLayouts.clear();
    }

    public String getLayoutId() {
        if (!innerLayouts.isEmpty())
            throw new RuntimeException("Must parse inner layouts first!");
        return layoutId;
    }

    public ArrayList<String> getAllStrings() {
        if (!innerLayouts.isEmpty())
            throw new RuntimeException("Must parse inner layouts first!");
        return allStrings;
    }

    public HashMap<String, WidgetResource> getWidgets() {
        if (!innerLayouts.isEmpty())
            throw new RuntimeException("Must parse inner layouts first!");
        return widgets;
    }

    @Override
    public String toString() {
        return "LayoutResource{" +
                "layoutId=" + layoutId +
                ", allStrings=" + allStrings +
                '}';
    }
}
