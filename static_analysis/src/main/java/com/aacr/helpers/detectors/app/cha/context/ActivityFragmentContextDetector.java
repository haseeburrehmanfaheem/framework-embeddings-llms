package com.aacr.helpers.detectors.app.cha.context;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.collections.Pair;
import com.aacr.helpers.accesscontrol.app.Component;
import com.aacr.helpers.accesscontrol.app.Protection;
import com.aacr.helpers.detectors.app.AppClassUtils;
import com.aacr.helpers.detectors.app.AppEntrypoint;
import com.aacr.helpers.detectors.app.cha.AppChaAnalyzer;
import com.aacr.helpers.detectors.app.cha.ChaUtils;
import com.aacr.helpers.parsers.ManifestParser;
import com.aacr.helpers.parsers.models.MapperResource;
import com.aacr.helpers.parsers.models.val.ValueResource;
import com.aacr.helpers.parsers.models.view.LayoutResource;
import com.aacr.helpers.parsers.models.view.preference.PreferenceScreenResource;
import com.aacr.helpers.utils.CallGraphUtils;
import com.aacr.helpers.utils.InstructionUtils;

import java.util.*;

public class ActivityFragmentContextDetector extends ManifestContextDetector {

    private final HashMap<Integer, MapperResource> allResIdMap;
    private final HashMap<String, LayoutResource> layoutResMap;
    private final HashMap<String, HashMap<String, ValueResource>> valResMap;
    HashMap<String, PreferenceScreenResource> preferenceResMap;

    private static final HashSet<String> DYNAMIC_TEXT_SETTERS = new HashSet<>(Arrays.asList(
            "setText",
            "addText",
            "setTitle",
            "setMessage",
            "setSummary",
            "setSingleLineTitle"
    ));

    private static final HashSet<String> LAYOUT_SETTERS = new HashSet<>(Arrays.asList(
            "setContentView",
            "inflate",
            "setLayoutResource"
    ));

    private static final HashSet<String> PREF_SETTERS = new HashSet<>(Arrays.asList(
            "addPreferencesFromResource"
    ));

    public ActivityFragmentContextDetector(ClassHierarchy cha, HashMap<Integer, MapperResource> allResIdMap,
                                           HashMap<String, LayoutResource> layoutResMap,
                                           HashMap<String, PreferenceScreenResource> preferenceResMap,
                                           HashMap<String, HashMap<String, ValueResource>> valResMap,
                                           ManifestParser manifestParser) {
        super(cha, manifestParser);
        this.allResIdMap = allResIdMap;
        this.layoutResMap = layoutResMap;
        this.preferenceResMap = preferenceResMap;
        this.valResMap = valResMap;
    }

