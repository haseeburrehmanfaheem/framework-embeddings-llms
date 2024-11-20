package com.aacr.wala.workshop.analyzers;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.collections.Pair;
import com.aacr.helpers.accesscontrol.framework.AccessControlUtils;
import com.aacr.helpers.detectors.framework.FrameworkAccessControlDetector;
import com.aacr.helpers.detectors.framework.FrameworkEntrypointDetector;
import com.aacr.helpers.detectors.framework.SystemServicesDetector;
import com.aacr.helpers.utils.CallGraphUtils;
import com.aacr.helpers.utils.IClassUtils;
import com.aacr.helpers.utils.PrintUtils;
import com.aacr.helpers.utils.ScopeUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.*;


public class FrameworkAnalyzer {

//    public static String fileName = "Logs/Logs_AOSP9";
//    public static String manifestPath = "Input/aosp/google9/9/Pixel3a/framework/framework-res/aosp9DecodedManifest.xml";

//    public static String fileName = "Logs/Logs_AOSP10";
//    public static String manifestPath = "Input/aosp/google10/1/image/framework/framework-res/DecodedManifest.xml";

//    public static String fileName = "Logs/Amazon";
//    public static String manifestPath = "Input/custom/Roms/Amazon/framework-res/DecodedManifest.xml";

//    public static String fileName = "Logs/Vivo";
//    public static String manifestPath = "Input/custom/Roms/Vivo/framework-res/DecodedManifest.xml";

    public static String fileName = "Logs/AOSP_Temp";
    public static String manifestPath = "Input/custom/Roms/Amazon/framework-res/DecodedManifest.xml";
    public static String manifestPath2 = "Input/custom/Roms/Amazon/framework-res/DecodedManifest2.xml";

    public static AnalysisScope scope;
    public static ClassHierarchy cha;
    public static AnalysisCacheImpl cache;

    public static HashMap<String, Permission_Level> permissionMap;
    public static HashMap<String, SSAAbstractInvokeInstruction> privateMethods;

    public static void launch() throws Exception {
//        SourceCodeReconstructor.reconstructSourceCode("deregisterProxyReceiver");
//        writeCallGraphOfEntryPoints();
//        SourceCodeReconstructor.reloadJadx();
        /*
         * Start the framework analysis from here
         */

//        permissionMap = getPermissionLevelsFromManifest(manifestPath2);
//        HashMap<String, Permission_Level> permissionMap2 = getPermissionLevelsFromManifest(manifestPath);
//
//        permissionMap.putAll(permissionMap2);
//
//        int curNum = 1;
//        PrintUtils.print("Analyzing: " + Integer.toString(curNum));
//
//        initializeFrameworkAnalyzer();
//
//        // ToDo: Check "startInterceptSmsBySender" - Access Check detection is not correct.
//        // We can look at all the method invokes in a potential access check
//
//        Pair<HashSet<IClass>, HashMap<IClass, String>> _systemServices = SystemServicesDetector.getSystemServices(scope, cha);
//        HashSet<IClass> systemServices = _systemServices.fst;
//
//        Pair<HashSet<DefaultEntrypoint>, HashMap<DefaultEntrypoint, String>> _entryPoints = FrameworkEntrypointDetector.getFrameworkEntrypoints(scope, cha, systemServices, _systemServices.snd);
//
//        HashSet<DefaultEntrypoint> entryPoints = _entryPoints.fst;
//        HashSet<String> eps = convertEntryToString(entryPoints);
//
//
//
//        ArrayList<DefaultEntrypoint> entryPointsList = new ArrayList<>();
//
//        int i = 1;
//        int n = eps.size();
////
////
////
//        for(DefaultEntrypoint ep: _entryPoints.snd.keySet()){
////            if(ep.getMethod().getName().toString().contains("getDisallowedSystemApps")){
////                HashMap<String, HashMap<IMethod, IClass>> _map = getInvokesForEachEntryPoint(ep, 5);
////            if (ep.getMethod().getDeclaringClass().isInterface())
////                ep = FrameworkAccessControlDetector.getConcreteEp(ep, cha);
//            writeEpToFile(ep.getMethod().getName().toString() + "|-|" + _entryPoints.snd.get(ep), "PythonAnalysis/logs_ep.txt");
////            }
////            entryPointsList.add(ep);
//
//
//        }

    }

    private static void writeCallGraphOfEntryPoints() {
        // Do the same thing with Call Graph of entry points and their classes
        // Get Access Control Protection while getting all the Invokes

    }

