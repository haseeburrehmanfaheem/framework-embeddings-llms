package com.aacr.helpers.detectors.app.ui;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.slicer.NormalReturnCaller;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.StatementWithInstructionIndex;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.graph.traverse.DFS;
import com.ibm.wala.util.graph.traverse.DFSDiscoverTimeIterator;
import com.ibm.wala.util.intset.IntSet;
import com.aacr.helpers.accesscontrol.app.Component;
import com.aacr.helpers.accesscontrol.app.Protection;
import com.aacr.helpers.dataflow.DefUseChain;
import com.aacr.helpers.detectors.app.AppClassUtils;
import com.aacr.helpers.detectors.app.AppEntrypoint;
import com.aacr.helpers.detectors.app.componentgraph.models.info.ComponentInfo;
import com.aacr.helpers.detectors.app.componentgraph.models.info.UiInfo;
import com.aacr.helpers.parsers.models.MapperResource;
import com.aacr.helpers.parsers.models.val.StringResource;
import com.aacr.helpers.parsers.models.val.ValueResource;
import com.aacr.helpers.parsers.models.view.LayoutResource;
import com.aacr.helpers.parsers.models.view.WidgetResource;
import com.aacr.helpers.utils.CallGraphUtils;
import com.ibm.wala.util.collections.Pair;

import java.util.*;

public class UiEntrypointDetector {

    private final HashMap<String, Pair<Component, ComponentInfo>> activityFragmentEps;
    private final ClassHierarchy appCha;
    private final AnalysisScope appScope;
    private final HashMap<Integer, MapperResource> allResIdMap;
    private final HashMap<String, HashMap<String, ValueResource>> valResMap;
    private final HashMap<String, LayoutResource> layoutResMap;
    private final HashMap<String, HashSet<String>> allWidgetsMap;

    private final HashMap<Component, ComponentInfo> activityFragmentInfo = new HashMap<>();

    // The layouts and their names that are associated with current component
    private final ArrayList<LayoutResource> curLayouts = new ArrayList<>();
    // All views detected in the component
    private final ArrayList<WidgetInCg> curWidgets = new ArrayList<>();

    public UiEntrypointDetector(HashMap<String, Pair<Component, ComponentInfo>> activityFragmentEps,
                                ClassHierarchy appCha, AnalysisScope appScope,
                                HashMap<Integer, MapperResource> allResIdMap,
                                HashMap<String, HashMap<String, ValueResource>> valResMap,
                                HashMap<String, LayoutResource> layoutResMap,
                                HashMap<String, HashSet<String>> allWidgetsMap) {
        this.activityFragmentEps = activityFragmentEps;
        this.appCha = appCha;
        this.appScope = appScope;
        this.allResIdMap = allResIdMap;
        this.valResMap = valResMap;
        this.layoutResMap = layoutResMap;
        this.allWidgetsMap = allWidgetsMap;
    }

    public HashMap<Component, ComponentInfo> extractUiEps() {

        // Extract UI EPs for all activity and fragment component classes
        for (IClass c : appCha) {
            if (AppClassUtils.shouldNotAnalyze(c, appScope))
                continue;
            Pair<Component, ComponentInfo> epComponent = activityFragmentEps.get(c.getName().toString());
            // Discard classes that are not declared activity or fragment components
            if (epComponent == null || epComponent.fst == null)
                continue;

            // The final derived map of UI entrypoint -> UI Protection
            HashMap<AppEntrypoint, Protection> eps = new HashMap<>();
            // Create list of EPs for identified activity class
//            ArrayList<DefaultEntrypoint> curActivityEps = new ArrayList<>();
//            for (IMethod m : c.getDeclaredMethods()) {
//                if (m.getName() != null && epComponent.getType().knownEpMethods.contains(m.getName().toString()))
//                    curActivityEps.add(new AppEntrypoint(m, appCha));
//            }

//            if (curActivityEps.isEmpty())
//                continue;

            if (epComponent.snd.epMap == null || epComponent.snd.epMap.isEmpty())
                continue;

            // Create the builder and options for single class CG
            AnalysisOptions options = CallGraphUtils.generateAnalysisOptions(appScope, appCha,
                    epComponent.snd.epMap.keySet(),//curActivityEps,
                    AnalysisOptions.ReflectionOptions.NONE, null);
            SSAPropagationCallGraphBuilder builder = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options,
                    new AnalysisCacheImpl(new DexIRFactory()), appCha);
            try {
                eps.putAll(extractViewEps(options, builder, epComponent.fst));
            } catch (Exception e) {
                e.printStackTrace();
            }
            activityFragmentInfo.put(epComponent.fst, new UiInfo(appScope, appCha, eps, curLayouts));
            curLayouts.clear();
            curWidgets.clear();
        }

