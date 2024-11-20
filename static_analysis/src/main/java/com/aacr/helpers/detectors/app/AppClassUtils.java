package com.aacr.helpers.detectors.app;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.types.TypeName;
import com.aacr.helpers.parsers.ManifestParser;

public class AppClassUtils {

    public static boolean shouldNotAnalyze(IClass c, AnalysisScope appScope) {
        if (c == null) return true;

        TypeName curClassName = c.getName();
        if (curClassName == null || curClassName.getPackage() == null)
            return true;

        return (!appScope.isApplicationLoader(c.getClassLoader())
                || curClassName.getPackage().toString().isEmpty()
                || !curClassName.getPackage().toString().startsWith(
                        ManifestParser.packageName.replace('.', '/')));
//                && !curClassName.getPackage().toString().startsWith("android/")
//                && !curClassName.getPackage().toString().startsWith("java/")
//                && !curClassName.getPackage().toString().startsWith("androidx/"));
    }

}
