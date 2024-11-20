package com.aacr.helpers.detectors.app.cha.context;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.*;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.UnimplementedError;
import com.aacr.helpers.accesscontrol.framework.AccessControlUtils;
import com.aacr.helpers.dataflow.DefUseChain;
import com.aacr.helpers.detectors.app.cha.CachedCallGraphs;
import com.aacr.helpers.detectors.app.cha.ChaUtils;
import com.aacr.helpers.utils.InstructionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.aacr.helpers.detectors.app.cha.ChaUtils.initParentClasses;

public class DataflowContextDetector {
    private final ClassHierarchy cha;

    private final int MAX_DEPTH = 15;

    private static final int CACHE_MAX_SIZE = 1000;
    private static final Cache<Pair<SSAInstruction, CGNode>, HashSet<Object>> cachedRets
            = CacheBuilder.newBuilder().maximumSize(CACHE_MAX_SIZE).build();
    private static final Cache<Pair<Integer, CGNode>, HashSet<Object>> cachedDefs
            = CacheBuilder.newBuilder().maximumSize(CACHE_MAX_SIZE).build();
    private static final Cache<Pair<Integer, CGNode>, HashSet<Object>> cachedUses
            = CacheBuilder.newBuilder().maximumSize(CACHE_MAX_SIZE).build();
    private final String CG_PARAM_DEF = "CG_PARAM_DEF";
    private final HashSet<String> DO_NOT_TRACE_PARAMS = new HashSet<>(Arrays.asList(
            "Landroid/content/Context"
    ));
    private final ArrayList<IClass> DYNAMIC_GETTER_CLASSES = new ArrayList<>();
    private final HashSet<String> DYNAMIC_GETTER_METHODS = new HashSet<>(Arrays.asList(
            "getString",
            "getText",
            "getInteger",
            "getStringArray",
            "getTextArray",
            "getIntArray",
            "getQuantityString",
            "getQuantityText",
            "getBoolean"
    ));

    private final ArrayList<IClass> ANDROID_UI_CLASSES = new ArrayList<>();
    private final ArrayList<IClass> ANDROID_VIEW_CLASS;
    private final ArrayList<IClass> ANDROID_PREFERENCE_CLASS;
    private final ArrayList<IClass> ANDROID_FRAGMENT_CLASS;
    private final ArrayList<IClass> ANDROID_ACTIVITY_CLASS;

    private final ArrayList<IClass> ANDROID_INTENT_CLASS;
    private final ArrayList<IClass> ANDROID_BUNDLE_CLASS;
    private final ArrayList<IClass> ANDROID_CONTEXT_CLASS;
    private final ArrayList<IClass> ANDROID_RESOURCES_CLASS;
    private final ArrayList<IClass> ANDROID_USER_HANDLE_CLASS;

    private final IClass COLLECTION_CLASS;
    private final HashSet<String> COLLECTION_MODIFIERS = new HashSet<>(Arrays.asList(
            "add",
            "set",
            "put",
            "addAll",
            "setAll",
            "putAll"
    ));
    private final HashSet<String> COLLECTION_GETTERS = new HashSet<>(Arrays.asList(
            "get",
            "remove",
            "pop",
            "getAll",
            "removeAll"
    ));

    private final HashMap<String, Integer> COLLECTION_MODIFIER_PARAM_NUM = new HashMap<>();

    private final List<Class<?>> PROBLEMATIC_CLASSES = Arrays.asList(
            SSAPhiInstruction.class,
            SSAAbstractUnaryInstruction.class,
            SSAAbstractBinaryInstruction.class
    );

    private static DataflowContextDetector instance = null;

    public synchronized static DataflowContextDetector getInstance(ClassHierarchy cha) {
        if (cha == null)
            throw new RuntimeException("CHA cannot be null");
        if (instance == null)
            instance = new DataflowContextDetector(cha);

        return instance;
    }

    private DataflowContextDetector(ClassHierarchy cha) {
        this.cha = cha;

        COLLECTION_CLASS = cha.lookupClass(TypeReference
                .find(ClassLoaderReference.Primordial, "Ljava/util/Collection"));

        ANDROID_INTENT_CLASS = initCompClasses("Intent");
        ANDROID_BUNDLE_CLASS = initCompClasses("Bundle");
        ANDROID_CONTEXT_CLASS = initCompClasses("Context");
        ANDROID_RESOURCES_CLASS = initCompClasses("Resources");
        ANDROID_USER_HANDLE_CLASS = initCompClasses("UserHandle");

        ANDROID_VIEW_CLASS = initCompClasses("View");
        ANDROID_PREFERENCE_CLASS = initCompClasses("Preference");
        ANDROID_FRAGMENT_CLASS = initCompClasses("Fragment");
        ANDROID_ACTIVITY_CLASS = initCompClasses("Activity");

        ANDROID_UI_CLASSES.addAll(ANDROID_VIEW_CLASS);
        ANDROID_UI_CLASSES.addAll(ANDROID_PREFERENCE_CLASS);
        //ANDROID_UI_CLASSES.addAll(ANDROID_FRAGMENT_CLASS);
        //ANDROID_UI_CLASSES.addAll(ANDROID_ACTIVITY_CLASS);

        DYNAMIC_GETTER_CLASSES.addAll(ANDROID_CONTEXT_CLASS);
        DYNAMIC_GETTER_CLASSES.addAll(ANDROID_RESOURCES_CLASS);
        DYNAMIC_GETTER_CLASSES.addAll(ANDROID_FRAGMENT_CLASS);
        DYNAMIC_GETTER_CLASSES.addAll(ANDROID_ACTIVITY_CLASS);

        COLLECTION_MODIFIER_PARAM_NUM.put("add", 1);
        COLLECTION_MODIFIER_PARAM_NUM.put("set", 2);
        COLLECTION_MODIFIER_PARAM_NUM.put("put", 2);
        COLLECTION_MODIFIER_PARAM_NUM.put("addAll", 1);
        COLLECTION_MODIFIER_PARAM_NUM.put("putAll", 1);
    }