    public static void launch2() throws Exception {

        /*
         * Start the framework analysis from here
         */

        ArrayList<String> manifestPaths = new ArrayList<>(Arrays.asList("Input/custom/ROMs/Xioami_Morocco/framework-res/DecodedManifest.xml",
                "Input/custom/ROMs/Xioami_Morocco/framework-res/DecodedManifest2.xml",
                "Input/aosp/google10/1/image/framework/framework-res/DecodedManifest.xml",
                "Input/custom/ROMs/LG/V405E/framework-res/DecodedManifest.xml",
                "Input/custom/Roms/Amazon/framework-res/DecodedManifest.xml",
                "Input/custom/Roms/Amazon/framework-res/DecodedManifest2.xml"));

        permissionMap = new HashMap<String, FrameworkAnalyzer.Permission_Level>();

        for(String p: manifestPaths){
            HashMap<String, FrameworkAnalyzer.Permission_Level> _temp = DataCollection.getPermissionLevelsFromManifest(p);
            permissionMap.putAll(_temp);
        }


        initializeFrameworkAnalyzer();


        Pair<HashSet<IClass>, HashMap<IClass, String>> _systemServices = SystemServicesDetector.getSystemServices(scope, cha);
        HashSet<IClass> systemServices = _systemServices.fst;

        Pair<HashSet<DefaultEntrypoint>, HashMap<DefaultEntrypoint, String>> _entryPoints = FrameworkEntrypointDetector.getFrameworkEntrypoints(scope, cha, systemServices, _systemServices.snd);

        HashSet<DefaultEntrypoint> entryPoints = _entryPoints.fst;
        HashSet<String> eps = convertEntryToString(entryPoints);

//
//
        ArrayList<DefaultEntrypoint> entryPointsList = new ArrayList<>();
        int i = 0;
//        for(DefaultEntrypoint ep: entryPoints){
//            entryPointsList.add(ep);
////            HashMap<IMethod, IClass> _map = getInvokesForEachEntryPoint(ep, 5);
////            writeCallMapToString(ep.getMethod().getName().toString(), _map, "PythonAnalysis/callGraph.txt");
//        }


        writeHashMapToFile(_entryPoints.snd, "Logs/serviceClassLookup.txt");

        HashSet<String> remove = new HashSet<>();

        // ep in aosp which get stuck on loop
        remove.add("registerReceiver");
        remove.add("removeAccountAsUser");
        remove.add("addSubInfoRecord");
        remove.add("requestNetwork");
        remove.add("getAuthToken");
        remove.add("startNextMatchingActivity");
        remove.add("installExistingPackageAsUser");
        remove.add("requestLocationUpdates");
        remove.add("startInstrumentation");
        remove.add("createAndManageUser");
        remove.add("filterEvent");
        remove.add("registerReceiverWithFeature");
        remove.add("finishReceiver");
        remove.add("setActiveProfile");

        final File folder = new File(fileName);
        HashSet<DefaultEntrypoint> filteredEntryPoints = listFilesForFolder(folder, entryPoints, remove);

//              HashSet<DefaultEntrypoint> filteredEntryPoints = filterEntryPoint(entryPoints, "updateProfileCount", true);
//        HashSet<DefaultEntrypoint> filteredEntryPoints = filterEntryPoint(entryPoints, "isSyncPending", true);

//        HashMap<DefaultEntrypoint, HashMap<String, ArrayList<HashMap<String, String>>>> resourceMap1 = FrameworkAccessControlDetector.detectFrameworkAccessControlDFS(scope, cha, filteredEntryPoints, 1, curNum, true);

    }