        return activityFragmentInfo;
    }

    public HashMap<Component, ComponentInfo> addDynamicStrings() {

        // Extract UI EPs for all activity and fragment component classes
        for (IClass c : appCha) {
            if (AppClassUtils.shouldNotAnalyze(c, appScope))
                continue;
            if (activityFragmentEps.get(c.getName().toString()) == null)
                continue;
            Component epComp = activityFragmentEps.get(c.getName().toString()).fst;
            if (epComp == null)
                continue;
            ComponentInfo epCompInfo = activityFragmentInfo.get(epComp);
            if (epCompInfo == null)
                continue;
            Pair<Component, ComponentInfo> epComponent = Pair.make(epComp, epCompInfo);

            if (epComponent.snd.epMap == null || epComponent.snd.epMap.isEmpty())
                continue;

            // Create the builder and options for single class CG
            AnalysisOptions options = CallGraphUtils.generateAnalysisOptions(appScope, appCha,
                    epComponent.snd.epMap.keySet(),//curActivityEps,
                    AnalysisOptions.ReflectionOptions.NONE, null);
            SSAPropagationCallGraphBuilder builder = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options,
                    new AnalysisCacheImpl(new DexIRFactory()), appCha);
            try {
                addDynamicStringsForComp(options, builder, epComponent);
            } catch (Exception e) {
                e.printStackTrace();
            }
            curLayouts.clear();
            curWidgets.clear();
        }

        return activityFragmentInfo;
    }

    // This method extracts UI callback EPs for a given activity class by using Data flow analysis
