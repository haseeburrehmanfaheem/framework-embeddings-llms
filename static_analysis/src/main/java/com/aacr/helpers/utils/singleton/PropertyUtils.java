package com.aacr.helpers.utils.singleton;

import java.util.ArrayList;
import java.util.HashMap;

// Preferably use UtilsInitializer instead of invoking init directly
public class PropertyUtils {

    private static final String PATH = "path";
    private static final String OUT_PATH = "outPath";
    private static PropertyUtils instance = null;

    //Maps Property name -> default value (if none, then null)
    private final HashMap<String, String> allPropStrings = new HashMap<>();

    //Use this method before getInstance if custom properties other than the default "Path" are needed
    public static synchronized void init(ArrayList<String> customPropStrings) {
        if (instance == null)
            instance = new PropertyUtils(customPropStrings);
    }

    //Use this method before getInstance if custom properties with default values are needed
    public static synchronized void init(HashMap<String, String> customPropStrings) {
        if (instance == null)
            instance = new PropertyUtils(customPropStrings);
    }

    //Directly use this method if only the default "Path" property is used
    public static synchronized PropertyUtils getInstance() {
        if (instance == null) {
            init((ArrayList<String>) null);
        }
        return instance;
    }

    //This ALWAYS MUST BE CALLED from all the other constructors
    private PropertyUtils() {
        //Add more values to the map here if more default properties are required in future
        allPropStrings.put(PATH, null);
        allPropStrings.put(OUT_PATH, null);
    }

    //Hide constructor for singleton
    private PropertyUtils(ArrayList<String> customPropStrings) {
        this();
        if (customPropStrings != null) {
            for (String prop : customPropStrings) {
                allPropStrings.put(prop, null);
            }
        }
    }

    //Hide constructor for singleton
    private PropertyUtils(HashMap<String, String> customPropStrings) {
        this();
        if (customPropStrings != null)
            allPropStrings.putAll(customPropStrings);
    }

    public HashMap<String, String> getAllPropStrings() {
        return allPropStrings;
    }

    // Looks like the best possible solution right now.
    // Ideally, this method should accept something that should be used
    // to fetch the value of property string from the allPropStrings list
    // But because we want to support arbitrary custom properties,
    // there seems to be no other choice but to use a direct String
    public String getProperty(String propertyStr) {
        String provided = System.getProperty(propertyStr);
        if (provided == null || provided.isEmpty()) {
            provided = allPropStrings.get(propertyStr);
        }
        return provided;
    }

    public String getPath() {
        return getProperty(PATH);
    }
    public String getOutPath() {
        return getProperty(OUT_PATH);
    }
}
