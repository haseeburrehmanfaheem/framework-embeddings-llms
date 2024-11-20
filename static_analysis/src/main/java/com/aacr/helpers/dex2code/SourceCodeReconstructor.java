package com.aacr.helpers.dex2code;

import com.aacr.helpers.utils.IClassUtils;
import com.aacr.helpers.utils.PrintUtils;
import jadx.api.DecompilationMode;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.plugins.JadxPlugin;
import jadx.plugins.input.dex.DexInputPlugin;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

//TODO: Create A method which can seperate the required method from the class

//TODO: Create A method which uses static analysis to generate a callgraph of all the invokes

// TODO: Create A method which can seperate lines which are access control in the source code, Conditional and Invokes, use Simple mode for easy removal of conditionals

// TODO: Prediction Pipeline for the source code using CodeBert.

// TODO: Try removing Log, lock, tag statements from predictions in the ML pipeline [x]

public class SourceCodeReconstructor {
//    public static String dexDirectory = "Input/custom/Roms/Amazon/framework";
    public static String dexDirectory = "Input/aosp/google10/1/image/framework";

    public static ArrayList<String> getUniqueClasses(String fileName) {
        ArrayList<String> uniqueClasses = new ArrayList<>();
        Set<String> seenClasses = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim() == ""){
                    continue;
                }
                if (line.contains("\\|-\\|")) {

                    String[] _temp_parts = line.split(":");
                    String[] parts = _temp_parts[0].split("\\|-\\|");
                    if (parts.length == 2) {
                        String className = parts[1].trim();

                        if(IClassUtils.isClassExclusionSuper(className)){
                            continue;
                        }

                        String[] parts2 = className.split("/");
                        String fName = parts2[parts2.length - 1];
                        if (fName.contains("$")) {
                            String[] temp = fName.split("\\$");
                            fName = temp[0];
                        }
                        if (!seenClasses.contains(fName)) {
                            PrintUtils.print(className);
                            PrintUtils.print(fName);
                            seenClasses.add(fName);
                            uniqueClasses.add(fName.toLowerCase());
                        }
                    }
                }
                else{
                    String[] parts = line.split(" ");
                    if (parts.length == 2) {

                        String className = parts[0].trim();

                        if(IClassUtils.isClassExclusionSuper(className)){
                            continue;
                        }

                        String[] parts2 = className.split("/");
                        String fName = parts2[parts2.length - 1];
                        if (fName.contains("$")) {
                            String[] temp = fName.split("\\$");
                            fName = temp[0];
                        }
                        if (!seenClasses.contains(fName)) {
                            PrintUtils.print(className);
                            PrintUtils.print(fName);
                            seenClasses.add(fName);
                            uniqueClasses.add(fName.toLowerCase());
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return uniqueClasses;
    }

    public static ArrayList<String> getUniqueClasses2(String fileName) {

        ArrayList<String> uniqueClasses = new ArrayList<>();
        Set<String> seenClasses = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim() == "") {
                    continue;
                }
                if (line.contains("|-|")) {


                    String[] parts = line.split("\\|-\\|");
                    if (parts.length == 2) {
                        String className = parts[1].trim();

                        if (IClassUtils.isClassExclusionSuper(className)) {
                            continue;
                        }

                        String[] parts2 = className.split("/");
                        String fName = parts2[parts2.length - 1];
                        if (fName.contains("$")) {
                            String[] temp = fName.split("\\$");
                            fName = temp[0];
                        }
                        if (!seenClasses.contains(fName)) {
                            PrintUtils.print(className);
                            PrintUtils.print(fName);
                            seenClasses.add(fName);
                            uniqueClasses.add(fName.toLowerCase());
                        }
                    }
                }


            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return uniqueClasses;
    }


    public static void reconstructSourceCode(String methodName){

        ArrayList<String> classes = getUniqueClasses2("Logs/serviceClassLookup.txt");

        JadxArgs args = new JadxArgs();
        args.setDecompilationMode(DecompilationMode.RESTRUCTURE);
        args.setOutDir(new File("jadx"));
        args.setRenameCaseSensitive(true);
        args.setReplaceConsts(true);
        args.setUseImports(true);
        args.setInlineMethods(true);
        args.setUseKotlinMethodsForVarNames(JadxArgs.UseKotlinMethodsForVarNames.APPLY);

        ArrayList<File> files = listFilesWithExtension(new File(dexDirectory), ".dex");

        for(File f: files){
//            PrintUtils.print(f.getName());
            args.getInputFiles().add(new File(f.getAbsolutePath()));
        }


        JadxDecompiler decompiler = new JadxDecompiler(args);
        JadxPlugin dexPlugin = new DexInputPlugin();
        decompiler.registerPlugin(dexPlugin);



        decompiler.load();

        Set<String> alreadySeen = new HashSet<>();

        for(JavaClass c: decompiler.getClasses()){
            if(alreadySeen.contains(c.getName()) || !classes.contains(c.getName().toLowerCase())){
                continue;
            }
            alreadySeen.add(c.getName());
//            PrintUtils.print("CLASS:\n" + c.getCode() + "\n\n\n");
            createFileWithBody("PythonAnalysis/aosp_classes/" + c.getName() + ".java", c.getCode());
//            for(JavaMethod m: c.getMethods()){
//                if(m.getFullName().toLowerCase().contains("onBootPhase".toLowerCase())){
//                    PrintUtils.print(">>> onBootPhase");
//                    InsnDecoder decoder = new InsnDecoder(m.getMethodNode());
//                    ICodeReader codeReader = m.getMethodNode().getCodeReader();
//                    int offset = codeReader.getCodeOffset();
//                    InsnNode[] ins = decoder.process(codeReader);
//
//                    for(InsnNode i: ins){
//                        if(i != null) {
//                            PrintUtils.print(i.toString());
//                        }
//                    }
//                    PrintUtils.print(">>> END");
//                }
//            }
        }

//        decompiler.save();
//        decompiler.registerPlugin();
    }

    public static void createFileWithBody(String fileName, String body) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write(body);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<File> listFilesWithExtension(File directory, String extension) {
        ArrayList<File> files = new ArrayList<>();
//        PrintUtils.print(Boolean.toString(directory.isDirectory()));
        if (directory.isDirectory()) {
            File[] fileList = directory.listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    if (file.isDirectory()) {
                        files.addAll(listFilesWithExtension(file, extension));
                    } else if (file.getName().endsWith(extension)) {
                        files.add(file);
//                        break;
                    }
                }
            }
        }

        return files;
    }


}