    private static HashMap<String, HashMap<IMethod, IClass>> getInvokesForEachEntryPoint(DefaultEntrypoint ep, int n){

        DefaultEntrypoint concreteEp = ep;

        if (ep.getMethod().getDeclaringClass().isInterface())
            concreteEp = FrameworkAccessControlDetector.getConcreteEp(ep, cha);

        ep = concreteEp;

        ArrayList<DefaultEntrypoint> singlePoint = new ArrayList<>();
        singlePoint.add(concreteEp);

        CallGraph cg = CallGraphUtils.buildCallGraph(CallGraphUtils.CGType.V01CFA, scope,
                cha, singlePoint,
                AnalysisOptions.ReflectionOptions.NONE, null);

        HashMap<IMethod, IClass> result = new HashMap<>();

        CGNode _node = CallGraphUtils.getNodeFromEntryPoint(cg, concreteEp);

        if(_node == null){
            return null;
        }

        // Create a queue to store methods to explore.
        Queue<CGNode> queue = new LinkedList<>();
        queue.add(_node);

        // Create a set to store methods that have already been explored.
        Set<CGNode> explored = new HashSet<>();

        int count = 5;
        while(count > 0){
            count -= 1;
            if(queue.isEmpty()){
                break;
            }
            Queue<CGNode> temp = new LinkedList<>();
            while(!queue.isEmpty()){
                CGNode curNode = queue.poll();

                if(explored.contains(curNode)){
                    continue;
                }

                explored.add(curNode);

                SSAInstruction[] ins = curNode.getIR().getInstructions();
                for(SSAInstruction i: ins){
                    if(i instanceof SSAAbstractInvokeInstruction){
                        if(AccessControlUtils.isAccessCheckInvokeLight((SSAAbstractInvokeInstruction) i)){
                            continue;
                        }
                        // Found a method call
                        MethodReference m = ((SSAAbstractInvokeInstruction) i).getDeclaredTarget();
                        DefaultEntrypoint e = DataCollection.getEntryFromMethodReference(m);


                        CGNode nnode = CallGraphUtils.getNodeFromEntryPoint(cg, e);
                        if(explored.contains(nnode)){
                            continue;
                        }
                        boolean flag = true;
                        for(String c: IClassUtils.exclusionClassesSuper){
                            if(m.getDeclaringClass().getName().toString().toLowerCase().contains(c.toLowerCase())){
                                flag = false;
                                break;
                            }
                        }
                        if(flag == true){
                            result.put(e.getMethod(), e.getMethod().getDeclaringClass());
                            if(nnode != null){
                                temp.add(nnode);
                            }
                        }

                    }
                }
            }
            queue.addAll(temp);
        }
        HashMap<String, HashMap<IMethod, IClass>> finalRes = new HashMap<>();
        finalRes.put(concreteEp.getMethod().getName().toString(), result);
        return finalRes;
    }


    private static void writeEpToFile(String ep, String filename) {

        try {
            File file = new File(filename);
            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(file, true);

            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(ep);
            bw.newLine();
            bw.flush();
            bw.close();
            fw.close();

            System.out.println("HashMap written to file successfully!");

        } catch (IOException e) {
            //
        }
    }

