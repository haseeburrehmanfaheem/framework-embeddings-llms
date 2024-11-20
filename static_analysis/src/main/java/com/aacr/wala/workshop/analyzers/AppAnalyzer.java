package com.aacr.wala.workshop.analyzers;

import com.aacr.wala.workshop.parsers.ManifestParser;

public class AppAnalyzer {
    public static void launch() {
        String appParentPath = System.getProperty("path");
        ManifestParser.parseManifest(appParentPath);
        /*
         * Start the app analysis from here
         */
    }
}
