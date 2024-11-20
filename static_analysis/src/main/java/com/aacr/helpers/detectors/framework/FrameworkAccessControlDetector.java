package com.aacr.helpers.detectors.framework;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.shrike.shrikeBT.IConditionalBranchInstruction.Operator;
import com.ibm.wala.ssa.*;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.traverse.DFS;
import com.ibm.wala.util.graph.traverse.DFSDiscoverTimeIterator;
import com.aacr.helpers.accesscontrol.framework.*;
import com.aacr.helpers.accesscontrol.framework.ComparativeAccessControlCheck.ACType;
import com.aacr.helpers.dataflow.DefUseChain;
import com.aacr.helpers.dataflow.Reachability;
import com.aacr.helpers.extension.ExtendedAccessControlEnforcement;
import com.aacr.helpers.extension.ExtendedAccessControlEnforcement.Location;
import com.aacr.helpers.extension.ExtendedMethodReference;
import com.aacr.helpers.utils.*;
import com.aacr.helpers.utils.CallGraphUtils.CGType;
import com.aacr.wala.workshop.analyzers.FrameworkAnalyzer;

import java.util.*;

/**
 * This class provides utility functions for partitioning & detecting access control of framework entrypoints.
 */
public class FrameworkAccessControlDetector {

    public static HashMap<String, HashSet<AccessControlEnforcement>> acMap = new HashMap<>();

//    /**
//     * Calls analyzeEntrypoint(..) sequentially on each inputted entrypoint. Returns a map of all detected permissions.
//     * @param scope AnalysisScope.
//     * @param cha ClassHierarchy.
//     * @param frameworkEntrypoints List of framework entrypoints.
//     * @return HashMap linking MethodReferences to Lists of AccessControlEnforcement.
//     */
//    public static HashMap<DefaultEntrypoint, HashMap<ExtendedMethodReference, ExtendedAccessControlEnforcement>> detectFrameworkAccessControl(AnalysisScope scope, ClassHierarchy cha, HashSet<DefaultEntrypoint> frameworkEntrypoints, boolean parallel)
//            throws Exception {
//        HashMap<DefaultEntrypoint, HashMap<ExtendedMethodReference, ExtendedAccessControlEnforcement>> accessControlMap = new HashMap<>();
//
//        if(parallel) {
//
//            //filter...
//
//            accessControlMap = ParallelFrameworkAccessControlDetector.detectFrameworkAccessControlParallel(scope, cha, frameworkEntrypoints);
//        } else {
//            for (DefaultEntrypoint ep : frameworkEntrypoints) {
//                if(!scope.isApplicationLoader(ep.getMethod().getDeclaringClass().getClassLoader())) continue;
//                try {
//                    HashMap<ExtendedMethodReference, ExtendedAccessControlEnforcement> acList = analyzeEntrypoint(scope, cha, ep);
//                    accessControlMap.put(ep, acList);
//                } catch (Exception e) {
//                    continue;
//                }
//            }
//        }
//
//        return accessControlMap;
//    }

    public static void addAccessControlToMap(DefaultEntrypoint ep, HashMap<ExtendedMethodReference, ExtendedAccessControlEnforcement> accessControlMap, AccessControlEnforcement ac, ExtendedMethodReference targetMethod, Location location) {

        if (!acMap.containsKey(ep.getMethod().getSignature()))
            acMap.put(ep.getMethod().getSignature(), new HashSet<>());
        acMap.get(ep.getMethod().getSignature()).add(ac);

        if(targetMethod == null) {
            targetMethod = new ExtendedMethodReference(ac.getCallingMethodClass(), ac.getCallingMethod());
        }

        if(targetMethod.getMethodReference().toString().contains("FakeRootClass, fakeRootMethod()")) {
            targetMethod = new ExtendedMethodReference(ep.getMethod().getDeclaringClass().getReference(), ep.getMethod().getReference());
        }

        if((location == Location.REACHABLE) && ComparisonUtils.compareMethodReferences(ac.getCallingMethod(), targetMethod.getMethodReference()) &&
                ComparisonUtils.compareTypeReferences(ac.getCallingMethodClass(), targetMethod.getClassReference())) {
            return;
        }

        ExtendedMethodReference finalTargetMethod = targetMethod;
        Optional<ExtendedMethodReference> optionalAccessControlEnforcementKey = accessControlMap.entrySet()
                .stream()
                .filter(e -> e.getKey().getMethodReference().toString().equals(finalTargetMethod.getMethodReference().toString()))
                .filter(e -> e.getKey().getClassReference().toString().equals(finalTargetMethod.getClassReference().toString()))
                .map(Map.Entry::getKey)
                .findFirst();

        if(optionalAccessControlEnforcementKey.isPresent()) {
            accessControlMap.get(optionalAccessControlEnforcementKey.get()).addAccessControl(ac, location);
        } else {
            ExtendedAccessControlEnforcement extendedAC = new ExtendedAccessControlEnforcement();
            extendedAC.addAccessControl(ac, location);
            accessControlMap.put(targetMethod, extendedAC);
        }
    }