    public DataflowContext getDataFlowContext(String api, ArrayList<String> curChain) {

        if (curChain.size() < 2)
            return null;

        ArrayList<HashSet<Object>> paramDefs = null;
        HashSet<Object> resultUses = null;

        int numParams = getNumberOfParams(api);
        TypeReference retType = getReturnType(api);
        CallGraph singleEpCg = CachedCallGraphs.buildSingleEpCg(curChain.get(curChain.size()-2), cha);

        if (numParams > 0) {
            paramDefs = new ArrayList<>();
            for (int param = 1; param <= numParams; param++) {
                HashSet<Object> curParamDefs = new HashSet<>();
                for(Pair<Integer, TypeReference> pVal : getParamValNums(singleEpCg, api, param))
                    if (!DO_NOT_TRACE_PARAMS.contains(pVal.snd.getName().toString()))
                        curParamDefs.addAll(traceValDefs(singleEpCg, curChain,
                                curChain.size() - 2, pVal.fst));
                    else
                        curParamDefs.add(pVal.snd.getName().toString());
                paramDefs.add(curParamDefs);
            }
        }

        if (retType != null && !retType.equals(TypeReference.Void)) {
            CGNode node = (CGNode) singleEpCg.getEntrypointNodes().toArray()[0];
            resultUses = traceResultUses(node, curChain, curChain.size() - 2,
                    getInvResultValNums(singleEpCg, api), new HashSet<>());
        }

        return new DataflowContext(
                api,
                paramDefs == null ? null : paramDefs.stream()
                        .map(p -> Pair.make(p, normalizeParamFlowSet(p)))
                        .collect(Collectors.toList()),
                Pair.make(resultUses, normalizeRetValFlowSet(resultUses))
        );
    }

    private HashSet<Object> traceResultUses(CGNode node, ArrayList<String> curChain,
                                            int curMethodInChain, List<Integer> resultValNums,
                                            HashSet<Pair<CGNode, Integer>> visited) {

        HashSet<Object> uses = new HashSet<>();
        for (int val : resultValNums) {
            if (visited.contains(Pair.make(node, val)) || visited.size() >= MAX_DEPTH)
                continue;
            visited.add(Pair.make(node, val));
            HashSet<Object> curUses = cachedUses.getIfPresent(Pair.make(val, node));
            if (curUses == null) {
                curUses = new HashSet<>();
                for (Iterator<SSAInstruction> it = node.getDU().getUses(val); it.hasNext(); ) {
                    SSAInstruction ins = it.next();
                    if (ins instanceof SSAReturnInstruction) {
                        if (curMethodInChain > 0) {
                            String nextMethod = curChain.get(curMethodInChain - 1);
                            CallGraph nextCg = CachedCallGraphs.buildSingleEpCg(nextMethod, cha);
                            CGNode nextNode = (CGNode) nextCg.getEntrypointNodes().toArray()[0];
                            curUses.addAll(traceResultUses(nextNode, curChain,
                                    curMethodInChain - 1,
                                    getInvResultValNums(nextCg, curChain.get(curMethodInChain)), visited));
                        } else {
                            curUses.add(Pair.make("Return ["
                                    + node.getMethod().getReturnType()
                                    + "]value to top method", ins));
                        }
                    } else if (ins instanceof SSAAbstractInvokeInstruction) {
                        SSAAbstractInvokeInstruction abIns = (SSAAbstractInvokeInstruction) ins;
                        if (isUiMethod(abIns) || isIntentOrBundleReturnMethod(abIns))
                            curUses.add(ins);
                        else if (abIns.getNumberOfReturnValues() > 0){
                            if (shouldTraceReturnUses(abIns, val, new HashSet<>()))
                                curUses.addAll(traceResultUses(node, curChain, curMethodInChain,
                                        Collections.singletonList(abIns.getReturnValue(0)), visited));
                        }
                    } else if (ins instanceof SSAFieldAccessInstruction) {
                        IClass clazz = node.getMethod().getDeclaringClass();
                        FieldReference curField = ((SSAFieldAccessInstruction) ins).getDeclaredField();

                        for (Pair<SSAGetInstruction, CGNode> p : getAllFieldGet(curField, clazz)) {
                            curUses.addAll(traceResultUses(p.snd, curChain, curMethodInChain,
                                    Collections.singletonList(p.fst.getDef()), visited));
                        }
                        IClass fieldClass = null;
                        try {
                            fieldClass = cha.lookupClass(curField.getFieldType());
                        } catch (Exception e) {
                            //ignore
                        }
                        if (fieldClass != null && cha.implementsInterface(fieldClass, COLLECTION_CLASS)) {
                            try {
                                ArrayList<Pair<SSAAbstractInvokeInstruction, CGNode>> fieldModifiers
                                        = getAllCollectionGetters(curField, clazz);
                                for (Pair<SSAAbstractInvokeInstruction, CGNode> m : fieldModifiers) {
                                    curUses.addAll(traceResultUses(m.snd, curChain, curMethodInChain,
                                            Collections.singletonList(m.fst.getDef()), visited));
                                }
                            } catch (Exception e) {
                                //ignore
                            }
                        }
                    } else if (isProblematicInstrType(ins)
                            || ins instanceof SSANewInstruction
                            || ins instanceof SSACheckCastInstruction) {
                        curUses.addAll(traceResultUses(node, curChain, curMethodInChain,
                                Collections.singletonList(ins.getDef()), visited));
                    } else {
                        curUses.add(ins);
                    }
                }
                cachedUses.put(Pair.make(val, node), curUses);
            }
            uses.addAll(curUses);
        }

        return uses;
    }

