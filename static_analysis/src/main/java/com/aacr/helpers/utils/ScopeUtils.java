package com.aacr.helpers.utils;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.dalvik.classLoader.DexFileModule;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.aacr.wala.workshop.utils.PropertyUtils;
import com.aacr.wala.workshop.utils.ValidationUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarFile;

public class ScopeUtils {

    public static AnalysisScope makeScope() throws IOException, ClassHierarchyException {

        ValidationUtils.validateDexParent();
        String dexPath = PropertyUtils.getPath();
        File dexDir = new File(dexPath);

        AnalysisScope scope = initScope();
        addDexFilesToScope(scope, dexDir);
        return scope;
    }

    public static AnalysisScope makeManagerScope(String parentDir) throws IOException, ClassHierarchyException {
        File dexDir = new File(parentDir);

        AnalysisScope scope = initScope();
        addFilesToScope(scope, dexDir, ".dex", (fName) -> !fName.toLowerCase().contains("service"));
        return scope;
    }

    public static AnalysisScope initScope() throws IOException {

        String rootDir = System.getProperty("user.dir");
        String android_path = String.format("%s/lib/android.java.jar", rootDir);
        String android_nonjava_path = String.format("%s/lib/android.nonjava.jar", rootDir);
        String exclusion_path = String.format("%s/dat/AndroidRegressionExclusions.txt", rootDir);

//        PrintUtils.print(rootDir + " " + android_nonjava_path + " " + android_path + " " + exclusion_path);

        AnalysisScope scope = AnalysisScopeReader.instance.readJavaScope("primordial.txt", new File(exclusion_path), AnalysisScopeReader.class.getClassLoader());
        scope.addInputStreamForJarToScope(ClassLoaderReference.Primordial, new FileInputStream(android_path));
        scope.setLoaderImpl(ClassLoaderReference.Application, "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");
        scope.addInputStreamForJarToScope(ClassLoaderReference.Application, new FileInputStream(android_nonjava_path));

        return scope;
    }

    public static void addDexFilesToScope(AnalysisScope scope, File dexDir) throws ClassHierarchyException, IOException {
        addFilesToScope(scope, dexDir, ".dex");
    }

    public static void addJarFilesToScope(AnalysisScope scope, File dexDir) throws ClassHierarchyException, IOException {
        addFilesToScope(scope, dexDir, ".jar");
    }

    public static void addFilesToScope(AnalysisScope scope, File dexDir, String extension)
            throws ClassHierarchyException, IOException {
        addFilesToScope(scope, dexDir, extension, (name) -> true);
    }

    // Todo: Remove isFramework and replace it with an exclusion file for testing
    public static void addFilesToScope(AnalysisScope scope, File dexDir, String extension, Function<String, Boolean> nameFilter)
            throws ClassHierarchyException, IOException {

        String rootDir = System.getProperty("user.dir");
        String android_path = String.format("%s/lib/android.java.jar", rootDir);
        String exclusion_path = String.format("%s/dat/AndroidRegressionExclusions.txt", rootDir);

        ClassHierarchy cha = ClassHierarchyFactory.make(scope);

        List<Path> dexPaths = FileUtils.retrievePathsMatchingExtFromParent(dexDir.getAbsolutePath(), extension);
        for(Path p : dexPaths) {
            if(nameFilter.apply(p.getFileName().toString())) {
                try {


                AnalysisScope appScope = AnalysisScopeReader.instance.readJavaScope("primordial.txt", new File(exclusion_path), AnalysisScopeReader.class.getClassLoader());


                Module module;
                if (extension.equals(".dex")) {
                    module = DexFileModule.make(new File(p.toString()));
                } else {
                    module = new JarFileModule(new JarFile(new File(p.toString())));
                }

                appScope.addInputStreamForJarToScope(ClassLoaderReference.Primordial, new FileInputStream(android_path));
                appScope.setLoaderImpl(ClassLoaderReference.Application, "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");
                appScope.addToScope(ClassLoaderReference.Application, module);
                ClassHierarchy appCha = ClassHierarchyFactory.make(appScope);

                IClassLoader appLoader = appCha.getFactory().getLoader(ClassLoaderReference.Application, appCha, appScope);
                Set<IClass> toRemove = new HashSet<>();
                Iterator<IClass> appIter = appLoader.iterateAllClasses();
                while (appIter.hasNext()) {
                    IClass k = appIter.next();
                    toRemove.add(k);
                }

                IClassLoader oriLoader = cha.getFactory().getLoader(ClassLoaderReference.Application, cha, scope);
                oriLoader.removeAll(toRemove);

                scope.setLoaderImpl(ClassLoaderReference.Application, "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");
                scope.addToScope(ClassLoaderReference.Application, module);
                ArrayList<Module> list = new ArrayList<>();
                list.add(module);
                oriLoader.init(list);
                cha = ClassHierarchyFactory.make(scope, cha.getFactory());
            }catch (Exception e){
                    // pass
                }
            }
        }
    }
}
