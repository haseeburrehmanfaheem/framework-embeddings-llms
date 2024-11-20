package com.aacr.helpers.parsers.models.view.preference;

import java.util.HashMap;

public class PreferenceScreenResource {
    public final String name;
    public final HashMap<String, String> props;
    public final HashMap<String, PreferenceResource> preferences;

    public PreferenceScreenResource(String name, HashMap<String, String> props,
                                    HashMap<String, PreferenceResource> preferences) {
        this.name = name;
        this.props = props;
        this.preferences = preferences;
    }

    @Override
    public String toString() {
        return "PreferenceScreenResource{" +
                "name=" + name +
                ", props=" + props +
                ", preferences=" + preferences +
                '}';
    }
}
