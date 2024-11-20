package com.aacr.helpers.detectors.app;

import com.ibm.wala.classLoader.BytecodeClass;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.classLoader.DexIMethod;
import com.ibm.wala.dalvik.dex.instructions.Instruction;
import com.ibm.wala.dalvik.dex.instructions.Invoke;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.TypeName;
import com.aacr.helpers.accesscontrol.app.Component;
import com.aacr.helpers.accesscontrol.app.Protection;
import com.aacr.helpers.detectors.app.componentgraph.models.info.ComponentInfo;
import com.ibm.wala.util.collections.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FragmentEntrypointDetector {

    public static HashMap<Component, ComponentInfo> getEps(ClassHierarchy appCha, AnalysisScope appScope,
                                                           HashMap<String, Pair<Component, ComponentInfo>> activityFragmentEps) {
        // Get the parent 'Fragment' classes (i.e., ones from android or androidx package or classes that extend those directly)
        ArrayList<IClass> parentFragments = getParentFragmentClasses(appCha, appScope);
        // Get the actual fragments defined by the current app
        HashMap<IClass, Component> fragments = getAllEpFragments(parentFragments, appCha, appScope);
        // Final result will be populated in this map
        HashMap<Component, ComponentInfo> compMap = new HashMap<>();

        for (Map.Entry<IClass, Component> e : fragments.entrySet()) {
            HashMap<AppEntrypoint, Protection> resultMap = new HashMap<>(
                    extractAllEpsFromFragmentClass(appCha, e.getKey(), activityFragmentEps));
            compMap.put(e.getValue(), new ComponentInfo(appScope, appCha, resultMap));
        }

        return compMap;
    }

    // Given a fragment class, extract all known EPs from the fragment
    private static HashMap<AppEntrypoint, Protection> extractAllEpsFromFragmentClass(ClassHierarchy cha,
                                                                                     IClass fragmentClass,
                                                                                     HashMap<String, Pair<Component, ComponentInfo>> activityFragmentEps) {
        HashMap<IClass, IMethod> allInvokers = extractInvokers(cha, fragmentClass);
        Protection activityProtection = getManifestProtections(allInvokers, activityFragmentEps);

        HashMap<AppEntrypoint, Protection> resultantMap = new HashMap<>();
        for (IMethod method : fragmentClass.getAllMethods()) {
            if (Component.Type.FRAGMENT.knownEpMethods.contains(method.getName().toString()))
                resultantMap.put(new AppEntrypoint(method, cha, fragmentClass.getReference()), activityProtection);
        }

        return resultantMap;
    }

    private static Protection getManifestProtections(HashMap<IClass, IMethod> allInvokers,
                                                     HashMap<String, Pair<Component, ComponentInfo>> activityFragmentEps) {
        for (IClass c : allInvokers.keySet()) {
            Pair<Component, ComponentInfo> comp = activityFragmentEps.get(c.getName().toString());
            if (comp != null && comp.fst != null)
                return comp.fst.getProtection();
        }

        return null;
    }

    private static HashMap<IClass, IMethod> extractInvokers(ClassHierarchy cha, IClass fragmentClass) {
        HashMap<IClass, IMethod> result = new HashMap<>();
        for (IClass c : cha) {
            if (AppClassUtils.shouldNotAnalyze(c, cha.getScope()))
                continue;
            try {
                for (IMethod m : c.getAllMethods()) {
                    if (m instanceof DexIMethod) {
                        for (Instruction ins : ((DexIMethod) m).getInstructions()) {
                            if (ins instanceof Invoke) {
                                Invoke inv = (Invoke) ins;
                                if (inv.clazzName.equals(fragmentClass.getName().toString())
                                        && inv.methodName.startsWith("<init")) {
                                    result.put(c, m);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                //ignore
            }
        }

        return result;
    }


    public static ArrayList<IClass> getParentFragmentClasses(ClassHierarchy appCha, AnalysisScope appScope) {
        ArrayList<IClass> fragmentClasses = new ArrayList<>();
        for (IClass c : appCha) {
            if (!appScope.isApplicationLoader(c.getClassLoader()))
                continue;
            TypeName curClass = c.getName();
            TypeName parentClass = null;
            try {
                parentClass = ((BytecodeClass)c).getSuperName();
            } catch (Exception e) {
                //ignore
            }
            if (curClass == null || curClass.getPackage() == null)
                continue;
            if (curClass.getPackage().toString().startsWith("android/")
                    && curClass.getClassName().toString().equals("Fragment")) {
                fragmentClasses.add(c);
                continue;
            }
            if (parentClass != null
                    && parentClass.getPackage() != null
                    && parentClass.getClassName() != null
                    && parentClass.getPackage().toString().startsWith("android/")
                    && parentClass.getClassName().toString().equals("Fragment"))
                fragmentClasses.add(c);
        }
        return fragmentClasses;
    }

    private static HashMap<IClass, Component> getAllEpFragments(ArrayList<IClass> parentFragments,
                                                                ClassHierarchy appCha, AnalysisScope appScope) {
        HashMap<IClass, Component> fragmentClasses = new HashMap<>();
        for (IClass c : appCha) {

            if (AppClassUtils.shouldNotAnalyze(c, appScope)
                    || c.isAbstract() || c.isInterface())
                continue;

            for (IClass parent : parentFragments) {
                if (parent.equals(c) || appCha.isSubclassOf(c, parent)) {
                    fragmentClasses.put(c, new Component(c.getName().toString(), Component.Type.FRAGMENT,
                            false, false, null, null, null));
                    break;
                }
            }
        }
        return fragmentClasses;
    }

//    public static HashMap<Component, ComponentInfo> getEps(HashMap<String, Component> activityEps,
//                                                           ClassHierarchy cha, AnalysisScope scope) {
//
//        HashMap<Component, ComponentInfo> compMap = new HashMap<>();
//        // Get the parent (i.e., the <some-package-name>.FragmentActivity) FragmentActivity classes
//        ArrayList<IClass> parentFragmentActivities = getParentFragmentActivityClasses(cha, scope);
//        // Get activities in the app that extend one of the above identified parent classes
//        HashMap<IClass, Component> fragmentActivities = getAllEpFragmentActivities(activityEps, parentFragmentActivities, cha, scope);
//
//        for (IClass activity : fragmentActivities.keySet()) {
//            CallGraph activityCg = CallGraphUtils.createComponentCg(scope, cha, activity, Component.Type.ACTIVITY);
//
//            if (activityCg == null)
//                continue;
//
//            HashMap<AppEntrypoint, Protection> resultMap = new HashMap<>();
//
//            InterproceduralCFG activityIcfg = new InterproceduralCFG(activityCg);
//            DFSDiscoverTimeIterator<BasicBlockInContext<ISSABasicBlock>> iterateDepthFirst = DFS.iterateDiscoverTime(activityIcfg);
//
//            // Iterate through the ICFG in DFS manner to extract the fragments
//            while (iterateDepthFirst.hasNext()) {
//                BasicBlockInContext<ISSABasicBlock> bb = iterateDepthFirst.next();
//
//                // Skip the primordials
//                if (!scope.isApplicationLoader(bb.getMethod().getDeclaringClass().getClassLoader()))
//                    continue;
//
//                ISSABasicBlock ebb = bb.getDelegate();
//                DefUse du = bb.getNode().getDU();
//
//                // Iterate through all instructions in the BB
//                for (SSAInstruction currentInstruction : ebb) {
//                    if (currentInstruction instanceof SSAAbstractInvokeInstruction) {
//                        SSAAbstractInvokeInstruction curInvokeInstr = (SSAAbstractInvokeInstruction) currentInstruction;
//
//                        // Only fragment transaction add and replace methods are of interest to us
//                        if (curInvokeInstr.getDeclaredTarget().getSignature().contains("FragmentTransaction.add(")
//                                || curInvokeInstr.getDeclaredTarget().getSignature().contains("FragmentTransaction.replace(")) {
//                            // Starts from zero because getDeclaredTarget.getParameterType params somehow does not consider 'this'
//                            int fragmentParam = 0;
//                            TypeReference type = curInvokeInstr.getDeclaredTarget().getParameterType(fragmentParam);
//                            if (!type.toString().contains("Fragment"))
//                                fragmentParam++;
//                            // When calling getUse, it DOES consider 0th param as 'this' for invokevirtual
//                            // So will need to add 1 to the fragmentParam
//                            ArrayList<TypeReference> fragmentType = InstructionUtils.getTargetTypeReference(
//                                    du.getDef(curInvokeInstr.getUse(fragmentParam + 1)), du);
//                            if (!fragmentType.isEmpty())
//                                resultMap.putAll(extractAllEpsFromFragmentClass(cha, fragmentType.get(0),
//                                        fragmentActivities.get(activity).getProtection()));
//                        }
//                    }
//                }
//            }
//            compMap.put(fragmentActivities.get(activity), new ComponentInfo(scope, cha, resultMap));
//        }
//
//        return compMap;
//    }

    // Given a fragment name, extract all known EPs from the fragment
//    private static HashMap<AppEntrypoint, Protection> extractAllEpsFromFragmentClass(ClassHierarchy cha,
//                                                                                         TypeReference fragmentType,
//                                                                                         Protection protection) {
//        HashMap<AppEntrypoint, Protection> resultantMap = new HashMap<>();
//        IClass fragmentClass = cha.lookupClass(fragmentType);
//        if (fragmentClass != null) {
//            for (IMethod method : fragmentClass.getAllMethods()) {
//                if (knownFragmentEps.contains(method.getName().toString()))
//                    resultantMap.put(new AppEntrypoint(method, cha), protection);
//            }
//        }
//
//        return resultantMap;
//    }

    // Get all activities that are:
    // (i) declared in manifest of the app
    //     AND
    // (ii) extend the one of the parent FragmentActivity identified in the previous step
//    private static HashMap<IClass, Component> getAllEpFragmentActivities(HashMap<String, Component> activityEps,
//                                                                ArrayList<IClass> parentFragmentActivities,
//                                                                ClassHierarchy cha, AnalysisScope scope) {
//        HashMap<IClass, Component> fragmentActs = new HashMap<>();
//        for (IClass c : cha) {
//            if (!scope.isApplicationLoader(c.getClassLoader()))
//                continue;
//            Component epComponent = activityEps.get(c.getName().toString());
//            if (epComponent == null)
//                continue;
//            for (IClass parent : parentFragmentActivities) {
//                if (cha.isSubclassOf(c, parent)) {
//                    fragmentActs.put(c, epComponent);
//                    break;
//                }
//            }
//        }
//        return fragmentActs;
//    }

    // Get activities that are used in this project as parent for fragment handling
    // This is usually FragmentActivity either from one of the android support packages or from androidx jetpack library
    // The returned arraylist is expected to usually have only one element, and at most two in unusual cases.
    // More than 2 should be VERY RARE (cannot think of a scenario that this could happen)
//    private static ArrayList<IClass> getParentFragmentActivityClasses(ClassHierarchy cha, AnalysisScope scope) {
//        ArrayList<IClass> fragmentActivities = new ArrayList<>();
//        for (IClass c : cha) {
//            if (!scope.isApplicationLoader(c.getClassLoader()))
//                continue;
//            if (c.getName().toString().endsWith("FragmentActivity"))
//                fragmentActivities.add(c);
//        }
//        return fragmentActivities;
//    }
}
