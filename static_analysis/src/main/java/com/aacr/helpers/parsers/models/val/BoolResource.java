package com.aacr.helpers.parsers.models.val;

public class BoolResource implements ValueResource {

    public static final String name = "bool";

    public final boolean value;

    public BoolResource(boolean value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "" + value;
    }
}
