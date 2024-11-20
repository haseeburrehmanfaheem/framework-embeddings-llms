package com.aacr.wala.workshop.utils;

public class PropertyUtils {
    public enum Property {
        PATH("path"),
        TYPE("type"),
        OUT("out"),
        NAME("name"),
        ;
        public final String key;
        Property(String key) {
            this.key = key;
        }
    }

    public static String getProperty(Property property) {
        return System.getProperty(property.key);
    }

    public static String getPath() {
        return getProperty(Property.PATH);
    }

    public static String getType() {
        return getProperty(Property.TYPE);
    }

    public static String getOut() {
        return getProperty(Property.OUT);
    }

    public static String getName() {
        return getProperty(Property.NAME);
    }

}
