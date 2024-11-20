package com.aacr.helpers.parsers.models.val;

import java.util.HashMap;

public class PluralsResource implements ValueResource {

    public static final String name = "plurals";
    public static final String innerElem = "item";
    public static final String innerAttr = "quantity";

    public final HashMap<String, String> values;

    public PluralsResource(HashMap<String, String> values) {
        this.values = values;
    }

    @Override
    public String toString() {
        return "" + values;
    }
}
