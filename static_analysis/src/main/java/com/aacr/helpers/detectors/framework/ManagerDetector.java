package com.aacr.helpers.detectors.framework;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.opencsv.CSVWriter;
import com.aacr.helpers.accesscontrol.framework.AccessControlEnforcement;
import com.aacr.helpers.detectors.ChaAnalyzer;
import com.aacr.helpers.utils.ScopeUtils;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class provides methods to detect the Manager APIs and the permission checks enforced for them
 */
public class ManagerDetector {

    private final ClassHierarchy cha;
    private final HashMap<String, HashSet<AccessControlEnforcement>> frameworkDefaultEps;
    private final HashMap<String, ArrayList<ArrayList<ChaAnalyzer.MethodInCallChain>>> serviceApiChains;
    private final HashMap<String, HashSet<ManagerChain>> managerChains = new HashMap<>();

    public ManagerDetector(String frameworkPath,
                           HashMap<String, HashSet<AccessControlEnforcement>> frameworkDefaultEps,
                           CSVWriter fwMethodPropCsvWriter)
            throws ClassHierarchyException, IOException {
        this.cha = ClassHierarchyFactory.makeWithRoot(ScopeUtils.makeManagerScope(frameworkPath));
        this.frameworkDefaultEps = frameworkDefaultEps;
        serviceApiChains = (new ChaAnalyzer(cha, new HashSet<>(frameworkDefaultEps.keySet())))
                .analyze(fwMethodPropCsvWriter);
    }

    public HashMap<String, HashSet<AccessControlEnforcement>> getManagerEps() {
        HashMap<String, HashSet<AccessControlEnforcement>> auxProtections = new HashMap<>();

        for (Map.Entry<String, HashSet<ManagerChain>> mChain : getManagerChains().entrySet()) {
            auxProtections.put(mChain.getKey(), mChain.getValue().stream()
                    .map(mc -> mc.fwAc).reduce(new HashSet<>(), (h1, h2)-> {
                        h1.addAll(h2);
                        return h1;
                    }));
        }

        return auxProtections;
    }

    public HashMap<String, HashSet<ManagerChain>> getManagerChains() {
        if (managerChains.isEmpty()) {
            for (Map.Entry<String, ArrayList<ArrayList<ChaAnalyzer.MethodInCallChain>>> e
                    : serviceApiChains.entrySet()) {
                for (ArrayList<ChaAnalyzer.MethodInCallChain> curChain : e.getValue()) {
                    for (int i = 0; i < curChain.size(); i++) {
                        String curMethodSign = curChain.get(i).methodSign;
                        IMethod curMethod = getMethodFromSign(curMethodSign);
                        if (curMethod == null || !curMethod.isPublic())
                            continue;
                        if (!managerChains.containsKey(curMethodSign))
                            managerChains.put(curMethodSign, new HashSet<>());
                        managerChains.get(curMethodSign)
                                .add(new ManagerChain(curChain.subList(i, curChain.size()), curMethodSign,
                                        e.getKey(), frameworkDefaultEps.get(e.getKey())));
                    }
                }
            }
        }

        return managerChains;
    }

    private IMethod getMethodFromSign(String methodSign) {
        IMethod target = null;
        try {
            IClass targetCls = cha.lookupClass(TypeReference.find(ClassLoaderReference.Application,
                    getClassNameFromSignature(methodSign)));
            for (IMethod tm : targetCls.getAllMethods()) {
                if (tm.getSignature().equals(methodSign))
                    target = tm;
            }
        } catch (Exception e) {
            //ignore
        }

        return target;
    }

    private String getClassNameFromSignature(String methodSignature) {
        String fullMethodName = methodSignature.substring(0, methodSignature.indexOf('('));
        return "L"+fullMethodName.substring(0, fullMethodName.lastIndexOf('.')).replace('.','/');
    }

    public static class ManagerChain implements Serializable {
        public final List<String> chain;
        public final String managerMethod;
        public final String serviceApi;
        public final HashSet<String> fwAcStr;
        transient public final HashSet<AccessControlEnforcement> fwAc;

        public ManagerChain(List<ChaAnalyzer.MethodInCallChain> chain, String managerMethod,
                            String serviceApi, HashSet<AccessControlEnforcement> fwAc) {
            this.chain = (chain == null || chain.isEmpty() ? null
                    : chain.stream().map(c -> c.methodSign).collect(Collectors.toList()));
            this.managerMethod = managerMethod;
            this.serviceApi = serviceApi;
            this.fwAc = fwAc;
            this.fwAcStr = fwAc.stream().map(ac -> "" + ac)
                    .collect(Collectors.toCollection(HashSet::new));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ManagerChain that = (ManagerChain) o;
            return Objects.equals(chain, that.chain);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chain);
        }

        public String[] toStringArr() {
            String chainStr = (chain == null || chain.isEmpty()) ? "" : chain.toString();
            String acStr = (fwAc == null || fwAc.isEmpty()) ? "" : fwAc.toString();
            return new String[] {chainStr, serviceApi, acStr};
        }
    }
}