    public static HashMap<ExtendedMethodReference, ExtendedAccessControlEnforcement> recordAccessControl(DefaultEntrypoint ep, AnalysisScope scope, CallGraph cg, ArrayList<Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement>> acList) {
        HashMap<ExtendedMethodReference, ExtendedAccessControlEnforcement> accessControlMap = new HashMap<>();
        for(Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> acPair : acList) {


            addAccessControlToMap(ep, accessControlMap, acPair.snd, null, Location.PRESENT);
            HashSet<ExtendedMethodReference> reachableCallers = Reachability.detectReachableCallers(ep, cg, scope, acPair.fst.getNode());
            for(ExtendedMethodReference reachableCaller : reachableCallers) {
                addAccessControlToMap(ep, accessControlMap, acPair.snd, reachableCaller, Location.REACHABLE);
            }
        }
        return accessControlMap;
    }

    private static IMethod getOverridingMethod(IClass concreteClazz, IMethod abstractMethod) {
        return concreteClazz.getMethod(abstractMethod.getSelector());
    }

    public static ArrayList<IClass> getConcreteSubClass(IClass intOrAbstractClazz, ClassHierarchy cha) {
        ArrayList<IClass> allConcreteClazz = new ArrayList<>();
        for (IClass c : cha) {
            if (c.getAllImplementedInterfaces().contains(intOrAbstractClazz)
                    && !c.getName().toString().contains("Stub"))
                allConcreteClazz.add(c);
        }

        return allConcreteClazz;
    }

    public static DefaultEntrypoint getConcreteEp(DefaultEntrypoint e, ClassHierarchy cha) {
        ArrayList<IClass> concrete = getConcreteSubClass(e.getMethod().getDeclaringClass(), cha);
        if (!concrete.isEmpty()) {
            for (IClass conCl : concrete) {
                IMethod method = getOverridingMethod(conCl, e.getMethod());
                if (method != null)
                    return new DefaultEntrypoint(method, cha);
            }
        }

        return e;
    }

