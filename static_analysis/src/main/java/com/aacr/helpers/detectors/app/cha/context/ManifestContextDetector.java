package com.aacr.helpers.detectors.app.cha.context;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.util.collections.Pair;
import com.aacr.helpers.accesscontrol.app.Component;
import com.aacr.helpers.accesscontrol.app.Protection;
import com.aacr.helpers.parsers.ManifestParser;

import java.util.HashMap;
import java.util.HashSet;

public class ManifestContextDetector {
    protected final ClassHierarchy cha;
    protected final ManifestParser manifestParser;
    private final HashMap<String, Context> cachedContexts = new HashMap<>();

    protected Context extractContext(IClass compClass, String methodInv, String detectedComp,
                                             HashSet<Pair<IClass, String>> detectedParents) {
        Protection manifestProtection = null;
        try {
            Component comp = manifestParser.getEpProtectionMap().get(compClass.getName().toString());
            manifestProtection = comp.getProtection();
        } catch (Exception e) {
            //ignore
        }
        return new ManifestContext(manifestProtection);
    }

    public ManifestContextDetector(ClassHierarchy cha, ManifestParser manifestParser) {
        this.cha = cha;
        this.manifestParser = manifestParser;
    }

    public Context getContext(IClass compClass, String methodInv, String detectedComp,
                                      HashSet<Pair<IClass, String>> detectedParents) {
        String className = compClass.getName().toString();
        if (!cachedContexts.containsKey(className))
            cachedContexts.put(className, extractContext(compClass, methodInv, detectedComp, detectedParents));

        return cachedContexts.get(className);
    }

    public static class ManifestContext implements Context {
        public final Protection manifestProtection;
        protected ManifestContext(Protection manifestProtection) {
            this.manifestProtection = manifestProtection;
        }

        @Override
        public String toEpString() {
            StringBuilder sb = new StringBuilder();
            if (manifestProtection != null
                    && manifestProtection.getOriginatingComponent() != null) {
                sb.append("ClassStr{")
                        .append(manifestProtection.getOriginatingComponent().getClassStr())
                        .append("}");

                sb.append("Type{")
                        .append(manifestProtection.getOriginatingComponent().getType().name())
                        .append("}");
            }

            return sb.toString();
        }

        public String toManifestProtectionString() {
            if (manifestProtection != null)
                return manifestProtection.toStringWithoutComponent();
            return "";
        }
    }
}
