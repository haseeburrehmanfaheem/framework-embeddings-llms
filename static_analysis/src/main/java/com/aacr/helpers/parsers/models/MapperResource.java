package com.aacr.helpers.parsers.models;

import com.aacr.helpers.parsers.models.val.ValueResource;
import com.aacr.helpers.parsers.models.view.LayoutResource;
import com.aacr.helpers.parsers.models.view.preference.PreferenceScreenResource;

import java.util.HashMap;

public class MapperResource {
    public final String name, type;
    public final int id;

    public MapperResource(String name, String type, int id) {
        this.name = name;
        this.type = type;
        this.id = id;
    }

    /**
     * Returns the layout resource of the same name if one exists in the map
     * @param layoutResMap The map created by layout parser which contains all parsed layout resources
     * @return The layout resource matching the name. If the mapper is not of type 'layout',
     * or if the resource does not exist in the map, then returns null
     */
    public LayoutResource toLayoutRes(HashMap<String, LayoutResource> layoutResMap) {
        if (!LayoutResource.name.equals(type))
            return null;
        return layoutResMap.get(name);
    }

    /**
     * Returns the preference screen resource of the same name if one exists in the map
     * @param prefScreenResMap The map created by pref screen parser which contains all parsed pref screen resources
     * @return The pref screen resource matching the name. If the mapper is not of type 'xml',
     * or if the resource does not exist in the map, then returns null
     */
    public PreferenceScreenResource toPrefScreenRes(HashMap<String, PreferenceScreenResource> prefScreenResMap) {
        if (!"xml".equals(type))
            return null;
        return prefScreenResMap.get(name);
    }

    /**
     * Returns the value resource of the same type and name if one exists in the map
     * @param valResMap The map created by value resource parser which contains all parsed value resources
     * @return The value resource matching the type and the name. If the mapper does not contain the resource
     * matching the type and name in the map, then returns null
     */
    public ValueResource toValRes(HashMap<String, HashMap<String, ValueResource>> valResMap) {
        ValueResource res = null;
        if (valResMap.containsKey(type)) {
            res = valResMap.get(type).get(name);
        }

        return res;
    }
}