    private ArrayList<Integer> getInvResultValNums(CallGraph singleEpCg, String invMethod) {
        CGNode node = (CGNode) singleEpCg.getEntrypointNodes().toArray()[0];
        ArrayList<Integer> ret = new ArrayList<>();
        for (SSAInstruction ins : node.getIR().getInstructions()) {
            if (ins instanceof SSAAbstractInvokeInstruction) {
                try {
                    SSAAbstractInvokeInstruction inv = (SSAAbstractInvokeInstruction) ins;
                    if (inv.getDeclaredTarget().getSignature().equals(invMethod)
                            && inv.getNumberOfReturnValues() == 1)
                        ret.add(inv.getReturnValue(0));
                } catch (Exception e) {
                    //ignore
                }
            }
        }

        return ret;
    }

    private boolean shouldTraceReturnUses(SSAAbstractInvokeInstruction abIns, int val,
                                          HashSet<Pair<SSAInstruction, CGNode>> visited) {
        CallGraph nextCg = null;
        try {
            nextCg = CachedCallGraphs.buildSingleEpCg(abIns.getDeclaredTarget(), cha);
        } catch (Exception | UnimplementedError e) {
            //ignore
        }
        if (nextCg == null)
            return false;
        CGNode nextNode = (CGNode) nextCg.getEntrypointNodes().toArray()[0];
        int paramValNum = nextNode.getIR().getParameter(getParamNumFromVal(abIns, val));

        Stack<Pair<SSAInstruction, Integer>> toAnalyze = new Stack<>();

        addAllUsesToStack(toAnalyze, visited, nextNode, paramValNum);
        while (!toAnalyze.isEmpty()) {
            Pair<SSAInstruction, Integer> insP = toAnalyze.pop();
            SSAInstruction ins = insP.fst;
            if (ins instanceof SSAReturnInstruction)
                return true;
            else if (isProblematicInstrType(ins)
                    || ins instanceof SSANewInstruction
                    || ins instanceof SSAFieldAccessInstruction)
                addAllUsesToStack(toAnalyze, visited, nextNode, ins.getDef());
            else if (ins instanceof SSAAbstractInvokeInstruction) {
                SSAAbstractInvokeInstruction nextAbIns = (SSAAbstractInvokeInstruction) ins;
                if (nextAbIns.getNumberOfReturnValues() > 0) {
                    if (isPrimitive(nextAbIns)
                            || shouldTraceReturnUses(nextAbIns, insP.snd, visited))
                        addAllUsesToStack(toAnalyze, visited, nextNode, nextAbIns.getReturnValue(0));
                }
            }
        }

        return false;
    }

    private void addAllUsesToStack(Stack<Pair<SSAInstruction, Integer>> st,
                                   HashSet<Pair<SSAInstruction, CGNode>> visited, CGNode node, int v) {
        for (Iterator<SSAInstruction> it = node.getDU().getUses(v); it.hasNext(); ) {
            SSAInstruction i = it.next();
            Pair<SSAInstruction, CGNode> curP = Pair.make(i, node);
            if (!visited.contains(curP)) {
                visited.add(curP);
                st.push(Pair.make(i, v));
            }
        }
    }

    private boolean isPrimitive(SSAAbstractInvokeInstruction inv) {
        try {
            return inv.getDeclaredTarget().getDeclaringClass().isPrimitiveType();
        } catch (Exception e) {
            //ignore
        }

        return true;
    }

    private boolean isUiMethod(SSAAbstractInvokeInstruction inv) {
        try {
            MethodReference mr = inv.getDeclaredTarget();
            IClass curClass = cha.lookupClass(mr.getDeclaringClass());
            return isEqualOrSubclassOfAny(curClass, ANDROID_UI_CLASSES);
        } catch (Exception e) {
            //ignore
        }

        return false;
    }

    private boolean isIntentOrBundleReturnMethod(SSAAbstractInvokeInstruction inv) {
        try {
            MethodReference mr = inv.getDeclaredTarget();
            IClass curClass = cha.lookupClass(mr.getDeclaringClass());
            return isEqualOrSubclassOfAny(curClass, ANDROID_INTENT_CLASS)
                    || isEqualOrSubclassOfAny(curClass, ANDROID_BUNDLE_CLASS);
        } catch (Exception e) {
            //ignore
        }

        return false;
    }

    private HashSet<Object> traceValDefs(CallGraph cg, ArrayList<String> curChain,
                                         int curMethodInChain, int valNum) {
        CGNode node = (CGNode) cg.getEntrypointNodes().toArray()[0];
        SymbolTable st = node.getIR().getSymbolTable();

        HashSet<Object> curDefs = cachedDefs.getIfPresent(Pair.make(valNum, node));
        if (curDefs == null) {
            curDefs = new HashSet<>();
            int paramNum = -1;
            if (!st.isParameter(valNum)) {
                HashSet<Object> defs = getDefFromVal(valNum, node, cg, new HashSet<>());
                for (Object def : defs) {
                    if (def instanceof Pair<?, ?>
                            && ((Pair<?, ?>) def).fst instanceof String
                            && ((Pair<?, ?>) def).snd instanceof Integer
                            && ((Pair<?, ?>) def).fst.equals(CG_PARAM_DEF))
                        paramNum = (Integer) ((Pair<?, ?>) def).snd;
                    else
                        curDefs.add(def);
                }
            }
            if (st.isParameter(valNum) || paramNum != -1) {
                if (curMethodInChain > 0) {
                    String nextMethod = curChain.get(curMethodInChain - 1);
                    CallGraph nextCg = CachedCallGraphs.buildSingleEpCg(nextMethod, cha);
                    for (Pair<Integer, TypeReference> pVal : getParamValNums(nextCg,
                            curChain.get(curMethodInChain),
                            paramNum == -1 ? getParamNumFromVal(node, valNum) : paramNum))
                        if (!DO_NOT_TRACE_PARAMS.contains(pVal.snd.getName().toString()))
                            curDefs.addAll(traceValDefs(nextCg, curChain,
                                    curMethodInChain - 1, pVal.fst));
                        else
                            curDefs.add(pVal.snd.getName().toString());
                } else {
                    String pType = "";
                    try {
                        int pNum = paramNum == -1 ? getParamNumFromVal(node, valNum) : paramNum;
                        pType = node.getMethod().getParameterType(pNum).getName().toString();
                    } catch (Exception e) {
                        //ignore
                    }
                    curDefs.add("From parameter: [" + pType + "] of top method");
                }
            }
            cachedDefs.put(Pair.make(valNum, node), curDefs);
        }

        return curDefs;
    }

