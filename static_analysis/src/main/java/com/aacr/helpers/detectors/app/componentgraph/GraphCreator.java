package com.aacr.helpers.detectors.app.componentgraph;

import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.aacr.helpers.accesscontrol.app.Component;
import com.aacr.helpers.accesscontrol.app.Protection;
import com.aacr.helpers.detectors.app.componentgraph.models.EdgeLabel;
import com.aacr.helpers.detectors.app.componentgraph.models.Node;
import com.aacr.helpers.detectors.app.componentgraph.models.info.ComponentInfo;
import com.aacr.helpers.utils.InstructionUtils;
import com.ibm.wala.util.collections.Pair;

import java.util.*;

import static com.aacr.helpers.detectors.app.Utils.getComponent;
import static com.aacr.helpers.detectors.app.Utils.startDfs;

public class GraphCreator {

    private final ClassHierarchy appCha;
    private final AnalysisScope appScope;
    private final HashMap<Component, ComponentInfo> compInfo;

    private final GraphManager manager = GraphManager.getInstance();

    public GraphCreator(ClassHierarchy appCha, AnalysisScope appScope, HashMap<Component, ComponentInfo> compInfo) {
        this.appCha = appCha;
        this.appScope = appScope;
        this.compInfo = compInfo;
    }

    public String createComponentGraph() {

        // Create a graph node for each of the activity component
        for (Map.Entry<Component, ComponentInfo> component : compInfo.entrySet()) {
            manager.addNode(component.getKey(), component.getValue());
        }

        // Create edges
        for (Node node : manager) {
            ArrayList<Pair<Node, EdgeLabel>> neighbors = extractNeighbors(node);
            for (Pair<Node, EdgeLabel> ngb : neighbors) {
                manager.addEdge(node, ngb.fst, ngb.snd);
            }
        }

        return manager.toGraphString();
    }

