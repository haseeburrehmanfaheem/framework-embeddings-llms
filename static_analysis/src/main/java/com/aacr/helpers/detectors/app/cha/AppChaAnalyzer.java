package com.aacr.helpers.detectors.app.cha;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.dex.instructions.Invoke;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
import com.opencsv.CSVWriter;
import com.aacr.helpers.detectors.ChaAnalyzer;
import com.aacr.helpers.detectors.app.cha.context.*;
import com.aacr.helpers.detectors.framework.ManagerDetector;
import com.aacr.helpers.parsers.ManifestParser;
import com.aacr.helpers.parsers.models.MapperResource;
import com.aacr.helpers.parsers.models.val.ValueResource;
import com.aacr.helpers.parsers.models.view.LayoutResource;
import com.aacr.helpers.parsers.models.view.preference.PreferenceScreenResource;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.aacr.helpers.detectors.app.cha.ChaUtils.initParentClasses;

public class AppChaAnalyzer extends ChaAnalyzer {

    private static final HashSet<String> knownAsyncEps = new HashSet<>(Arrays.asList(
            "onPreExecute",
            "doInBackground",
            "onPostExecute",
            "onProgressUpdate",
            "onCancelled"
    ));

    private static final HashSet<String> knownHandlerEps = new HashSet<>(Arrays.asList(
            "handleMessage"
    ));

    private static final HashSet<String> knownRunnableEps = new HashSet<>(Arrays.asList(
            "run"
    ));

    private static final HashSet<String> knownThreadEps = new HashSet<>(Arrays.asList(
            "run"
    ));

    public static final String FRAGMENT = "Fragment";
    public static final String ASYNC_TASK = "AsyncTask";
    public static final String HANDLER = "Handler";
    public static final String ACTIVITY = "Activity";
    public static final String SERVICE = "Service";
    public static final String RECEIVER = "Receiver";
    public static final String PROVIDER = "Provider";
    public static final String RUNNABLE = "Runnable";
    public static final String THREAD = "Thread";
    public static final String CALLBACK = "Callback";
    public static final String UNKNOWN = "Unknown";

    private final HashMap<String, ArrayList<IClass>> parentClasses = new HashMap<>();
//    private final CallbackContextDetector contextDetector;
    private final CallbackContextDetector contextDetector;
    private final DataflowContextDetector dfContextDetector;
    private final CallbackParentDetector callbackParentDetector;
    private final AppAccessControlDetector appAcDetector;
    private final HashMap<String, HashSet<ManagerDetector.ManagerChain>> managerChains;

    public AppChaAnalyzer(ClassHierarchy cha, HashMap<Integer, MapperResource> allResIdMap,
                          HashMap<String, HashMap<String, ValueResource>> valResMap,
                          HashMap<String, LayoutResource> layoutResMap,
                          HashMap<String, PreferenceScreenResource> preferenceResMap,
                          ManifestParser manifestParser,
                          HashMap<String, HashSet<ManagerDetector.ManagerChain>> managerChains) {
        super(cha, new HashSet<>(managerChains.keySet()));

        contextDetector = new CallbackContextDetector(cha, allResIdMap, valResMap,
                layoutResMap, preferenceResMap, manifestParser);
        dfContextDetector = DataflowContextDetector.getInstance(cha);
        appAcDetector = AppAccessControlDetector.getInstance(cha);

        callbackParentDetector = new CallbackParentDetector(cha);

        this.managerChains = managerChains;

        parentClasses.put(FRAGMENT, initParentFragments());
        parentClasses.put(ASYNC_TASK, initParentAsyncTasks());
        parentClasses.put(HANDLER, initParentHandlers());
        parentClasses.put(ACTIVITY, initParentActivities());
        parentClasses.put(SERVICE, initParentServices());
        parentClasses.put(RECEIVER, initParentReceivers());
        parentClasses.put(PROVIDER, initParentProviders());
        parentClasses.put(RUNNABLE, initParentRunnables());
        parentClasses.put(THREAD, initParentThreads());
    }

