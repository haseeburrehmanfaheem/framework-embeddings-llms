package com.aacr.wala.workshop.utils;

import java.io.File;

public class ValidationUtils {

    private static void exitPrintingError() {
        System.out.println("usage: gradle startAnalysis " +
                "-Dpath=[path to parent folder of analysis files] " +
                "-Dname=[RomName] " +
                "-Dtype=[App|Framework] " +
                "-Dout=[path to output folder]");
        System.exit(0);
    }

    public static void validateProperties() {
        for (PropertyUtils.Property property : PropertyUtils.Property.values()) {
            String propVal = PropertyUtils.getProperty(property);
            if (propVal == null || propVal.isEmpty()) {
                exitPrintingError();
            }
        }
    }
    public static void validateDexParent() {
        File dexDir = new File(PropertyUtils.getPath());
        if (!dexDir.exists() || !dexDir.isDirectory()) {
            exitPrintingError();
        }
    }
}
