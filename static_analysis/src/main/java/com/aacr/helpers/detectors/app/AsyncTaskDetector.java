package com.aacr.helpers.detectors.app;

import com.ibm.wala.classLoader.BytecodeClass;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.aacr.helpers.accesscontrol.app.Component;
import com.aacr.helpers.accesscontrol.app.Protection;
import com.aacr.helpers.detectors.app.componentgraph.models.info.ComponentInfo;

import java.util.*;

public class AsyncTaskDetector {

    private static final ArrayList<IClass> parentAsyncTasks = new ArrayList<>();
    private static final HashSet<String> knownAsyncEps = new HashSet<>(Arrays.asList(
            "onPreExecute",
            "doInBackground",
            "onPostExecute",
            "onProgressUpdate",
            "onCancelled"
    ));

    public static HashMap<Component, ComponentInfo> getEps(HashMap<Component, ComponentInfo> components,
                                                           ClassHierarchy appCha, AnalysisScope appScope) {
        initParentAsyncTasks(appCha);
        for (Map.Entry<Component, ComponentInfo> comp : components.entrySet()) {
            Utils.startDfs(comp.getValue().getCg(), (nodeProtectionPair, callChain) -> {
                CGNode node = nodeProtectionPair.fst;
                Protection protection = nodeProtectionPair.snd;
                for (Iterator<CallSiteReference> it = node.iterateCallSites(); it.hasNext(); ) {
                    MethodReference call = it.next().getDeclaredTarget();
                    if (call != null && isAsyncTask(call.getDeclaringClass(), appCha)
                            && call.getName() != null && call.getName().toString().contains("execute")) {
                        comp.getValue().addEps(getAsyncEps(call.getDeclaringClass(), appCha, protection));
                    }
                }
            }, components, appCha, appScope);
        }
        return components;
    }

    private static void initParentAsyncTasks(ClassHierarchy appCha) {
        AnalysisScope appScope = appCha.getScope();
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
                    && curClass.getClassName().toString().equals("AsyncTask")) {
                parentAsyncTasks.add(c);
                continue;
            }
            if (parentClass != null
                    && parentClass.getPackage() != null
                    && parentClass.getClassName() != null
                    && parentClass.getPackage().toString().startsWith("android/")
                    && parentClass.getClassName().toString().equals("AsyncTask"))
                parentAsyncTasks.add(c);
        }
    }

    private static boolean isAsyncTask(TypeReference t, ClassHierarchy appCha) {
        IClass c = appCha.lookupClass(t);
        if (c == null)
            return false;
        for (IClass p : parentAsyncTasks) {
            if (c.equals(p) || appCha.isSubclassOf(c, p))
                return true;
        }

        return false;
    }

    private static HashMap<AppEntrypoint, Protection> getAsyncEps(TypeReference t, ClassHierarchy appCha, Protection protection) {
        HashMap<AppEntrypoint, Protection> result = new HashMap<>();
        IClass c = appCha.lookupClass(t);
        if (c == null)
            return result;
        for (IMethod m : c.getAllMethods()) {
            if (m.getName() != null && knownAsyncEps.contains(m.getName().toString()))
                result.put(new AppEntrypoint(m, appCha, t), protection);
        }

        return result;
    }
}