    private static void writeHashMapToFile(HashMap<DefaultEntrypoint, String> _map, String filename) {
        try {
            File file = new File(filename);
            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(file);
            BufferedWriter bw = new BufferedWriter(fw);

            for (DefaultEntrypoint element : _map.keySet()) {
                bw.write(element.getMethod().getName().toString());
                bw.write("|-|");
                bw.write(_map.get(element));
                bw.newLine();
                bw.flush();
            }

            bw.close();
            fw.close();

            System.out.println("HashMap written to file successfully!");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeHashSetToFile(HashSet<String> set, String filename) {
        try {
            File file = new File(filename);
            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(file);
            BufferedWriter bw = new BufferedWriter(fw);

            for (String element : set) {
                bw.write(element);
                bw.newLine();
                bw.flush();
            }

            bw.close();
            fw.close();

            System.out.println("HashSet written to file successfully!");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static HashSet<String> convertEntryToString(HashSet<DefaultEntrypoint> entryPoints) {
        HashSet<String> eps = new HashSet<>();
        for(DefaultEntrypoint ep: entryPoints){
            eps.add(ep.getMethod().getName().toString());
        }
        return eps;
    }

    public static HashSet<DefaultEntrypoint> listFilesForFolder(final File folder, HashSet<DefaultEntrypoint> entryPoints, HashSet<String> remove) {
        HashSet<String> completed = new HashSet<>();
        for (final File fileEntry : folder.listFiles()) {
            String file_name = fileEntry.getName();
            String[] parts = file_name.split("_");
            file_name = parts[1];
            completed.add(file_name.toLowerCase());
        }

        HashSet<DefaultEntrypoint> filtered = new HashSet<>();
        for(DefaultEntrypoint ep: entryPoints){
            String name = ep.getMethod().getName().toString();
            if(!completed.contains(name.toLowerCase())){
                if(!remove.contains(name)) {
                   filtered.add(ep);
                }
            }
        }

        return filtered;
    }



//    // This is patching Method Calls, Modify it to patch FieldAccessInstructions
//    public static ArrayList<DefaultEntrypoint> patchCallGraph(CallGraph cg){
//        ArrayList<DefaultEntrypoint> newEntries = new ArrayList<DefaultEntrypoint>();
//        for (Iterator<CGNode> it = cg.iterator(); it.hasNext();) {
//            CGNode nd = it.next();
//            if(nd.getIR()==null)
//                continue;
//            else{
//                IR ir = nd.getIR();
//                if(ir.getMethod().getName().toString().contains("access$")){
//                    if(cg.getSuccNodeCount(nd)==0){
//
//                        for(SSAInstruction s : ir.getInstructions()){
//                            if (s instanceof com.ibm.wala.ssa.SSAAbstractInvokeInstruction) {
//                                com.ibm.wala.ssa.SSAAbstractInvokeInstruction call = (com.ibm.wala.ssa.SSAAbstractInvokeInstruction) s;
//                                IMethod meth = cg.getClassHierarchy().resolveMethod(call.getDeclaredTarget());
//                                DefaultEntrypoint de = new DefaultEntrypoint(meth,cg.getClassHierarchy());
//                                newEntries.add(de);
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        return newEntries;
//    }

    public static HashMap<String, Permission_Level> getPermissionLevelsFromManifest(String path) throws IOException {

        HashMap<String, Permission_Level> permissionLevelMap = new HashMap<>();

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
                        for (Permission_Level pm : Permission_Level.values()) {
                            if (pm.getValue().equals(permissionLevel)) {
    //                            PrintUtils.printBold(name + ": " + pm.getLevel());
                                permissionLevelMap.put(name, pm);
                                assigned = true;
                                break;
                            }

                            if (permissionLevel.contains(pm.getValue())) {
                                permissionLevelMap.put(name, pm);
                                assigned = true;
                                break;
                            }

                        }

                    if (!assigned) {
                        permissionLevelMap.put(name, Permission_Level.UNKNOWN);
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

    public enum Permission_Level {
        SIGNATURE_STRING("signature", 3),
        DANGEROUS_STRING("dangerous", 2),
        INSTANT_STRING("instant", 2),
        PRIVILEGED_STRING("privileged", 3),
        APPOP_STRING("appop", 3),
        DEVELOPMENT_STRING("development", 3),
        DOCUMENTER_STRING("development", 3),
        OEM_STRING("oem", 3),
        VENDOR_STRING("vendor", 3),
        VERIFIER_STRING("verifier", 3),
        PREINSTALLED_STRING("preinstalled", 3),
        PRE23_STRING("pre23", 3),
        SERENDIPITY_STRING("serendipity", 3),
        INSTALLER_STRING("installer", 3),
        NORMAL_STRING("normal", 1),
        UNKNOWN("UNKNOWN", -1),
        NORMAL("0x0", 1), // lets make normal as one, Initial value 0
        INSTANT("0x1000", 1), //
        DANGEROUS("0x1", 2),
        SIGNATURE("0x2", 3),
        UPDATE_APP_OPS_STATE("0x112", 3),
        HARMFUL_APP("0x202", 2), // need to recheck this
        SIGNATURE_WELLBEING("0x20002", 3),
        SIGNATURE_SYSTEM("0x12", 3),
        SIGNATURE_SETUP("0x802", 3),
        SIGNATURE_APPOP("0x42", 3),
        SIGNATURE_INCIDENTREPORTAPPROVER("0x100002", 3),
        SIGNATURE_SYSTEM_OR_APPOP("0x52", 3),
        SIGNATURE_SYSTEM_DEVELOPMENT("0x32", 3),
        SIGNATURE_SYSTEM_DEVELOPMENT_APPOP("0x72", 3),
        SIGNATURE_SYSTEM_VERIFIER_OEM_VENDORPRIVILEGED("0xc212", 3),
        SIGNATURE_INSTALLER_VERIFIER("0x302", 3),
        SIGNATURE_VERIFIER_CONFIGURATOR("0x80202", 3),
        SIGNATURE_INSTALLER_VERIFIER_WELLBEING("0x20302", 3),
        SIGNATURE_PREINSTALLED("0x402", 3),
        SIGNATURE_INSTALLER("0x102", 3),
        SIGNATURE_TEXTCLASSIFIER("0x10002", 3),
        SIGNATURE_APPOP_PRE23_PREINSTALLED("0x4C2", 3),
        SIGNATURE_DEVELOPMENT_APPOP_PRE23_PREINSTALLED("0x4E2", 3),
        SIGNATURE_SYSTEM_VENDORPRIVILEGED("0x8012", 3);

        private String value;
        private int level;

        Permission_Level(String value, int level) {
            this.value = value;
            this.level = level;
        }

        public String getValue() {
            return this.value;
        }

        public int getLevel() {
            return this.level;
        }

        public String getString(){
            return this.toString();
        }
    }



    public static void writeToFile(String filename, HashMap<DefaultEntrypoint, HashMap<String, ArrayList<HashMap<String, String>>>> resourceMap) {
        BufferedWriter writer = null;
        try {
            // create a new file with the given filename
            File file = new File(filename);

            // create a new FileWriter object
            FileWriter fw = new FileWriter(file);

            // create a new BufferedWriter object
            writer = new BufferedWriter(fw);

            // write the text to the file
            for(DefaultEntrypoint ep: resourceMap.keySet()){
                writer.write("EntryPoint: " + ep.getMethod().getSignature().toString() + "\n");
                HashMap<String, ArrayList<HashMap<String, String>>> resources = resourceMap.get(ep);
                for(String res: resources.keySet()){
                    writer.write("Resource: " + res);
                    for(HashMap<String, String> chars: resources.get(res)){
                        writer.write("\nFeatures:\n" + chars.get("Features") + "\nAccessControl:\n" + chars.get("AccessControl") + "\n");
                    }
                    writer.write("\n\n");
                }
                writer.write("\n");
            }

        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        } finally {
            try {
                // close the writer
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing writer: " + e.getMessage());
            }
        }
    }

    public static void writeToFileEPList(HashSet<String> eps, String _fileName){

        try{
            File file = new File(_fileName);
            FileWriter fw = new FileWriter(file);
            BufferedWriter writer = new BufferedWriter(fw);

            for(String ep: eps){
                try{
                    writer.write(ep);
                    writer.newLine();
                }catch (Exception e){
                    PrintUtils.print("Failed for the ep!");
                }
            }

        } catch (IOException e) {
            PrintUtils.print("Failed Writing EP List");
            e.printStackTrace();

        }
    }

    public static void writeToFile2(String filename, HashMap<String, ArrayList<HashMap<String, String>>> resources, Pair<String, String> entryPointWrapperAC, HashMap<String, String> epFeatures) {
        BufferedWriter writer = null;
        try {
            // create a new file with the given filename
            File file = new File(filename);

            // create a new FileWriter object
            FileWriter fw = new FileWriter(file);

            // create a new BufferedWriter object
            writer = new BufferedWriter(fw);

            // write the text to the file
            writer.write("<EP>: " + entryPointWrapperAC.fst + "\n");
            writer.write("<Wrapper_Access_Control>: " + entryPointWrapperAC.snd + "\n\n");


            for(String epKey: epFeatures.keySet()){
                writer.write(epFeatures.get(epKey) + "\n\n");
            }


            for(String res: resources.keySet()){
//                String resourceAccessControl = resources;
                writer.write("<Resource>: " + res);
                for(HashMap<String, String> chars: resources.get(res)){
                    writer.write("\n<ResourceWrapperAccessControl>: " + chars.get("ResourceWrapperAccessControl") + "\n<ResourceFeatures>:\n" + chars.get("Features") + "\n" + chars.get("AccessControl") + "\n");
                }
                writer.write("\n\n");
            }

        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        } finally {
            try {
                // close the writer
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing writer: " + e.getMessage());
            }
        }
    }

    private static void initializeFrameworkAnalyzer() throws ClassHierarchyException, IOException {
        scope = ScopeUtils.makeScope();
        cha = ClassHierarchyFactory.make(scope);
        cache = new AnalysisCacheImpl(new DexIRFactory());
    }

    private static HashSet<DefaultEntrypoint> filterEntryPoint(HashSet<DefaultEntrypoint> entryPoints, String targetEntryPoint, boolean useTarget){
        HashSet<DefaultEntrypoint> filtered = new HashSet<>();
        if(!useTarget){
            return entryPoints;
        }
        for(DefaultEntrypoint ep: entryPoints){
            if (ep.getMethod().getName().toString().equals(targetEntryPoint)) {
                filtered.add(ep);
            }
        }
//        return getRandomElements(filtered, 100);
        return filtered;
    }

    public static HashSet<DefaultEntrypoint> getRandomElements(HashSet<DefaultEntrypoint> set, int n) {
        HashSet<DefaultEntrypoint> result = new HashSet<>();
        Random rand = new Random();
        DefaultEntrypoint[] array = set.toArray(new DefaultEntrypoint[0]);

        while (result.size() < n) {
            int index = rand.nextInt(array.length);
            result.add(array[index]);
        }
        return result;
    }

//    public static DefaultEntrypoint getEntryFromMethodReference(MethodReference m){
//        try{
//            DefaultEntrypoint ep = new DefaultEntrypoint(m, cha);
//            if (ep.getMethod().getDeclaringClass().isInterface())
//                return FrameworkAccessControlDetector.getConcreteEp(ep, cha);
//            return ep;
//        }catch (UnimplementedError e){
//            return null;
//        }
//    }
}
