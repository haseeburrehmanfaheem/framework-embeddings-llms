package com.aacr.helpers.detectors.app.cha;

import com.ibm.wala.classLoader.BytecodeClass;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.dex.instructions.Invoke;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.*;
import com.ibm.wala.util.debug.UnimplementedError;
import com.aacr.helpers.detectors.app.AppClassUtils;
import com.aacr.helpers.detectors.app.AppEntrypoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ChaUtils {

    public static HashSet<MethodReference> getInitMethods(TypeReference clazzType, ClassHierarchy cha) {
        HashSet<MethodReference> initMethods = new HashSet<>();
        IClass clazz = null;
        try {
            clazz = cha.lookupClass(clazzType);
        } catch (Exception e) {
            //ignore
        }
        if (clazz != null) {
            for (IMethod method : clazz.getDeclaredMethods()) {
                if (method.isInit())
                    initMethods.add(method.getReference());
            }
        }

        return initMethods;
    }

    public static HashSet<String> getInitMethods(IClass clazz) {
        HashSet<String> initMethods = new HashSet<>();
        for (IMethod method : clazz.getDeclaredMethods()) {
            if (method.isInit())
                initMethods.add(method.getName().toString());
        }

        return initMethods;
    }

    public static String getClassNameFromSignature(String methodSignature) {
        String fullMethodName = methodSignature.substring(0, methodSignature.indexOf('('));
        return "L"+fullMethodName.substring(0, fullMethodName.lastIndexOf('.')).replace('.','/');
    }

    public static ArrayList<IClass> initParentClasses(BiFunction<String, String, Boolean> classPackageNameFilter,
                                                      ClassHierarchy cha) {
        ArrayList<IClass> parents = new ArrayList<>();
        for (IClass c : cha) {
            TypeName curClass = c.getName();
            TypeName parentClass = null;
            try {
                parentClass = ((BytecodeClass<?>)c).getSuperName();
            } catch (Exception e) {
                //ignore
            }
            if (curClass == null || curClass.getPackage() == null)
                continue;
            if (classPackageNameFilter.apply(curClass.getPackage().toString(),
                    curClass.getClassName().toString())) {
                parents.add(c);
                continue;
            }
            if (parentClass != null
                    && parentClass.getPackage() != null
                    && parentClass.getClassName() != null
                    && classPackageNameFilter.apply(parentClass.getPackage().toString(),
                    parentClass.getClassName().toString()))
                parents.add(c);
        }

        return parents;
    }

    public static Collection<AppEntrypoint> getEpsFilterByInvocation(IClass clazz, ClassHierarchy cha,
                                                                     Function<MethodReference, Boolean> invocationFilter) {
        HashSet<AppEntrypoint> eps = new HashSet<>();
        HashSet<String> added = new HashSet<>();
        for (IMethod m : clazz.getDeclaredMethods()) {
            try {
                for (Object ins : ((IBytecodeMethod<?>)m).getInstructions()) {
                    if (ins instanceof Invoke) {
                        try {
                            MethodReference inv = getMethodRef((Invoke) ins, cha);
                            if (inv != null && invocationFilter.apply(inv)
                                    && !added.add(inv.getSignature()))
                                eps.add(new AppEntrypoint(inv, cha, clazz.getReference()));
                        } catch (Exception | UnimplementedError e) {
                            //ignore
                        }
                    }
                }
            } catch (Exception e) {
                //ignore
            }
        }

        return eps;
    }

    private static MethodReference getMethodRef(Invoke inv, ClassHierarchy cha) {
        try {
            TypeReference type = TypeReference.find(ClassLoaderReference.Application, inv.clazzName);
            IClass clazz = cha.lookupClass(type);
            return clazz.getMethod(Selector.make(inv.methodName+inv.descriptor)).getReference();
        } catch (Exception e) {
            //ignore
        }

        return null;
    }


    public static Collection<AppEntrypoint> getEpsFilterByMethodName(IClass clazz, ClassHierarchy cha,
                                                                     Function<String, Boolean> methodNameFilter) {
        HashSet<AppEntrypoint> eps = new HashSet<>();
        HashSet<String> added = new HashSet<>();
        for (IMethod m : clazz.getAllMethods()) {
            try {
                if (methodNameFilter.apply(m.getName().toString())
                        && !AppClassUtils.shouldNotAnalyze(m.getDeclaringClass(), cha.getScope())
                        && added.add(m.getSelector().toString()))
                    eps.add(new AppEntrypoint(m, cha, clazz.getReference()));
            } catch (Exception e) {
                //ignore
            }
        }

        return eps;
    }
}