    private ArrayList<Pair<Integer, TypeReference>> getParamValNums(CallGraph cg, String method, int param) {
        ArrayList<Pair<Integer, TypeReference>> retVals = new ArrayList<>();
        CGNode node = (CGNode) cg.getEntrypointNodes().toArray()[0];
        for (SSAInstruction ins : node.getIR().getInstructions()) {
            if (ins instanceof SSAAbstractInvokeInstruction) {
                SSAAbstractInvokeInstruction abIns = (SSAAbstractInvokeInstruction) ins;
                if (abIns.getDeclaredTarget().getSignature().equals(method)) {
                    TypeReference pType = abIns.getDeclaredTarget().getParameterType(param-1);
                    retVals.add(Pair.make(abIns.getUse(abIns.isStatic() ? param - 1 : param), pType));
                }
            }
        }

        return retVals;
    }

    private int getParamNumFromVal(CGNode node, int valNum) {
        int[] paramVals = node.getIR().getParameterValueNumbers();
        for (int i = 0; i < paramVals.length; i++) {
            if (paramVals[i] == valNum)
                return i;
        }
        return -1;
    }

    private int getParamNumFromVal(SSAAbstractInvokeInstruction inv, int valNum) {
        try {
            for (int i = 0; i < inv.getNumberOfUses(); i++) {
                if (inv.getUse(i) == valNum)
                    return i;
            }
        } catch (Exception e) {
            //ignore
        }
        return -1;
    }

    public HashSet<Object> getDefFromVal(int val, CGNode node, CallGraph compCg,
                                         HashSet<Pair<CGNode, Integer>> visited) {

        if (visited.contains(Pair.make(node, val)) || visited.size() >= MAX_DEPTH)
            return new HashSet<>();
        visited.add(Pair.make(node, val));

        HashSet<Object> retVal = cachedDefs.getIfPresent(Pair.make(val, node));
        if (retVal == null) {
            retVal = new HashSet<>();
            try {
                Object paramDef = getDefFromValNum(node, val, compCg);
                if (paramDef instanceof SSAInstruction)
                    retVal.addAll(getReturnVal((SSAInstruction) paramDef, node, compCg, visited));
                else if (paramDef instanceof Collection) {
                    for (Object def : (Collection<?>) paramDef) {
                        CGNode curNode = node;
                        if (def instanceof Pair) {
                            curNode = (CGNode) ((Pair<?, ?>) def).fst;
                            def = ((Pair<?, ?>) def).snd;
                        }
                        if (def instanceof SSAInstruction)
                            retVal.addAll(getReturnVal((SSAInstruction) def, curNode, compCg, visited));
                        else if (def != null)
                            retVal.add(def);
                    }
                } else if (paramDef != null) {
                    retVal.add(paramDef);
                }
                if (paramDef != null && retVal.isEmpty())
                    retVal.add(paramDef);
            } catch (Exception e) {
                //ignore
            }
            cachedDefs.put(Pair.make(val, node), retVal);
        }

        return retVal;
    }

