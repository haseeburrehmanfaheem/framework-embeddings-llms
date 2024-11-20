package com.aacr.helpers.detectors.app.cha;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.dex.instructions.Invoke;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.MethodReference;
import com.aacr.helpers.detectors.app.AppEntrypoint;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

public class CallbackParentDetector {
    private final ClassHierarchy cha;

    public CallbackParentDetector(ClassHierarchy cha) {
        this.cha = cha;
    }

    public HashSet<IClass> getCallbackParents(IClass callbackClass) {
        HashSet<String> initMethods = ChaUtils.getInitMethods(callbackClass);
        return extractParents(callbackClass, initMethods);
    }

    public HashSet<MethodReference> getCallbackParents(IClass callbackClass, String callbackSignature) {
        HashSet<String> initMethods = ChaUtils.getInitMethods(callbackClass);
        HashSet<IClass> parents = extractParents(callbackClass, initMethods);

        return extractRegistrations(parents, callbackClass, callbackSignature);
    }

    private HashSet<IClass> extractParents(IClass callbackClass, HashSet<String> initMethods) {
        String clazzName = callbackClass.getName().toString();
        HashSet<IClass> parents = new HashSet<>();
        for (IClass c : cha) {
//            if (AppClassUtils.shouldNotAnalyze(c, cha.getScope()))
//                continue;
            for (IMethod m : c.getDeclaredMethods()) {
                try {
                    for (Object ins : ((IBytecodeMethod<?>)m).getInstructions()) {
                        if (ins instanceof Invoke) {
                            Invoke inv = (Invoke) ins;
                            if (inv.clazzName.equals(clazzName) && (initMethods.contains(inv.methodName)))
                                parents.add(c);
                        }
                    }
                } catch (Exception e) {
                    //ignore
                }
            }
        }

        return parents;
    }

    private HashSet<MethodReference> extractRegistrations(HashSet<IClass> parents,
                                                          IClass callbackClass,
                                                          String callbackSignature) {
        HashSet<MethodReference> registrations = new HashSet<>();
        String tmpCallbackName = callbackSignature.substring(0, callbackSignature.indexOf('('));
        final String callbackName = StringUtils.capitalize(
                tmpCallbackName.substring(tmpCallbackName.lastIndexOf('.')+1));

        if (!parents.isEmpty()) {
            for (IClass parent : parents) {
                Collection<AppEntrypoint> eps = ChaUtils.getEpsFilterByInvocation(parent, cha, (inv) -> {
//                    for (int i=0; i<inv.getNumberOfParameters(); i++) {
//                        try {
//                            IClass param = cha.lookupClass(inv.getParameterType(i));
//                            if (callbackClass.equals(param) || cha.isSubclassOf(callbackClass, param))
//                                return true;
//                        } catch (Exception e) {
//                            //ignore
//                        }
//                    }
//                    return false;
                    String invName = inv.getName().toString();
                    if (!invName.startsWith("set") && !invName.startsWith("add"))
                        return false;
                    if (!invName.endsWith("Listener"))
                        return false;
                    return invName.contains(callbackName);
                });
                System.out.println();
            }
        }

        return registrations;
    }

    private Collection<MethodReference> getRegistrationsFromUses(CGNode node, String callbackClazzName,
                                                                 HashSet<SSAInstruction> uses) {
        HashSet<MethodReference> registrations = new HashSet<>();
        DefUse du = node.getDU();

        for (SSAInstruction use : uses) {
            if (use instanceof SSAAbstractInvokeInstruction) {
                MethodReference target = ((SSAAbstractInvokeInstruction) use).getDeclaredTarget();
                if (!target.isInit()
                        || !target.getDeclaringClass().getName().toString().equals(callbackClazzName))
                    registrations.add(target);
            } else if (use instanceof SSAPutInstruction) {
                SSAPutInstruction putIns = (SSAPutInstruction) use;
                HashSet<SSAInstruction> newUses = getAllUses(putIns.getVal(), node);
                registrations.addAll(getRegistrationsFromUses(node, callbackClazzName, newUses));
            } else if (use instanceof SSANewInstruction) {
                SSANewInstruction newUse = (SSANewInstruction) use;
                if (!newUse.getConcreteType().getName().toString().equals(callbackClazzName))
                    registrations.addAll(ChaUtils.getInitMethods(newUse.getConcreteType(), cha));
            }
        }

        return registrations;
    }

    private HashSet<SSAInstruction> getAllUses(int valNum, CGNode node) {
        HashSet<SSAInstruction> uses = new HashSet<>();
        for (Iterator<SSAInstruction> it = node.getDU().getUses(valNum);
             it.hasNext();) {
            SSAInstruction i = it.next();
            uses.add(i);
        }

        return uses;
    }
}