    public HashMap<String, ArrayList<ChainInfo>> extractChainInfo(CSVWriter appCw,
                                                                  CSVWriter methodPropCw) {
        HashMap<String, ArrayList<ArrayList<MethodInCallChain>>> allChains = analyze(methodPropCw);
        HashMap<String, ArrayList<ChainInfo>> result = new HashMap<>();

        appCw.writeNext(new String[] {
                "API", "Call Chain", "Top Component", "Entrypoint", "Non Cancellable Dialog",
                "Manifest Protection", "Dataflow Context", "Programmatic AC"
        });

        for (Map.Entry<String, ArrayList<ArrayList<MethodInCallChain>>> curChains : allChains.entrySet()) {
            ArrayList<ChainInfo> curChainInfos = new ArrayList<>();

            for(ArrayList<MethodInCallChain> curChain : curChains.getValue()) {
                try {
                    IClass curClass = cha.lookupClass(TypeReference.find(ClassLoaderReference.Application,
                            ChaUtils.getClassNameFromSignature(curChain.get(0).methodSign)));
                    HashSet<Pair<IClass, String>> detectedParents = null;
                    String detectedComp = "";
                    for (String comp : parentClasses.keySet()) {
                        if (isKnownComponent(curClass, comp)) {
                            detectedComp = comp;
                            break;
                        }
                    }
                    if (detectedComp.isEmpty()) {
                        if (isCallbackMethod(curChain.get(0).methodSign)) {
                            detectedComp = CALLBACK;
                            detectedParents = callbackParentDetector
                                    .getCallbackParents(curClass).stream()
                                    .map(clazz -> {
                                        String curComp = "";
                                        for (String comp : parentClasses.keySet()) {
                                            if (isKnownComponent(clazz, comp)) {
                                                curComp = comp;
                                                break;
                                            }
                                        }
                                        if (curComp.isEmpty())
                                            curComp = UNKNOWN;
                                        return Pair.make(clazz, curComp);
                                    }).collect(Collectors.toCollection(HashSet::new));
                        } else {
                            detectedComp = UNKNOWN;
                        }
                    }
                    Context curCompContext = null;
                    try {
                        curCompContext = contextDetector.getContext(curClass,
                                curChain.get(0).methodSign, detectedComp, detectedParents);
                    } catch (Exception e) {
                        //ignore
                    }
                    DataflowContextDetector.DataflowContext curDfContext = null;
                    ArrayList<String> methodSignChain = curChain.stream()
                            .map(c -> c.methodSign).collect(Collectors.toCollection(ArrayList::new));
                    try {
                        curDfContext = dfContextDetector.getDataFlowContext(curChains.getKey(),
                                methodSignChain);
                    } catch (Exception e) {
                        //ignore
                    }
                    HashSet<AppAccessControlDetector.AppProgrammaticAc> pAc = new HashSet<>();
                    //TODO: Uncomment this and think of ways to optimize it
//                    try {
//                        pAc = appAcDetector.extractAccessControls(methodSignChain);
//                    } catch (Exception e) {
//                        //ignore
//                    }
                    for (ArrayList<String> mChain : getMergedChains(curChain)) {
                        ChainInfo ci = new ChainInfo(mChain, detectedComp,
                                curCompContext, curDfContext, pAc);
                        saveChainToFile(appCw, mChain.get(mChain.size()-1), ci);
                        curChainInfos.add(ci);
                    }
                } catch (Exception e) {
                    //ignore
                }
            }

            result.put(curChains.getKey(), curChainInfos);
        }

        return result;
    }

    private HashSet<ArrayList<String>> getMergedChains(ArrayList<MethodInCallChain> curChain) {
        List<String> curChainSigns = curChain.stream().map(c -> c.methodSign).collect(Collectors.toList());
        HashSet<ManagerDetector.ManagerChain> relManagerChains = managerChains
                .get(curChain.get(curChain.size() - 1).methodSign);
        HashSet<ArrayList<String>> allMergedChains = new HashSet<>();
        for (ManagerDetector.ManagerChain mChain : relManagerChains) {
            if (mChain.chain.size() > 1) {
                ArrayList<String> mergedChain = new ArrayList<>(curChainSigns);
                mergedChain.addAll(mChain.chain.subList(1, mChain.chain.size()));
                allMergedChains.add(mergedChain);
            } else {
                allMergedChains.add(new ArrayList<>(curChainSigns));
            }
        }

        return allMergedChains;
    }