    private HashSet<Object> getReturnVal(SSAInstruction target, CGNode curNode,
                                         CallGraph compCg, HashSet<Pair<CGNode, Integer>> visited) {
        Pair<SSAInstruction, CGNode> curKey = Pair.make(target, curNode);

        HashSet<Object> retVals = cachedRets.getIfPresent(curKey);
        if (retVals == null) {
            retVals = new HashSet<>();
            if (isProblematicInstrType(target)) {
                for (int v : getValNumFromProbInstrType(target, curNode)) {
                    retVals.addAll(getDefFromVal(v, curNode, compCg, visited));
                }
            } else if (target instanceof SSAFieldAccessInstruction) {
                IClass clazz = curNode.getMethod().getDeclaringClass();
                FieldReference curField = ((SSAFieldAccessInstruction) target).getDeclaredField();
                ArrayList<Pair<SSAPutInstruction, CGNode>> fieldPuts = getAllFieldPut(curField, clazz);
                for (Pair<SSAPutInstruction, CGNode> p : fieldPuts) {
                    retVals.addAll(getDefFromVal(p.fst.getVal(), p.snd,
                            CachedCallGraphs.buildClassCg(clazz, cha), visited));
                }
                IClass fieldClass = null;
                try {
                    fieldClass = cha.lookupClass(curField.getFieldType());
                } catch (Exception e) {
                    //ignore
                }
                if (fieldClass != null && cha.implementsInterface(fieldClass, COLLECTION_CLASS)) {
                    try {
                        ArrayList<Pair<SSAAbstractInvokeInstruction, CGNode>> fieldModifiers
                                = getAllCollectionModifiers(curField, clazz);
                        for (Pair<SSAAbstractInvokeInstruction, CGNode> m : fieldModifiers) {
                            int fVal = COLLECTION_MODIFIER_PARAM_NUM.get(m.fst.getDeclaredTarget().getName().toString());
                            retVals.addAll(getDefFromVal(m.fst.getUse(fVal), m.snd,
                                    CachedCallGraphs.buildClassCg(clazz, cha), visited));
                        }
                    } catch (Exception e) {
                        //ignore
                    }
                }
            } else if (target instanceof SSAAbstractInvokeInstruction) {
                SSAAbstractInvokeInstruction abIns = (SSAAbstractInvokeInstruction) target;
                if (isDynamicGetter(abIns)
                        || AccessControlUtils.isPermissionCheckingMethod(abIns)
                        || AccessControlUtils.isPermissionEnforcingMethod(abIns)) {
                    int retInsVal = abIns.getUse(InstructionUtils.getParameterIndex(abIns, 0));
                    retVals.addAll(getDefFromVal(retInsVal, curNode, compCg, visited));
                } else if (isEqualsMethod(abIns)) {
                    retVals.addAll(getDefFromVal(abIns.getUse(0), curNode, compCg, visited));
                    retVals.addAll(getDefFromVal(abIns.getUse(1), curNode, compCg, visited));
                } else if(isStringBuilderMethod(abIns, "toString")) {
                    int sbVal = abIns.getUse(0);
                    for (Iterator<SSAInstruction> it = curNode.getDU().getUses(sbVal); it.hasNext(); ) {
                        SSAInstruction sbIns = it.next();
                        if (sbIns instanceof SSAAbstractInvokeInstruction) {
                            SSAAbstractInvokeInstruction sbInv = (SSAAbstractInvokeInstruction) sbIns;
                            if ((sbInv.getDeclaredTarget().isInit() && sbInv.getNumberOfUses() > 1)
                                    || (isStringBuilderMethod((SSAAbstractInvokeInstruction) sbIns,
                                    "append"))) {
                                int retInsVal = sbInv.getUse(1);
                                retVals.addAll(getDefFromVal(retInsVal, curNode, compCg, visited));
                            }
                        }
                    }
                } else if (isStringFormatMethod(abIns)) {
                    for (int i = 0; i < abIns.getDeclaredTarget().getNumberOfParameters(); i++) {
                        if (isStringType(abIns.getDeclaredTarget().getParameterType(i)))
                            retVals.addAll(getDefFromVal(abIns.getUse(i), curNode, compCg, visited));
                    }
                } else {
                    Set<CGNode> nodes = compCg.getNodes(abIns.getDeclaredTarget());
                    for (CGNode node : nodes) {
                        for (SSAInstruction ins : node.getIR().getInstructions()) {
                            try {
                                if (ins instanceof SSAReturnInstruction) {
                                    int retInsVal = ((SSAReturnInstruction) ins).getResult();
                                    if (retInsVal != -1)
                                        retVals.addAll(getDefFromVal(retInsVal, node, compCg, visited));
                                }
                            } catch (Exception e) {
                                //ignore
                            }
                        }
                    }
                }
            } else if (target instanceof SSANewInstruction) {
                SSANewInstruction newIns = (SSANewInstruction) target;
                for (Iterator<SSAInstruction> it = curNode.getDU().getUses(newIns.getDef());
                     it.hasNext(); ) {
                    SSAInstruction ins = it.next();
                    if (ins instanceof SSAAbstractInvokeInstruction
                            && ((SSAAbstractInvokeInstruction) ins).getDeclaredTarget().isInit()) {
                        SSAAbstractInvokeInstruction abIns = (SSAAbstractInvokeInstruction) ins;
                        int start = abIns.isStatic() ? 0 : 1;
                        for (int i = start; i < abIns.getNumberOfUses(); i++) {
                            retVals.addAll(getDefFromVal(abIns.getUse(i), curNode, compCg, visited));
                        }
                    }
                }
            } else if (target instanceof SSACheckCastInstruction) {
                SSACheckCastInstruction ccIns = (SSACheckCastInstruction) target;
                retVals.addAll(getDefFromVal(ccIns.getVal(), curNode, compCg, visited));
            }

            if (retVals.isEmpty())
                retVals.add(target);

            cachedRets.put(curKey, retVals);
        }

        return retVals;
    }

    private Object getDefFromValNum(CGNode node, int valNum, CallGraph cg) {
        Object retVal = null;

        try {
            SymbolTable st = node.getIR().getSymbolTable();
            if (st.isConstant(valNum))
                retVal = st.getConstantValue(valNum);
            else if (st.isParameter(valNum)) {
                InterproceduralCFG icfg = new InterproceduralCFG(cg);
                HashSet<Object> foundDef = new HashSet<>(DefUseChain.chainWithNodes(valNum,
                        icfg.getEntry(node), icfg, new ArrayList<>()));
                if (!foundDef.isEmpty())
                    retVal = foundDef;
                else
                    retVal = Pair.make(CG_PARAM_DEF, getParamNumFromVal(node, valNum));
            }
            if (retVal == null)
                retVal = node.getDU().getDef(valNum);
        } catch (Exception e) {
            //make peace with it and move on
        }

        return retVal;
    }

    private ArrayList<Pair<SSAPutInstruction, CGNode>> getAllFieldPut(FieldReference fr, IClass curClass) {
        ArrayList<Pair<SSAPutInstruction, CGNode>> ret = new ArrayList<>();
        CallGraph cg = CachedCallGraphs.buildClassCg(curClass, cha);
        for (CGNode n : cg) {
            if (n.getMethod().getDeclaringClass().equals(curClass)) {
                for (SSAInstruction i : n.getIR().getInstructions()) {
                    if (i instanceof SSAPutInstruction) {
                        SSAPutInstruction fi = (SSAPutInstruction) i;
                        if (fi.getDeclaredField().equals(fr))
                            ret.add(Pair.make(fi, n));
                    }
                }
            }
        }

        return ret;
    }

    private ArrayList<Pair<SSAGetInstruction, CGNode>> getAllFieldGet(FieldReference fr, IClass curClass) {
        ArrayList<Pair<SSAGetInstruction, CGNode>> ret = new ArrayList<>();
        CallGraph cg = CachedCallGraphs.buildClassCg(curClass, cha);
        for (CGNode n : cg) {
            if (n.getMethod().getDeclaringClass().equals(curClass)) {
                for (SSAInstruction i : n.getIR().getInstructions()) {
                    if (i instanceof SSAGetInstruction) {
                        SSAGetInstruction fi = (SSAGetInstruction) i;
                        if (fi.getDeclaredField().equals(fr))
                            ret.add(Pair.make(fi, n));
                    }
                }
            }
        }

        return ret;
    }

