package com.aacr.helpers.parsers.models.view.preference;

import java.util.HashMap;

public class PreferenceResource {
    public final String name;
    public final String key;
    public final HashMap<String, String> props;
    public final HashMap<String, HashMap<String, String>> internal;

    public PreferenceResource(String name, String key, HashMap<String, String> props,
                              HashMap<String, HashMap<String, String>> internal) {
        this.name = name;
        this.key = key;
        this.props = props;
        this.internal = internal;
    }

    @Override
    public String toString() {
        return "PreferenceResource{" +
                "name=" + name +
                ", key=" + key +
                ", props=" + props +
                ", internal=" + internal +
                '}';
    }
}
