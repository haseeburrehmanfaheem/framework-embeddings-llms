package com.aacr.wala.workshop.analyzers;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.aacr.helpers.utils.CallGraphUtils;
import com.aacr.helpers.utils.ClassHierarchyUtils;
import com.aacr.helpers.utils.IClassUtils;
import com.aacr.helpers.utils.InstructionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.function.Function;

public class EntryPointDetectorFresh {
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


    public static HashSet<IClass> getSystemServices(AnalysisScope scope, ClassHierarchy cha) {
        CallGraph cg = null;
        try {
            ArrayList<DefaultEntrypoint> filteredEntrypoints = getFilteredCallGraphEntrypoints(cha);
            cg = CallGraphUtils.buildCallGraph(CallGraphUtils.CGType.V01CFA, scope,
                    cha, filteredEntrypoints, AnalysisOptions.ReflectionOptions.NONE, SSAOptions.getAllBuiltInPiNodes());
        } catch (Exception e ) {
            e.printStackTrace();
            return new HashSet<>();
        }

        HashSet<IClass> serviceClasses = new HashSet<>();
        if(cg == null) {
            return serviceClasses;
        }

        for (CGNode nd : cg) {
            if (scope.isApplicationLoader(nd.getMethod().getDeclaringClass().getClassLoader()) && (nd.getIR() != null)) {

                for (Iterator<SSAInstruction> it = nd.getIR().iterateAllInstructions(); it.hasNext(); ) {
                    SSAInstruction s = it.next();

                    if (s instanceof com.ibm.wala.ssa.SSAAbstractInvokeInstruction) {
                        if (s.toString().contains("Landroid/os/ServiceManager, addService(") || s.toString().contains("publishBinderService(")) {
                            DefUse du = nd.getDU();
                            if (du != null) {
                                int binderObjectIndex = InstructionUtils.getParameterIndex(((SSAAbstractInvokeInstruction) s), 1);

                                SSAInstruction secondInstr = du.getDef(s.getUse(binderObjectIndex));
                                if (secondInstr != null) {
                                    ArrayList<TypeReference> targetTypes = InstructionUtils.getTargetTypeReference(secondInstr, du);
                                    for(TypeReference targetType : targetTypes) {
                                        String serviceClass = targetType.getName().toString();
                                        try {
                                            IClass serviceIClass = cha.lookupClass(TypeReference.find(ClassLoaderReference.Application, serviceClass.replace("$Stub", "")));
                                            if (!IClassUtils.exclusionClasses.contains(serviceIClass.getName().toString())) {
                                                serviceClasses.add(serviceIClass);
                                            }
                                        } catch (Exception e) {
                                            //e.printStackTrace();
                                        }
                                    }
                                } else if(binderObjectIndex == 1 && !nd.getMethod().isStatic()) {
                                    //in this case, the service class is 'this'
                                    try {
                                        IClass serviceIClass = nd.getMethod().getDeclaringClass();
                                        if (!IClassUtils.exclusionClasses.contains(serviceIClass.getName().toString())) {
                                            serviceClasses.add(serviceIClass);
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
        return serviceClasses;
    }

    public static HashSet<DefaultEntrypoint> getFrameworkEntrypoints(AnalysisScope scope, ClassHierarchy cha, HashSet<IClass> systemServices) {
        HashSet<DefaultEntrypoint> frameworkEntrypoints = new HashSet<>();
        for(IClass serviceClass : systemServices) {
            ArrayList<DefaultEntrypoint> newEntrypoints = listServiceEntrypoints(cha, serviceClass);
            if(newEntrypoints != null) {
                frameworkEntrypoints.addAll(newEntrypoints);
            }
        }
        return frameworkEntrypoints;
    }

    /** Finds a system service class's exposed entrypoints and returns them.
     * @param cha ClassHierarchy.
     * @param c System service class.
     * @return ArrayList of DefaultEntrypoints, each representing an entrypoint to the framework.
     */
    public static ArrayList<DefaultEntrypoint> listServiceEntrypoints(ClassHierarchy cha, IClass c) {
        if(c == null) return null;
        ArrayList<DefaultEntrypoint> frameworkEntrypoints = new ArrayList<>();

        IClass publicInt = getServicePublicInterface(c, cha);
        if (publicInt != null) {
            for (IMethod m : publicInt.getDeclaredMethods())
                frameworkEntrypoints.add(new DefaultEntrypoint(m, cha));
        }

        return frameworkEntrypoints;
    }

    private static IClass getServicePublicInterface(IClass c, ClassHierarchy cha) {
        for (IClass sup : c.getAllImplementedInterfaces()) {
            if (sup.getAllImplementedInterfaces().size() == 1
                    && sup.getAllImplementedInterfaces().contains((cha.lookupClass(TypeReference
                    .find(ClassLoaderReference.Application, "Landroid/os/IInterface")))))
                return sup;
        }

        return null;
    }
}
