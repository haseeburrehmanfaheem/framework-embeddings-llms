package com.aacr.wala.workshop.analyzers;

import com.aacr.helpers.accesscontrol.framework.*;
import com.aacr.helpers.detectors.framework.FrameworkAccessControlDetector;
import com.aacr.helpers.detectors.framework.FrameworkEntrypointDetector;
import com.aacr.helpers.utils.*;
import com.aacr.wala.workshop.utils.PropertyUtils;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.UnimplementedError;
import com.opencsv.CSVWriter;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class DataCollection {

    public static AnalysisScope scope;
    public static ClassHierarchy cha;
    public static AnalysisCacheImpl cache;
    public static HashSet<String> alreadyAnalysed;

    public static HashMap<String, FrameworkAnalyzer.Permission_Level> permissionMap;

    public static ArrayList<DefaultEntrypoint> entryPoints;

    public static HashSet<String> knownSinks;

    public static void launch() throws Exception {

        safeInitialize();
//        initializePermissions("InputNew/Manifests");
//        alreadyAnalysed = getAlreadyAnalysedList("aosp_list.txt");
        alreadyAnalysed = new HashSet<>();
        getEntryPoints();
        writeEPToFile(PropertyUtils.getName() + "_eps.txt", false);
//        return;

        initializeSinks("sinkListSusi");

//        HashSet<IClass> systemServices = EntryPointDetectorFresh.getSystemServices(scope, cha);
//        HashSet<DefaultEntrypoint> eps = EntryPointDetectorFresh.getFrameworkEntrypoints(scope, cha, systemServices);

        /*
        HashSet<DefaultEntrypoint> eps = new HashSet<>(entryPoints);

        int sinkNum = 0;
        HashSet<String> sinksInEPs = new HashSet<>();

        for(DefaultEntrypoint ep: eps){
            if(Feature.isSink(ep.getMethod().getName().toString())){
                sinkNum += 1;
                sinksInEPs.add(ep.getMethod().getName().toString());
            }
        }

        getPathsAndProtectionsForSinksSerial();
        List<DefaultEntrypoint> test = entryPoints.subList(0, 10);

         */

//        HashMap<String, HashSet<String>> allEP = ParallelFrameworkAccessControlDetector.parallelReconstruct(scope, cha, new HashSet<>(entryPoints));

    }

    private static void writeEPToFile(String filePath, boolean append){
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, append))) {
            for (DefaultEntrypoint ep : entryPoints) {
                writer.write(createEPString(ep));
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    We just modify the getAlreadyAnalysedList with AOSP list
    private static HashSet<String> getAlreadyAnalysedList(String filePath) throws FileNotFoundException {
        HashSet<String> linesSet = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                linesSet.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return linesSet;
    }

    private static void getPathsAndProtectionsForSinksSerial() throws Exception {
        HashMap<String, HashSet<String>> allEPPaths = new HashMap<>();

        for(DefaultEntrypoint ep: entryPoints){
            if(ep.getMethod().getName().toString().contains("setEnabled")) { // dism, filterEvent

                long startTime = System.currentTimeMillis();

                HashSet<String> paths = wrapperGetPaths(ep, new ArrayList<>());
                if (paths != null) {
                    if (paths.size() > 0) {
                        allEPPaths.put(createEPString(ep), paths);
                    }
                }
                long endTime = System.currentTimeMillis();
                long elapsedTime = endTime - startTime;

                System.out.println("Time taken: " + elapsedTime + " milliseconds");
            }

        }

    }

    public static HashSet<String> wrapperGetPaths(DefaultEntrypoint ep, ArrayList<String> path) throws Exception {
        HashSet<String> visitedEPs = new HashSet<>();
        int depth = 0;
        HashSet<String> paths = getPathsAndProtectionsForSinks(ep, new LinkedList<>(), visitedEPs, depth);
        return paths;
    }

    public static void appendKeysToFile(HashMap<String, HashSet<String>> hashMap, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            for (String key : hashMap.keySet()) {
                writer.write(key);
                writer.newLine();
            }
        }
    }

    private static HashSet<String> getPathsAndProtectionsForSinks(DefaultEntrypoint ep, LinkedList<String> parentPath, HashSet<String> visitedEPs, int depth) throws Exception {


        if(visitedEPs.contains(createEPString(ep))){
            return null;
        }

        visitedEPs.add(createEPString(ep));

        if(depth > 5){
            return null;
        }

        CallGraph cg = null;
        CGNode node = null;
        InterproceduralCFG icfg = null;
        SSACFG cfg = null;
        ISSABasicBlock entryBB = null;
        DefUse du = null;
        SymbolTable st = null;
        PointerAnalysis pa = null;

        try{
            ArrayList<DefaultEntrypoint> singleEP = new ArrayList<>(Arrays.asList(ep));
            Pair<CallGraph, PointerAnalysis> cg_pa = buildCallGraph(singleEP);
            pa = cg_pa.snd;
            cg = cg_pa.fst;
            cg = patchCallGraphReturnCG(singleEP, cg);
            node =  CallGraphUtils.getNodeFromEntryPoint(cg, ep);
            icfg = buildICFG(cg);
            cfg = node.getIR().getControlFlowGraph();
            entryBB = cfg.entry();
            du = node.getDU();
            st = node.getIR().getSymbolTable();

            if(pa == null || du == null || st == null || entryBB == null || node == null || cg == null || icfg == null || cfg == null){
                return null;
            }
        }catch (Exception e){
            PrintUtils.print("[ERR]: " + e.getMessage());
            return null;
        }

        HashSet<Integer> visited = new HashSet<>();
        LinkedList<String> path = new LinkedList<>(parentPath);
        LinkedList<ISSABasicBlock> stack = new LinkedList<>();
        HashSet<String> allPaths = new HashSet<>();
        HashMap<Integer, Integer> blockInsMap = new HashMap<>();

        /*
        Adding the first element to the stack
         */

        stack.push(entryBB);

        while(!stack.isEmpty()){
            if(allPaths.size() > 30){
                return allPaths;
            }
            ISSABasicBlock cur = stack.pop();
            int insCount = 0;
            if(!visited.contains(cur.getNumber())){
                visited.add(cur.getNumber());

                BasicBlockInContext<ISSABasicBlock> block = new BasicBlockInContext<ISSABasicBlock>(node, cur);

                for(Iterator<SSAInstruction> it = cur.iterator(); it.hasNext();){
                    SSAInstruction i = it.next();

                    String ins = resolveInstruction(i, node);

                    String filePath = "addCrossProfileListener.txt";
                    String contentToAppend = i.toString(st) + " <sep> " + ins + "\n";

                    try {
                        // Use true as the second parameter to FileWriter to enable append mode
                        FileWriter fileWriter = new FileWriter(filePath, true);
                        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

                        // Append the content to the file
                        bufferedWriter.write(contentToAppend);
                        bufferedWriter.newLine();

                        bufferedWriter.close();
                        System.out.println("Content appended successfully.");
                    } catch (IOException e) {
                        System.out.println("An error occurred while trying to append to the file: " + e.getMessage());
                    }


                    if(IClassUtils.isInstructionExclusionFromPath(i)){
                        continue;
                    }


                    if(i instanceof SSAAbstractInvokeInstruction){
                        Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> pe = FrameworkAccessControlDetector.analyzeInvokeInstruction(((SSAAbstractInvokeInstruction) i), du, icfg, block, st);
                        if(pe != null){
                            String peString = handlePeInvoke(ins, pe.snd, (SSAAbstractInvokeInstruction) i);
                            path.add(peString + "{" + ins + "}");
                            insCount += 1;
                        }
                        else{

                            if(!ins.contains("exception") && !ins.contains("[new]")){
                                path.add(ins);
                                insCount += 1;

                                if(Feature.isSinkOld(((SSAAbstractInvokeInstruction) i).getDeclaredTarget().getName().toString())) {
                                    allPaths.add(path.toString());
                                }

                                DefaultEntrypoint invokeEp = getEntryFromMethodReference(((SSAAbstractInvokeInstruction) i).getDeclaredTarget());

                                if (invokeEp != null) {
                                    if (invokeEp.getMethod().getDeclaringClass().isInterface())
                                        invokeEp = FrameworkAccessControlDetector.getConcreteEp(ep, cha);

                                    if (!IClassUtils.excludeFromFurtherAnalysis((SSAAbstractInvokeInstruction) i)) {
                                        if (!visited.contains(createEPString(invokeEp))) {
                                            HashSet<String> recPaths = getPathsAndProtectionsForSinks(invokeEp, new LinkedList<>(path), visitedEPs, depth + 1);
                                            if (recPaths != null) {
                                                allPaths.addAll(recPaths);
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }else if(i instanceof SSAConditionalBranchInstruction){
                        Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> pe = FrameworkAccessControlDetector.analyzeConditionalBranchInstruction(i, icfg, block, st, du);

                        if(pe != null){
                            String acCheck = resolveAC(pe.snd);
                            path.add(acCheck + " { CONDITIONAL BRANCH INSTRUCTION }");
                            insCount += 1;
                        }
                    }else if(i instanceof SSAPutInstruction || i instanceof SSAReturnInstruction){
                        path.add(ins);
                        insCount += 1;
                        allPaths.add(path.toString());
                    }else{
                        path.add(ins);
                        insCount += 1;
                    }
                }

                blockInsMap.put(cur.getNumber(), insCount);

                if(cur.isExitBlock()){
                    allPaths.add(path.toString());
                }else{
                    // add children
                    stack.push(cur);
                    for(ISSABasicBlock next:cfg.getNormalSuccessors(cur)){
                        if(!visited.contains(next.getNumber())){
                            stack.push(next);
                        }
                    }
                }

            }
            else {
                int toRemove = blockInsMap.get(cur.getNumber());
                while(toRemove > 0){
                    path.removeLast();
                    toRemove -= 1;
                }
                visited.remove(cur.getNumber());
            }
        }

//        allPaths.add(new HashSet<>());

        return allPaths;
    }

    private static String handlePeInvoke(String ins, AccessControlEnforcement snd, SSAAbstractInvokeInstruction s) throws Exception {
        String acCheck = resolveAC(snd);

        if(snd instanceof PotentialMethodNameAccessControl){
            int l = getPermissionLevelInt(((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getName().toString());
            String lString = getPermissionLevel(((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getName().toString());

            if(l != -1 && l != 404){
                String t1 = "[AC] Potential " + lString + "(" + Integer.toString(l) + ")";
                return t1;
            }

            ArrayList<String> allChecks = new ArrayList<>();
            int depth = 0;

            getAllChecksInDepth(s, allChecks, depth);

            int resolvedAC = analyseChecks(allChecks);

            if(resolvedAC == -1){
                // not an access check
                return ins;
            }

            String newCheck = "[AC] Potential(" +Integer.toString(resolvedAC) + ")" + " {" + ins + "}";
//
            return newCheck;

        }

        return acCheck;

    }

    private static int analyseChecks(ArrayList<String> allChecks) {
        int cur = -1;

        for(String ins: allChecks){
            int num = extractNumberFromInstruction(ins);
            cur = AND(cur, num);
        }

        return cur;

    }

    public static int AND(int _n1, int _n2){

//        if(_n1 == 0){
//            return _n2;
//        }else if(_n2 == 0){
//            return _n1;
//        }

        if(_n1 > _n2){
            return _n1;
        }
        return _n2;
    }

    public static int extractNumberFromInstruction(String instruction) {
        Pattern pattern = Pattern.compile("\\((\\d+)\\)");
        Matcher matcher = pattern.matcher(instruction);

        if (matcher.find()) {
            String numberString = matcher.group(1);
            return Integer.parseInt(numberString);
        }

        return -1; // If no number is found, return a default value or handle it accordingly
    }

    private static void getAllChecksInDepth(SSAAbstractInvokeInstruction s, ArrayList<String> allChecks, int depth) throws Exception {

        if(depth > 3){
            return;
        }

        DefaultEntrypoint ep = getEntryFromMethodReference(s.getDeclaredTarget());

        if(ep == null){
            return;
        }

        CallGraph cg = null;
        CGNode node = null;
        InterproceduralCFG icfg = null;
        SSACFG cfg = null;
        ISSABasicBlock entryBB = null;
        DefUse du = null;
        SymbolTable st = null;


        try{
            ArrayList<DefaultEntrypoint> singleEP = new ArrayList<>(Arrays.asList(ep));
            Pair<CallGraph, PointerAnalysis> cg_pa = buildCallGraph(singleEP);

            cg = cg_pa.fst;
            cg = patchCallGraphReturnCG(singleEP, cg);
            node =  CallGraphUtils.getNodeFromEntryPoint(cg, ep);
            icfg = buildICFG(cg);
            cfg = node.getIR().getControlFlowGraph();
            entryBB = cfg.entry();
            du = node.getDU();
            st = node.getIR().getSymbolTable();

            if(du == null || st == null || entryBB == null || node == null || cg == null || icfg == null || cfg == null){
                return;
            }
        }catch (Exception e){
            PrintUtils.print("[ERR]: " + e.getMessage());
            return;
        }

//        HashSet<Integer> visited = new HashSet<>();
//        LinkedList<ISSABasicBlock> stack = new LinkedList<>();
//        stack.add(entryBB);
//
//        while(!stack.isEmpty()){
//            ISSABasicBlock cur = stack.pop();
//            if(visited.contains(cur.getNumber())){
//                continue;
//            }
//
//            visited.add(cur.getNumber());
//
//            if(cur.isExitBlock()){
//                continue;
//            }
//
//
//            for(Iterator<SSAInstruction> it = cur.iterator(); it.hasNext();){
//                SSAInstruction i = it.next();
//                if(i instanceof SSAAbstractInvokeInstruction){
//                    ISSABasicBlock b = node.getIR().getBasicBlockForInstruction(i);
//                    BasicBlockInContext<ISSABasicBlock> block = new BasicBlockInContext<ISSABasicBlock>(node, b);
//                    Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> pe = FrameworkAccessControlDetector.analyzeInvokeInstruction(((SSAAbstractInvokeInstruction) i), du, icfg, block, st);
//
//                    if(pe != null){
//                        String acCheck = resolveAC(pe.snd);
//                        if(!(pe.snd instanceof PotentialMethodNameAccessControl)){
//                            allChecks.add(acCheck);
//                        }else{
//                            getAllChecksInDepth(((SSAAbstractInvokeInstruction) i), allChecks, depth+1);
//                        }
//                    }
//                }else if(i instanceof SSAConditionalBranchInstruction){
//                    ISSABasicBlock b = node.getIR().getBasicBlockForInstruction(i);
//                    BasicBlockInContext<ISSABasicBlock> block = new BasicBlockInContext<ISSABasicBlock>(node, b);
//                    Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> pe = FrameworkAccessControlDetector.analyzeConditionalBranchInstruction(i, icfg, block, st, du);
//
//                    if(pe != null){
//                        String acCheck = resolveAC(pe.snd);
//                        if(!(pe.snd instanceof PotentialMethodNameAccessControl)){
//                            allChecks.add(acCheck);
//                        }
//                    }
//                }
//            }
//
//            for(ISSABasicBlock child: cfg.getNormalSuccessors(cur)){
//                stack.push(child);
//            }
//        }


        for(SSAInstruction i: node.getIR().getInstructions()){

            if(i instanceof SSAAbstractInvokeInstruction){
                ISSABasicBlock b = node.getIR().getBasicBlockForInstruction(i);
                BasicBlockInContext<ISSABasicBlock> block = new BasicBlockInContext<ISSABasicBlock>(node, b);
                Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> pe = FrameworkAccessControlDetector.analyzeInvokeInstruction(((SSAAbstractInvokeInstruction) i), du, icfg, block, st);

                if(pe != null){
                    String acCheck = resolveAC(pe.snd);
                    if(!(pe.snd instanceof PotentialMethodNameAccessControl)){
                        allChecks.add(acCheck);
                    }else{
                        getAllChecksInDepth(((SSAAbstractInvokeInstruction) i), allChecks, depth+1);
                    }
                }
            }else if(i instanceof SSAConditionalBranchInstruction){
                ISSABasicBlock b = node.getIR().getBasicBlockForInstruction(i);
                BasicBlockInContext<ISSABasicBlock> block = new BasicBlockInContext<ISSABasicBlock>(node, b);
                Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> pe = FrameworkAccessControlDetector.analyzeConditionalBranchInstruction(i, icfg, block, st, du);

                if(pe != null){
                    String acCheck = resolveAC(pe.snd);
                    if(!(pe.snd instanceof PotentialMethodNameAccessControl)){
                        allChecks.add(acCheck);
                    }
                }
            }

        }

    }

    private static void initializeSinks(String sinkListSusi) {
        knownSinks = Feature.getSinks(sinkListSusi);
    }

    /*
    * Initializes global variables*/
    public static void safeInitialize() throws ClassHierarchyException, IOException {

        scope = ScopeUtils.makeScope();
        cha = ClassHierarchyFactory.make(scope);
        cache = new AnalysisCacheImpl(new DexIRFactory());

        permissionMap = new HashMap<>();
        entryPoints = new ArrayList<>();
    }


    public static void analyzeEntryPointsSerial() {
        ArrayList<HashMap<String, String>> allEps = new ArrayList<>();
        int cur = 0;
        int total = entryPoints.size();
        for(DefaultEntrypoint ep: entryPoints) {
//        if(ep.getMethod().getName().toString().equals("killAllBackgroundProcesses")){
            try {
                PrintUtils.print("(" + Integer.toString(cur) + "/" + Integer.toString(total) + ") EP: " + ep.getMethod().getName().toString());
                ep = Feature.getConcreteEP(ep);
                HashMap<String, String> feats = analyzeEntryPoint(ep);
                allEps.add(feats);
                cur += 1;
            } catch (Exception e) {
                //
            }
//        }
        }
        // Write to CSV
        try {
            writeDataToCSV(allEps, "aosp2.csv");
        } catch (IOException e) {
            //
        }

    }

    public static void writeDataToCSV(ArrayList<HashMap<String, String>> dataList, String fileName)
            throws IOException {
        CSVWriter writer = new CSVWriter(new FileWriter(fileName));

        // Write headers
        HashMap<String, String> firstRow = dataList.get(0);
        String[] headers = new String[firstRow.size()];
        int headerIndex = 0;
        for (String key : firstRow.keySet()) {
            headers[headerIndex++] = key;
        }
        writer.writeNext(headers);

        // Write data rows
        for (HashMap<String, String> dataRow : dataList) {
            String[] rowData = new String[dataRow.size()];
            int dataIndex = 0;
            for (String value : dataRow.values()) {
                rowData[dataIndex++] = value;
            }
            writer.writeNext(rowData);
        }

        writer.close();
    }

    public static void writeDataToFile(HashMap<String, HashSet<String>> data, String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (Map.Entry<String, HashSet<String>> entry : data.entrySet()) {
                String key = entry.getKey();
                HashSet<String> lines = entry.getValue();

                // Write key (ep) to file
                writer.write("EP: " + key);
                writer.newLine();

                // Write lines (HashSet) to file
                for (String line : lines) {
                    writer.write("ControlFlow: " + line);
                    writer.newLine();
                }

                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static HashMap<String, String> analyzeEntryPoint(DefaultEntrypoint ep) {
        HashMap<String, String> surfaceFeatures = getSurfaceFeaturesFromEP(ep);
        RecursiveData recursiveFeatures = getRecursiveFeaturesFromEP(ep);
        HashMap<String, String> recFeats = recursiveFeatures.getFeatures();

        surfaceFeatures.putAll(recFeats);

        return surfaceFeatures;

    }

    public static HashMap<String, String> getSurfaceFeaturesFromEP(DefaultEntrypoint ep) {
        return Feature.getFeatures(ep);
    }

    public static RecursiveData getRecursiveFeaturesFromEP(DefaultEntrypoint ep) {
        ArrayList<String> instructions = new ArrayList<>();
        ArrayList<String> allInvokes = new ArrayList<>();
        ArrayList<String> protections = new ArrayList<>();

        HashSet<String> visited = new HashSet<>();

        analyzeEpRecursive(ep, protections, instructions, allInvokes, visited);

        return new RecursiveData(instructions, protections, allInvokes);
    }

    public static String join(ArrayList<String> list, String delimiter) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(list.get(0));
        for (int i = 1; i < list.size(); i++) {
            sb.append(delimiter).append(list.get(i));
        }
        return sb.toString();
    }

    public static void analyzeEpRecursive(DefaultEntrypoint ep, ArrayList<String> protections, ArrayList<String> instructions, ArrayList<String> invokes, HashSet<String> visited) {

        visited.add(createEPString(ep));

        CallGraph cg = null;
        CGNode node = null;
        InterproceduralCFG icfg = null;
        SSACFG cfg = null;
        ISSABasicBlock entryBB = null;
        DefUse du = null;
        SymbolTable st = null;

        try{
            ArrayList<DefaultEntrypoint> singleEP = new ArrayList<>(Arrays.asList(ep));
            Pair<CallGraph, PointerAnalysis> cg_pa = buildCallGraph(singleEP);
//            if(cg_pa == null){
//                PrintUtils.print("Cant Build Call Graph! Why?");
//            }
            cg = cg_pa.fst;
            cg = patchCallGraphReturnCG(singleEP, cg);
            node =  CallGraphUtils.getNodeFromEntryPoint(cg, ep);
            icfg = buildICFG(cg);
            cfg = node.getIR().getControlFlowGraph();
            entryBB = cfg.entry();
            du = node.getDU();
            st = node.getIR().getSymbolTable();

            if(du == null || st == null || entryBB == null || node == null || cg == null || icfg == null || cfg == null){
                return;
            }
        }catch (Exception e){
            PrintUtils.print("[ERR]: " + e.getMessage());
            return;
        }


        for(SSAInstruction s: node.getIR().getInstructions()){
            if(s == null) {

            }
            try {
                if(IClassUtils.isInstructionExclusionFromPath(s)){
                    continue;
                }

                String ins = resolveInstruction(s, node);
                SSACFG.BasicBlock block = cfg.getBlockForInstruction(s.iIndex());
                BasicBlockInContext<ISSABasicBlock> bb = new BasicBlockInContext<ISSABasicBlock>(node, block);

                if(s instanceof SSAAbstractInvokeInstruction){
                    Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> pe = FrameworkAccessControlDetector.analyzeInvokeInstruction(((SSAAbstractInvokeInstruction) s), du, icfg, bb, st);
                    if(pe!=null){
                        String acCheck = resolveAC(pe.snd);
                        if(pe.snd instanceof PotentialMethodNameAccessControlGet){
                            int l = getPermissionLevelInt(((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getName().toString());
                            String lString = getPermissionLevel(((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getName().toString());
                            String t1 = "[AC] PotentialGet " + lString + "(" + Integer.toString(l) + ")";
                            t1 = t1 + " {" + ins + "}";
                            protections.add(t1);

                            if(l == 404 || l == -1){

                                DefaultEntrypoint invokeEp = getEntryFromMethodReference(((SSAAbstractInvokeInstruction) s).getDeclaredTarget());
                                invokeEp = Feature.getConcreteEP(invokeEp);

                                if(invokeEp == null){
                                    continue;
                                }
                                if(!visited.contains(createEPString(invokeEp))){
                                    analyzeEpRecursive(invokeEp, protections, instructions, invokes, visited);
                                }
                            }
                        }
                        else if(pe.snd instanceof PotentialMethodNameAccessControl){
                            int l = getPermissionLevelInt(((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getName().toString());
                            String lString = getPermissionLevel(((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getName().toString());
                            String t1 = "[AC] Potential " + lString + "(" + Integer.toString(l) + ")";
                            t1 = t1 + " {" + ins + "}";
                            protections.add(t1);

                            if(l == 404 || l == -1){

                                DefaultEntrypoint invokeEp = getEntryFromMethodReference(((SSAAbstractInvokeInstruction) s).getDeclaredTarget());
                                invokeEp = Feature.getConcreteEP(invokeEp);

                                if(invokeEp == null){
                                    continue;
                                }
                                if(!visited.contains(createEPString(invokeEp))){
                                    analyzeEpRecursive(invokeEp, protections, instructions, invokes, visited);
                                }
//
                            }
                        }
                        else{
                            protections.add(acCheck);
                        }
                    }
                    else{

                        if(!ins.contains("exception") && !ins.contains("[new]")){
                            instructions.add(ins);
                        }

                        if(!IClassUtils.isExclusionMethod((SSAAbstractInvokeInstruction) s)){
                            String methName = ((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getName().toString();
                            String className = ((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getDeclaringClass().getName().toString();
                            invokes.add("[METH]: " + methName + " [CLASS]: " + className);
                            try{
                                DefaultEntrypoint invokeEp = getEntryFromMethodReference(((SSAAbstractInvokeInstruction) s).getDeclaredTarget());
                                invokeEp = Feature.getConcreteEP(invokeEp);
                                if(!visited.contains(createEPString(invokeEp))){
                                    analyzeEpRecursive(invokeEp, protections, instructions, invokes, visited);
                                }

                            }catch (Exception e){
                                //
                            }

                        }
                    }
                }else if(s instanceof SSAConditionalBranchInstruction){
                    Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> pe = FrameworkAccessControlDetector.analyzeConditionalBranchInstruction(s, icfg, bb, st, du);

                    if(pe != null){
                        String acCheck = resolveAC(pe.snd);
                        protections.add(acCheck + " { CONDITIONAL BRANCH INSTRUCTION }");
                    }
                }else{
                    instructions.add(ins);
                }


            }catch (Exception e){
                continue;
            }


        }

//        for(String ins: protections){
//            if(ins.contains("[AC]")){
//                // is access control:
//                int num = AACR.Helper.extractNumberFromInstruction(ins);
//                curLevel = AACR.Helper.AND(curLevel, num);
//            }
//        }

    }

    public static String resolveInstruction(SSAInstruction s, CGNode node){
        // TODO: Add the functionality to check if it is a file, intent, network, or storage related instruction. Detecting Sinks with higher accuracy.
        if(s instanceof SSAAbstractInvokeInstruction){
            return "[inv]: " + InstructionResolver.resolveInvokeInstruction(((SSAAbstractInvokeInstruction) s), node);
        }else if(s instanceof SSAGetInstruction){
            return "[get]: " + InstructionResolver.resolveGetFieldInstruction(((SSAGetInstruction) s), node);
        }else if(s instanceof SSACheckCastInstruction) {
            return "[chk]: " + InstructionResolver.resolveCheckCastInstruction(((SSACheckCastInstruction) s), node);
        } else if (s instanceof SSAPutInstruction){
            return "[put]: " + InstructionResolver.resolvePutFieldInstruction(((SSAPutInstruction) s), node);
        } else if (s instanceof SSABinaryOpInstruction){
            return "[bin]: " + InstructionResolver.resolveBinOpInstruction(((SSABinaryOpInstruction) s), node);
        } else if (s instanceof SSAReturnInstruction){
            return "[ret]: " + InstructionResolver.resolveReturnTypeInstruction(((SSAReturnInstruction) s), node);
        }else if(s instanceof SSANewInstruction){
            return "[new]: " + InstructionResolver.resolveSSANewInstruction(((SSANewInstruction) s), node);
        }else if(s instanceof SSAInstanceofInstruction){
            return "[ins]: " + s.toString();
        }else if(s instanceof SSAAbstractThrowInstruction || s instanceof SSAThrowInstruction){
            return "[thr]: " + s.toString();
        }else{
            return "[NON]: " + s.toString();
        }
    }


    public static void initializePermissions(String directoryPath) throws IOException {
//        ArrayList<String> manifestPaths = new ArrayList<>(Arrays.asList("Input/custom/ROMs/Xioami_Morocco/framework-res/DecodedManifest.xml",
//                "Input/custom/ROMs/Xioami_Morocco/framework-res/DecodedManifest2.xml",
//                "Input/custom/ROMs/Vivo/framework-res/DecodedManifest.xml",
//                "Input/aosp/google10/1/image/framework/framework-res/DecodedManifest.xml",
//                "Input/custom/ROMs/LG/V405E/framework-res/DecodedManifest.xml",
//                "Input/custom/Roms/Amazon/framework-res/DecodedManifest.xml",
//                "Input/custom/Roms/Amazon/framework-res/DecodedManifest2.xml"));

        ArrayList<String> manifestPaths = new ArrayList<>();

        Files.walk(Paths.get(directoryPath))
                .filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().endsWith("xml"))
                .forEach(file -> manifestPaths.add(file.toString()));

        for(String p: manifestPaths){
            try{
                HashMap<String, FrameworkAnalyzer.Permission_Level> _temp = getPermissionLevelsFromManifest(p);
                permissionMap.putAll(_temp);
            }catch (Exception e){
                PrintUtils.print_box("Could not read: " + p);
            }
        }
    }

    public static InterproceduralCFG buildICFG(CallGraph cg){
        return new InterproceduralCFG(cg);
    }
        public static HashMap<String, FrameworkAnalyzer.Permission_Level> getPermissionLevelsFromManifest(String path) throws IOException {

            HashMap<String, FrameworkAnalyzer.Permission_Level> permissionLevelMap = new HashMap<>();

//        String path = "Input/framework/framework-res/DecodedManifest.xml";
//        String path = FrameworkAnalyzer.manifestPath;

            // maintain a permissionAccessLevel hashmap and get the permission levels and return a hashmap of permission and its level

            BufferedReader reader;

            reader = new BufferedReader(new FileReader(path));
            String line = reader.readLine();

            try{
                while (line != null) {

                    if("" != line.trim() && line.contains("<permission ")) {
//                    PrintUtils.print("Line: " + line);
//                    PrintUtils.print(line);
                        String name = StringUtils.substringBetween(line, "name=\"", "\" android:");

                        if (name != null) {

                            //                    PrintUtils.print(name);
                            String[] dots = name.split("\\.");
                            name = dots[dots.length - 1];
                            String permissionLevel = StringUtils.substringBetween(line, "protectionLevel=\"", "\"");

                            boolean assigned = false;
                            for (FrameworkAnalyzer.Permission_Level pm : FrameworkAnalyzer.Permission_Level.values()) {
                                if(permissionLevel.toLowerCase().equals(pm.getValue())){
                                    permissionLevelMap.put(name, pm);
                                    assigned = true;
                                    break;
                                }

                            }

                            if(!assigned){
                                for (FrameworkAnalyzer.Permission_Level pm : FrameworkAnalyzer.Permission_Level.values()) {

                                    if (permissionLevel.toLowerCase().contains(pm.getValue())) {
                                        //                            PrintUtils.printBold(name + ": " + pm.getLevel());
                                        permissionLevelMap.put(name, pm);
                                        assigned = true;
                                        break;
                                    }

                                }
                            }

                            if (!assigned) {
                                permissionLevelMap.put(name, FrameworkAnalyzer.Permission_Level.UNKNOWN);
                            }
                        }

                    }
                    line = reader.readLine();

                }
                reader.close();
                return permissionLevelMap;

            }catch (Exception e){
                e.printStackTrace();
                return null;
            }
        }

    public static String resolveAC(AccessControlEnforcement accessControl){

        int accessLevel = 404;
        String accessLevelString = "null";

        if(accessControl instanceof ComparativeAccessControlCheck){
            String permLevel = ((ComparativeAccessControlCheck) accessControl).getAccessControlInstruction().getDeclaredTarget().toString();
            accessLevel = getPermissionLevelInt(permLevel);
            accessLevelString = getPermissionLevel(permLevel);

        }else if(accessControl instanceof PermissionCheck){
            String permLevel = ((PermissionCheck) accessControl).getSingleEnforcement();
            HashSet<String> temp = new HashSet<>();
            temp.add(permLevel);

            permLevel = temp.toString();

            accessLevel = getPermissionLevelInt(permLevel);
            accessLevelString = getPermissionLevel(permLevel);

        }else if(accessControl instanceof PotentialMethodNameAccessControl){
            String permLevel = ((PotentialMethodNameAccessControl) accessControl).getAccessControlInstruction().getDeclaredTarget().toString();
            accessLevel = getPermissionLevelInt(permLevel);
            accessLevelString = getPermissionLevel(permLevel);
        }

        return accessLevelString + "(" + Integer.toString(accessLevel) + ")";
    }

    public static Pair<CallGraph, PointerAnalysis> buildCallGraph(ArrayList<DefaultEntrypoint> entryPoints){
        Pair<CallGraph, PointerAnalysis> cg_pa = CallGraphUtils.buildCallGraphPA(CallGraphUtils.CGType.V01CFA, scope, cha, entryPoints, AnalysisOptions.ReflectionOptions.NONE, null);
        return cg_pa;
    }

    public static CallGraph patchCallGraphReturnCG(ArrayList<DefaultEntrypoint> singlePoint, CallGraph cg){

        ArrayList<DefaultEntrypoint> newSet = patchCallGraph(cg);

        if (newSet.size() > 0) {
            singlePoint.addAll(newSet);
            try {
                cg = CallGraphUtils.buildCallGraph(CallGraphUtils.CGType.V01CFA, scope,
                        cha, singlePoint,
                        AnalysisOptions.ReflectionOptions.NONE, null);
            } catch (Exception e) {
                PrintUtils.print("Could not Build a call graph after addition");
                return null;
            }
        }
        return cg;
    }

    public static ArrayList<DefaultEntrypoint> patchCallGraph(CallGraph cg){
        ArrayList<DefaultEntrypoint> newEntries = new ArrayList<DefaultEntrypoint>();
        for (Iterator<CGNode> it = cg.iterator(); it.hasNext();) {
            CGNode nd = it.next();
            if(nd.getIR()==null)
                continue;
            else{
                IR ir = nd.getIR();
                if(ir.getMethod().getName().toString().contains("access$")){
                    if(cg.getSuccNodeCount(nd)==0){

                        for(SSAInstruction s : ir.getInstructions()){
                            if (s instanceof com.ibm.wala.ssa.SSAAbstractInvokeInstruction) {
                                com.ibm.wala.ssa.SSAAbstractInvokeInstruction call = (com.ibm.wala.ssa.SSAAbstractInvokeInstruction) s;
                                IMethod meth = cg.getClassHierarchy().resolveMethod(call.getDeclaredTarget());
                                DefaultEntrypoint de = new DefaultEntrypoint(meth,cg.getClassHierarchy());
                                newEntries.add(de);
                            }
                        }
                    }
                }
            }
        }
        return newEntries;
    }

    public static void getEntryPoints() {
        Pair<HashSet<IClass>, HashMap<IClass, String>> systemServices = getSystemServices(scope, cha);
        HashSet<IClass> systemServicesClasses = systemServices.fst;
        Pair<HashSet<DefaultEntrypoint>, HashMap<DefaultEntrypoint, String>> _entryPoints = FrameworkEntrypointDetector.getFrameworkEntrypoints(scope, cha, systemServicesClasses, systemServices.snd);

        for(DefaultEntrypoint ep: _entryPoints.fst){
//            if(!alreadyAnalysed.contains(createEPString(ep))){
//                entryPoints.add(ep);
//            }
//            if(createEPString(ep).contains("addCrossProfileIntentFilter")){
//                entryPoints.add(ep);
//            }
        }

//        entryPoints = new ArrayList<>(_entryPoints.fst);
    }

    public static String createEPString(DefaultEntrypoint ep){
        String c = ep.getMethod().getDeclaringClass().getName().toString();
        String[] arc = c.split("/");
        String _c = arc[arc.length-1];

        _c.replace(".java", "");

        String test = ep.getMethod().getName().toString() + "_" + _c
                + "_" + Integer.toString(ep.getMethod().getNumberOfParameters());
//            String c = ep.getMethod().getName().toString() + "_" + Integer.toString(ep.hashCode());

        return test;
    }

    public static DefaultEntrypoint getEntryFromMethodReference(MethodReference m){
        try{
            DefaultEntrypoint ep = new DefaultEntrypoint(m, cha);
            if (ep.getMethod().getDeclaringClass().isInterface())
                return FrameworkAccessControlDetector.getConcreteEp(ep, cha);
            return ep;
        }catch (UnimplementedError e){
            return null;
        }
    }

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

                                        DefaultEntrypoint _ep = getEntryFromMethodReference(((SSAAbstractInvokeInstruction) firstInstruction).getDeclaredTarget());
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
                                        if (!IClassUtils.exclusionClasses.contains(serviceIClass.getName().toString()) && serviceIClass.toString().contains("Service")) {
                                            serviceClasses.add(serviceIClass);
                                            _map.put(serviceIClass, _serviceName);
                                        }
                                    } catch (Exception e) {
                                    }

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
        return Pair.make(serviceClasses, _map);
//        return serviceClasses;
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

    public static int getPermissionLevelInt(String s){
        if(s.toLowerCase().contains("permission")){
            String[] last = s.split("[.]", 0);
            String _s = last[last.length - 1];
            _s = _s.replace(";", "");
            _s = _s.replace("[", "");
            _s = _s.replace("]", "");
            if(permissionMap.containsKey(_s)){
                return permissionMap.get(_s).getLevel();
            }else{
                return 3;
            }
        }else if(s.toLowerCase().contains("uid")
                || s.toLowerCase().contains("system")
                || s.toLowerCase().contains("package") || s.toLowerCase().contains("process") || s.toLowerCase().contains("pid")
                || s.toLowerCase().contains("tid") || s.toLowerCase().contains("profile")) {
            return 3;
        }else if(s.toLowerCase().contains("app") || s.toLowerCase().contains("user")){
            // treat app id as Normal? Lets see what happens
            return 3;
        } else if (s.toLowerCase().contains("profile")) {
            return 1;
        } else {
            return -1;
        }
    }

    public static String getPermissionLevel(String s){
        if(s.toLowerCase().contains("permission")){
            String[] last = s.split("[.]", 0);
            String _s = last[last.length - 1];
            _s = _s.replace(";", "");
            _s = _s.replace("]", "");
            _s = _s.replace("[", "");
            if(permissionMap.containsKey(_s)){
                return "[AC] " + permissionMap.get(_s).getString();
            }else{
                return "[AC] " + _s;
            }
        }else if(s.toLowerCase().contains("uid")){
            return "[AC] UID CHECK";
        }else if(s.toLowerCase().contains("system")){
            return "[AC] SYSTEM CHECK";
        }else if(s.toLowerCase().contains("package")){
            return "[AC] PACKAGE CHECK";
        }else if(s.toLowerCase().contains("process")){
            return "[AC] PROCESS CHECK";
        }else if(s.toLowerCase().contains("appid")){
            return "[AC] APP ID CHECK";
        }else if(s.toLowerCase().contains("pid")){
            return "[AC] PID CHECK";
        }else if(s.toLowerCase().contains("tid")){
            return "[AC] TID CHECK";
        }else if(s.toLowerCase().contains("user")){
            return "[AC] USER CHECK";
        }else if(s.toLowerCase().contains("profile")){
            return "[AC] PROFILE CHECK";
        }else{
            return "[AC] NONE: " + s.toLowerCase() ;
        }
    }

    public static class InstructionResolver{
        public static String resolveAC(AccessControlEnforcement accessControl){

            int accessLevel = 404;
            String accessLevelString = "null";

            if(accessControl instanceof ComparativeAccessControlCheck){
                String permLevel = ((ComparativeAccessControlCheck) accessControl).getAccessControlInstruction().getDeclaredTarget().toString();
                accessLevel = getPermissionLevelInt(permLevel);
                accessLevelString = getPermissionLevel(permLevel);

            }else if(accessControl instanceof PermissionCheck){
                String permLevel = ((PermissionCheck) accessControl).getSingleEnforcement();
                HashSet<String> temp = new HashSet<>();
                temp.add(permLevel);

                permLevel = temp.toString();

                accessLevel = getPermissionLevelInt(permLevel);
                accessLevelString = getPermissionLevel(permLevel);

            }else if(accessControl instanceof PotentialMethodNameAccessControl){
                String permLevel = ((PotentialMethodNameAccessControl) accessControl).getAccessControlInstruction().getDeclaredTarget().toString();
                accessLevel = getPermissionLevelInt(permLevel);
                accessLevelString = getPermissionLevel(permLevel);
            }

            return accessLevelString + "(" + Integer.toString(accessLevel) + ")";
        }

        public static int getPermissionLevelInt(String s){
            if(s.toLowerCase().contains("permission")){
                String[] last = s.split("[.]", 0);
                String _s = last[last.length - 1];
                _s = _s.replace(";", "");
                _s = _s.replace("[", "");
                _s = _s.replace("]", "");
                if(permissionMap.containsKey(_s)){
                    return permissionMap.get(_s).getLevel();
                }else{
                    return -1;
                }
            }else if(s.toLowerCase().contains("uid")
                    || s.toLowerCase().contains("system")
                    || s.toLowerCase().contains("package") || s.toLowerCase().contains("process") || s.toLowerCase().contains("pid")
                    || s.toLowerCase().contains("tid") || s.toLowerCase().contains("profile")) {
                return 3;
            }else if(s.toLowerCase().contains("app") || s.toLowerCase().contains("user")){
                // treat app id as Normal? Lets see what happens
                return 3;
            } else if (s.toLowerCase().contains("profile")) {
                return 1;
            } else {
                return -1;
            }
        }

        public static String getPermissionLevel(String s){
            if(s.toLowerCase().contains("permission")){
                String[] last = s.split("[.]", 0);
                String _s = last[last.length - 1];
                _s = _s.replace(";", "");
                _s = _s.replace("]", "");
                _s = _s.replace("[", "");
                if(permissionMap.containsKey(_s)){
                    return "[AC] " + permissionMap.get(_s).getString();
                }else{
                    return "[AC] " + _s + " - KEY NOT FOUND";
                }
            }else if(s.toLowerCase().contains("uid")){
                return "[AC] UID CHECK";
            }else if(s.toLowerCase().contains("system")){
                return "[AC] SYSTEM CHECK";
            }else if(s.toLowerCase().contains("package")){
                return "[AC] PACKAGE CHECK";
            }else if(s.toLowerCase().contains("process")){
                return "[AC] PROCESS CHECK";
            }else if(s.toLowerCase().contains("appid")){
                return "[AC] APP ID CHECK";
            }else if(s.toLowerCase().contains("pid")){
                return "[AC] PID CHECK";
            }else if(s.toLowerCase().contains("tid")){
                return "[AC] TID CHECK";
            }else if(s.toLowerCase().contains("user")){
                return "[AC] USER CHECK";
            }else if(s.toLowerCase().contains("profile")){
                return "[AC] PROFILE CHECK";
            }else{
                return "[AC] NONE: " + s.toLowerCase() ;
            }
        }

        public static String resolveInvokeInstruction(SSAAbstractInvokeInstruction s, CGNode node){

            SymbolTable st = node.getIR().getSymbolTable();
            IR ir = node.getIR();
            DefUse du = node.getDU();

            String res = "";

            if(s.getNumberOfDefs() > 0){
                // has a declaration
                String _t = resolvePrimitiveType(s.getDeclaredResultType());

                int defVal = s.getDef();

                if(defVal != -1){
                    String value = resolveValueInInstruction(defVal, node, st, du, ir);
                    res += _t + " " + value + " = ";
                }
            }

            if(!s.isStatic()){
                // invoke virtual
                int numUses = s.getNumberOfUses();

                // first use is the class or definition instruction
                int classVal = s.getUse(0);

                SSAInstruction k = du.getDef(classVal);

                if(k!= null){
                    int defGet = k.getDef();
                    String vv = InstructionResolver.resolveValueInInstruction(defGet, node, st, du, ir);


                    if(s.getDeclaredTarget().getName().toString().contains("<init>")){

                        String cls = InstructionResolver.resolvePrimitiveType(s.getDeclaredTarget().getDeclaringClass());
                        res += cls + " " + vv + " = new " + cls;

                    }else{
                        res += vv + "." + s.getDeclaredTarget().getName().toString();
                    }

                }else{
                    res += s.getDeclaredTarget().getName().toString();
                }

                ArrayList<String> params = new ArrayList<String>();
                for(int i = 1; i < numUses; i++){
                    int v = s.getUse(i);
                    String value = resolveValueInInstruction(v, node, st, du, ir);
                    params.add(value);
                }
                String p = InstructionResolver.join(params, ",");
                p = "(" + p + ");";
                return res + p;

            }else{
                // invoke static
                int numUses = s.getNumberOfUses();

                // get declared class of the instruction

                String cls = resolvePrimitiveType(s.getDeclaredTarget().getDeclaringClass());

                res +=  cls + "." + s.getDeclaredTarget().getName().toString();

                ArrayList<String> params = new ArrayList<String>();
                for(int i = 0; i < numUses; i++){
                    int v = s.getUse(i);
                    String value = resolveValueInInstruction(v, node, st, du, ir);
                    params.add(value);
                }
                String p = InstructionResolver.join(params, ",");
                p = "(" + p + ");";
                return res + p;
            }

        }

        public static String join(ArrayList<String> list, String delimiter) {
            if (list == null || list.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(list.get(0));
            for (int i = 1; i < list.size(); i++) {
                sb.append(delimiter).append(list.get(i));
            }
            return sb.toString();
        }

        private static String resolveValueInInstruction(int v, CGNode node, SymbolTable st, DefUse du, IR ir) {

            if(v == 1){
                return "this";
            }else if(v == -1){
                return "";
            }

            if(st.isParameter(v)){
                return "param" + Integer.toString(v);
            }else if(!st.isConstant(v)){

                SSAInstruction x = du.getDef(v);

                if(x.getDef() != v){
                    if(x instanceof SSAGetInstruction){
                        return InstructionResolver.resolveGetFieldInstruction(((SSAGetInstruction) x), node);
                    }else if(x instanceof SSAPutInstruction){
                        return InstructionResolver.resolvePutFieldInstruction(((SSAPutInstruction) x), node);
                    }else if(x instanceof SSAAbstractInvokeInstruction){
                        return InstructionResolver.resolveInvokeInstruction(((SSAAbstractInvokeInstruction) x), node);
                    }else if(x instanceof SSABinaryOpInstruction){
                        return InstructionResolver.resolveBinOpInstruction(((SSABinaryOpInstruction) x), node);
                    }else if(x instanceof SSACheckCastInstruction){
                        return InstructionResolver.resolveCheckCastInstruction(((SSACheckCastInstruction) x), node);
                    }else if(x instanceof SSAReturnInstruction) {
                        return InstructionResolver.resolveReturnTypeInstruction(((SSAReturnInstruction) x), node);
                    } else{
                        int finalVal = x.getDef();

                        if(finalVal == -1){
                            return "";
                        }

                        return "var" + finalVal;
                    }
                }
                else{
                    int finalVal = x.getDef();

                    if(finalVal == -1){
                        return "";
                    }

                    return "var" + finalVal;
                }

            }else{
                String constVal = st.getConstantValue(v).toString();
                return  constVal;
            }
        }

        public static String resolveGetFieldInstruction(SSAGetInstruction s, CGNode node){

            String res = "";

            SymbolTable st = node.getIR().getSymbolTable();
            IR ir = node.getIR();
            DefUse du = node.getDU();

            if(s.getNumberOfDefs() > 0){

                String defType = resolvePrimitiveType(((SSAGetInstruction) s).getDeclaredFieldType());

                String var = "var" + s.getDef();
                res += defType + " " + var + " = ";
            }

            String fieldName = s.getDeclaredField().getName().toString();

            if(!s.isStatic()){
                int valDef = s.getUse(0);
                String val = InstructionResolver.resolveValueInInstruction(valDef, node, st, du, ir);
                res += val + "." + fieldName;
            }else{
                res += fieldName;
            }

            return res + ";";
        }

        public static String resolveReturnTypeInstruction(SSAReturnInstruction s, CGNode node){
            SymbolTable st = node.getIR().getSymbolTable();
            IR ir = node.getIR();
            DefUse du = node.getDU();

            int retVal = s.getResult();
            String retType = node.getMethod().getReturnType().getName().toString();
            if(retType == null || retVal == -1){
                retType = "NONE";
            }

            retType = expandType(retType);

            if(st.isConstant(retVal)){
                return retType + " " + st.getConstantValue(retVal).toString();
            }else{
                SSAInstruction x = du.getDef(retVal);
                return retType + " " + InstructionResolver.resolveValueInInstruction(retVal, node, st, du, ir);
            }

        }

        public static String resolveBinOpInstruction(SSABinaryOpInstruction s, CGNode node){

            String res = "";

            IR ir = node.getIR();
            DefUse du = node.getDU();
            SymbolTable st = node.getIR().getSymbolTable();


            int defVal = s.getDef();

            if(defVal != -1){
                String def_str = InstructionResolver.resolveValueInInstruction(defVal, node, st, du, ir);
                res += def_str + " = ";
            }


            if(s.getNumberOfUses() >= 2) {

                int op1 = s.getUse(0);
                int op2 = s.getUse(1);

                String op1_str = InstructionResolver.resolveValueInInstruction(op1, node, st, du, ir);
                String op2_str = InstructionResolver.resolveValueInInstruction(op2, node, st, du, ir);

                String op = s.getOperator().toString();

                res += op1_str + " " + op + " " + op2_str;

            }

            return res;
        }

        public static String resolvePutFieldInstruction(SSAPutInstruction s, CGNode node) {

            int rightDef = s.getUse(1);
            int leftDef = s.getUse(0);

            IR ir = node.getIR();
            DefUse du = node.getDU();
            SymbolTable st = node.getIR().getSymbolTable();

            String fieldName = s.getDeclaredField().getName().toString();

            String right = InstructionResolver.resolveValueInInstruction(rightDef, node, st, du, ir);
            String left = InstructionResolver.resolveValueInInstruction(leftDef, node, st, du, ir);


            return  left + "."  + fieldName + " = " + right + ";";

        }

        public static String resolveCheckCastInstruction(SSACheckCastInstruction s, CGNode node){
            int resVal = s.getVal();
            int resDef = s.getDef();

            IR ir = node.getIR();
            DefUse du = node.getDU();
            SymbolTable st = node.getIR().getSymbolTable();

            String val = InstructionResolver.resolveValueInInstruction(resVal, node, st, du, ir);
            String def = InstructionResolver.resolveValueInInstruction(resDef, node, st, du, ir);

            TypeReference[] _types = s.getDeclaredResultTypes();

            if(_types.length > 1){


                String tDef = resolvePrimitiveType(_types[0]);
                String tVal = resolvePrimitiveType(_types[1]);


                return tDef + " " + def + " = (" + tVal + ") " + val;

            }else if(_types.length > 0){
                String _t = resolvePrimitiveType(_types[0]);
                return _t + " " + def + " = (" + _t + ") " + val;
            }else{
                return  def + " = " + val;
            }
        }

        public static String getLastSplitByDelimiter(String s, String d){
            String[] ar = s.split(d);
            if (ar.length > 0){
                return ar[ar.length - 1];
            }else{
                return s;
            }
        }

        public static String resolvePrimitiveType(TypeReference t){
            if(t.isPrimitiveType()){
                return InstructionResolver.expandType(t.getName().toString());
            }
            return getLastSplitByDelimiter(t.getName().toString(), "/");
        }

        public static String resolveSSANewInstruction(SSANewInstruction s, CGNode node){
            IR ir = node.getIR();
            DefUse du = node.getDU();
            SymbolTable st = node.getIR().getSymbolTable();

            TypeReference t = s.getConcreteType();
            String type = InstructionResolver.resolvePrimitiveType(t);

            int valDef = s.getDef();
            String strVal = InstructionResolver.resolveValueInInstruction(valDef, node, st, du, ir);

            int numUses = s.getNumberOfUses();

            ArrayList<String> params = new ArrayList<String>();

            for(int i = 0; i < numUses; i++){
                int v = s.getUse(i);
                String value = resolveValueInInstruction(v, node, st, du, ir);
                params.add(value);
            }

            String p = InstructionResolver.join(params, ",");
            p = "(" + p + ");";


            return type + " " + strVal + " = new " + type + p;

        }

        public static String expandType(String shorthand) {
            switch (shorthand) {
                case "B":
                    return "byte";
                case "C":
                    return "char";
                case "D":
                    return "double";
                case "F":
                    return "float";
                case "I":
                    return "int";
                case "J":
                    return "long";
                case "S":
                    return "short";
                case "V":
                    return "void";
                case "Z":
                    return "boolean";
                default:
                    return shorthand;
            }
        }

    }

}


