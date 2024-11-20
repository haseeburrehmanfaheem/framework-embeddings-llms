package com.aacr.helpers.detectors.app.cha;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.aacr.helpers.detectors.app.AppClassUtils;
import com.aacr.helpers.parsers.models.MapperResource;
import com.aacr.helpers.parsers.models.val.ValueResource;

import java.util.ArrayList;
import java.util.HashMap;

public class DialogAnalyzer {

    private final ClassHierarchy cha;
    private final HashMap<Integer, MapperResource> allResIdMap;
    private final HashMap<String, HashMap<String, ValueResource>> valResMap;

    private final ArrayList<IClass> dialogParents;

    public DialogAnalyzer(ClassHierarchy cha, HashMap<Integer, MapperResource> allResIdMap,
                          HashMap<String, HashMap<String, ValueResource>> valResMap) {
        this.cha = cha;
        this.allResIdMap = allResIdMap;
        this.valResMap = valResMap;
        dialogParents = initParentDialogs();
    }

    private ArrayList<IClass> initParentDialogs() {
        return ChaUtils.initParentClasses((packageName, className) ->
                (packageName.startsWith("android/") || packageName.startsWith("androidx/"))
                        && className.equals("AlertDialog$Builder"), cha);
    }

    private boolean isDialog(IClass clazz) {
        for (IClass parent : dialogParents) {
            if (clazz.equals(parent) || cha.isSubclassOf(clazz, parent))
                return true;
        }

        return false;
    }

    public void analyzeDialogs() {
        for (IClass c : cha) {
            if (AppClassUtils.shouldNotAnalyze(c, cha.getScope()))
                continue;
            if (isDialog(c)) {
                analyzeDialog(c);
            }
        }
    }

    private void analyzeDialog(IClass dialogClass) {
//        HashSet<String> initMethods = ChaUtils.getInitMethods(dialogClass);
//        ArrayList<IMethod> allInvokers = getInvokers(dialogClass, initMethods);
//        if (initMethod != null) {
//            CallGraph dialogCg = CallGraphUtils.makeSingleEpCg(dialogClass, initMethod, cha);
//
//        }
    }

//    private ArrayList<IMethod> getInvokers(IClass dialogClass, HashSet<String> initMethods) {
//        for ()
//    }

    public static class DialogContext {
        public final boolean isCancellable;
        public final String title;
        public final String message;
        public final String iconFile;
        public final String viewId;

        public DialogContext(boolean isCancellable, String title, String message, String iconFile, String viewId) {
            this.isCancellable = isCancellable;
            this.title = title;
            this.message = message;
            this.iconFile = iconFile;
            this.viewId = viewId;
        }
    }
}
