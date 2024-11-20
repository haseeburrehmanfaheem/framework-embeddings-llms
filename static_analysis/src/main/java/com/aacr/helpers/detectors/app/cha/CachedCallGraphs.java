package com.aacr.helpers.detectors.app.cha;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.debug.UnimplementedError;
import com.aacr.helpers.detectors.app.AppEntrypoint;
import com.aacr.helpers.utils.CallGraphUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public class CachedCallGraphs {
    private static final int CG_CACHE_MAX_SIZE = 1000;
    private static final Cache<String, CallGraph> cachedCg
            = CacheBuilder.newBuilder().maximumSize(CG_CACHE_MAX_SIZE).build();

    public static CallGraph buildSingleEpCg(String method, ClassHierarchy cha) {
        CallGraph cg = cachedCg.getIfPresent(method);
        if (cg == null) {
            try {
                IMethod curMethod = getMethodFromSignature(method, cha);
                Collection<AppEntrypoint> eps = Collections.singleton(new AppEntrypoint(curMethod, cha,
                        cha.lookupClass(TypeReference.find(ClassLoaderReference.Application,
                                ChaUtils.getClassNameFromSignature(method))).getReference()));
                AnalysisOptions options = CallGraphUtils.generateAnalysisOptions(cha.getScope(), cha,
                        eps, AnalysisOptions.ReflectionOptions.NONE, null);
                SSAPropagationCallGraphBuilder builder = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options,
                        new AnalysisCacheImpl(new DexIRFactory()), cha);
                cg = builder.makeCallGraph(options, null);
                cachedCg.put(method, cg);
            } catch (Exception | UnimplementedError e) {
                throw new RuntimeException("Error building CG", e);
            }
        }
        return cg;
    }

    public static CallGraph buildSingleEpCg(MethodReference method, ClassHierarchy cha) {
        CallGraph cg = cachedCg.getIfPresent(method.getSignature());
        if (cg == null) {
            try {
                Collection<AppEntrypoint> eps = Collections.singleton(new AppEntrypoint(method, cha,
                        cha.lookupClass(method.getDeclaringClass()).getReference()));
                AnalysisOptions options = CallGraphUtils.generateAnalysisOptions(cha.getScope(), cha,
                        eps, AnalysisOptions.ReflectionOptions.NONE, null);
                SSAPropagationCallGraphBuilder builder = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options,
                        new AnalysisCacheImpl(new DexIRFactory()), cha);
                cg = builder.makeCallGraph(options, null);
                cachedCg.put(method.getSignature(), cg);
            } catch (Exception | UnimplementedError e) {
                throw new RuntimeException("Error building CG", e);
            }
        }
        return cg;
    }

    public static CallGraph buildClassCg(IClass clazz, ClassHierarchy cha) {
        String className = clazz.getName().toString();
        CallGraph cg = cachedCg.getIfPresent(className);
        if (cg == null) {
            try {
                Collection<AppEntrypoint> eps = getEps(clazz, cha);
                AnalysisOptions options = CallGraphUtils.generateAnalysisOptions(cha.getScope(), cha,
                        eps, AnalysisOptions.ReflectionOptions.NONE, null);
                SSAPropagationCallGraphBuilder builder = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options,
                        new AnalysisCacheImpl(new DexIRFactory()), cha);
                cg = builder.makeCallGraph(options, null);
                cachedCg.put(className, cg);
            } catch (Exception | UnimplementedError e) {
                throw new RuntimeException("Error building CG", e);
            }
        }
        return cg;
    }

    public static IMethod getMethodFromSignature(String methodSign, ClassHierarchy cha) {
        IMethod curMethod = null;
        try {
            IClass curClass = cha.lookupClass(TypeReference.find(ClassLoaderReference.Application,
                    ChaUtils.getClassNameFromSignature(methodSign)));
            curMethod = curClass.getMethod(Selector
                    .make(methodSign.substring(methodSign.lastIndexOf('.')+1)));
        } catch (Exception e) {
            //ignore
        }
        return curMethod;
    }

    private static Collection<AppEntrypoint> getEps(IClass clazz, ClassHierarchy cha) {
        HashSet<AppEntrypoint> eps = new HashSet<>();
        try {
            for (IMethod m : clazz.getDeclaredMethods()) {
                if (m.isPublic())
                    eps.add(new AppEntrypoint(m, cha, clazz.getReference()));
            }
        } catch (Exception e) {
            //ignore
        }
        return eps;
    }
}
