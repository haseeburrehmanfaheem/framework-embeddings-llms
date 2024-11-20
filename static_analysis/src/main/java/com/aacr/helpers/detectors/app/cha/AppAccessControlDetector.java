package com.aacr.helpers.detectors.app.cha;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.ExplicitCallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.shrike.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.traverse.DFSPathFinder;
import com.aacr.helpers.accesscontrol.framework.AccessControlUtils;
import com.aacr.helpers.detectors.app.cha.context.DataflowContextDetector;

import java.util.*;

public class AppAccessControlDetector {
    private final int MAX_AC_CACHE_SIZE = 1000;
    private final ClassHierarchy cha;
    private final Cache<Pair<String, String>, HashSet<AppProgrammaticAc>> cachedAc = CacheBuilder
            .newBuilder().maximumSize(MAX_AC_CACHE_SIZE).build();

    private static AppAccessControlDetector instance = null;

    public static synchronized AppAccessControlDetector getInstance(ClassHierarchy cha) {
        if (instance == null)
            instance = new AppAccessControlDetector(cha);
        return instance;
    }

    private AppAccessControlDetector(ClassHierarchy cha) {
        this.cha = cha;
    }

    public HashSet<AppProgrammaticAc> extractAccessControls(ArrayList<String> curChain) {
        HashSet<AppProgrammaticAc> allAc = new HashSet<>();
        for (int i = 0; i < curChain.size() - 1; i++) {
            HashSet<AppProgrammaticAc> ac = cachedAc.getIfPresent(Pair
                    .make(curChain.get(i), curChain.get(i+1)));
            if (ac == null) {
                ac = extractMethodAc(curChain.get(i), curChain.get(i + 1));
                cachedAc.put(Pair.make(curChain.get(i), curChain.get(i+1)), ac);
            }
            if (!ac.isEmpty())
                allAc.addAll(ac);
        }

        return allAc;
    }

    private HashSet<AppProgrammaticAc> extractMethodAc(String curMethodSign, String nextMethodSign) {
        HashSet<AppProgrammaticAc> curAc = new HashSet<>();
        try {
            if (!isReachableAc(curMethodSign))
                return curAc;
            CallGraph curMethodCg = CachedCallGraphs.buildSingleEpCg(curMethodSign, cha);
            ExplicitCallGraph.ExplicitNode curNode
                    = (ExplicitCallGraph.ExplicitNode) curMethodCg.getEntrypointNodes().toArray()[0];
            ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = curNode.getCFG();
            for (Conditional con : getConditionalsInPaths(cfg, nextMethodSign)) {
                AppProgrammaticAc bbAc = extractAcFromCon(con, curNode, curMethodCg);
                if (bbAc != null)
                    curAc.add(bbAc);
            }
        } catch (Exception e) {
            //ignore
        }

        return curAc;
    }

