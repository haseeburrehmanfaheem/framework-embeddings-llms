package com.aacr.helpers.detectors.app.cha.context;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.util.collections.Pair;
import com.aacr.helpers.detectors.app.cha.AppChaAnalyzer;
import com.aacr.helpers.parsers.ManifestParser;
import com.aacr.helpers.parsers.models.MapperResource;
import com.aacr.helpers.parsers.models.val.ValueResource;
import com.aacr.helpers.parsers.models.view.LayoutResource;
import com.aacr.helpers.parsers.models.view.WidgetResource;
import com.aacr.helpers.parsers.models.view.preference.PreferenceScreenResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class CallbackContextDetector extends ActivityFragmentContextDetector {

//    private final HashMap<String, CallbackRegistrations> cachedRegistrations;

    public CallbackContextDetector(ClassHierarchy cha, HashMap<Integer, MapperResource> allResIdMap,
                                   HashMap<String, HashMap<String, ValueResource>> valResMap,
                                   HashMap<String, LayoutResource> layoutResMap,
                                   HashMap<String, PreferenceScreenResource> preferenceResMap,
                                   ManifestParser manifestParser) {
        super(cha, allResIdMap, layoutResMap, preferenceResMap, valResMap, manifestParser);
//        cachedRegistrations = new HashMap<>();
    }

    @Override
    protected Context extractContext(IClass activityClass, String methodInv, String detectedComp,
                                     HashSet<Pair<IClass, String>> detectedParents) {

        if (!detectedComp.equals(AppChaAnalyzer.CALLBACK)
                || detectedParents == null
                || detectedParents.isEmpty())
            return super.extractContext(activityClass, methodInv, detectedComp, detectedParents);

        ArrayList<CallbackParentContext> allParents = new ArrayList<>();

        for (Pair<IClass, String> parent : detectedParents) {
            Context parentContext = getContext(parent.fst, methodInv, parent.snd, null);
            if (parentContext instanceof ActivityFragmentContext)
                allParents.add(new CallbackParentContext(null, (ActivityFragmentContext) parentContext));
            //TODO: Uncomment this code too
//            if (parentContext instanceof ActivityFragmentContext) {
//                ActivityFragmentContext activityFragmentContext = (ActivityFragmentContext) parentContext;
//                CallbackRegistrations classCallbackRegistrations = getCallbacks(parent.fst, activityFragmentContext);
                //TODO: Find target widget and uncomment the later code
//                WidgetResource widget = findCallbackWidget(activityFragmentContext, methodInv);
//                allParents.add(new CallbackParentContext(widget, activityFragmentContext));
//            } else {
//                System.out.println("Check!!");
//            }
        }

        return new CallbackContext(allParents);
    }

//    private CallbackRegistrations getCallbacks(IClass clazz, ActivityFragmentContext context) {
//        String clazzName = clazz.getName().toString();
//        if (!cachedRegistrations.containsKey(clazzName))
//            cachedRegistrations.put(clazzName, extractRegistrations(context));
//        return cachedRegistrations.get(clazzName);
//    }

//    private CallbackRegistrations extractRegistrations(ActivityFragmentContext activityFragmentContext) {
//        HashMap<String, ArrayList<CallbackRegistrations.RegistrationSite>> registrations = new HashMap<>();
//        for (CGNode node : activityFragmentContext.compCg) {
//            IClass curClass = node.getMethod().getDeclaringClass();
//            if (AppClassUtils.shouldNotAnalyze(curClass, cha.getScope()))
//                continue;
//            try {
//                for (SSAInstruction i : node.getIR().getInstructions()) {
//                    if (i instanceof SSAAbstractInvokeInstruction) {
//                        SSAAbstractInvokeInstruction inv = (SSAAbstractInvokeInstruction) i;
//                        if (isCallbackRegistration(inv)) {
//                            String callback = getCallbackFromRegistration(inv.getDeclaredTarget().getName().toString());
//                            if (!registrations.containsKey(callback))
//                                registrations.put(callback, new ArrayList<>());
//                            registrations.get(callback).add(processCallbackRegistration(inv, node));
//                        }
//                    }
//                }
//            } catch (Exception e) {
//                //ignore
//            }
//        }
//
//        return new CallbackRegistrations(registrations);
//    }

//    private CallbackRegistrations.RegistrationSite processCallbackRegistration(SSAAbstractInvokeInstruction inv,
//                                                                               CGNode node) {
//        DefUse du = node.getDU();
//        SSAInstruction def = du.getDef(inv.getUse(0));
//        String castClass = "";
//
//        while (def != null
//                && !(def instanceof SSANewInstruction)
//                && !(def instanceof SSAAbstractInvokeInstruction)) {
//            SSAInstruction newDef = null;
//            try {
//                if (def instanceof SSACheckCastInstruction) {
//                    newDef = du.getDef(def.getUse(0));
//                    TypeReference[] resultTypes = ((SSACheckCastInstruction) def).getDeclaredResultTypes();
//                    if (resultTypes.length > 0)
//                        castClass = resultTypes[0].getName().toString();
//                } else if (def instanceof SSAGetInstruction) {
//                    Iterator<SSAInstruction> uses = du.getUses(def.getUse(0));
//                    while (uses != null && uses.hasNext()) {
//                        SSAInstruction cur = uses.next();
//                        if (cur instanceof SSAPutInstruction)
//                            newDef = du.getDef(((SSAPutInstruction) cur).getVal());
//                    }
//                } else if (def instanceof SSAPutInstruction) {
//                    newDef = du.getDef(((SSAPutInstruction) def).getVal());
//                }
//            } catch (Exception e) {
//                //ignore
//            }
//            def = newDef;
//        }
//
//        if (def instanceof SSANewInstruction)
//            System.out.println("New detected!!!");
//        else if (def instanceof SSAAbstractInvokeInstruction) {
//            SSAAbstractInvokeInstruction abIns = (SSAAbstractInvokeInstruction) def;
//            String mName = abIns.getDeclaredTarget().getName().toString();
//            if (mName.equals("findViewById") || mName.contains("findPreference"))
//                System.out.println("WIDGET");
//        }
//        return new CallbackRegistrations.RegistrationSite(inv, node, castClass);
//    }

//    private boolean isCallbackRegistration(SSAAbstractInvokeInstruction abIns) {
//        if (abIns.getDeclaredTarget() == null || abIns.getDeclaredTarget().getName() == null)
//            return false;
//        String methodName = abIns.getDeclaredTarget().getName().toString();
//
//        return ((methodName.startsWith("set") || methodName.startsWith("add"))
//                && methodName.endsWith("Listener"));
//    }
//
//    private String getCallbackFromRegistration(String registrationName) {
//        int endInd = registrationName.lastIndexOf("Listener");
//        return registrationName.substring(3, endInd);
//    }

    public static class CallbackContext implements Context {
        public final ArrayList<CallbackParentContext> allParents;
        public CallbackContext(ArrayList<CallbackParentContext> allParents) {
            this.allParents = allParents;
        }

        @Override
        public String toEpString() {
            if (allParents == null || allParents.isEmpty())
                return "";
            StringBuilder sbEp = new StringBuilder();
            for (CallbackParentContext cbP : allParents) {
                if (cbP.parent != null)
                    sbEp.append(cbP.parent.toEpString());
            }

            return sbEp.toString();
        }

        public String toManifestProtectionString() {
            if (allParents == null || allParents.isEmpty())
                return "";
            StringBuilder sbEp = new StringBuilder();
            for (CallbackParentContext cbP : allParents) {
                if (cbP.parent != null)
                    sbEp.append(cbP.parent.toManifestProtectionString());
            }

            return sbEp.toString();
        }
    }

    public static class CallbackParentContext {
        public final WidgetResource widget;
        public final ActivityFragmentContext parent;
        public CallbackParentContext(WidgetResource widget, ActivityFragmentContext parent) {
            this.parent = parent;
            this.widget = widget;
        }
    }

//    private static class CallbackRegistrations {
//        public final HashMap<String, ArrayList<RegistrationSite>> registrationSites;
//
//        private CallbackRegistrations(HashMap<String, ArrayList<RegistrationSite>> registrationSites) {
//            this.registrationSites = registrationSites;
//        }
//
//        private static class RegistrationSite {
//            public final SSAAbstractInvokeInstruction instruction;
//            public final CGNode node;
//            public final WidgetResource widget;
//            public final String nonWidget;
//
//            private RegistrationSite(SSAAbstractInvokeInstruction instruction, CGNode node, WidgetResource widget) {
//                this.instruction = instruction;
//                this.node = node;
//                this.widget = widget;
//                this.nonWidget = null;
//            }
//
//            private RegistrationSite(SSAAbstractInvokeInstruction instruction, CGNode node, String nonWidget) {
//                this.instruction = instruction;
//                this.node = node;
//                this.widget = null;
//                this.nonWidget = nonWidget;
//            }
//        }
//    }
}