    private ArrayList<Pair<SSAAbstractInvokeInstruction, CGNode>> getAllCollectionModifiers(FieldReference fr,
                                                                                            IClass curClass) {
        ArrayList<Pair<SSAAbstractInvokeInstruction, CGNode>> ret = new ArrayList<>();
        HashSet<SSAAbstractInvokeInstruction> curUniques = new HashSet<>();
        CallGraph cg = CachedCallGraphs.buildClassCg(curClass, cha);
        for (CGNode n : cg) {
            if (n.getMethod().getDeclaringClass().equals(curClass)) {
                for (SSAInstruction i : n.getIR().getInstructions()) {
                    if (i instanceof SSAGetInstruction) {
                        SSAGetInstruction fi = (SSAGetInstruction) i;
                        if (fi.getDeclaredField().equals(fr)) {
                            DefUse du = n.getDU();
                            for (Iterator<SSAInstruction> it = du.getUses(fi.getDef()); it.hasNext(); ) {
                                SSAInstruction ins = it.next();
                                if (ins instanceof SSAAbstractInvokeInstruction) {
                                    SSAAbstractInvokeInstruction abIns = (SSAAbstractInvokeInstruction) ins;
                                    if (COLLECTION_MODIFIERS.contains(abIns.getDeclaredTarget().getName().toString())
                                            && curUniques.add(abIns))
                                        ret.add(Pair.make(abIns, n));
                                }
                            }
                        }
                    }
                }
            }
        }

        return ret;
    }

    private ArrayList<Pair<SSAAbstractInvokeInstruction, CGNode>> getAllCollectionGetters(FieldReference fr,
                                                                                          IClass curClass) {
        ArrayList<Pair<SSAAbstractInvokeInstruction, CGNode>> ret = new ArrayList<>();
        HashSet<SSAAbstractInvokeInstruction> curUniques = new HashSet<>();
        CallGraph cg = CachedCallGraphs.buildClassCg(curClass, cha);
        for (CGNode n : cg) {
            if (n.getMethod().getDeclaringClass().equals(curClass)) {
                for (SSAInstruction i : n.getIR().getInstructions()) {
                    if (i instanceof SSAGetInstruction) {
                        SSAGetInstruction fi = (SSAGetInstruction) i;
                        if (fi.getDeclaredField().equals(fr)) {
                            DefUse du = n.getDU();
                            for (Iterator<SSAInstruction> it = du.getUses(fi.getDef()); it.hasNext(); ) {
                                SSAInstruction ins = it.next();
                                if (ins instanceof SSAAbstractInvokeInstruction) {
                                    SSAAbstractInvokeInstruction abIns = (SSAAbstractInvokeInstruction) ins;
                                    if (abIns.getNumberOfReturnValues() > 0
                                            && COLLECTION_GETTERS.contains(
                                                    abIns.getDeclaredTarget().getName().toString())
                                            && curUniques.add(abIns))
                                        ret.add(Pair.make(abIns, n));
                                }
                            }
                        }
                    }
                }
            }
        }

        return ret;
    }

    private TypeReference getReturnType(String api) {
        IMethod curMethod = null;
        try {
            curMethod = getMethodFromSignature(api);
        } catch (Exception e) {
            //ignore
        }
        if (curMethod != null)
            return curMethod.getReturnType();
        else {
            try {
                String returnStr = api.substring(api.lastIndexOf(')') + 1);
                if (returnStr.endsWith(";"))
                    returnStr = returnStr.substring(0, returnStr.length()-1);
                if (returnStr.length() == 1) {
                    TypeName tn  = TypeName.string2TypeName(returnStr);
                    return TypeReference.find(ClassLoaderReference.Application, tn);
                }
                return TypeReference.find(ClassLoaderReference.Application, returnStr);
            } catch (Exception e) {
                //ignore
            }
        }

        return null;
    }

    private int getNumberOfParams(String api) {
        int args = StringUtils
                .countMatches(api.substring(api.indexOf('(')+1, api.indexOf(')')), ";");
        if (args > 0)
            args += 1;

        return args;
    }

    private IMethod getMethodFromSignature(String methodSign) {
        IClass curClass = cha.lookupClass(TypeReference.find(ClassLoaderReference.Application,
                ChaUtils.getClassNameFromSignature(methodSign)));
        IMethod curMethod = null;
        try {
            curMethod = curClass.getMethod(Selector
                    .make(methodSign.substring(methodSign.lastIndexOf('.')+1)));
        } catch (Exception e) {
            //ignore
        }
        return curMethod;
    }

    private HashSet<Integer> getValNumFromProbInstrType(SSAInstruction ins, CGNode node) {
        HashSet<Integer> newValueNumbers = new HashSet<>();
        DefUse du = node.getDU();

        HashSet<SSAInstruction> visitedInstrs = new HashSet<>(Collections.singletonList(ins));
        Stack<SSAInstruction> nextInstrs = new Stack<>();
        nextInstrs.push(ins);

        while (!nextInstrs.isEmpty()) {
            SSAInstruction curInstr = nextInstrs.pop();
            for(int i = 0; i < curInstr.getNumberOfUses(); i++) {
                int curValNum = curInstr.getUse(i);
                if (node.getIR().getSymbolTable().isConstant(curValNum)) {
                    newValueNumbers.add(curValNum);
                    continue;
                }
                SSAInstruction nextInstr = du.getDef(curValNum);
                if (nextInstr != null) {
                    if (isProblematicInstrType(nextInstr)) {
                        if(!visitedInstrs.contains(nextInstr)) {
                            nextInstrs.push(nextInstr);
                            visitedInstrs.add(curInstr);
                        }
                    } else {
                        newValueNumbers.add(curValNum);
                    }
                }
            }
        }

        return newValueNumbers;
    }

    private boolean isProblematicInstrType(SSAInstruction ins) {
        Class<?> curCls = ins.getClass();
        for (Class<?> pc : PROBLEMATIC_CLASSES) {
            if (pc.isAssignableFrom(curCls))
                return true;
        }

        return false;
    }