    private boolean isReachableAc(String methodSign) {
        IMethod m = getMethodFromSignature(methodSign);
        if (!(m instanceof IBytecodeMethod))
            return false;
        try {
            for (CallSiteReference callSite : ((IBytecodeMethod<?>) m).getCallSites()) {
                try {
                    for (AccessControlUtils.MethodSet ms : AccessControlUtils.MethodSet.values()) {
                        if (ms.methodNames.contains(callSite.getDeclaredTarget().getSelector().getName().toString()))
                            return true;
                    }
                } catch (Exception e) {
                    //ignore
                }
            }
        } catch (Exception e) {
            //ignore
        }

        return false;
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

    AppProgrammaticAc extractAcFromCon(Conditional con, CGNode node, CallGraph cg) {
        DataflowContextDetector dfCtxDet = DataflowContextDetector.getInstance(cha);
        HashSet<Object> defs1 = dfCtxDet.getDefFromVal(con.use1, node, cg, new HashSet<>());
        HashSet<Object> defs2 = dfCtxDet.getDefFromVal(con.use2, node, cg, new HashSet<>());

        return getAcFromDefs(defs1, defs2, con, dfCtxDet);
    }

    private AppProgrammaticAc getAcFromDefs(HashSet<Object> defs1, HashSet<Object> defs2,
                                            Conditional con, DataflowContextDetector dfDet) {
        defs1.addAll(defs2);

        AppProgrammaticAc.Type type = null;
        HashSet<Object> val = new HashSet<>();

        for (Object obj : defs1) {
            if (obj instanceof SSAAbstractInvokeInstruction) {
                if (((SSAAbstractInvokeInstruction) obj).getDeclaredTarget().getName().toString().equals("getAction"))
                    type = AppProgrammaticAc.Type.ACTION;
                else {
                    DataflowContextDetector.NORMALIZED_PARAM_FLOW norm = dfDet.normalizeParamFlow(obj);
                    if (norm.equals(DataflowContextDetector.NORMALIZED_PARAM_FLOW.USER_IDENTITY))
                        type = AppProgrammaticAc.Type.USER_ID;
                    else if (norm.equals(DataflowContextDetector.NORMALIZED_PARAM_FLOW.APP_IDENTITY)) {
                        type = AppProgrammaticAc.Type.APP_ID;
                    }
                }
            } else if (obj instanceof String) {
                val.add(obj);
                if (((String) obj).contains("permission"))
                    type = AppProgrammaticAc.Type.PERMISSION;
            } else if (obj instanceof Integer) {
                val.add(obj);
            }
        }

        if (type != null && !val.isEmpty())
            return new AppProgrammaticAc(type, val, con.op);

        return null;
    }

    private HashSet<Conditional> getConditionalsInPaths(
            ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg, String nextMethodSign) {
        HashSet<Conditional> conditionals = new HashSet<>();
        List<ISSABasicBlock> curBbs = (new DFSPathFinder<>(cfg, cfg.entry(),
                (endBb -> getBbForMethod(cfg, nextMethodSign).contains(endBb)))).find();
        for (ISSABasicBlock bb : curBbs) {
            HashSet<Conditional> conds = shouldAnalyzeFurther(bb);
            if (conds != null && !conds.isEmpty())
                conditionals.addAll(conds);
        }

        return conditionals;
    }

    private HashSet<ISSABasicBlock> getBbForMethod(
            ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg, String target) {
        HashSet<ISSABasicBlock> curBbs = new HashSet<>();
        for (SSAInstruction ins : cfg.getInstructions()) {
            if (ins instanceof SSAAbstractInvokeInstruction
                    && ((SSAAbstractInvokeInstruction) ins).getDeclaredTarget().getSignature().equals(target)) {
                curBbs.add(cfg.getBlockForInstruction(ins.iIndex()));
            }
        }

        return curBbs;
    }

    private HashSet<Conditional> shouldAnalyzeFurther(ISSABasicBlock bb) {
        try {
            IClass curCls = bb.getMethod().getDeclaringClass();
            if(cha.getScope().isApplicationLoader(curCls.getClassLoader())
                    && !curCls.getName().toString().startsWith("Ljava/")
                    && !curCls.getName().toString().startsWith("Landroid/")
                    && !curCls.getName().toString().startsWith("Landroidx/"))
                return getConditionals(bb);
        } catch (Exception e) {
            //ignore
        }

        return null;
    }

    private HashSet<Conditional> getConditionals(ISSABasicBlock bb) {
        HashSet<Conditional> conditionals = new HashSet<>();
        if (bb instanceof SSACFG.BasicBlock) {
            for (SSAInstruction ins : ((SSACFG.BasicBlock) bb).getAllInstructions()) {
                if (ins instanceof SSAConditionalBranchInstruction) {
                    try {
                        SSAConditionalBranchInstruction conIns = (SSAConditionalBranchInstruction) ins;
                        conditionals.add(new Conditional(
                                conIns.getUse(0),
                                conIns.getUse(1),
                                (IConditionalBranchInstruction.Operator) (conIns).getOperator()
                        ));
                    } catch (Exception e) {
                        //ignore
                    }
                }
            }
        }

        return conditionals.isEmpty() ? null : conditionals;
    }

    private static class Conditional {
        public final int use1;
        public final int use2;
        public final IConditionalBranchInstruction.Operator op;

        private Conditional(int use1, int use2, IConditionalBranchInstruction.Operator op) {
            this.use1 = use1;
            this.use2 = use2;
            this.op = op;
        }
    }

    public static class AppProgrammaticAc {
        public final Type type;
        public final HashSet<Object> val;
        public final IConditionalBranchInstruction.Operator operator;

        public AppProgrammaticAc(Type type, HashSet<Object> val,
                                 IConditionalBranchInstruction.Operator operator) {
            this.type = type;
            this.val = val;
            this.operator = operator;
        }

        @Override
        public String toString() {
            return "type=" + type +
                    ", val=" + val +
                    ", operator=" + operator;
        }

        public enum Type {
            PERMISSION,
            ACTION,
            USER_ID,
            APP_ID
        }
    }

}
