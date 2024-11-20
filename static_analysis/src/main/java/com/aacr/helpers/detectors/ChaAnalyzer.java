package com.aacr.helpers.detectors;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.dex.instructions.GetField;
import com.ibm.wala.dalvik.dex.instructions.Invoke;
import com.ibm.wala.dalvik.dex.instructions.PutField;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.opencsv.CSVWriter;
import com.aacr.helpers.detectors.app.cha.ChaUtils;

import java.util.*;
import java.util.stream.Collectors;

public class ChaAnalyzer {

    protected final ClassHierarchy cha;
    private final HashSet<String> serviceApis;

    private final HashMap<String, HashSet<String>> cbKeywords = new HashMap<>();

    private final HashSet<String> SWITCH_CHECKBOX_CALLBACKS = new HashSet<>(Arrays.asList(
            "onCheckedChanged",
            "onPreferenceChange"
    ));
    private final HashSet<String> SWITCH_CHECKBOX_KEYWORDS = new HashSet<>(Arrays.asList(
            "enable",
            "disable",
            "toggle",
            "switch",
            "on",
            "off",
            "turnOn",
            "turnOff",
            "update",
            "edit"
    ));

    private final HashSet<String> LONG_CLICK_CALLBACKS = new HashSet<>(Arrays.asList(
            "onLongClick",
            "onLongPress",
            "onLongTap"
    ));
    private final HashSet<String> LONG_CLICK_KEYWORDS = new HashSet<>(Arrays.asList(
            "select",
            "edit",
            "show",
            "open",
            "options"
    ));

    private final HashSet<String> DOUBLE_TAP_CALLBACKS = new HashSet<>(Arrays.asList(
            "onDoubleTap",
            "onDoubleTapConfirmed",
            "onDoubleClick"
    ));
    private final HashSet<String> DOUBLE_TAP_KEYWORDS = new HashSet<>(Arrays.asList(
            "wake"
    ));

    private final HashSet<String> SEEKBAR_CALLBACKS = new HashSet<>(Arrays.asList(
            "onProgressChanged",
            "onDrag"
    ));
    private final HashSet<String> SEEKBAR_KEYWORDS = new HashSet<>(Arrays.asList(
            "increase",
            "increment",
            "decrease",
            "decrement",
            "up",
            "down",
            "change",
            "update"
    ));

    private final HashSet<String> SWIPE_CALLBACKS = new HashSet<>(Arrays.asList(
            "onSwiped"
    ));
    private final HashSet<String> SWIPE_KEYWORDS = new HashSet<>(Arrays.asList(
            "unlock",
            "open",
            "close"
    ));

    private final HashMap<String, HashSet<String>> invokers = new HashMap<>();
    private final HashMap<String, ArrayList<ArrayList<MethodInCallChain>>> chains = new HashMap<>();
    private final HashMap<String, MethodInCallChain> methodProps = new HashMap<>();

    public ChaAnalyzer(ClassHierarchy cha, HashSet<String> serviceApis) {
        this.cha = cha;
        this.serviceApis = serviceApis;

        initSpecialKeywordMap(SWITCH_CHECKBOX_CALLBACKS, SWITCH_CHECKBOX_KEYWORDS);
        initSpecialKeywordMap(LONG_CLICK_CALLBACKS, LONG_CLICK_KEYWORDS);
        initSpecialKeywordMap(DOUBLE_TAP_CALLBACKS, DOUBLE_TAP_KEYWORDS);
        initSpecialKeywordMap(SEEKBAR_CALLBACKS, SEEKBAR_KEYWORDS);
        initSpecialKeywordMap(SWIPE_CALLBACKS, SWIPE_KEYWORDS);
    }

    private void initSpecialKeywordMap(HashSet<String> cbSet, HashSet<String> keywords) {
        for (String cb : cbSet)
            cbKeywords.put(cb, keywords);
    }

    public void saveMethodPropToFile(CSVWriter methodPropsCw, MethodInCallChain method) {
        if (methodPropsCw != null) {
            methodPropsCw.writeNext(new String[]{method.methodSign,
                    (method.bagOfWords == null || method.bagOfWords.isEmpty()
                            ? "" : method.bagOfWords.toString())});
        }
    }

    public HashMap<String, ArrayList<ArrayList<MethodInCallChain>>> analyze(CSVWriter methodPropCw) {
        createInvokers();
        for (String s : serviceApis) {
            if (invokers.containsKey(s))
                chains.put(s, extractChains(methodPropCw, s, new HashSet<>()));
        }

        return chains;
    }

    private ArrayList<ArrayList<MethodInCallChain>> extractChains(CSVWriter methodPropCw,
                                                                  String sink, HashSet<String> visited) {
        ArrayList<ArrayList<MethodInCallChain>> ans = new ArrayList<>();
        visited.add(sink);

        if (invokers.containsKey(sink)) {
            for (String inv : invokers.get(sink)) {
                if (!visited.contains(inv)) {
                    ArrayList<ArrayList<MethodInCallChain>> cur = extractChains(methodPropCw, inv,
                            new HashSet<>(visited));
                    cur.forEach(l -> {
                        l.add(getAndSaveMethodProp(methodPropCw, sink));
                        ans.add(l);
                    });
                }
            }
        }

        visited.remove(sink);
        if (ans.isEmpty())
            ans.add(new ArrayList<>(Collections.singletonList(getAndSaveMethodProp(methodPropCw, sink))));
        return ans;
    }

