package com.aacr.helpers.utils;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;

import java.util.ArrayList;
import java.util.function.Function;

/**
 * This class contains utility methods for getting information from a ClassHierarchy.
 */
public class ClassHierarchyUtils {

    /**
     * Takes in functions that can help filter out noise in the class hierarchy. Finds and returns entrypoints
     * leading to points of interest defined by the filter functions.
     * @param cha ClassHierarchy
     * @param classFunction Function used to specify what kinds of classes should be examined.
     * @param methodFunction Function used to specify what kinds of methods should be examined.
     * @param instructionFunction Function used to specify what kinds of instructions should be examined.
     * @return List of entrypoints leading to points of interest defined by the filter functions.
     */
    public static ArrayList<DefaultEntrypoint> generateCallGraphEntrypoints(ClassHierarchy cha, Function<IClass, Boolean> classFunction,
        Function<IMethod, Boolean> methodFunction, Function<Object, Boolean> instructionFunction) {

        return genericGenerateCallGraphEntrypoints(cha,
            (c) -> classFunction.apply(c),
            (m) -> methodFunction.apply(m),
            (i) -> instructionFunction.apply(i));
    }

    /**
     * Goes through class hierarchy. Does not examine classes, functions, and methods that do not meet
     * the function requirements. Upon encountering an instruction of interest, the enclosing method is
     * added as an entrypoint.
     * @param cha ClassHierarchy
     * @param classFunction Function used to specify what kinds of classes should be examined.
     * @param methodFunction Function used to specify what kinds of methods should be examined.
     * @param instructionFunction Function used to specify what kinds of instructions should be examined.
     * @return List of entrypoints.
     */
    public static ArrayList<DefaultEntrypoint> genericGenerateCallGraphEntrypoints(ClassHierarchy cha, Function<IClass, Boolean> classFunction,
                                                                            Function<IMethod, Boolean> methodFunction, Function<Object, Boolean> instructionFunction) {
        ArrayList<DefaultEntrypoint> filteredEntrypoints = new ArrayList<>();
        for (IClass c : cha) {
            if (classFunction != null && !classFunction.apply(c)) continue;
            for (IMethod m : c.getAllMethods()) {
                if (methodFunction != null && !methodFunction.apply(m)) continue;
                if (instructionFunction == null) {
                    filteredEntrypoints.add(new DefaultEntrypoint(m, cha));
                    continue;
                }
                try {
                    IBytecodeMethod bm = (IBytecodeMethod) m;
                    if (bm != null && bm.getInstructions() != null) {
                        for (Object i : bm.getInstructions()) {
                            if (i != null && instructionFunction.apply(i)) {
                                filteredEntrypoints.add(new DefaultEntrypoint(m, cha));
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    continue;
                }
            }
        }
        return filteredEntrypoints;
    }
}