    @Override
    protected Collection<String> getCallSiteSignatures(IBytecodeMethod<?> m, IClass originalClass) throws InvalidClassFileException {
        Collection<String> callSites = m.getCallSites().stream()
                .map(c->c.getDeclaredTarget().getSignature())
                .collect(Collectors.toSet());

        if (m.getName() == null)
            return callSites;

        if (isKnownComponent(originalClass, ASYNC_TASK)
                && m.getName().toString().startsWith("execute"))
            callSites.addAll(getKnownAsyncCallSites(originalClass));

        if (isKnownComponent(originalClass, HANDLER)
                && m.getName().toString().startsWith("send"))
            callSites.addAll(getKnownHandlerCallSites(originalClass));

        if (isKnownComponent(originalClass, THREAD)
                && (m.getName().toString().startsWith("start")
                    || m.getName().toString().startsWith("resume")))
            callSites.addAll(getKnownThreadCallSites(originalClass));

        callSites.addAll(getRunnableCallSitesIfExists(m));

        return callSites;
    }

    private Collection<String> getKnownAsyncCallSites(IClass asyncTaskClass) {
        HashSet<String> result = new HashSet<>();
        for (IMethod m : asyncTaskClass.getDeclaredMethods()) {
            if (m.getName() != null && knownAsyncEps.contains(m.getName().toString())) {
                result.add(m.getSignature());
            }
        }
        for (IMethod m : asyncTaskClass.getAllMethods()) {
            if (!m.getDeclaringClass().equals(asyncTaskClass)
                    && m.getName() != null
                    && knownAsyncEps.contains(m.getName().toString()))
                result.add(getRealSignature(m, asyncTaskClass));
        }
        return result;
    }

    private Collection<String> getKnownHandlerCallSites(IClass handlerClass) {
        HashSet<String> result = new HashSet<>();
        for (IMethod m : handlerClass.getDeclaredMethods()) {
            if (m.getName() != null && knownHandlerEps.contains(m.getName().toString())) {
                result.add(m.getSignature());
            }
        }
        for (IMethod m : handlerClass.getAllMethods()) {
            if (!m.getDeclaringClass().equals(handlerClass)
                    && m.getName() != null
                    && knownHandlerEps.contains(m.getName().toString()))
                result.add(getRealSignature(m, handlerClass));
        }
        return result;
    }

    private Collection<String> getKnownThreadCallSites(IClass threadClass) {
        HashSet<String> result = new HashSet<>();
        for (IMethod m : threadClass.getDeclaredMethods()) {
            if (m.getName() != null && knownThreadEps.contains(m.getName().toString())) {
                result.add(m.getSignature());
            }
        }
        for (IMethod m : threadClass.getAllMethods()) {
            if (!m.getDeclaringClass().equals(threadClass)
                    && m.getName() != null
                    && knownThreadEps.contains(m.getName().toString()))
                result.add(getRealSignature(m, threadClass));
        }
        return result;
    }

    private HashSet<String> getKnownRunnableEps(IClass runnableClass) {
        HashSet<String> result = new HashSet<>();
        for (IMethod m : runnableClass.getDeclaredMethods()) {
            if (m.getName() != null && knownRunnableEps.contains(m.getName().toString())) {
                result.add(m.getSignature());
            }
        }
        for (IMethod m : runnableClass.getAllMethods()) {
            if (!m.getDeclaringClass().equals(runnableClass)
                    && m.getName() != null
                    && knownRunnableEps.contains(m.getName().toString()))
                result.add(getRealSignature(m, runnableClass));
        }
        return result;
    }

    private Collection<String> getRunnableCallSitesIfExists(IBytecodeMethod<?> curMethod) {
        HashSet<String> result = new HashSet<>();
        IClass runnableClass = null;
        try {
            for (Object ins : curMethod.getInstructions()) {
                if (ins instanceof Invoke) {
                    Invoke inv = (Invoke) ins;
                    IClass invClass = cha.lookupClass(TypeReference.find(ClassLoaderReference.Application, inv.clazzName));
                    if (invClass == null
                            || invClass.getName().getPackage().toString().startsWith("java.")
                            || invClass.getName().getPackage().toString().startsWith("android.")
                            || invClass.getName().getPackage().toString().startsWith("androidx."))
                        continue;
                    if (runnableClass != null && inv.descriptor.contains("Runnable")) {
                        result.addAll(getKnownRunnableEps(runnableClass));
                    } else if (inv.methodName.equals("<init>") && isKnownComponent(invClass, RUNNABLE)) {
                        runnableClass = invClass;
                    }
                }
            }
        } catch (Exception e) {
            //ignore
        }
        return result;
    }

    private boolean isKnownComponent(IClass c, String component) {
        if (c == null)
            return false;
        for (IClass p : parentClasses.get(component)) {
            if (c.equals(p) || cha.isSubclassOf(c, p) || c.getAllImplementedInterfaces().contains(p))
                return true;
        }

        return false;
    }