    @Override
    protected Context extractContext(IClass activityClass, String methodInv, String detectedComp,
                                             HashSet<Pair<IClass, String>> detectedParents) {
        ManifestContext manifestContext = (ManifestContext) super.extractContext(activityClass,
                methodInv, detectedComp, detectedParents);

        if (!detectedComp.equals(AppChaAnalyzer.ACTIVITY) && !detectedComp.equals(AppChaAnalyzer.FRAGMENT))
            return manifestContext;

        Collection<AppEntrypoint> eps = ChaUtils.getEpsFilterByMethodName(activityClass, cha, method ->
            Component.Type.FRAGMENT.knownEpMethods.contains(method)
                    || Component.Type.ACTIVITY.knownEpMethods.contains(method)
        );
        AnalysisOptions options = CallGraphUtils.generateAnalysisOptions(cha.getScope(), cha,
                eps, AnalysisOptions.ReflectionOptions.NONE, null);
        SSAPropagationCallGraphBuilder builder = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options,
                new AnalysisCacheImpl(new DexIRFactory()), cha);
        try {
            return processComponent(builder.makeCallGraph(options, null),
                    manifestContext.manifestProtection);
        } catch (Exception e) {
            throw new RuntimeException("Error building CG", e);
        }
    }

    private ActivityFragmentContext processComponent(CallGraph compCg, Protection manifestProtection) {

        HashSet<LayoutResource> curLayouts = new HashSet<>();
        HashSet<PreferenceScreenResource> curPrefScreens = new HashSet<>();
        HashSet<String> dynamicUiElements = new HashSet<>();
        boolean nonCancellableDialog = false;
        for (CGNode node : compCg) {
            IClass curClass = node.getMethod().getDeclaringClass();
            if (AppClassUtils.shouldNotAnalyze(curClass, cha.getScope()))
                continue;
            try {
                for (SSAInstruction i : node.getIR().getInstructions()) {
                    if (i instanceof SSAAbstractInvokeInstruction) {
                        SSAAbstractInvokeInstruction inv = (SSAAbstractInvokeInstruction) i;
                        if (isLayoutSetter(inv)) {
                            for (int layoutId : getIdFromInv(inv, node, compCg)) {
                                if (layoutId != -1) {
                                    MapperResource res = allResIdMap.get(layoutId);
                                    curLayouts.add(res.toLayoutRes(layoutResMap));
                                }
                            }
                        } else if (isPrefSetter(inv)) {
                            for (int prefId : getIdFromInv(inv, node, compCg)) {
                                if (prefId != -1) {
                                    MapperResource res = allResIdMap.get(prefId);
                                    curPrefScreens.add(res.toPrefScreenRes(preferenceResMap));
                                }
                            }
                        } else if (isDynamicResAddition(inv)) {
                            int paramValue = inv.getUse(InstructionUtils.getParameterIndex(inv, 0));
                            dynamicUiElements.addAll(getStrFromVal(paramValue, node, compCg));
                        } else if (isSetCancellable(inv)) {
                            int paramValue = inv.getUse(InstructionUtils.getParameterIndex(inv, 0));
                            nonCancellableDialog = isNonCancellable(paramValue, node, compCg);
                        }
                    }
                }
            } catch (Exception e) {
                //ignore
            }
        }

        return new ActivityFragmentContext(curLayouts, curPrefScreens, dynamicUiElements,
                manifestProtection, nonCancellableDialog);
    }

    private ArrayList<Integer> getIdFromInv(SSAAbstractInvokeInstruction inv, CGNode node, CallGraph compCg) {
        ArrayList<Integer> layoutIds = new ArrayList<>();
        HashSet<Object> defs = DataflowContextDetector.getInstance(cha).getDefFromVal(inv.getUse(1),
                node, compCg, new HashSet<>());
        for (Object def : defs) {
            if (def instanceof Number)
                layoutIds.add(((Number) def).intValue());
        }

        return layoutIds;
    }

    private HashSet<String> getStrFromVal(int val, CGNode node, CallGraph compCg) {
        HashSet<String> retVal = new HashSet<>();
        try {
            DataflowContextDetector dfCtxDet = DataflowContextDetector.getInstance(cha);
            HashSet<Object> paramDef = dfCtxDet.getDefFromVal(val, node, compCg, new HashSet<>());
            for (Object def : paramDef) {
                String defStr = "";
                if (def instanceof Number)
                    defStr = getStringFromResInt(((Number) def).intValue());
                else if (def instanceof String)
                    defStr = (String) def;

                if (!defStr.isEmpty())
                    retVal.add(defStr);
            }
        } catch (Exception e) {
            //ignore
        }

        return retVal;
    }

    private boolean isNonCancellable(int val, CGNode node, CallGraph compCg) {
        try {
            DataflowContextDetector dfCtxDet = DataflowContextDetector.getInstance(cha);
            HashSet<Object> paramDef = dfCtxDet.getDefFromVal(val, node, compCg, new HashSet<>());
            for (Object def : paramDef) {
                String defStr = "";
                if (def instanceof Number
                        && ((Number) def).intValue() == 0)
                    return true;
            }
        } catch (Exception e) {
            //ignore
        }

        return false;
    }

    private String getStringFromResInt(int resIntValue) {
        try {
            return allResIdMap.get(resIntValue)
                    .toValRes(valResMap)
                    .toString();
        } catch (Exception e) {
            //ignore
            return "";
        }
    }

    private boolean isLayoutSetter(SSAAbstractInvokeInstruction abIns) {
        if (abIns.getDeclaredTarget() == null || abIns.getDeclaredTarget().getName() == null)
            return false;

        return LAYOUT_SETTERS.contains(abIns.getDeclaredTarget().getName().toString());
    }

    private boolean isPrefSetter(SSAAbstractInvokeInstruction abIns) {
        if (abIns.getDeclaredTarget() == null || abIns.getDeclaredTarget().getName() == null)
            return false;

        return PREF_SETTERS.contains(abIns.getDeclaredTarget().getName().toString());
    }

    private boolean isDynamicResAddition(SSAAbstractInvokeInstruction abIns) {
        if (abIns.getDeclaredTarget() == null || abIns.getDeclaredTarget().getName() == null)
            return false;

        return DYNAMIC_TEXT_SETTERS.contains(abIns.getDeclaredTarget().getName().toString());
    }

    private boolean isSetCancellable(SSAAbstractInvokeInstruction abIns) {
        if (abIns.getDeclaredTarget() == null || abIns.getDeclaredTarget().getName() == null)
            return false;

        return abIns.getDeclaredTarget().getName().toString().equals("setCancelable");
    }

    public static class ActivityFragmentContext extends ManifestContext {
        public final HashSet<LayoutResource> layouts;
        public final HashSet<PreferenceScreenResource> preferences;
        public final HashSet<String> dynamicElements;
        public final boolean nonCancellableDialog;

        public ActivityFragmentContext(HashSet<LayoutResource> layouts,
                                       HashSet<PreferenceScreenResource> preferences,
                                       HashSet<String> dynamicElements,
                                       Protection manifestProtection,
                                       boolean nonCancellableDialog) {
            super(manifestProtection);
            this.layouts = layouts;
            this.preferences = preferences;
            this.dynamicElements = dynamicElements;
            this.nonCancellableDialog = nonCancellableDialog;
        }

        @Override
        public String toEpString() {
            StringBuilder sb = new StringBuilder();
            if (layouts != null && !layouts.isEmpty())
                sb.append("Layouts{")
                        .append(layouts)
                        .append("}");

            if (dynamicElements != null && !dynamicElements.isEmpty())
                sb.append("DynamicStrings{")
                        .append(dynamicElements)
                        .append("}");

            if (preferences != null && !preferences.isEmpty())
                sb.append("Preferences{")
                        .append(preferences)
                        .append("}");

            return sb.append(super.toEpString()).toString();
        }

        public String toNonCancellableDialogStr() {
            return "{Non cancellable dialog=" + nonCancellableDialog + "}";
        }
    }
}