    /**
     * Creates call graph based on one particular entrypoint and
     * performs an access control analysis on one particular entrypoint.
     * @param scope AnalysisScope.
     * @param cha ClassHierarchy.
     * @param ep Entrypoint of interest.
     * @return ArrayList of corresponding AccessControlEnforcement, or an empty list.
     */
    public static HashMap<ExtendedMethodReference, ExtendedAccessControlEnforcement> analyzeEntrypoint(AnalysisScope scope,
                                                                                                       ClassHierarchy cha, DefaultEntrypoint ep) {
        try {

            DefaultEntrypoint concreteEp = ep;

            if (ep.getMethod().getDeclaringClass().isInterface())
                concreteEp = getConcreteEp(ep, cha);

            ArrayList<Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement>>  peList = new ArrayList<>();
            ArrayList<DefaultEntrypoint> singlePoint = new ArrayList<>();
            singlePoint.add(concreteEp);

            CallGraph cg = CallGraphUtils.buildCallGraph(CGType.V01CFA, scope,
                    cha, singlePoint,
                    AnalysisOptions.ReflectionOptions.NONE, null);

            InterproceduralCFG icfg = new InterproceduralCFG(cg);
            DFSDiscoverTimeIterator<BasicBlockInContext<ISSABasicBlock>> iterateDepthFirst = DFS
                    .iterateDiscoverTime(icfg);

            boolean clearCallingIdentityActivated = false;
            while (iterateDepthFirst.hasNext()) {
                BasicBlockInContext<ISSABasicBlock> bb = iterateDepthFirst.next();
                if (!scope.isApplicationLoader(bb.getNode().getMethod().getDeclaringClass().getClassLoader())) continue;
                ArrayList<Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement>>  peSubset = analyzeBasicBlockInContext(icfg, bb, clearCallingIdentityActivated);
                peList.addAll(peSubset);
            }
            return FrameworkAccessControlDetector.recordAccessControl(ep, scope, cg, peList);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public static void test(AnalysisScope scope,
                            ClassHierarchy cha, HashSet<DefaultEntrypoint> filteredEntryPoints) {

        for (DefaultEntrypoint ep : filteredEntryPoints) {
            if(!scope.isApplicationLoader(ep.getMethod().getDeclaringClass().getClassLoader())) continue;

            try {

                DefaultEntrypoint concreteEp = ep;

                if (ep.getMethod().getDeclaringClass().isInterface())
                    concreteEp = getConcreteEp(ep, cha);

                ArrayList<Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement>>  peList = new ArrayList<>();
                ArrayList<DefaultEntrypoint> singlePoint = new ArrayList<>();
                singlePoint.add(concreteEp);

                CallGraph cg = CallGraphUtils.buildCallGraph(CGType.V01CFA, scope,
                        cha, singlePoint,
                        AnalysisOptions.ReflectionOptions.NONE, null);

                InterproceduralCFG icfg = new InterproceduralCFG(cg);
                DFSDiscoverTimeIterator<BasicBlockInContext<ISSABasicBlock>> iterateDepthFirst = DFS
                        .iterateDiscoverTime(icfg);

                boolean clearCallingIdentityActivated = false;

                while (iterateDepthFirst.hasNext()) {
                    BasicBlockInContext<ISSABasicBlock> bb = iterateDepthFirst.next();
                    if (!scope.isApplicationLoader(bb.getNode().getMethod().getDeclaringClass().getClassLoader())) continue;
                    if(!bb.getNode().getMethod().getSignature().equals(ep.getMethod().getSignature())) continue;

                    ISSABasicBlock ebb = bb.getDelegate();
                    SymbolTable st = bb.getNode().getIR().getSymbolTable();
                    DefUse du = bb.getNode().getDU();

                    for(Iterator<SSAInstruction> it = ebb.iterator(); it.hasNext(); ) {
                        SSAInstruction i =  it.next();
                        if(i.toString().contains("clearCallingIdentity")) {
                            clearCallingIdentityActivated = true;
                        } else if(i.toString().contains("restoreCallingIdentity")) {
                            clearCallingIdentityActivated = false;
                        }
                        if(clearCallingIdentityActivated) continue;

                        if(i instanceof SSAAbstractInvokeInstruction){
                            System.out.println(((SSAAbstractInvokeInstruction) i).getDeclaredTarget().getName().toString());
                        }else{
                            System.out.println(InstructionUtils.getInstructionType(i));
                        }
                    }
                }
            } catch (Exception e) {
                continue;
            }
        }

    }

    public static HashSet<DefaultEntrypoint> getInvokes(AnalysisScope scope, ClassHierarchy cha, DefaultEntrypoint ep) {
        HashSet<DefaultEntrypoint> invokes = new HashSet<>();
        DefaultEntrypoint concreteEp = ep;

        if (ep.getMethod().getDeclaringClass().isInterface())
            concreteEp = getConcreteEp(ep, cha);

        ArrayList<Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement>>  peList = new ArrayList<>();
        ArrayList<DefaultEntrypoint> singlePoint = new ArrayList<>();
        singlePoint.add(concreteEp);

        CallGraph cg = CallGraphUtils.buildCallGraph(CGType.V01CFA, scope,
                cha, singlePoint,
                AnalysisOptions.ReflectionOptions.NONE, null);

        InterproceduralCFG icfg = new InterproceduralCFG(cg);
        DFSDiscoverTimeIterator<BasicBlockInContext<ISSABasicBlock>> iterateDepthFirst = DFS
                .iterateDiscoverTime(icfg);

        boolean clearCallingIdentityActivated = false;

        while (iterateDepthFirst.hasNext()) {
            BasicBlockInContext<ISSABasicBlock> bb = iterateDepthFirst.next();
            if (!scope.isApplicationLoader(bb.getNode().getMethod().getDeclaringClass().getClassLoader())) continue;

            ISSABasicBlock ebb = bb.getDelegate();
            SymbolTable st = bb.getNode().getIR().getSymbolTable();
            DefUse du = bb.getNode().getDU();

            for(Iterator<SSAInstruction> it = ebb.iterator(); it.hasNext(); ) {
                SSAInstruction i =  it.next();
                if(i.toString().contains("clearCallingIdentity")) {
                    clearCallingIdentityActivated = true;
                } else if(i.toString().contains("restoreCallingIdentity")) {
                    clearCallingIdentityActivated = false;
                }
                if(clearCallingIdentityActivated) continue;

                if(i instanceof SSAAbstractInvokeInstruction){
                    System.out.println(((SSAAbstractInvokeInstruction) i).getDeclaredTarget().getName().toString());
                }else{
                    System.out.println(InstructionUtils.getInstructionType(i));
                }
            }
        }
        return invokes;
    }



    /**
     * Analyzes each basic block in interprocedural control flow graph for invoke and conditionalbranch instructions.
     * The instructions of interest are passed along for further analysis and their access control information is returned.
     * The method ignores access control in between a clearCallingIdentity() and restoreCallingIdentity().
     * @param icfg The interprocedural control flow graph in which the basic block of interest is located.
     * @param bb Basic block of interest.
     * @return ArrayList of AccessControlEnforcement objects corresponding to the basic block.
     * @throws Exception
     */
    public static ArrayList<Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement>>  analyzeBasicBlockInContext(InterproceduralCFG icfg,
                                                                                                                             BasicBlockInContext<ISSABasicBlock> bb, boolean clearCallingIdentityActivated) throws Exception {
        ISSABasicBlock ebb = bb.getDelegate();
        SymbolTable st = bb.getNode().getIR().getSymbolTable();
        DefUse du = bb.getNode().getDU();

        ArrayList<Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement>>  peList = new ArrayList<>();
        for(Iterator<SSAInstruction> it = ebb.iterator(); it.hasNext(); ) {
            SSAInstruction i =  it.next();
            Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement>  pe = null;

            if(i.toString().contains("clearCallingIdentity")) {
                clearCallingIdentityActivated = true;
            } else if(i.toString().contains("restoreCallingIdentity")) {
                clearCallingIdentityActivated = false;
            }

            if(clearCallingIdentityActivated) continue;

            if (i instanceof SSAAbstractInvokeInstruction) {
                pe = analyzeInvokeInstruction(((SSAAbstractInvokeInstruction) i), du, icfg, bb, st);
            } else if (i instanceof SSAConditionalBranchInstruction) {
                pe = analyzeConditionalBranchInstruction(((SSAConditionalBranchInstruction) i),
                        icfg, bb, st, du);
            }
            if (pe != null) {
                peList.add(pe);
            }
        }
        return peList;
    }



    /**
     * Analyzes an invoke instruction to see if it invokes an access control-related method.
     *
     * @param i    SSAAbstractInvokeInstruction of interest.
     * @param du
     * @param icfg Interprocedural control flow graph.
     * @param bb   Basic block containing the SSAAbstractInvokeInstruction.
     * @param st   SymbolTable.
     * @return AccessControlEnforcement object corresponding to the SSAAbstractInvokeInstruction.
     * @throws Exception
     */
    public static Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> analyzeInvokeInstruction(SSAAbstractInvokeInstruction i, DefUse du, InterproceduralCFG icfg, BasicBlockInContext<ISSABasicBlock> bb, SymbolTable st) throws Exception {
        if (AccessControlUtils.isPermissionEnforcingMethod(i) || AccessControlUtils.isPermissionCheckingMethod(i)) {
            Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> pe = handlePermissionCheckOrEnforcement(st, icfg, bb, i);
            if (pe == null){
                return Pair.make(bb, new PotentialMethodNameAccessControl(
                        bb.getMethod().getReference(),
                        bb.getMethod().getDeclaringClass().getReference(), i, "permission"
                ));
            }else{
                return pe;
            }

        } else if (i.getDeclaredTarget().getName().toString().equals("equals")) {
            return analyzeConditionalBranchInstruction(i, icfg, bb, st, du);
        } else if (AccessControlUtils.nameRelatedToAccessControl(i.getDeclaredTarget().getName().toString()) &&
                !IClassUtils.isClassExclusionSuper(i.getDeclaredTarget().getDeclaringClass().getName().toString())) {



            Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> paramCheck = FrameworkAccessControlDetector.handlePermissionCheckOrEnforcement(st, icfg, bb, i);

            if(paramCheck != null){
                return paramCheck;
            }

            String nonPerm = "";

            String _name = i.getDeclaredTarget().getName().toString();

            if(_name.startsWith("get")){
                // Not an access check
                return null;
            }

            if(_name.toLowerCase().contains("package")){
                nonPerm = "package";
            }else if(_name.toLowerCase().contains("process")){
                nonPerm = "process";
            }
            else if(_name.toLowerCase().contains("user")){
                nonPerm = "user";
            }else if(_name.toLowerCase().contains("permission")){
                nonPerm = "permission";
            }else if(_name.toLowerCase().contains("uid")){
                nonPerm = "uid";
            }
            else if(_name.toLowerCase().contains("pid")){
                nonPerm = "uid";
            }

            return Pair.make(bb, new PotentialMethodNameAccessControl(
                    bb.getMethod().getReference(),
                    bb.getMethod().getDeclaringClass().getReference(), i, nonPerm
            ));
        }
//        else if(AccessControlUtils.nameRelatedToAccessControlGet(i.getDeclaredTarget().getName().toString()) &&
//                !IClassUtils.isClassExclusionSuper(i.getDeclaredTarget().getDeclaringClass().getName().toString())) {
//
//            String paramCheck = FrameworkAccessControlDetector.handlePermissionCheckOrEnforcementLight(st, icfg, bb, i);
//
//            if(paramCheck == null){
//                paramCheck = "MethodNameAC";
//            }
//            String _name = i.getDeclaredTarget().getName().toString();
//            if(_name.toLowerCase().contains("package")){
//                paramCheck = "package";
//            }else if(_name.toLowerCase().contains("process")){
//                paramCheck = "process";
//            }
//            else if(_name.toLowerCase().contains("user")){
//                paramCheck = "user";
//            }else if(_name.toLowerCase().contains("uid")){
//                paramCheck = "uid";
//            }
//            else if(_name.toLowerCase().contains("pid")){
//                paramCheck = "uid";
//            }
//
//            return Pair.make(bb, new PotentialMethodNameAccessControlGet(
//                    bb.getMethod().getReference(),
//                    bb.getMethod().getDeclaringClass().getReference(), i, paramCheck
//            ));
//        }
        return null;
    }


    /**
     * Analyzes a conditional branch instruction to see if the left-hand or right-hand sides are the
     * results of an invocation to an access control-related method.
     * @param i SSAConditionalBranchInstruction of interest.
     * @param icfg Interprocedural control flow graph.
     * @param bb Basic block containing the SSAAbstractInvokeInstruction.
     * @param st SymbolTable.
     * @param du DefUse object.
     * @return AccessControlEnforcement object corresponding to the SSAConditionalBranchInstruction, or null, if none is found.
     * @throws Exception
     */
    public static Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> analyzeConditionalBranchInstruction(
            SSAInstruction i, InterproceduralCFG icfg,
            BasicBlockInContext<ISSABasicBlock> bb, SymbolTable st, DefUse du) throws Exception {

        int firstUse = i.getUse(0);
        int secondUse = i.getUse(1);

        Pair<ArrayList<Object>, ArrayList<Object>> uses = Pair
                .make(InstructionUtils.getDefinition(firstUse, icfg, bb, st, du),
                        InstructionUtils.getDefinition(secondUse, icfg, bb, st, du));

        /**
         * If just at least one 'side' of the inequality contains an invocation to an access control-related instruction,
         * then we create a new AccessControlCheck object. We first examine the left-hand side. If no access control is
         * detected, then we move to the right-hand side.
         */
        Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> potentialAccessControl = handlePredicate(st, icfg, bb, i, uses.fst, 0);
        if(potentialAccessControl == null) {
            potentialAccessControl = handlePredicate(st, icfg, bb, i, uses.snd, 1);
        }
        return potentialAccessControl;
    }

    /**
     * If an instruction is found to invoke a method such as checkCallingPermission(..)
     * or enforceCallingPermission(..), then this method can be used to return a PermissionCheck
     * object describing the access control check.
     * @param st SymbolTable.
     * @param icfg Interprocedural control flow graph.
     * @param bb Basic block.
     * @param i SSAAbstractInvokeInstruction where the access control check method is invoked.
     * @return PermissionCheck object or null.
     * @throws Exception
     */
    public static Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> handlePermissionCheckOrEnforcement(SymbolTable st,
                                                                                                                         InterproceduralCFG icfg, BasicBlockInContext<ISSABasicBlock> bb,
                                                                                                                         SSAAbstractInvokeInstruction i)
            throws Exception {
//        int paramIndex = InstructionUtils.getParameterIndex(i, 0);
//        if (st.isStringConstant(i.getUse(paramIndex))) {
//            return Pair.make(bb, new PermissionCheck(
//                    InstructionUtils.getIndexForInstruction(i, bb.getNode()), i, bb.getDelegate().getMethod().getReference(), bb.getDelegate().getMethod().getDeclaringClass().getReference(), st.getStringValue(i.getUse(paramIndex))));
//        } else if (!st.isConstant(i.getUse(paramIndex))) {
//            Object permList = DefUseChain
//                    .chain(i.getUse(paramIndex), bb, icfg, new ArrayList<>());
//            Set<Object> permSet = new HashSet<>((ArrayList<Object>) permList);
//
//            return Pair.make(bb, new PermissionCheck(
//                    InstructionUtils.getIndexForInstruction(i, bb.getNode()), i, bb.getDelegate().getMethod().getReference(), bb.getDelegate().getMethod().getDeclaringClass().getReference(), permSet));
//        }
//        return null;
        int nUses = i.getNumberOfUses();
        for(int param = 0; param < nUses; param++){
            try {
                int paramIndex = InstructionUtils.getParameterIndex(i, param);
                if (st.isStringConstant(i.getUse(paramIndex))) {
                    if (st.getStringValue(i.getUse(paramIndex)).toLowerCase().contains("permission")) {
                        return Pair.make(bb, new PermissionCheck(
                                InstructionUtils.getIndexForInstruction(i, bb.getNode()), i, bb.getDelegate().getMethod().getReference(), bb.getDelegate().getMethod().getDeclaringClass().getReference(), st.getStringValue(i.getUse(paramIndex))));
                    }

                } else if (!st.isConstant(i.getUse(paramIndex))) {
                    Object permList = DefUseChain
                            .chain(i.getUse(paramIndex), bb, icfg, new ArrayList<>());
                    if (permList.toString().toLowerCase().contains("permission")) {
                        Set<Object> permSet = new HashSet<>((ArrayList<Object>) permList);
                        return Pair.make(bb, new PermissionCheck(
                                InstructionUtils.getIndexForInstruction(i, bb.getNode()), i, bb.getDelegate().getMethod().getReference(), bb.getDelegate().getMethod().getDeclaringClass().getReference(), permSet));
                    }
                }
            }catch (Exception e){
                // pass
            }
        }

        return null;
    }

    public static String handlePermissionCheckOrEnforcementLight(SymbolTable st,
                                                                 InterproceduralCFG icfg, BasicBlockInContext<ISSABasicBlock> bb,
                                                                 SSAAbstractInvokeInstruction i)
            throws Exception {
//        int paramIndex = InstructionUtils.getParameterIndex(i, 0);
        int nUses = i.getNumberOfUses();
        for(int param = 0; param < nUses; param++){
            try {
                int paramIndex = InstructionUtils.getParameterIndex(i, param);
                if (st.isStringConstant(i.getUse(paramIndex))) {
                    if (st.getStringValue(i.getUse(paramIndex)).toLowerCase().contains("permission")) {
                        return st.getStringValue(i.getUse(paramIndex));
                    }

                } else if (!st.isConstant(i.getUse(paramIndex))) {
                    Object permList = DefUseChain
                            .chain(i.getUse(paramIndex), bb, icfg, new ArrayList<>());
                    if (permList.toString().toLowerCase().contains("permission")) {
                        return permList.toString();
                    }
                }
            }catch (Exception e){
                // pass
            }
        }

        return null;
    }


    /**
     * Evaluates one of an SSAConditionalBranchInstruction's predicates to determine whether
     * it retrieves an access control-related field or invokes an access control-related method.
     * If so, the appropriate PermissionCheck or ComparativeAccessControlCheck object is returned.
     * @param st SymbolTable.
     * @param icfg Interprocedural control flow graph.
     * @param bb Basic block.
     * @param i SSAConditionalBranch instruction.
     * @param definitions Definitions (SSAInstructions or constants) associated with a particular predicate.
     * @param use which use are we looking at right now?
     * @return AccessControlEnforcement object or null.
     * @throws Exception
     */
    public static Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> handlePredicate(SymbolTable st,
                                                                                                      InterproceduralCFG icfg, BasicBlockInContext<ISSABasicBlock> bb,
                                                                                                      SSAInstruction i, ArrayList<Object> definitions, int use) throws Exception {
        for(Object definition : definitions) {
            if (definition instanceof SSAAbstractInvokeInstruction) {
                if(AccessControlUtils.isPermissionCheckingMethod((SSAAbstractInvokeInstruction) definition)) {
                    return handlePermissionCheckOrEnforcement(st, icfg, bb, (SSAAbstractInvokeInstruction) definition);
                } else if (AccessControlUtils.isGettingMethod((SSAAbstractInvokeInstruction) definition)) {
                    Object otherValue = null;
                    int otherUseNumber = (use == 0) ? 1 : 0;
                    ArrayList<Object> otherDefs = InstructionUtils.getDefinition(i.getUse(otherUseNumber), icfg, bb, st, bb.getNode().getDU());
                    if(otherDefs != null && otherDefs.size() > 0) {
                        otherValue = otherDefs.get(0);
                    }
                    Operator operator = Operator.EQ;
                    if (i instanceof SSAConditionalBranchInstruction)
                        operator = (Operator) ((SSAConditionalBranchInstruction) i).getOperator();

                    return Pair.make(bb, new ComparativeAccessControlCheck(
                            InstructionUtils.getIndexForInstruction(i, bb.getNode()), ComparativeAccessControlCheck.ACType.Getting, bb.getDelegate().getMethod().getReference(), bb.getDelegate().getMethod().getDeclaringClass().getReference(), i, ((SSAAbstractInvokeInstruction) definition), operator.toString(), otherValue));
                } else if (AccessControlUtils.isCheckingMethod((SSAAbstractInvokeInstruction) definition)) {
                    Object otherValue = null;
                    Operator operator = Operator.EQ;
                    if (i instanceof SSAConditionalBranchInstruction)
                        operator = (Operator) ((SSAConditionalBranchInstruction) i).getOperator();

                    return Pair.make(bb, new ComparativeAccessControlCheck(
                            InstructionUtils.getIndexForInstruction(i, bb.getNode()), ACType.Checking, bb.getDelegate().getMethod().getReference(), bb.getDelegate().getMethod().getDeclaringClass().getReference(), i, ((SSAAbstractInvokeInstruction) definition), operator.toString(), otherValue));
                }
            }
        }
        return null;
    }

    /**
     * Takes in a list of entrypoints and partitions them, based on number of available processors.
     * @param frameworkEntrypoints HashSet of DefaultEntrypoints, each an entrypoint to the Android framework.
     * @return List of lists, with each sublist containing a proportion of the framework entrypoints.
     */
    public static ArrayList<ArrayList<DefaultEntrypoint>> partitionEntrypoints(HashSet<DefaultEntrypoint> frameworkEntrypoints) {
        ArrayList<ArrayList<DefaultEntrypoint>> partitionedLists = new ArrayList<>();
        int numPartitions = Runtime.getRuntime().availableProcessors() - 1;
        if(numPartitions > 60) {
            numPartitions = 60;
        }
        int numEntrypoints = frameworkEntrypoints.size();

        if(numEntrypoints < numPartitions) {
            for(DefaultEntrypoint de : frameworkEntrypoints) {
                partitionedLists.add(new ArrayList<>(Arrays.asList(new DefaultEntrypoint[]{de})));
            }
        } else {
            int partitionMaxSize = (int) Math.floor((double)numEntrypoints / numPartitions);
            int currentPartitions = 0;
            int startIndex = 0;
            int endIndex;

            ArrayList<DefaultEntrypoint> entrypointList = new ArrayList<>(frameworkEntrypoints);
            while (currentPartitions < numPartitions) {
                if (((startIndex + partitionMaxSize) < numEntrypoints) && (currentPartitions
                        < numPartitions - 1)) {
                    endIndex = startIndex + partitionMaxSize;
                } else {
                    endIndex = numEntrypoints;
                }

                partitionedLists.add(new ArrayList<>(entrypointList.subList(startIndex, endIndex)));
                startIndex = endIndex;
                currentPartitions++;
            }
        }
        return partitionedLists;
    }

    public static HashMap<DefaultEntrypoint, HashMap<String, ArrayList<HashMap<String, String>>>> detectFrameworkAccessControlDFS(AnalysisScope scope, ClassHierarchy cha, HashSet<DefaultEntrypoint> filteredEntryPoints, int maxNum, int curNum, boolean b) throws Exception {

        if(b){
            ParallelFrameworkAccessControlDetector.detectFrameworkAccessControlParallel(scope, cha, filteredEntryPoints);
            return null;
        }else {


            int size = filteredEntryPoints.size();

            // for zero based indexing

            curNum = curNum - 1;

            int start = (size / maxNum) * curNum;
            int end = (curNum == maxNum - 1) ? size : (size / maxNum) * (curNum + 1);

            PrintUtils.print("Analyzing entry points from START:END - " + start + ":" + end);

            if (size == 1) {
                start = 0;
                end = 1;
            }

            HashMap<DefaultEntrypoint, HashMap<String, ArrayList<HashMap<String, String>>>> res = new HashMap<>();

            ArrayList<DefaultEntrypoint> toBeProcessed = new ArrayList<>();

            for (DefaultEntrypoint ee : filteredEntryPoints) {
                toBeProcessed.add(ee);
            }

            int count = 0;
            for (DefaultEntrypoint ep : toBeProcessed.subList(start, end)) {
                try {
                    PrintUtils.print(Integer.toString(count) + " " + ep.getMethod().getName().toString());
                    count += 1;

                    Pair<HashMap<String, String>, Pair<Pair<String, String>, HashMap<String, ArrayList<HashMap<String, String>>>>> _temp = ResourceMapper_1.DFS(scope, cha, ep);
                    ResourceMapper_1.InvokeWrapper(scope, cha, ep);
//                res.put(ep, _temp.snd);

                    if(_temp != null){
                        String filename = FrameworkAnalyzer.fileName + "/accessControlEP_" + ep.getMethod().getName().toString();
                        FrameworkAnalyzer.writeToFile2(filename, _temp.snd.snd, _temp.snd.fst, _temp.fst);
                        PrintUtils.print("Written to file");
                    }else{
                        PrintUtils.print("Temp is null");
                    }

                } catch (Exception e) {
                    // pass
                    e.printStackTrace();
                    PrintUtils.print("Caught Exception for EP: " + ep.getMethod().getName().toString());
                }

            }

            return res;
        }
    }

    public static Integer OR(Integer n1, Integer n2){
        // treat -1 as null
        // -1 AND x = x
        // -1 OR x = x
        if(n1 == -1){
            if(n2 == -1){
                return -1;
            }else{
                return n2;
            }
        }else if(n1 != -1){
            if(n2 == -1){
                return n1;
            }
        }

        if(n1 == n2){
            return n1;
        }else{
            if(n2 > n1){
                return n1;
            }
            return n2;
        }
    }

    public static Integer AND(Integer n1, Integer n2){
        // treat -1 as null
        // -1 AND x = x
        // -1 OR x = x
        if(n1 == -1){
            if(n2 == -1){
                return -1;
            }else{
                return n2;
            }
        }else if(n1 != -1){
            if(n2 == -1){
                return n1;
            }
        }

        if(n1 == n2){
            return n1;
        }else{
            if(n2 > n1){
                return n2;
            }
            return n1;
        }
    }
}