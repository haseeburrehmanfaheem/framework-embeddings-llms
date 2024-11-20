package com.aacr.helpers.utils;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.aacr.helpers.accesscontrol.framework.AccessControlUtils;

import java.util.Arrays;
import java.util.HashSet;

public class IClassUtils {

    public static final HashSet<String> exclusionClasses = new HashSet<>(Arrays.asList(
            "Object",
            "IBinder",
            "binder",
            "IInterface"
    ));

    public static final HashSet<String> exclusionFromPath = new HashSet<>(Arrays.asList(
            "stringbuilder",
            "util/slog",
            "wakelock",
            "log",
            "hasNext",
            "<init>",
            "<clinit>"
    ));


    public static final HashSet<String> exclusionClassesSuper = new HashSet<>(Arrays.asList(
            "Ljava/lang",
            "Ljava/util",
            "Landroid/util",
            "Landroid/os",
            "Lcom/google",
            "Ljava/lang/Object",
            "Landroid/os/IBinder",
            "handler",
            "Ljava/io/File",
            "stringbuilder",
            "util/slog",
            "Landroid/content",
            "Landroid/os/Binder",
            "Landroid/os/IInterface",
            "com/sun",
            "sun/swing",
            "org/apache/xerces",
            "org/netbeans/",
            "Ljava/lang/Thread",
            "Ljava/util/Iterator"
    ));

    public static final HashSet<String> exclusionMethods = new HashSet<>(Arrays.asList(
            "<init>",
            "<clinit>",
            "handleMessage",
            "append",
            "recycle"
    ));

    public static boolean isClassExclusionSuper(String c){
        for(String s: exclusionClassesSuper){
            if(c.toLowerCase().contains(s.toLowerCase())){
                return true;
            }
        }
        return false;
    }

    public static boolean isInstructionExclusionFromPath(SSAInstruction s){
        String c = "";
        if (s instanceof SSAAbstractInvokeInstruction){
            c = ((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getDeclaringClass().getName().toString();
            for(String ss: exclusionFromPath){
                if(c.toLowerCase().contains(ss.toLowerCase())){
                    return true;
                }
            }
            c = ((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getName().toString();
            for(String ss: exclusionFromPath){
                if(c.toLowerCase().contains(ss.toLowerCase())){
                    return true;
                }
            }
        }else if (s instanceof SSAGetInstruction){
            // if it is not static I can just get it from the invokes using def use analysis
//            if(((SSAGetInstruction) s).getNumberOfDefs() > 0){
//                return true;
//            }

            c = ((SSAFieldAccessInstruction) s).getDeclaredField().getFieldType().getName().toString();
            for(String ss: exclusionFromPath){
                if(c.toLowerCase().contains(ss.toLowerCase())){
                    return true;
                }
            }
        }else if(s instanceof SSAGotoInstruction || s instanceof SSAMonitorInstruction || s instanceof SSAPhiInstruction){
            return true;
        }else if(s instanceof SSAReturnInstruction){
            if(((SSAReturnInstruction) s).returnsVoid()){
                return true;
            }
        }else if(s instanceof SSANewInstruction){
            c = ((SSANewInstruction) s).getConcreteType().getName().toString();
            for(String ss: exclusionFromPath){
                if(c.toLowerCase().contains(ss.toLowerCase())){
                    return true;
                }
            }
        }else{
            c = s.toString();
            for(String ss: exclusionFromPath){
                if(c.toLowerCase().contains(ss.toLowerCase())){
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean excludeFromFurtherAnalysis(SSAAbstractInvokeInstruction m){
        return isExclusionMethod(m);
    }

    public static boolean isExclusionMethod(SSAAbstractInvokeInstruction m){
        for(String s: exclusionMethods){
            if(m.getDeclaredTarget().getName().toString().toLowerCase().contains(s.toLowerCase())){
                return true;
            }
        }

        if(isClassExclusionSuper(m.getDeclaredTarget().getDeclaringClass().getName().toString())){
            return true;
        }

        return AccessControlUtils.isCheckingMethod(m) || AccessControlUtils.isGettingMethod(m) || m.getDeclaredTarget().getSignature().toLowerCase().contains("clearCallingIdentity".toLowerCase()) || m.getDeclaredTarget().getSignature().toLowerCase().contains("restoreCallingIdentity".toLowerCase());
    }


//    //Todo: Remove this and change implementation to standardize the string format
//    public static String toDotName(IClass c) {
//        String ans = "";
//        try {
//            ans = c.getName().getPackage().toString().replace('/', '.')
//                    + "."
//                    + c.getName().getClassName().toString().replace('/', '.');//.replace('$', '.');
//        } catch (NullPointerException e) {
//            //ignore
//        }
//
//        return ans;
//    }
//
//    public static IClass getClassFromDotName(String dotName, ClassHierarchy cha) {
//        TypeReference type = TypeReference.find(ClassLoaderReference.Application,
//                "L"+dotName.replace('.', '/'));
//        if (type != null)
//            return cha.lookupClass(type);
//
//        return null;
//    }

    public static IClass getClass(ClassHierarchy cha, String clazzStr) {
        TypeReference type = TypeReference.find(ClassLoaderReference.Application, clazzStr);
        if (type != null)
            return cha.lookupClass(type);

        return null;
    }

}
