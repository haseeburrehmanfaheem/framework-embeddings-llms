package com.aacr.helpers.detectors.app;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.TypeReference;
import com.aacr.helpers.accesscontrol.app.Component;
import com.aacr.helpers.accesscontrol.app.Protection;
import com.aacr.helpers.detectors.app.componentgraph.models.info.ComponentInfo;
import com.ibm.wala.util.collections.Pair;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;
import java.util.function.BiConsumer;

public class Utils {
    public static Protection getEpProtection(CGNode node, HashMap<Component, ComponentInfo> compInfo, ClassHierarchy appCha) {
        Component comp = getComponent(node.getMethod().getDeclaringClass().getName().toString(), compInfo, appCha);
        ComponentInfo info = compInfo.get(comp);
        if (info != null && info.epMap != null && !info.epMap.isEmpty()) {
            for (AppEntrypoint ep : info.epMap.keySet()) {
                if (ep.getMethod().getReference().equals(node.getMethod().getReference()))
                    return info.epMap.get(ep);
            }
        }

        return null;
    }

    public static Component getComponent(String compClazzStr, HashMap<Component, ComponentInfo> compInfo, ClassHierarchy appCha) {
        if (compClazzStr.contains("$"))
            compClazzStr = compClazzStr.substring(0, compClazzStr.indexOf('$'));
        TypeReference compClazz = TypeReference.find(appCha.getScope().getApplicationLoader(), compClazzStr);
        if (compClazz != null) {
            IClass cls = appCha.lookupClass(compClazz);
            if (cls != null && cls.getName() != null) {
                for (Component comp : compInfo.keySet()) {
                    if (comp != null && comp.getClassStr() != null && comp.getClassStr().equals(cls.getName().toString()))
                        return comp;
                }
            }
        }

        return new Component(compClazzStr);
    }

    public static void startDfs(CallGraph cg, BiConsumer<Pair<CGNode, Protection>, Stack<CGNode>> doStuff,
                                HashMap<Component, ComponentInfo> compInfo, ClassHierarchy appCha, AnalysisScope appScope) {
        if (cg == null)
            return;
        for (CGNode node : cg.getEntrypointNodes()) {

            if (AppClassUtils.shouldNotAnalyze(node.getMethod().getDeclaringClass(), appScope))
                continue;

            Protection protection = getEpProtection(node, compInfo, appCha);
            doDfs(Pair.make(node, protection), doStuff, cg, new Stack<>());
        }
    }

    private static void doDfs(Pair<CGNode, Protection> node,
                       BiConsumer<Pair<CGNode, Protection>, Stack<CGNode>> doStuff,
                       CallGraph cg, Stack<CGNode> visited) {

        IClass curClassName = node.fst.getMethod().getDeclaringClass();
        if (AppClassUtils.shouldNotAnalyze(curClassName,
                node.fst.getMethod().getDeclaringClass().getClassHierarchy().getScope()))
            return;

        doStuff.accept(node, (Stack<CGNode>) visited.clone());
        visited.push(node.fst);
        for (Iterator<CGNode> it = cg.getSuccNodes(node.fst); it.hasNext(); ) {
            CGNode succNode = it.next();
            if (!visited.contains(succNode))
                doDfs(Pair.make(succNode, node.snd), doStuff, cg, visited);
        }
        visited.pop();
    }
}
