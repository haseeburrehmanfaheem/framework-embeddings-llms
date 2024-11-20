package com.aacr.helpers.parsers.models.val;

import java.util.ArrayList;
import java.util.HashMap;

public interface ValueResource {
    static ValueResource createValRes(String name, ArrayList<String> input) {

        ValueResource res = null;

        switch (name) {
            case BoolResource.name:
                String val = input.isEmpty() ? "" : input.get(0);
                res = new BoolResource(Boolean.parseBoolean(val));
                break;
            case StringArrayResource.name:
                res = new StringArrayResource(input);
                break;
            case StringResource.name:
                res = new StringResource(input.isEmpty() ? "" : input.get(0));
                break;
        }

        return res;
    }

    static ValueResource createValRes(String name, HashMap<String, String> input) {

        if (name.equals(PluralsResource.name)) {
            return new PluralsResource(input);
        }

        return null;
    }
}