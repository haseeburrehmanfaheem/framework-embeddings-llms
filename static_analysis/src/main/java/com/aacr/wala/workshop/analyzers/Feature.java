package com.aacr.wala.workshop.analyzers;

import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.TypeReference;
import com.aacr.helpers.detectors.framework.FrameworkAccessControlDetector;
import com.aacr.helpers.utils.IClassUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class Feature {
    private final static String[] sinkMethods =
            {"start", "save", "send", "dump", "set", "dial", "broadcast", "bind", "transact",
                    "write", "update", "perform", "notify", "insert", "enqueue", "replace",
                    "show", "dispatch", "print", "println", "create", "adjust", "revoke", "insert", "delete", "update",
                    "query", "openFile", "cache", "getExternal", "file", "touch", "request", "execute", "sensor", "onKey",
            };

    private final static String[] writerMethods =
            {"writeArray", "writeBooleanArray", "writeBundle", "writeByte", "writeByteArray", "writeCharArray",
                    "writeCharSequence", "writeDouble", "writeDoubleArray", "writeFloat", "writeFloatArray",
                    "writeInt", "writeIntArray", "writeList", "writeLong", "writeLongArray", "writeMap",
                    "writeParcelable", "writeParcelableArray", "writeString", "writeStringArray",
                    "writeStringList", "writeStrongBinder", "writeStrongInterface", "writeTypedArray",
                    "writeTypedList", "writeObject", "add", "putExtra", "putExtras", "add", "post", "put"};

    public static HashMap<String, String> getFeaturesFromInvoke(SSAAbstractInvokeInstruction i){
        DefaultEntrypoint entryPoint = DataCollection.getEntryFromMethodReference(i.getDeclaredTarget());
        DefaultEntrypoint ep = getConcreteEP(entryPoint);
        HashMap<String, String> features = new HashMap<>();

        return getFeatures(ep);
    }

    public static HashMap<String, String> getFeatures(DefaultEntrypoint entryPoint){

        HashMap<String, String> features = new HashMap<>();

        DefaultEntrypoint ep = getConcreteEP(entryPoint);


        /*
        Method Name
        Class Name
        Class base Name
        Method Name Contains Get
        Method Name Contains Set
        Method Name Contains On
        Method Name Contains Run
        Method Modifier
        case ABSTRACT : return (sClass.isAbstract()? Type.TRUE : Type.FALSE);
				case FINAL : return (Modifier.isFinal(sClass.getModifiers())? Type.TRUE : Type.FALSE);
				case PRIVATE : return (sClass.isPrivate()? Type.TRUE : Type.FALSE);
				case PROTECTED : return (sClass.isProtected()? Type.TRUE : Type.FALSE);
				case PUBLIC : return (sClass.isPublic()? Type.TRUE : Type.FALSE);
				case STATIC : return (Modifier.isStatic(sClass.getModifiers())? Type.TRUE : Type.FALSE);
			}


        Method is Thread
        Method has Parameters
        Method Parameter Number

        Method Returns Constant
        Method Returns Void

        Method Returns Interface
        Method Return Type

        Method has an Interface parameter

        Method has Sink KeyWord
        Method has writer KeyWord


        */

        if(ep == null){
            return null;
        }

        String methodName = ep.getMethod().getName().toString();
        String className = ep.getMethod().getDeclaringClass().getName().toString();

        String[] classSplit = ep.getMethod().getDeclaringClass().getName().toString().split("/");
        String classNameBase = "Nan";
        try{
            classNameBase = classSplit[classSplit.length-2];
        }catch (Exception e){

        }


        boolean containsGet = methodName.toLowerCase().contains("get");
        boolean containsSet = methodName.toLowerCase().contains("set");
        boolean containsRun = methodName.toLowerCase().contains("run");

        boolean startsWithGet = methodName.toLowerCase().startsWith("get");
        boolean startsWithSet = methodName.toLowerCase().startsWith("set");
        boolean startsWithOn = methodName.toLowerCase().startsWith("on");
        boolean startWithRun = methodName.toLowerCase().startsWith("run");

        boolean isStatic = ep.getMethod().isStatic();
        boolean isPublic = ep.getMethod().isPublic();
        boolean isFinal = ep.getMethod().isFinal();
        boolean isSynchro = ep.getMethod().isSynchronized();
        boolean isProtected = ep.getMethod().isProtected();
        boolean isAbstract = ep.getMethod().isAbstract();

        boolean isThreadRun = className.toLowerCase().contains("runnable");
        boolean hasExceptionHandler = ep.getMethod().hasExceptionHandler();

        int numParams = 0;
        if (isStatic){
            numParams = ep.getMethod().getNumberOfParameters();
        }else{
            numParams = ep.getMethod().getNumberOfParameters() - 1;
        }

        boolean hasParams = numParams > 0;

        boolean returnsPrimitive = ep.getMethod().getReturnType().isPrimitiveType();
        boolean returnsVoid = ep.getMethod().getReturnType().equals(TypeReference.Void);

        boolean returnsInterface = ep.getMethod().getReturnType().getClass().isInterface();

        String returnType = ep.getMethod().getReturnType().getName().toString();

        boolean parameterHasInterface = checkParameterHasInterface(ep);

        boolean methodHasSinkKeyWord = checkMethodHasSinkKeyWord(methodName);
        boolean methodHasWriterKeyWord = checkMethodHasWriterKeyWord(methodName);

        features.put("methodName", methodName);
        features.put("className", className);
        features.put("classNameBase", classNameBase);
        features.put("containsGet", Boolean.toString(containsGet));
        features.put("containsSet", Boolean.toString(containsSet));
        features.put("containsRun", Boolean.toString(containsRun));
        features.put("startsWithGet", Boolean.toString(startsWithGet));
        features.put("startsWithSet", Boolean.toString(startsWithSet));
        features.put("startsWithOn", Boolean.toString(startsWithOn));
        features.put("startWithRun", Boolean.toString(startWithRun));
        features.put("isStatic", Boolean.toString(isStatic));
        features.put("isPublic", Boolean.toString(isPublic));
        features.put("isFinal", Boolean.toString(isFinal));
        features.put("isSynchro", Boolean.toString(isSynchro));
        features.put("isProtected", Boolean.toString(isProtected));
        features.put("isAbstract", Boolean.toString(isAbstract));
        features.put("isThreadRun", Boolean.toString(isThreadRun));
        features.put("hasExceptionHandler", Boolean.toString(hasExceptionHandler));
        features.put("hasParams", Boolean.toString(hasParams));
        features.put("returnsPrimitive", Boolean.toString(returnsPrimitive));
        features.put("returnsVoid", Boolean.toString(returnsVoid));
        features.put("returnsInterface", Boolean.toString(returnsInterface));
        features.put("parameterHasInterface", Boolean.toString(parameterHasInterface));
        features.put("methodHasSinkKeyWord", Boolean.toString(methodHasSinkKeyWord));
        features.put("methodHasWriterKeyWord", Boolean.toString(methodHasWriterKeyWord));
        features.put("returnType", DataCollection.InstructionResolver.expandType(returnType));
        features.put("numParams", Integer.toString(numParams));

        return features;
    }



    public static HashSet<String> getSinks(String filePath){

        HashSet<String> lines = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();  // Remove trailing whitespace
                lines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;

    }

    public static boolean isSink(String target){

        for(String s: IClassUtils.exclusionClasses){
            if(target.toLowerCase().contains(s)){
                return false;
            }
        }
        return true;
    }

    public static boolean isSinkOld(String target){

        for(String s0: DataCollection.knownSinks){
            if(target.toLowerCase().equals(s0.toLowerCase())){
                return true;
            }
        }

        for(String s1: sinkMethods){
            if(target.toLowerCase().contains(s1.toLowerCase())){
                return true;
            }
        }

        for(String s2: writerMethods){
            if(target.toLowerCase().contains(s2.toLowerCase())){
                return true;
            }
        }

        return false;

    }

    private static boolean checkMethodHasSinkKeyWord(String methodName) {
        for(String m: sinkMethods){
            if(methodName.toLowerCase().contains(m.toLowerCase())){
                return true;
            }
        }
        return false;
    }

    private static boolean checkMethodHasWriterKeyWord(String methodName) {
        for(String m: writerMethods){
            if(methodName.toLowerCase().contains(m.toLowerCase())){
                return true;
            }
        }
        return false;
    }

    private static boolean checkParameterHasInterface(DefaultEntrypoint ep) {
        int numParams = ep.getMethod().getNumberOfParameters();
        int start = 0;
        if(!ep.getMethod().isStatic()){
            start = 1;
        }
        for(int i = start; start < numParams; start++){
            try{
                if(ep.getMethod().getParameterType(i).getClass().isInterface()){
                    return true;
                }
            }catch (Exception e){
                // pass
            }
        }
        return false;
    }


    public static DefaultEntrypoint getConcreteEP(DefaultEntrypoint ep){

        try {
            if (ep.getMethod().getDeclaringClass().isInterface()){
                DefaultEntrypoint concreteEp = FrameworkAccessControlDetector.getConcreteEp(ep, DataCollection.cha);
                ep = concreteEp;
            }
        }catch (Exception e){
            return ep;
        }

        return ep;
    }

    public static Collection<SSAInstruction> getForwardSlice(SSAInstruction s){
        return null;
    }

    public static Collection<SSAInstruction> getBackwardSlice(SSAInstruction s){
        return null;
    }
}