    private void createInvokers() {
        for (IClass c : cha) {
            for (IMethod m : c.getAllMethods()) {
                try {
                    if (m instanceof IBytecodeMethod) {
                        IBytecodeMethod<?> dm = (IBytecodeMethod<?>) m;
                        String realSign = getRealSignature(dm, c);
                        if(realSign == null
                                || realSign.isEmpty()
                                || realSign.startsWith("java.")
                                || realSign.startsWith("org.apache"))
                            continue;
                        for (String targetStr : getCallSiteSignatures(dm, c)) {
                            try {
                                if(targetStr == null
                                        || targetStr.isEmpty()
                                        || targetStr.startsWith("java.")
                                        || targetStr.startsWith("org.apache"))
                                    continue;
                                if (!invokers.containsKey(targetStr))
                                    invokers.put(targetStr, new HashSet<>());
                                invokers.get(targetStr).add(realSign);
                            } catch (Exception e) {
                                //ignore
                            }
                        }
                    }
                } catch (Exception e) {
                    //ignore
                }
            }
        }
    }

    private MethodInCallChain getAndSaveMethodProp(CSVWriter methodPropCw, String methodSign) {
        if (!methodProps.containsKey(methodSign)) {
            IMethod m = null;
            try {
                IClass curClass = cha.lookupClass(TypeReference.find(ClassLoaderReference.Application,
                        ChaUtils.getClassNameFromSignature(methodSign)));
                m = curClass.getMethod(Selector
                        .make(methodSign.substring(methodSign.lastIndexOf('.')+1)));
            } catch (Exception e) {
                //ignore
            }

            HashMap<String, HashSet<String>> methodPropMap = new HashMap<>();
            if (m != null) {
                HashSet<String> varNames = new HashSet<>();
                HashSet<String> invMethodNames = new HashSet<>();
                HashSet<String> specialKeywords = new HashSet<>();
                try {
                    if (m instanceof IBytecodeMethod) {
                        for (Object ins : ((IBytecodeMethod<?>) m).getInstructions()) {
                            try {
                                String fName = null;
                                String mName = null;

                                if (ins instanceof GetField)
                                    fName = ((GetField) ins).fieldName;
                                else if (ins instanceof PutField)
                                    fName = ((PutField) ins).fieldName;
                                else if (ins instanceof Invoke)
                                    mName = ((Invoke) ins).methodName;

                                if (fName != null && !fName.isEmpty())
                                    varNames.add(fName);
                                if (mName != null && !mName.isEmpty())
                                    invMethodNames.add(mName);
                            } catch (Exception e) {
                                //ignore
                            }
                        }
                    }
                    if (isCallbackMethod(methodSign)) {
                        String curCb = methodSign
                                .substring(methodSign.lastIndexOf('.')+1);
                        curCb = curCb.substring(0, curCb.indexOf('('));
                        if (cbKeywords.containsKey(curCb))
                            specialKeywords.addAll(cbKeywords.get(curCb));
                    }
                } catch (Exception e) {
                    //ignore
                }
                if (!varNames.isEmpty())
                    methodPropMap.put(MethodInCallChain.LOCAL_VARS, varNames);
                if (!invMethodNames.isEmpty())
                    methodPropMap.put(MethodInCallChain.INV_METHOD_NAMES, invMethodNames);
                if (!specialKeywords.isEmpty())
                    methodPropMap.put(MethodInCallChain.SPECIAL_KEYWORDS, specialKeywords);
            }
            MethodInCallChain method = new MethodInCallChain(methodSign,
                    methodPropMap.isEmpty() ? null : methodPropMap);
            saveMethodPropToFile(methodPropCw, method);
            methodProps.put(methodSign, method);
        }

        return methodProps.get(methodSign);
    }

    protected Collection<String> getCallSiteSignatures(IBytecodeMethod<?> m, IClass originalClass) throws InvalidClassFileException {
        return m.getCallSites().stream()
                .map(c->c.getDeclaredTarget().getSignature())
                .collect(Collectors.toList());
    }

    protected String getRealSignature(IMethod originalMethod, IClass declaringClass) {
        if (originalMethod.getDeclaringClass().equals(declaringClass))
            return originalMethod.getSignature();
        return declaringClass.getName().toString().replace('/','.').substring(1)
                + "." + originalMethod.getSelector().toString();
    }

    protected boolean isCallbackMethod(String method) {
        return method.substring(method.lastIndexOf('.')+1).startsWith("on");
    }

    public static class MethodInCallChain {
        public final String methodSign;
        public final HashMap<String, HashSet<String>> bagOfWords;

        public static final String LOCAL_VARS = "LOCAL_VARS";
        public static final String INV_METHOD_NAMES = "INV_METHOD_NAMES";
        public static final String SPECIAL_KEYWORDS = "SPECIAL_KEYWORDS";

        public MethodInCallChain(String methodSign, HashMap<String, HashSet<String>> bagOfWords) {
            this.methodSign = methodSign;
            this.bagOfWords = bagOfWords;
        }

        @Override
        public String toString() {
            return "MethodInCallChain{" +
                    "methodSign='" + methodSign + '\'' +
                    ", bagOfWords=" + bagOfWords +
                    '}';
        }
    }
}