    private ArrayList<IClass> initParentAsyncTasks() {
        return initParentClasses((packageName, className) ->
                (packageName.startsWith("android/") || packageName.startsWith("androidx/"))
                        && className.equals("AsyncTask"), cha);
    }

    private ArrayList<IClass> initParentHandlers() {
        return initParentClasses((packageName, className) ->
                (packageName.startsWith("android/") || packageName.startsWith("androidx/"))
                        && className.equals("Handler"), cha);
    }

    private ArrayList<IClass> initParentFragments() {
        return initParentClasses((packageName, className) ->
                (packageName.startsWith("android/") || packageName.startsWith("androidx/"))
                        && className.endsWith("Fragment")/*className.equals("Fragment") || className.equals("DialogFragment"))*/, cha);
    }

    private ArrayList<IClass> initParentActivities() {
        return initParentClasses((packageName, className) ->
                (packageName.startsWith("android/") || packageName.startsWith("androidx/"))
                        && className.equals("Activity"), cha);
    }

    private ArrayList<IClass> initParentProviders() {
        return initParentClasses((packageName, className) ->
                (packageName.startsWith("android/") || packageName.startsWith("androidx/"))
                        && className.equals("ContentProvider"), cha);
    }

    private ArrayList<IClass> initParentReceivers() {
        return initParentClasses((packageName, className) ->
                (packageName.startsWith("android/") || packageName.startsWith("androidx/"))
                        && className.equals("BroadcastReceiver"), cha);
    }

    private ArrayList<IClass> initParentServices() {
        return initParentClasses((packageName, className) ->
                (packageName.startsWith("android/") || packageName.startsWith("androidx/"))
                        && className.equals("Service"), cha);
    }

    private ArrayList<IClass> initParentRunnables() {
        return initParentClasses((packageName, className) ->
                (packageName.startsWith("java/"))
                        && className.equals("Runnable"), cha);
    }

    private ArrayList<IClass> initParentThreads() {
        return initParentClasses((packageName, className) ->
                (packageName.startsWith("java/"))
                        && className.equals("Thread"), cha);
    }

    private void saveChainToFile(CSVWriter cw, String api, ChainInfo curChain) {
        cw.writeNext(ArrayUtils.add(curChain.getStrArray(), api));
    }

    public static class ChainInfo {
        public final ArrayList<String> chain;
        public final String topComponent;
        public final Context compContext;
        public final DataflowContextDetector.DataflowContext dataflowContext;
        public final HashSet<AppAccessControlDetector.AppProgrammaticAc> programmaticAc;

        private ChainInfo(ArrayList<String> chain, String topComponent, Context compContext,
                          DataflowContextDetector.DataflowContext dataflowContext,
                          HashSet<AppAccessControlDetector.AppProgrammaticAc> programmaticAc) {
            this.chain = chain;
            this.topComponent = topComponent;
            this.compContext = compContext;
            this.dataflowContext = dataflowContext;
            this.programmaticAc = programmaticAc;
        }

        public String toChainStr() {
            return chain == null || chain.isEmpty() ? "" : chain.toString();
        }

        public String toEpStr() {
            return compContext == null ? "" : compContext.toEpString();
        }

        public String toManifestStr() {
            return compContext == null ? "" : compContext.toManifestProtectionString();
        }

        public String toDataFlowStr() {
            return dataflowContext == null ? "" : dataflowContext.toString();
        }

        public String toProgrammaticAcStr() {
            return programmaticAc == null ? "" : programmaticAc.toString();
        }

        public String toNonCancellableDialogStr() {
            if (compContext instanceof ActivityFragmentContextDetector.ActivityFragmentContext)
                return ((ActivityFragmentContextDetector.ActivityFragmentContext) compContext)
                        .toNonCancellableDialogStr();
            return "";
        }

        public String[] getStrArray() {
            String[] strArr = new String[7];

            // Print the chain
            strArr[0] = toChainStr();

            // Print the top component
            strArr[1] = topComponent;

            // Print EP string
            strArr[2] = toEpStr();

            // Print non-cancellable dialog string
            strArr[3] = toNonCancellableDialogStr();

            // Print Manifest(s) string
            strArr[4] = toManifestStr();

            // Print Dataflow string
            strArr[5] = toDataFlowStr();

            // Print Programmatic AC string
            strArr[6] = toProgrammaticAcStr();

            return strArr;
        }
    }
}