    private boolean isDynamicGetter(SSAAbstractInvokeInstruction ins) {
        try {
            MethodReference mIns = ins.getDeclaredTarget();
            IClass curClass = cha.lookupClass(mIns.getDeclaringClass());
            return isEqualOrSubclassOfAny(curClass, DYNAMIC_GETTER_CLASSES)
                    && DYNAMIC_GETTER_METHODS.contains(mIns.getName().toString());
        } catch (Exception e) {
            //ignore
        }

        return false;
    }

    private boolean isEqualsMethod(SSAAbstractInvokeInstruction ins) {
        try {
            MethodReference mIns = ins.getDeclaredTarget();
            return mIns.getName().toString().equals("equals");
        } catch (Exception e) {
            //ignore
        }

        return false;
    }

    private boolean isStringBuilderMethod(SSAAbstractInvokeInstruction ins, String methodName) {
        try {
            MethodReference mIns = ins.getDeclaredTarget();
            TypeReference curCls = mIns.getDeclaringClass();
            return curCls.getName().toString().startsWith("Ljava/")
                    && curCls.getName().toString().endsWith("StringBuilder")
                    && mIns.getName().toString().equals(methodName);
        } catch (Exception e) {
            //ignore
        }

        return false;
    }

    private boolean isStringFormatMethod(SSAAbstractInvokeInstruction ins) {
        try {
            MethodReference mIns = ins.getDeclaredTarget();
            TypeReference curCls = mIns.getDeclaringClass();
            return ins.isStatic()
                    && curCls.getName().toString().startsWith("Ljava/")
                    && curCls.getName().toString().endsWith("String")
                    && mIns.getName().toString().equals("format");
        } catch (Exception e) {
            //ignore
        }

        return false;
    }

    private boolean isStringType(TypeReference type) {
        return type.getName().toString().startsWith("Ljava/")
                && type.getName().getClassName().toString().equals("String");
    }

    private ArrayList<IClass> initCompClasses(String compName) {
        return initParentClasses((packageName, className) ->
                (packageName.startsWith("android/") || packageName.startsWith("androidx/"))
                        && className.equals(compName), cha);
    }

    private boolean isEqualOrSubclassOfAny(IClass c, Collection<IClass> tC) {
        for (IClass t : tC) {
            try {
                if (t.equals(c) || cha.isSubclassOf(c, t) || cha.implementsInterface(c, t))
                    return true;
            } catch (Exception e) {
                //ignore
            }
        }

        return false;
    }

    private NORMALIZED_RET_VAL normalizeRetValFlowSet(HashSet<Object> retUses) {
        if (retUses == null || retUses.isEmpty())
            return NORMALIZED_RET_VAL.UNKNOWN;
        NORMALIZED_RET_VAL curCtx = NORMALIZED_RET_VAL.UNDISCLOSED;

        for (Object obj : retUses) {
            NORMALIZED_RET_VAL retValCtx = normalizeRetVal(obj);
            if (retValCtx.equals(NORMALIZED_RET_VAL.DISCLOSED)) {
                curCtx = retValCtx;
                break;
            } else if (retValCtx.equals(NORMALIZED_RET_VAL.USER_DISCLOSED)) {
                if (!curCtx.equals(NORMALIZED_RET_VAL.DISCLOSED))
                    curCtx = retValCtx;
            }
        }

        return curCtx;
    }

    private NORMALIZED_RET_VAL normalizeRetVal(Object obj) {
        if (obj instanceof Pair
                && ((Pair<?, ?>) obj).fst instanceof String
                && ((String) ((Pair<?, ?>) obj).fst).startsWith("Return [")
                && ((String) ((Pair<?, ?>) obj).fst).endsWith("]value to top method"))
            return NORMALIZED_RET_VAL.DISCLOSED;
        if (obj instanceof SSAAbstractInvokeInstruction) {
            SSAAbstractInvokeInstruction abIns = (SSAAbstractInvokeInstruction) obj;
            try {
                if (isIntentOrBundleReturnMethod(abIns))
                    return NORMALIZED_RET_VAL.DISCLOSED;
                if (isUiMethod(abIns))
                    return NORMALIZED_RET_VAL.USER_DISCLOSED;
            } catch (Exception e) {
                //ignore
            }
        }
        return NORMALIZED_RET_VAL.UNDISCLOSED;
    }

    private NORMALIZED_PARAM_FLOW normalizeParamFlowSet(HashSet<Object> paramDefs) {
        if (paramDefs == null || paramDefs.isEmpty())
            return NORMALIZED_PARAM_FLOW.UNKNOWN;
        NORMALIZED_PARAM_FLOW curCtx = NORMALIZED_PARAM_FLOW.UNMODIFIABLE;
        for (Object obj : paramDefs) {
            NORMALIZED_PARAM_FLOW ctx = normalizeParamFlow(obj);
            if (ctx.equals(NORMALIZED_PARAM_FLOW.MODIFIABLE)) {
                curCtx = ctx;
                break;
            } else if (ctx.equals(NORMALIZED_PARAM_FLOW.USER_MODIFIABLE)) {
                if (!curCtx.equals(NORMALIZED_PARAM_FLOW.MODIFIABLE))
                    curCtx = ctx;
            } else if (ctx.equals(NORMALIZED_PARAM_FLOW.APP_IDENTITY)
                    || ctx.equals(NORMALIZED_PARAM_FLOW.USER_IDENTITY)) {
                if (!curCtx.equals(NORMALIZED_PARAM_FLOW.MODIFIABLE)
                        && !curCtx.equals(NORMALIZED_PARAM_FLOW.USER_MODIFIABLE))
                    curCtx = ctx;
            } else if (ctx.equals(NORMALIZED_PARAM_FLOW.NOT_TRACED)) {
                if (!curCtx.equals(NORMALIZED_PARAM_FLOW.MODIFIABLE)
                        && !curCtx.equals(NORMALIZED_PARAM_FLOW.USER_MODIFIABLE)
                        && !curCtx.equals(NORMALIZED_PARAM_FLOW.APP_IDENTITY)
                        && !curCtx.equals(NORMALIZED_PARAM_FLOW.USER_IDENTITY))
                    curCtx = NORMALIZED_PARAM_FLOW.NOT_TRACED;
            }
        }

        return curCtx;
    }

