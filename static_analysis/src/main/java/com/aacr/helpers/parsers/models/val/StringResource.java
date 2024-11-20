package com.aacr.helpers.parsers.models.val;

public class StringResource implements ValueResource {

    public static final String name = "string";

    public final String value;

    public StringResource(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "" + value;
    }
}