    private ArrayList<Pair<Node, EdgeLabel>> extractNeighbors(Node src) {
        ArrayList<Pair<Node, EdgeLabel>> res = new ArrayList<>();

        if (src == null || src.miscInfo == null || src.miscInfo.getCg() == null) return res;

        CallGraph cg = src.miscInfo.getCg();

        startDfs(cg, (Pair<CGNode, Protection> node, Stack<CGNode> parents) -> {
            try {
                for (SSAInstruction instruction : node.fst.getIR().getInstructions()) {
                    if (instruction instanceof SSAAbstractInvokeInstruction) {
                        SSAAbstractInvokeInstruction abIns = (SSAAbstractInvokeInstruction) instruction;

                        // If this is a known component invocation method
                        for (Component.Type type : Component.Type.values()) {
                            if (type.knownInvokeMethods != null
                                    && type.knownInvokeMethods.contains(abIns.getDeclaredTarget().getName().toString())) {
                                // Find the creation site of the intent being passed
                                Pair<Node, EdgeLabel> ngb = findDstns(abIns, node, parents, cg);
                                if (ngb != null)
                                    res.add(ngb);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                //ignore
            }
        }, compInfo, appCha, appScope);

        return res;
    }

    private Pair<Node, EdgeLabel> findDstns(SSAAbstractInvokeInstruction abIns,
                                            Pair<CGNode, Protection> startNode,
                                            Stack<CGNode> callChain, CallGraph cg) {
        int intentUseNum = findIntentParamNumber(abIns);
        Pair<Object, CGNode> intentDef = findDef(abIns, intentUseNum, startNode.fst, callChain, cg);
        if (intentDef == null)
            return null;
        CGNode defNode = intentDef.snd;
        if (intentDef.fst instanceof SSANewInstruction) {
            SSANewInstruction instr = (SSANewInstruction) intentDef.fst;
            int nextInd = instr.iIndex() + 1;
            if (defNode.getIR().getInstructions().length > nextInd) {
                SSAInstruction nextIns = defNode.getIR().getInstructions()[nextInd];
                Object classDef = nextIns;
                if (nextIns instanceof SSAAbstractInvokeInstruction
                        && ((SSAAbstractInvokeInstruction) nextIns)
                        .getDeclaredTarget().getName().equals(MethodReference.initAtom)) {
                    int useNum = findClassOrStrParamNumber((SSAAbstractInvokeInstruction) nextIns);
                    Pair<Object, CGNode> ret = findDef(nextIns, useNum, defNode, callChain, cg);
                    if (ret != null)
                        classDef = ret.fst;
                }
                String clzzStr = classDef != null ? extractClassStr(classDef) : null;
                if (clzzStr != null && !clzzStr.isEmpty()) {
                    Component comp = getComponent(clzzStr, compInfo, appCha);
                    if (manager.getNode(comp) == null)
                        manager.addExternalNode(comp);
                    return Pair.make(manager.getNode(comp), new EdgeLabel(startNode.snd));
                }
            }
        }

        return null;
    }

    private int findClassOrStrParamNumber(SSAAbstractInvokeInstruction abIns) {
        MethodReference method = abIns.getDeclaredTarget();
        for (int i=0; i<method.getNumberOfParameters(); i++) {
            TypeReference type = method.getParameterType(i);
            if (type == null)
                break;
            TypeName curName = type.getName();
            if (curName != null && curName.toString().equals("Ljava/lang/Class"))
                return i;
            else if (curName != null && curName.toString().equals("Ljava/lang/String"))
                return i;
        }

        return -1;
    }

    private int findIntentParamNumber(SSAAbstractInvokeInstruction abIns) {
        MethodReference target = abIns.getDeclaredTarget();
        for (int i=0; i < target.getNumberOfParameters(); i++) {
            TypeReference type = target.getParameterType(i);
            if (type != null && type.getName() != null && type.getName().toString().equals("Landroid/content/Intent"))
                return InstructionUtils.getParameterIndex(abIns, i);
        }

        return -1;
    }

    private Pair<Object, CGNode> findDef(SSAInstruction inputIns, int useNum, CGNode node, Stack<CGNode> callChain, CallGraph cg) {
        if (useNum == -1)
            return null;
        int valNum = useNum;
        if (useNum < inputIns.getNumberOfUses())
            valNum = inputIns.getUse(useNum);

        Object def = getDef(valNum, node.getIR().getSymbolTable(), node.getDU());
        CGNode target = node;

        if (def == null && !callChain.isEmpty()) {
            CGNode caller = callChain.pop();
            for (SSAInstruction ins : caller.getIR().getInstructions()) {
                if (ins instanceof SSAAbstractInvokeInstruction) {
                    SSAAbstractInvokeInstruction call = (SSAAbstractInvokeInstruction) ins;
                    if (call.getDeclaredTarget().equals(node.getMethod().getReference())) {
                        Pair<Object, CGNode> res = findDef(call, InstructionUtils.findParameterUseNumber(call, valNum), caller, callChain, cg);
                        if (res != null) {
                            def = res.fst;
                            target = res.snd;
                            break;
                        }
                    }
                }
            }
            callChain.push(caller);
        }

        if (def instanceof SSAAbstractInvokeInstruction) {
            Pair<SSANewInstruction, CGNode> res = findIntentInit(node, (SSAAbstractInvokeInstruction) def, cg, new HashSet<>());
            if (res != null) {
                def = res.fst;
                target = res.snd;
            }
        }

        if (def == null) return null;
        return Pair.make(def, target);
    }

    private Pair<SSANewInstruction, CGNode> findIntentInit(CGNode node, SSAAbstractInvokeInstruction defIns,
                                                           CallGraph cg, HashSet<CGNode> visited) {
        if (visited.contains(node))
            return null;
        visited.add(node);
        CGNode impSucc = null;
        for (Iterator<CGNode> it = cg.getSuccNodes(node); it.hasNext(); ) {
            CGNode succ = it.next();
            if (succ.getMethod().getReference().equals(defIns.getDeclaredTarget())) {
                impSucc = succ;
                break;
            }
        }
        if (impSucc != null) {
            for (SSAInstruction ins : impSucc.getIR().getInstructions()) {
                if (ins instanceof SSAReturnInstruction) {
                    int resVal = ((SSAReturnInstruction)ins).getResult();
                    SSAInstruction def = impSucc.getDU().getDef(resVal);
                    if (def instanceof SSANewInstruction)
                        return Pair.make((SSANewInstruction) def, impSucc);
                    else if (def instanceof SSAAbstractInvokeInstruction)
                        return findIntentInit(impSucc, (SSAAbstractInvokeInstruction) def, cg, visited);
                }
            }
        }

        return null;
    }

    private Object getDef(int valueNumber, SymbolTable st, DefUse du) {
        if (!st.isParameter(valueNumber)) {
            if(st.isConstant(valueNumber)) {
                return st.getConstantValue(valueNumber);
            } else {
                return du.getDef(valueNumber);
            }
        }
        return null;
    }

    private String extractClassStr(Object def) {
        if (def instanceof String)
            return (String) def;
        if (def instanceof SSALoadMetadataInstruction) {
            Object token = ((SSALoadMetadataInstruction) def).getToken();
            if (token instanceof TypeReference) {
                return ((TypeReference)token).getName().toString();
            }
        }

        return null;
    }
}


/*

private ArrayList<Object> findDefs(int valNum, BasicBlockInContext<ISSABasicBlock> startBb,
                                       InterproceduralCFG icfg) {
        ArrayList<Object> defs = new ArrayList<>();
        DefUseChain.addAvailableDefinition(defs, valNum, startBb.getNode().getIR().getSymbolTable(), startBb.getNode().getDU());
        DefUseChain.getParameterDef(valNum, startBb, icfg, defs, 0);

        return defs;
    }

private ArrayList<Pair<Node, EdgeLabel>> extractNeighbors(Node src) {
        ArrayList<Pair<Node, EdgeLabel>> res = new ArrayList<>();

        if (src == null || src.miscInfo == null || src.miscInfo.getCg() == null) return res;

        InterproceduralCFG icfg = new InterproceduralCFG(src.miscInfo.getCg());
        // Traverse the ICFG in DFS fashion
        DFSDiscoverTimeIterator<BasicBlockInContext<ISSABasicBlock>> dfsIterator = DFS.iterateDiscoverTime(icfg);
        while (dfsIterator.hasNext()) {
            BasicBlockInContext<ISSABasicBlock> bb = dfsIterator.next();
            TypeName curClassName = bb.getNode().getMethod().getDeclaringClass().getName();
            if (curClassName.getPackage() == null
                    || curClassName.getPackage().toString().isEmpty()
                    || curClassName.getPackage().toString().startsWith("android/")
                    || curClassName.getPackage().toString().startsWith("androidx/")
                    || curClassName.getPackage().toString().startsWith("java/"))
                continue;

            ArrayList<SSAInstruction> allIns = new ArrayList<>();
            for (SSAInstruction inst : bb) {
                allIns.add(inst);
            }

            for (SSAInstruction instruction : allIns) {
                try {
                    if (instruction instanceof SSAAbstractInvokeInstruction) {
                        SSAAbstractInvokeInstruction abIns = (SSAAbstractInvokeInstruction) instruction;
                        // If there is an activity invocation
                        if (Component.Type.ACTIVITY.knownInvokeMethods
                                .contains(abIns.getDeclaredTarget().getName().toString())) {
                            // Find the creation site of the intent being passed
                            for (String intentClass : findIntentClass(abIns, bb, icfg)) {
                                Component dstnComp = getComponent(intentClass);
                                res.add(Pair.make(manager.getNode(dstnComp), null));
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return res;
    }

    private ArrayList<String> findIntentClass(SSAAbstractInvokeInstruction abIns,
                                          BasicBlockInContext<ISSABasicBlock> bb,
                                          InterproceduralCFG icfg) {
        ArrayList<String> allReachableClasses = new ArrayList<>();
        ArrayList<Object> intentDefs = findDefs(abIns.getUse(1), bb, icfg);
        for (Object def : intentDefs) {
            if (def instanceof SSANewInstruction) {
                SSANewInstruction instr = (SSANewInstruction) def;
                int nextInd = instr.iIndex() + 1;
                if (bb.getNode().getIR().getInstructions().length > nextInd) {
                    SSAInstruction nextIns = bb.getNode().getIR().getInstructions()[nextInd];
                    if (nextIns instanceof SSAAbstractInvokeInstruction
                            && ((SSAAbstractInvokeInstruction) nextIns)
                            .getDeclaredTarget().getName().equals(MethodReference.initAtom)) {
                        ArrayList<Object> defs = DefUseChain.getParameterDef(nextIns.getUse(1), bb, icfg, new ArrayList<>(), 0);
                        for (Object classDef : defs) {
                            String clzzStr = extractClassStr(classDef);
                            if (clzzStr != null && !clzzStr.isEmpty())
                                allReachableClasses.add(clzzStr);
                        }
                    }
                }
            }
        }

        return allReachableClasses;
    }

* */