package com.aacr.helpers.detectors.framework;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
import com.aacr.helpers.utils.CallGraphUtils;
import com.aacr.helpers.utils.ClassHierarchyUtils;
import com.aacr.helpers.utils.IClassUtils;
import com.aacr.helpers.utils.InstructionUtils;
import com.aacr.wala.workshop.analyzers.DataCollection;
//import com.aacr.wala.workshop.analyzers.AACR;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.function.Function;

/**
 * Detects system services
 */
public class SystemServicesDetector {

    /**
     * Specifies functions to filter out classes, methods, and instructions not associated with system service registration.
     * Uses those functions to detect entrypoints that reach invocations to system service registration methods.
     * @param cha ClassHierarchy.
     * @return ArrayList of DefaultEntrypoints that reach invocations to system service registration methods.
     */
    public static ArrayList<DefaultEntrypoint> getFilteredCallGraphEntrypoints(ClassHierarchy cha) {
        Function<IClass, Boolean> classFunction = s -> {
            String sString = s.toString();
            boolean returnBool = !sString.contains("Primordial") && !sString.contains("apache") && !sString.contains("junit") && !sString.contains("ServiceManager") && !sString.contains("Lcom/android/server/SystemService");
            sString = null;
            return returnBool;
        };
        Function<IMethod, Boolean> methodFunction = s -> true;
        Function<Object, Boolean> instructionFunction = s -> {
            String sString = s.toString();
            boolean returnBool = sString.contains("Landroid/os/ServiceManager addService") || sString.contains("publishBinderService");
            sString = null;
            return returnBool;
        };
        return ClassHierarchyUtils.generateCallGraphEntrypoints(cha, classFunction, methodFunction, instructionFunction);
    }

    public static String getReturnFromNode(DefaultEntrypoint ep, CGNode node){
        SSACFG cfg = node.getIR().getControlFlowGraph();
        SymbolTable st = node.getIR().getSymbolTable();
        String name = "";
        for(SSAInstruction s: cfg.getInstructions()){
            if(s instanceof SSAReturnInstruction){
                int use = ((SSAReturnInstruction) s).getUse(0);
                if(st.isStringConstant(use)){
                    name = st.getStringValue(use);
                }
            }
        }
        return name;
    }

    /**
     * Creates call graph using entrypoints that lead to system service registration invocations. Extracts service information from
     * system service registration points.
     * @param scope AnalysisScope.
     * @param cha ClassHierarchy.
     * @return HashSet of IClass, each IClass representing a system service class.
     */
    public static Pair<HashSet<IClass>, HashMap<IClass, String>> getSystemServices(AnalysisScope scope, ClassHierarchy cha) {
        CallGraph cg = null;
        HashMap<IClass, String> _map = new HashMap<>();
        HashSet<IClass> serviceClasses = new HashSet<>();

        try {
            ArrayList<DefaultEntrypoint> filteredEntrypoints = getFilteredCallGraphEntrypoints(cha);
            cg = CallGraphUtils.buildCallGraph(CallGraphUtils.CGType.V01CFA, scope,
                    cha, filteredEntrypoints, AnalysisOptions.ReflectionOptions.NONE, SSAOptions.getAllBuiltInPiNodes());
        } catch (Exception e ) {
            e.printStackTrace();
            return Pair.make(serviceClasses, new HashMap<>());
        }


        if(cg == null) {
            return Pair.make(serviceClasses, new HashMap<>());
        }

        for (CGNode nd : cg) {
            if (scope.isApplicationLoader(nd.getMethod().getDeclaringClass().getClassLoader()) && (nd.getIR() != null)) {

                for (Iterator<SSAInstruction> it = nd.getIR().iterateAllInstructions(); it.hasNext(); ) {
                    SSAInstruction s = it.next();

                    if (s instanceof com.ibm.wala.ssa.SSAAbstractInvokeInstruction) {
                        if (s.toString().contains("Landroid/os/ServiceManager, addService(") || s.toString().contains("publishBinderService(")) {
                            DefUse du = nd.getDU();
                            SymbolTable st = nd.getIR().getSymbolTable();
                            String _serviceName = "";
                            if (du != null) {
                                int binderObjectIndex = InstructionUtils.getParameterIndex(((SSAAbstractInvokeInstruction) s), 1);
                                int stringParam = InstructionUtils.getParameterIndex(((SSAAbstractInvokeInstruction) s), 0);

                                SSAInstruction secondInstr = du.getDef(s.getUse(binderObjectIndex));

                                if(st.isStringConstant(s.getUse(stringParam))){
                                    // pass
                                    _serviceName = st.getStringValue(s.getUse(stringParam));
                                }else{
                                    SSAInstruction firstInstruction = du.getDef(s.getUse(stringParam));
                                    if(firstInstruction != null && (firstInstruction instanceof SSAAbstractInvokeInstruction)){

                                        DefaultEntrypoint _ep = DataCollection.getEntryFromMethodReference(((SSAAbstractInvokeInstruction) firstInstruction).getDeclaredTarget());
                                        CGNode node = CallGraphUtils.getNodeFromEntryPoint(cg, _ep);
                                        _serviceName = getReturnFromNode(_ep, node);

                                    }
                                }


                                if (secondInstr != null) {
                                    ArrayList<TypeReference> targetTypes = InstructionUtils.getTargetTypeReference(secondInstr, du);
                                    for(TypeReference targetType : targetTypes) {
                                        String serviceClass = targetType.getName().toString();
                                        try {
                                            IClass serviceIClass = cha.lookupClass(TypeReference.find(ClassLoaderReference.Application, serviceClass.replace("$Stub", "")));
                                            if (!IClassUtils.exclusionClasses.contains(serviceIClass.getName().toString()) && serviceIClass.toString().contains("Service")) {
                                                serviceClasses.add(serviceIClass);
                                                _map.put(serviceIClass, _serviceName);
                                            }
                                        } catch (Exception e) {
                                            //e.printStackTrace();
                                        }
                                    }
                                } else if(binderObjectIndex == 1 && !nd.getMethod().isStatic()) {
                                    //in this case, the service class is 'this'
                                    try {
                                        IClass serviceIClass = nd.getMethod().getDeclaringClass();
                                        if (!IClassUtils.exclusionClasses.contains(serviceIClass.getName().toString()) && serviceIClass.toString().contains("Service")) {
                                            serviceClasses.add(serviceIClass);
                                            _map.put(serviceIClass, _serviceName);
                                        }
                                    } catch (Exception e) {
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return Pair.make(serviceClasses, _map);
//        return serviceClasses;
    }

}