    public NORMALIZED_PARAM_FLOW normalizeParamFlow(Object def) {
        if (def instanceof String) {
            if (DO_NOT_TRACE_PARAMS.contains(def))
                return NORMALIZED_PARAM_FLOW.NOT_TRACED;
            else if (((String) def).startsWith("From parameter: [")
                    && ((String) def).endsWith("] of top method"))
                return NORMALIZED_PARAM_FLOW.MODIFIABLE;
            else
                return NORMALIZED_PARAM_FLOW.UNMODIFIABLE;
        } else if (def instanceof SSAInstruction) {
            if (def instanceof SSAAbstractInvokeInstruction) {
                SSAAbstractInvokeInstruction abIns = (SSAAbstractInvokeInstruction) def;
                try {
                    if (isIntentOrBundleReturnMethod(abIns))
                        return NORMALIZED_PARAM_FLOW.MODIFIABLE;
                    if (isUiMethod(abIns))
                        return NORMALIZED_PARAM_FLOW.USER_MODIFIABLE;
                    NORMALIZED_PARAM_FLOW potentialId = getNormalizedIdIfExists(abIns);
                    if (potentialId != null)
                        return potentialId;
                } catch (Exception e) {
                    //ignore
                }
            }
        }

        return NORMALIZED_PARAM_FLOW.UNMODIFIABLE;
    }

    private NORMALIZED_PARAM_FLOW getNormalizedIdIfExists(SSAAbstractInvokeInstruction abIns) {
        if (isAppIdGetter(abIns))
            return NORMALIZED_PARAM_FLOW.APP_IDENTITY;
        if (isUserIdGetter(abIns))
            return NORMALIZED_PARAM_FLOW.USER_IDENTITY;

        return null;
    }

    private boolean isUserIdGetter(SSAAbstractInvokeInstruction abIns) {
        try {
            boolean result
                    = AccessControlUtils.containsTarget(abIns, AccessControlUtils.MethodSet.GetUserIdMethods)
                    || AccessControlUtils.containsTarget(abIns, AccessControlUtils.MethodSet.GetUidMethods)
                    || AccessControlUtils.containsTarget(abIns, AccessControlUtils.MethodSet.GetUserHandleMethods);
            if (result)
                return true;
            else {
                IClass curCls = cha.lookupClass(abIns.getDeclaredTarget().getReturnType());
                return isEqualOrSubclassOfAny(curCls, ANDROID_USER_HANDLE_CLASS);
            }
        } catch (Exception e) {
            //ignore
        }

        return false;
    }

    private boolean isAppIdGetter(SSAAbstractInvokeInstruction abIns) {
        try {
            return AccessControlUtils.containsTarget(abIns,
                    AccessControlUtils.MethodSet.GetAppIdMethods);
        } catch (Exception e) {
            //ignore
        }

        return false;
    }

    public static class DataflowContext {
        public final String api;
        public final List<Pair<HashSet<Object>, NORMALIZED_PARAM_FLOW>> paramDefs;
        public final Pair<HashSet<Object>, NORMALIZED_RET_VAL> resultUses;
        public final NORMALIZED_PARAM_FLOW aggregatedParamFlow;
        public boolean isUserId;
        public boolean isAppId;

        public DataflowContext(String api,
                               List<Pair<HashSet<Object>, NORMALIZED_PARAM_FLOW>> paramDefs,
                               Pair<HashSet<Object>, NORMALIZED_RET_VAL> resultUses) {
            this.api = api;
            this.paramDefs = paramDefs;
            this.resultUses = resultUses;

            HashSet<NORMALIZED_PARAM_FLOW> allParams = (paramDefs == null ? null : paramDefs.stream()
                    .map(pd -> pd.snd).collect(Collectors.toCollection(HashSet::new)));

            if (allParams != null) {
                isUserId = allParams.contains(NORMALIZED_PARAM_FLOW.USER_IDENTITY);
                isAppId = allParams.contains(NORMALIZED_PARAM_FLOW.APP_IDENTITY);
                aggregatedParamFlow = aggregateParamFlows(allParams);
            } else {
                isUserId = false;
                isAppId = false;
                aggregatedParamFlow = null;
            }
        }

        private NORMALIZED_PARAM_FLOW aggregateParamFlows(HashSet<NORMALIZED_PARAM_FLOW> allParams) {
            if (allParams.isEmpty())
                return null;
            if (allParams.contains(NORMALIZED_PARAM_FLOW.MODIFIABLE))
                return NORMALIZED_PARAM_FLOW.MODIFIABLE;
            else if (allParams.contains(NORMALIZED_PARAM_FLOW.USER_MODIFIABLE))
                return NORMALIZED_PARAM_FLOW.USER_MODIFIABLE;
            else if (allParams.contains(NORMALIZED_PARAM_FLOW.UNMODIFIABLE)
                    || allParams.contains(NORMALIZED_PARAM_FLOW.USER_IDENTITY)
                    || allParams.contains(NORMALIZED_PARAM_FLOW.APP_IDENTITY))
                return NORMALIZED_PARAM_FLOW.UNMODIFIABLE;

            return NORMALIZED_PARAM_FLOW.UNKNOWN;
        }

        @Override
        public String toString() {
            return "param=" + aggregatedParamFlow +
                    ", result=" + (resultUses != null ? resultUses.snd : "") +
                    ", isUserId=" + isUserId +
                    ", isAppId=" + isAppId;
        }
    }

    public enum NORMALIZED_PARAM_FLOW {
        UNMODIFIABLE,
        USER_IDENTITY,
        APP_IDENTITY,
        USER_MODIFIABLE,
        MODIFIABLE,
        NOT_TRACED,
        UNKNOWN
    }

    public enum NORMALIZED_RET_VAL {
        DISCLOSED,
        USER_DISCLOSED,
        UNDISCLOSED,
        UNKNOWN
    }

}