//    private HashMap<AppEntrypoint, Protection> extractViewEps(AnalysisOptions options,
//                                                              SSAPropagationCallGraphBuilder builder,
//                                                              Component epComponent, IClass compClass)
//            throws CallGraphBuilderCancelException {
//        // Hashmap that will contain EPs from current activity
//        HashMap<AppEntrypoint, Protection> eps = new HashMap<>();
//
//        // Build CG and ICFG for the activity class
//        CallGraph compCg = builder.makeCallGraph(options, null);
//        InterproceduralCFG compIcfg = new InterproceduralCFG(compCg);
//
//        preProcessComponent(appCha, compCg, compClass);
//
//        for (Widget widget : curWidgets) {
//            Layout parent = null;
//            WidgetResource widgetRes = null;
//            try {
//                parent = getParentLayout(widget.name);
//                widgetRes = parent.res.getWidgets().get(widget.name);
//            } catch (Exception e) {
//                //ignore
//            }
//            Protection curUiProtection;
//            if (parent == null) {
//                curUiProtection = new Protection(null, null, null, epComponent);
//            } else {
//                curUiProtection = new Protection(parent.name, parent.res, widgetRes, epComponent);
//            }
//            DefUse du = widget.initNode.getDU();
//            ArrayList<SSAInstruction> relevantInstructions = new ArrayList<>();
//            for (Iterator<SSAInstruction> it = du.getUses(widget.valNum); it.hasNext(); ) {
//                relevantInstructions.add(it.next());
//            }
//            curUiProtection.addDynamicStrings(
//                    extractDynamicTextsFromInstr(relevantInstructions, compIcfg, compIcfg.getEntry(widget.initNode))
//            );
//            for (SSAInstruction instr : relevantInstructions) {
//                if (isRegisterCallbackStatement(instr)) {
//                    ArrayList<AppEntrypoint> allEps = extractCallbackEps(compIcfg,
//                            (SSAAbstractInvokeInstruction) instr,
//                            compIcfg.getEntry(widget.initNode));
//                    for (AppEntrypoint ep : allEps)
//                        eps.put(ep, curUiProtection);
//                }
//            }
//
//        }
//
//        return eps;
//    }

    // This method extracts UI callback EPs for a given activity class by using Data flow analysis
    private HashMap<AppEntrypoint, Protection> extractViewEps(AnalysisOptions options,
                                                              SSAPropagationCallGraphBuilder builder,
                                                              Component epComponent)
            throws CallGraphBuilderCancelException {
        // Hashmap that will contain EPs from current activity
        HashMap<AppEntrypoint, Protection> eps = new HashMap<>();

        // Build CG and ICFG for the activity class
        CallGraph compCg = builder.makeCallGraph(options, null);

        preProcessComponent(compCg);

        InterproceduralCFG compIcfg = new InterproceduralCFG(compCg);
        // Traverse the ICFG in DFS fashion
        DFSDiscoverTimeIterator<BasicBlockInContext<ISSABasicBlock>> dfsIterator = DFS.iterateDiscoverTime(compIcfg);

        while (dfsIterator.hasNext()) {
            BasicBlockInContext<ISSABasicBlock> bb = dfsIterator.next();

            IClass curClass = bb.getNode().getMethod().getDeclaringClass();

            if (AppClassUtils.shouldNotAnalyze(curClass, appScope))
                continue;

            SymbolTable st = bb.getNode().getIR().getSymbolTable();
            for (SSAInstruction instruction : bb) {
                try {
                    if (instruction instanceof SSAAbstractInvokeInstruction) {
                        SSAAbstractInvokeInstruction abIns = (SSAAbstractInvokeInstruction) instruction;
                        // Sometimes, setContentView is called twice or inside a conditional statement
                        // Instead of trying to resolve precisely (which will require complex dataflow analysis),
                        // we simply consider both layouts
//                        if (isLayoutSetter(abIns)) {
//                            // setContentView always only has one parameter, and therefore it is always at param index "1"
//                            // overloaded inflate from layout inflater (which is of interest to us) is also the same
//                            if (st.isIntegerConstant(abIns.getUse(1))) {
//                                MapperResource layoutRes = allResIdMap.get(st.getIntValue(abIns.getUse(1)));
//                                curLayouts.add(new Layout(layoutRes.name, layoutRes.toLayoutRes(layoutResMap)));
//                            }
//                        } else
                        if (abIns.getDeclaredTarget().getSignature().contains("findViewById")) {
                            // findViewById always only has one parameter, and therefore it is always at param index "1"
                            int viewId = -1;
                            String viewIdStr = "";
                            ArrayList<WidgetResource> widgets = new ArrayList<>();
                            if (st.isIntegerConstant(abIns.getUse(1)))
                                viewId = st.getIntValue(abIns.getUse(1));

                            //Todo: Check why this happens???
    //                            if (curLayout.widgets == null || allResIdMap.get(viewId) == null) continue;

                            try {
                                viewIdStr = allResIdMap.get(viewId).name;
                                for (LayoutResource curLayout : curLayouts) {
                                    if (curLayout.getWidgets().get(viewIdStr) != null) {
                                        widgets.add(curLayout.getWidgets().get(viewIdStr));
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                //ignore
                            }
                            try {
                                if (widgets.isEmpty()) {
                                    for (String parentIdStr : allWidgetsMap.get(viewIdStr)) {
                                        if (layoutResMap.get(parentIdStr).getWidgets().get(viewIdStr) != null)
                                            widgets.add(layoutResMap.get(parentIdStr).getWidgets().get(viewIdStr));
                                    }
                                }
                            } catch (Exception e) {
                                //ignore
                            }

                            //Todo: Solve this by iteratively adding all included layouts with @layout values
                            // This is done. But is it done correctly? (i.e., do we need entire forest traversal?)
    //                            if (widget == null) continue;

                            Protection curUiProtection = new Protection(widgets, epComponent);

                            try {
                                Collection<Statement> relevantStmts = extractRelevantStatements(compCg,
                                        builder.getPointerAnalysis(), bb.getNode(), abIns);
    //                                curUiProtection.addDynamicStrings(extractDynamicTexts(relevantStmts, activityIcfg, bb));
                                for (Statement stmt : relevantStmts) {
                                    if (stmt instanceof StatementWithInstructionIndex) {
                                        SSAInstruction instr = ((StatementWithInstructionIndex) stmt).getInstruction();
                                        if (isRegisterCallbackStatement(instr)) {
                                            ArrayList<AppEntrypoint> allEps = extractCallbackEps(compIcfg,
                                                    (SSAAbstractInvokeInstruction) instr, bb);
                                            for (AppEntrypoint ep : allEps)
                                                eps.put(ep, curUiProtection);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                System.out.println("Exception in "+epComponent.toStringWithoutProtection()+"::instruction:"+instruction);
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return eps;
    }

    // This method extracts UI callback EPs for a given activity class by using Data flow analysis
    private void addDynamicStringsForComp(AnalysisOptions options, SSAPropagationCallGraphBuilder builder,
                                          Pair<Component, ComponentInfo> epComponent)
            throws CallGraphBuilderCancelException {

        // Build CG and ICFG for the activity class
        CallGraph compCg = builder.makeCallGraph(options, null);

//        preProcessComponent(compCg);

        InterproceduralCFG compIcfg = new InterproceduralCFG(compCg);
        // Traverse the ICFG in DFS fashion
        DFSDiscoverTimeIterator<BasicBlockInContext<ISSABasicBlock>> dfsIterator = DFS.iterateDiscoverTime(compIcfg);

        while (dfsIterator.hasNext()) {
            BasicBlockInContext<ISSABasicBlock> bb = dfsIterator.next();

            IClass curClass = bb.getNode().getMethod().getDeclaringClass();

            if (AppClassUtils.shouldNotAnalyze(curClass, appScope))
                continue;

            SymbolTable st = bb.getNode().getIR().getSymbolTable();
            for (SSAInstruction instruction : bb) {
                try {
                    if (instruction instanceof SSAAbstractInvokeInstruction) {
                        SSAAbstractInvokeInstruction abIns = (SSAAbstractInvokeInstruction) instruction;
                        // Sometimes, setContentView is called twice or inside a conditional statement
                        // Instead of trying to resolve precisely (which will require complex dataflow analysis),
                        // we simply consider both layouts
//                        if (isLayoutSetter(abIns)) {
//                            // setContentView always only has one parameter, and therefore it is always at param index "1"
//                            // overloaded inflate from layout inflater (which is of interest to us) is also the same
//                            if (st.isIntegerConstant(abIns.getUse(1))) {
//                                MapperResource layoutRes = allResIdMap.get(st.getIntValue(abIns.getUse(1)));
//                                curLayouts.add(new Layout(layoutRes.name, layoutRes.toLayoutRes(layoutResMap)));
//                            }
//                        } else
                        if (abIns.getDeclaredTarget().getSignature().contains("findViewById")) {
                            // findViewById always only has one parameter, and therefore it is always at param index "1"
//                            int viewId = -1;
//                            String viewIdStr = "";
//                            ArrayList<WidgetResource> widgets = new ArrayList<>();
//                            if (st.isIntegerConstant(abIns.getUse(1)))
//                                viewId = st.getIntValue(abIns.getUse(1));
//
//                            //Todo: Check why this happens???
//                            //                            if (curLayout.widgets == null || allResIdMap.get(viewId) == null) continue;
//
//                            try {
//                                viewIdStr = allResIdMap.get(viewId).name;
//                                for (LayoutResource curLayout : curLayouts) {
//                                    if (curLayout.getWidgets().get(viewIdStr) != null) {
//                                        widgets.add(curLayout.getWidgets().get(viewIdStr));
//                                        break;
//                                    }
//                                }
//                            } catch (Exception e) {
//                                //ignore
//                            }
//                            try {
//                                if (widgets.isEmpty()) {
//                                    for (String parentIdStr : allWidgetsMap.get(viewIdStr)) {
//                                        if (layoutResMap.get(parentIdStr).getWidgets().get(viewIdStr) != null)
//                                            widgets.add(layoutResMap.get(parentIdStr).getWidgets().get(viewIdStr));
//                                    }
//                                }
//                            } catch (Exception e) {
//                                //ignore
//                            }

                            //Todo: Solve this by iteratively adding all included layouts with @layout values
                            // This is done. But is it done correctly? (i.e., do we need entire forest traversal?)
                            //                            if (widget == null) continue;

//                            Protection curUiProtection = new Protection(widgets, epComponent);

                            try {
                                Collection<Statement> relevantStmts = extractRelevantStatements(compCg,
                                        builder.getPointerAnalysis(), bb.getNode(), abIns);
                                ArrayList<String> dynamicStr = extractDynamicTexts(relevantStmts, compIcfg, bb);
                                for (Statement stmt : relevantStmts) {
                                    if (stmt instanceof StatementWithInstructionIndex) {
                                        SSAInstruction instr = ((StatementWithInstructionIndex) stmt).getInstruction();
                                        if (isRegisterCallbackStatement(instr)) {
                                            ArrayList<AppEntrypoint> allEps = extractCallbackEps(compIcfg,
                                                    (SSAAbstractInvokeInstruction) instr, bb);
                                            for (AppEntrypoint ep : allEps) {
                                                ComponentInfo curInfo = epComponent.snd;
                                                for (AppEntrypoint curEp : curInfo.epMap.keySet()) {
                                                    if (curEp.getMethod().getSignature()
                                                            .equals(ep.getMethod().getSignature())) {
                                                        curInfo.epMap.get(curEp).addDynamicStrings(dynamicStr);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void preProcessComponent(CallGraph compCg) {

        // Preprocess CG to build the value map for all layouts and widgets
        for (CGNode node : compCg) {
            IClass curClass = node.getMethod().getDeclaringClass();
            if (AppClassUtils.shouldNotAnalyze(curClass, appScope))
                continue;
//            if (!curClass.equals(compClass) && !appCha.isSubclassOf(compClass, curClass))
//                continue;
            try {
                SymbolTable st = node.getIR().getSymbolTable();
                for (SSAInstruction i : node.getIR().getInstructions()) {
                    if (i instanceof SSAAbstractInvokeInstruction) {
                        SSAAbstractInvokeInstruction inv = (SSAAbstractInvokeInstruction) i;
                        if (isLayoutSetter(inv)) {
                            MapperResource res = allResIdMap.get(st.getIntValue(inv.getUse(1)));
                            curLayouts.add(res.toLayoutRes(layoutResMap));
//                        } else if (inv.getDeclaredTarget().getName().toString().equals("findViewById")) {
//                            int valNum = inv.getReturnValue(0);
//                            String resStr = allResIdMap.get(st.getIntValue(inv.getUse(1))).name;
//                            curWidgets.add(new Widget(node, valNum, resStr));
                        }
                    }
                }
            } catch (Exception e) {
                //ignore
            }
        }
    }

//    private Layout getParentLayout(String widgetStr) {
//        if (!curLayouts.isEmpty()) {
//            for (Layout layout : curLayouts) {
//                if (layout.res.getWidgets().containsKey(widgetStr))
//                    return layout;
//            }
//        }
//
//        return null;
//    }

    private ArrayList<String> extractDynamicTexts(Collection<Statement> relevantStmts,
                                                  InterproceduralCFG activityIcfg,
                                                  BasicBlockInContext<ISSABasicBlock> bb) {
        ArrayList<SSAInstruction> filteredStmts = new ArrayList<>();
        for (Statement stmt : relevantStmts) {
            // We only care for the statements that have instructions
            if (stmt instanceof StatementWithInstructionIndex) {
                filteredStmts.add(((StatementWithInstructionIndex) stmt).getInstruction());
            }
        }

        return extractDynamicTextsFromInstr(filteredStmts, activityIcfg, bb);
    }

    /**
     * This method extracts strings used to initialize UI dynamically from the input collection of statements
     * @param relevantStmts Collection of statements from which the strings need to be extracted
     * @param activityIcfg ICFG of the activity being analyzed
     * @param bb basic block containing the statement corresponding to the findViewById invocation
     * @return All strings that are used to dynamically initialize this UI element
     */
    private ArrayList<String> extractDynamicTextsFromInstr(Collection<SSAInstruction> relevantStmts,
                                                         InterproceduralCFG activityIcfg,
                                                         BasicBlockInContext<ISSABasicBlock> bb) {
        ArrayList<String> res = new ArrayList<>();

        SymbolTable st = bb.getNode().getIR().getSymbolTable();

        for (SSAInstruction instruction : relevantStmts) {
            // We only care for the invoke instructions
            if (instruction instanceof SSAAbstractInvokeInstruction) {
                SSAAbstractInvokeInstruction invokeInstr = (SSAAbstractInvokeInstruction) instruction;
                // We only care for the instructions corresponding to set or add text
                if (invokeInstr.getDeclaredTarget().getSignature().contains("setText")
                        || invokeInstr.getDeclaredTarget().getSignature().contains("addText")) {
                    // The first parameter must be the string constant we are looking for
                    try {
                        if (st.isStringConstant(invokeInstr.getUse(1))) {
                            res.add(st.getStringValue(instruction.getUse(1)));
                        } else if (st.isIntegerConstant(invokeInstr.getUse(1))) {
                            String strName = allResIdMap.get(st.getIntValue(invokeInstr.getUse(1))).name;
                            StringResource sr = (StringResource) valResMap.get(StringResource.name).get(strName);
                            res.add(sr.value);
                        } else {
                            ArrayList<Object> defs = DefUseChain.chain(invokeInstr.getUse(1), bb, activityIcfg, new ArrayList<>());
                            for (Object obj : defs) {
                                if (obj instanceof String)
                                    res.add((String) obj);
                                else if (obj instanceof Integer) {
                                    String strName = allResIdMap.get(obj).name;
                                    StringResource sr = (StringResource) valResMap.get(StringResource.name).get(strName);
                                    res.add(sr.value);
                                }
                            }
                        }
                    } catch (Exception e) {
                        //e.printStackTrace();
                    }
                }
            }
        }

        return res;
    }

    //  This method needs to:
    //  (1) Extract the concrete type of the first parameter being passed - say T - This is difficult
    //  (2) Extract the type of interface expected by the method's first parameter - say I - This is easy
    //  (3) Iterate over the overriden methods of T and find ones that are declared by I - This is easy
    //  (4) Find the method that we are interested in by looking at registrationMethod - Use EdgeMiner mapping?
    //  (5) Create a default entry point out of this identified callback method and return it - This is easy
    private ArrayList<AppEntrypoint> extractCallbackEps(InterproceduralCFG compIcfg,
                                                        SSAAbstractInvokeInstruction instruction,
                                                        BasicBlockInContext<ISSABasicBlock> bb) {
        ArrayList<AppEntrypoint> eps = new ArrayList<>();
        HashSet<String> interfaceMethods = new HashSet<>();
        // Add all declared methods of the interface in the hashset
        // Here, we know that the type of first parameter of the registerCallback method is going to be the interface we want
        TypeReference interfaceType = instruction.getDeclaredTarget().getParameterType(0);
        IClass interfaceClass = appCha.lookupClass(interfaceType);

        // If we don't know what the interface class is, then we cannot go any further
        if (interfaceClass == null)
            return eps;

        for(IMethod m : interfaceClass.getDeclaredMethods()) {
            interfaceMethods.add(m.getReturnType().toString()+m.getSelector().toString());
        }

        // The value number for the actual object being passed as the first parameter
        int paramValueNum = instruction.getUse(1);

        // 'this' is being passed as the first parameter
        if (paramValueNum == 1) {
            // So extract the class from CHA and add all its overriden methods matching the interface of interest
            // into entry points of interest
            eps.addAll(getIntMethodsFromClass(bb.getNode().getMethod().getDeclaringClass(), interfaceMethods));
        } else {
            // Check how to get the definition of the object and get the type from there
            ArrayList<Object> allDefs = new ArrayList<>();
            SSAInstruction defInstr = bb.getNode().getDU().getDef(paramValueNum);
            if (defInstr != null)
                allDefs.add(defInstr);
            try {
                allDefs.addAll(DefUseChain.chain(paramValueNum, bb, compIcfg, new ArrayList<>()));
            } catch (Exception e) {
                //ignore
            }
            for (Object instr : allDefs) {
                if (instr instanceof SSANewInstruction) {
                    eps.addAll(getIntMethodsFromClass(((SSANewInstruction)instr).getConcreteType(), interfaceMethods));
                } else if(instr instanceof SSAAbstractInvokeInstruction) {
                    TypeReference tr = ((SSAAbstractInvokeInstruction)instr).getDeclaredResultType();
                    if (!interfaceType.equals(tr))
                        eps.addAll(getIntMethodsFromClass(tr, interfaceMethods));
                } else if (instr instanceof SSAFieldAccessInstruction) {
                    TypeReference tr = ((SSAFieldAccessInstruction)instr).getDeclaredFieldType();
                    if (!interfaceType.equals(tr))
                        eps.addAll(getIntMethodsFromClass(tr, interfaceMethods));
                }
            }
        }

        return eps;
    }

    private ArrayList<AppEntrypoint> getIntMethodsFromClass(TypeReference t, HashSet<String> intMethods) {
        IClass c = appCha.lookupClass(t);
        if (c == null)
            return new ArrayList<>();
        return getIntMethodsFromClass(c, intMethods);
    }

    private ArrayList<AppEntrypoint> getIntMethodsFromClass(IClass c, HashSet<String> intMethods) {
        ArrayList<AppEntrypoint> res = new ArrayList<>();

        for (IMethod m : c.getDeclaredMethods()) {
            if (intMethods.contains(m.getReturnType().toString()+m.getSelector().toString())) {
                res.add(new AppEntrypoint(m, appCha, c.getReference()));
            }
        }

        return res;
    }

    private Collection<Statement> extractRelevantStatements(CallGraph cg, PointerAnalysis pa, CGNode node,
                                                                   SSAAbstractInvokeInstruction abIns) throws CancelException {
        IntSet indices = node.getIR().getCallInstructionIndices(abIns.getCallSite());
        NormalReturnCaller currentCallStmt = new NormalReturnCaller(node, indices.intIterator().next());
        return Slicer.computeForwardSlice(currentCallStmt, cg,
                pa, Slicer.DataDependenceOptions.NO_BASE_NO_EXCEPTIONS,
                Slicer.ControlDependenceOptions.NONE);
    }

    private boolean isRegisterCallbackStatement(SSAInstruction instruction) {
        if (instruction instanceof SSAAbstractInvokeInstruction) {
            SSAAbstractInvokeInstruction abIns = (SSAAbstractInvokeInstruction) instruction;
            String methodName = abIns.getDeclaredTarget().getName().toString();
            return ((methodName.startsWith("set") || methodName.startsWith("add"))
                    && methodName.endsWith("Listener"));
        }
        return false;
    }

    private boolean isLayoutSetter(SSAAbstractInvokeInstruction abIns) {
        if (abIns.getDeclaredTarget() == null || abIns.getDeclaredTarget().getName() == null)
            return false;
        String methodName = abIns.getDeclaredTarget().getName().toString();

        return methodName.equals("setContentView")
                || methodName.equals("inflate")
                || methodName.equals("setLayoutResource");
    }

    private static class WidgetInCg {
        public final CGNode initNode;
        public final int valNum;
        public final String name;

        private WidgetInCg(CGNode initNode, int valNum, String name) {
            this.initNode = initNode;
            this.valNum = valNum;
            this.name = name;
        }
    }

//    @Deprecated
//    public static ArrayList<AppEntrypoint> getEps(ClassHierarchy appCha, AnalysisScope appScope) {
//        ArrayList<AppEntrypoint> eps = new ArrayList<>();
//        for (IClass c : appCha) {
//            //Check if the class being considered implements "View" or "Widget"
//            HashSet<IClass> uiInterfaces = isTargetUIElement(c, appScope);
//            //If it does, then find all UI callback methods and add them as EPs
//            if (!uiInterfaces.isEmpty()) {
//                eps.addAll(getTargetUiEps(c, uiInterfaces, appCha));
//            }
//        }
//        return eps;
//    }

//    private static ArrayList<AppEntrypoint> getTargetUiEps(
//            IClass c, HashSet<IClass> uiInterfaces, ClassHierarchy appCha) {
//        //Arraylist to hold the final result
//        ArrayList<AppEntrypoint> targetUiEps = new ArrayList<>();
//
//        //Cached hashset to be used later for comparison
//        HashSet<String> targetUiMethodNameAndDescriptorCache = new HashSet<>();
//        for (IClass uiInterface : uiInterfaces) {
//            for (IMethod potentialUiMethod : uiInterface.getDeclaredMethods()) {
//                targetUiMethodNameAndDescriptorCache.add(
//                        potentialUiMethod.getName().toString()+"::"+potentialUiMethod.getDescriptor().toString()
//                );
//            }
//        }
//
//        //Check if any of current class's method matches with one of the identified potential methods above
//        for (IMethod m : c.getDeclaredMethods()) {
//            if (targetUiMethodNameAndDescriptorCache
//                    .contains(m.getName().toString()+"::"+m.getDescriptor().toString())) {
//                targetUiEps.add(new AppEntrypoint(m, appCha));
//            }
//        }
//
//        return targetUiEps;
//    }

//    private static HashSet<IClass> isTargetUIElement(IClass appClass, AnalysisScope appScope) {
//        HashSet<IClass> uiInterfaces = new HashSet<>();
//
//        if (!appScope.isApplicationLoader(appClass.getClassLoader()))
//            return uiInterfaces;
//
//        //Search within all ancestor interfaces and see if we can find a UI related interface
//        for (IClass implInterface : appClass.getAllImplementedInterfaces()) {
//            if (implInterface.getName().toString().startsWith("Landroid/view/")
//                    || implInterface.getName().toString().startsWith("Landroid/widget/")
//                    || implInterface.getName().toString().startsWith("Landroid/content/DialogInterface")) {
//                //If we do, then add it to list of ui interfaces for current class
//                uiInterfaces.add(implInterface);
//            }
//        }
//        return uiInterfaces;
//    }
}
