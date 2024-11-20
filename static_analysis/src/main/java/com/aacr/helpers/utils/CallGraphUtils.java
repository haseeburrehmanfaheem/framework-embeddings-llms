package com.aacr.helpers.utils;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.ssa.SSAPiNodePolicy;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.Pair;
import com.aacr.helpers.accesscontrol.app.Component;
import com.aacr.helpers.detectors.app.AppEntrypoint;

import java.util.ArrayList;
import java.util.Collection;

/**
 * This class contains utility methods for working with call graphs.

 */
public class CallGraphUtils {

    public enum CGType {
        RTA((language, options, cache, cha) -> Util.makeRTABuilder(options, cache, cha)),
        V01CFA(Util::makeVanillaZeroOneCFABuilder),
        ZERO1CFA(Util::makeZeroOneCFABuilder)
        ;

        public final MakeBuilder makeFun;
        CGType(MakeBuilder makeFun) {
            this.makeFun = makeFun;
        }
    }

    private interface MakeBuilder {
        CallGraphBuilder makeBuilder(Language language, AnalysisOptions options, AnalysisCache cache, ClassHierarchy cha);
    }

    /**
     * This method generates AnalysisOptions.
     * @param scope AnalysisScope.
     * @param cha ClassHierarchy.
     * @param entrypoints List of entrypoints that will be used to construct a callgraph.
     * @param reflectionOptions Reflection options.
     * @param piNodePolicy Pi Node Policy.
     * @return AnalysisOptions object.
     */
    public static <T extends DefaultEntrypoint> AnalysisOptions generateAnalysisOptions(AnalysisScope scope,
                                                                                        ClassHierarchy cha,
                                                                                        Collection<T> entrypoints,
        AnalysisOptions.ReflectionOptions reflectionOptions, SSAPiNodePolicy piNodePolicy) {

        AnalysisOptions options = new AnalysisOptions();
        options.setAnalysisScope(scope);

        if(entrypoints != null) {
            options.setEntrypoints(entrypoints);
        } else {
            options.setEntrypoints(new AllApplicationEntrypoints(scope, cha));
        }

        if(reflectionOptions != null) {
            options.setReflectionOptions(reflectionOptions);
        }

        if(piNodePolicy != null) {
            SSAOptions ssaOptions = new SSAOptions();
            ssaOptions.setPiNodePolicy(piNodePolicy);
            options.setSSAOptions(ssaOptions);
        }
        return options;
    }

    /**
     * Returns built callgraph.
     * @param type Call graph type.
     * @param scope AnalysisScope.
     * @param cha ClassHierarchy.
     * @param entrypoints Entrypoints to the Call Graph.
     * @param reflectionOptions Reflection options.
     * @param piNodePolicy Pi Node Policy.
     * @return CallGraph or null.
     */
    public static CallGraph buildCallGraph(CGType type, AnalysisScope scope, ClassHierarchy cha, Collection<DefaultEntrypoint> entrypoints, AnalysisOptions.ReflectionOptions reflectionOptions,
                                           SSAPiNodePolicy piNodePolicy) {
        AnalysisCacheImpl cache = new AnalysisCacheImpl(new DexIRFactory());
        AnalysisOptions options = generateAnalysisOptions(scope, cha, entrypoints, reflectionOptions, piNodePolicy);
        // Create the analysis options, setting the pointer analysis to TypeBasedPointerAnalysis



        CallGraph cg = null;
        CallGraphBuilder cgb = type.makeFun.makeBuilder(Language.JAVA, options, cache, cha);


        try {
            cg = cgb.makeCallGraph(options, null);
            return cg;
        } catch (Exception e) {
            return null;
        }
    }

    public static Pair<CallGraph, PointerAnalysis> buildCallGraphPA(CGType type, AnalysisScope scope, ClassHierarchy cha, Collection<DefaultEntrypoint> entrypoints, AnalysisOptions.ReflectionOptions reflectionOptions,
                                                                  SSAPiNodePolicy piNodePolicy) {
        AnalysisCacheImpl cache = new AnalysisCacheImpl(new DexIRFactory());
        AnalysisOptions options = generateAnalysisOptions(scope, cha, entrypoints, reflectionOptions, piNodePolicy);
        // Create the analysis options, setting the pointer analysis to TypeBasedPointerAnalysis

        try {
            CallGraph cg = null;
            CallGraphBuilder<InstanceKey> cgb = type.makeFun.makeBuilder(Language.JAVA, options, cache, cha);
            cg = cgb.makeCallGraph(options, null);
            PointerAnalysis<InstanceKey> pa = cgb.getPointerAnalysis();
//            PointerAnalysis<InstanceKey> pa = null;
            return Pair.make(cg, pa);
        } catch (Exception e) {
            return null;
        } catch (StackOverflowError e){
            PrintUtils.print("Stack Overflow Error");
            return null;
        }
    }

    /**
     * This method generates a call graph of a given app component using the default entry points
     * @param appScope Analysis scope containing dex files of the app
     * @param appCha Class hierarchy of the app
     * @param clazz Class corresponding to the app component
     * @param type Type of the app component
     * @return Call Graph of the component using the vanilla 0-1 CFA builder
     */
    public static CallGraph createComponentCg(AnalysisScope appScope, ClassHierarchy appCha, IClass clazz, Component.Type type) {

        if (clazz == null) return null;

        ArrayList<DefaultEntrypoint> curEps = new ArrayList<>();
        for (IMethod m : clazz.getAllMethods()) {
            if (m.getName() != null && type.knownEpMethods.contains(m.getName().toString()))
                curEps.add(new DefaultEntrypoint(m, appCha));
        }

        if (curEps.isEmpty())
            return null;

        // Create the builder and options for single class CG
        AnalysisOptions options = generateAnalysisOptions(appScope, appCha, curEps,
                AnalysisOptions.ReflectionOptions.NONE, null);
        SSAPropagationCallGraphBuilder builder = Util.makeZeroOneCFABuilder(Language.JAVA, options,
                new AnalysisCacheImpl(new DexIRFactory()), appCha);

        try {
            return builder.makeCallGraph(options);
        } catch (CancelException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * This method generates a call graph of a given app component using the specified entry points
     * @param appScope Analysis scope containing dex files of the app
     * @param appCha Class hierarchy of the app
     * @param curEps Entry points to the CG
     * @return Call Graph of the component using the vanilla 0-1 CFA builder
     */
    public static CallGraph createComponentCg(AnalysisScope appScope, ClassHierarchy appCha, Collection<AppEntrypoint> curEps) {

        if (curEps == null || curEps.isEmpty())
            return null;

        // Create the builder and options for single class CG
        AnalysisOptions options = generateAnalysisOptions(appScope, appCha, new ArrayList<>(curEps),
                AnalysisOptions.ReflectionOptions.NONE, null);
        SSAPropagationCallGraphBuilder builder = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options,
                new AnalysisCacheImpl(new DexIRFactory()), appCha);

        try {
            return builder.makeCallGraph(options);
        } catch (CancelException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static CGNode getNodeFromEntryPoint(CallGraph cg, Entrypoint ep){
        for (CGNode nd : cg) {

            if (ComparisonUtils.compareMethodReferences(nd.getMethod().getReference(),
                    ep.getMethod().getReference())) {
                return nd;
            }
        }
        return null;
    }
}
