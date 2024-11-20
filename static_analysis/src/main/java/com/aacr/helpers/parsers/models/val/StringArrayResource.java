package com.aacr.helpers.parsers.models.val;

import java.util.ArrayList;

public class StringArrayResource implements ValueResource {

    public static final String name = "string-array";
    public static final String innerElem = "item";

    public final ArrayList<String> values;

    public StringArrayResource(ArrayList<String> values) {
        this.values = values;
    }

    @Override
    public String toString() {
        return "" + values;
    }
}
