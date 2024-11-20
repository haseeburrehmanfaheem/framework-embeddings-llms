package com.aacr.helpers.detectors.framework;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
import com.aacr.helpers.accesscontrol.framework.AccessControlEnforcement;
import com.aacr.helpers.accesscontrol.framework.AccessControlUtils;
import com.aacr.helpers.accesscontrol.framework.PotentialMethodNameAccessControl;
import com.aacr.helpers.utils.CallGraphUtils;
import com.aacr.helpers.utils.IClassUtils;
import com.aacr.helpers.utils.InstructionUtils;
import com.aacr.helpers.utils.PrintUtils;
import com.aacr.wala.workshop.analyzers.DataCollection;
import com.aacr.wala.workshop.analyzers.FrameworkAnalyzer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class ResourceMapper_1 {


    public static void InvokeWrapper(AnalysisScope scope, ClassHierarchy cha, DefaultEntrypoint ep){

        if (ep.getMethod().getDeclaringClass().isInterface())
            ep = FrameworkAccessControlDetector.getConcreteEp(ep, cha);


        ArrayList<DefaultEntrypoint> singlePoint = new ArrayList<>();
        singlePoint.add(ep);

        try {
            CallGraph cg = CallGraphUtils.buildCallGraph(CallGraphUtils.CGType.V01CFA, scope,
                    cha, singlePoint,
                    AnalysisOptions.ReflectionOptions.NONE, null);

            if (cg == null) {
                return;
            }


            ArrayList<DefaultEntrypoint> newSet = DataCollection.patchCallGraph(cg);

            if (newSet.size() > 0) {
                singlePoint.addAll(newSet);
                try {
                    cg = CallGraphUtils.buildCallGraph(CallGraphUtils.CGType.V01CFA, scope,
                            cha, singlePoint,
                            AnalysisOptions.ReflectionOptions.NONE, null);
                } catch (Exception e) {
                    PrintUtils.print("Could not Build a call graph after addition");
                    return;
                }
            }

            InterproceduralCFG icfg = new InterproceduralCFG(cg);

            SSACFG cfg = CallGraphUtils.getNodeFromEntryPoint(cg, ep).getIR().getControlFlowGraph();
            CGNode _node = CallGraphUtils.getNodeFromEntryPoint(cg, ep);

            if(_node == null || cfg == null){
                return;
            }

            ISSABasicBlock entry = cfg.entry();

            AcmNode entryAcmNode = new AcmNode(AcmNode.NodeTypes.EntryPoint, ep.getMethod().getName().toString(), ep.getMethod().getDeclaringClass().getName().toString());
            HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> _paths = getPaths(ep, entryAcmNode, cg, cfg, icfg, entry);
            HashMap<AcmNode, ArrayList<AcmNode>> results = getProtection(_paths, entryAcmNode);

            // Only Need This and the Method Invocations
            int _entryPointProtection = getProtectionForEntryPathWrapper(_paths, entryAcmNode);

            ArrayList<String> invokes = getInvokesFromEntryPointWrapper(_paths, entryAcmNode);

            writeToFile(ep.getMethod().getName().toString() + "|-|" + ep.getMethod().getDeclaringClass().getName().toString(), _entryPointProtection, invokes, "PythonAnalysis/aosp_labels");

        }catch (Exception e){
            return;
        }


    }

    public static void writeToFile(String name, Integer protection, ArrayList<String> lines, String fileName) {
        try {
            // Create a file object
            File file = new File(fileName);

            // Create a buffered writer to write to the file
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));

            writer.newLine();
            writer.write(name + ":" + Integer.toString(protection));
            writer.newLine();
            writer.flush();
            // Write each line in the ArrayList to the file
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
                writer.flush();
            }

            // Close the writer to flush the buffer and release resources
            writer.close();
        } catch (IOException e) {
            System.out.println("Error writing file: " + e.getMessage());
        }
    }

    private static ArrayList<String> getInvokesFromEntryPointWrapper(HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> graph, AcmNode entryAcmNode) {
        ArrayList<String> ans = new ArrayList<>();
        ArrayList<ArrayList<AcmNode>> paths = graph.get(entryAcmNode);
        ArrayList<Integer> pathProtections = new ArrayList<>();
        HashSet<AcmNode> visited = new HashSet<>();

        for (ArrayList<AcmNode> path : paths) {
            ArrayList<String> pathInvokes = getInvokesFromEntryPath(graph, path, visited);
            ans.addAll(pathInvokes);
        }

        return ans;
    }

    private static ArrayList<String> getInvokesFromEntryPath(HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> graph, ArrayList<AcmNode> path, HashSet<AcmNode> visited) {
        ArrayList<String> ans = new ArrayList<>();
        for(AcmNode n : path){
            if(visited.contains(n)){
                continue;
            }
            visited.add(n);
            if (graph.containsKey(n)) {
                if (n instanceof ResourceNode) {
                    if(((ResourceNode) n).nodeType == AcmNode.NodeTypes.InvokeResource){
                        ans.add(((ResourceNode) n).resourceName);
                    }
                }

                ArrayList<ArrayList<AcmNode>> _paths = graph.get(n);
                ArrayList<String> pathInvokes = new ArrayList<>();
                for (ArrayList<AcmNode> _path : _paths) {
                    ArrayList<String> _temp = getInvokesFromEntryPath(graph, _path, visited);
                    pathInvokes.addAll(_temp);
                }

            } else {
                if (n instanceof ResourceNode) {
                    if(((ResourceNode) n).nodeType == AcmNode.NodeTypes.InvokeResource){
                        ans.add(((ResourceNode) n).resourceName);
                    }
                }
            }

        }
        return ans;
    }

    public static Pair<HashMap<String, String>, Pair<Pair<String, String>, HashMap<String, ArrayList<HashMap<String, String>>>>> DFS(AnalysisScope scope, ClassHierarchy cha, DefaultEntrypoint ep) throws Exception {
        DefaultEntrypoint concreteEp = ep;

        if (ep.getMethod().getDeclaringClass().isInterface())
            concreteEp = FrameworkAccessControlDetector.getConcreteEp(ep, cha);

        ep = concreteEp;

        ArrayList<DefaultEntrypoint> singlePoint = new ArrayList<>();
        singlePoint.add(concreteEp);

        // experiment with reflection options

        // patching the call graph and getting new entrypoints
//        ArrayList<DefaultEntrypoint> newSet = FrameworkAnalyzer.patchCallGraph(cg);
//
//        if (newSet.size() > 0) {
//            singlePoint.addAll(newSet);
//            try {
//                cg = CallGraphUtils.buildCallGraph(CallGraphUtils.CGType.V01CFA, scope,
//                        cha, singlePoint,
//                        AnalysisOptions.ReflectionOptions.NONE, null);
//            } catch (Exception e) {
//                PrintUtils.print("Could not Build a call graph after addition");
//                return null;
//            }
//        }

        try {
            CallGraph cg = CallGraphUtils.buildCallGraph(CallGraphUtils.CGType.V01CFA, scope,
                    cha, singlePoint,
                    AnalysisOptions.ReflectionOptions.NONE, null);

            ArrayList<DefaultEntrypoint> newSet = DataCollection.patchCallGraph(cg);

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

//            ArrayList<DefaultEntrypoint> _patch = FrameworkAnalyzer.patchCallGraph(_cg);

//            CallGraph cg = CallGraphUtils.buildCallGraph(CallGraphUtils.CGType.V01CFA, scope,
//                    cha, _patch,
//                    AnalysisOptions.ReflectionOptions.NONE, null);

            InterproceduralCFG icfg = new InterproceduralCFG(cg);

            SSACFG cfg = CallGraphUtils.getNodeFromEntryPoint(cg, concreteEp).getIR().getControlFlowGraph();
            ISSABasicBlock entry = cfg.entry();

            CGNode _node = CallGraphUtils.getNodeFromEntryPoint(cg, ep);
//            SymbolTable st = _node.getIR().getSymbolTable();


            AcmNode entryAcmNode = new AcmNode(AcmNode.NodeTypes.EntryPoint, ep.getMethod().getName().toString(), ep.getMethod().getDeclaringClass().getName().toString());
            HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> _paths = getPaths(concreteEp, entryAcmNode, cg, cfg, icfg, entry);
            HashMap<AcmNode, ArrayList<AcmNode>> results = getProtection(_paths, entryAcmNode);

//        HashMap<String, String> epFeatures = getEpFeatures(entryAcmNode, _paths, cg, concreteEp, icfg, cfg, st);
            HashMap<String, String> epFeatures = new HashMap<>();

            int _entryPointProtection = getProtectionForEntryPathWrapper(_paths, entryAcmNode);

            HashMap<String, ArrayList<HashMap<String, String>>> res = new HashMap<>();

            for (AcmNode key : results.keySet()) {
                String _key = "";
                String _features = "";
                String _protection = "";
                if (key instanceof ResourceNode) {
                    _key = ((ResourceNode) key).createKey();
                    _features = ((ResourceNode) key).createFeatures();
                }
                ArrayList<AcmNode> protection = results.get(key);

                int resourceAccessControl = getResourceAccessControl(protection);

                ArrayList<String> _protections = new ArrayList<>();

                for (AcmNode node : protection) {
                    _protections.add(((AccessControlNode) node).getAccessControlString());
                }
                _protection = joinStrings(_protections, "\n");
                HashMap<String, String> _temp = new HashMap<>();
                _temp.put("ResourceWrapperAccessControl", Integer.toString(resourceAccessControl));
                _temp.put("Features", _features);

                _temp.put("AccessControl", _protection);

                if (!res.containsKey(_key)) {
                    res.put(_key, new ArrayList<HashMap<String, String>>());
                }
                res.get(_key).add(_temp);
            }

            return Pair.make(epFeatures, Pair.make(Pair.make(ep.getMethod().getSignature(), Integer.toString(_entryPointProtection)), res));
        }catch (Exception e){
            return null;
        }
    }

    private static HashMap<String, String> getEpFeatures(AcmNode entryAcmNode, HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> paths, CallGraph cg, DefaultEntrypoint concreteEp, InterproceduralCFG icfg, SSACFG cfg, SymbolTable st) {

        HashMap<String, String> epFeatures = new HashMap<>();
        String controlFlowEdges = getEPControlFlowEdges(concreteEp, cg, cfg, st);
        epFeatures.put("epCFG", controlFlowEdges);

        // ToDo: EP - ControlFlowFeatures - take from cfg

        /*
        ControlFlowGraph
        1 - NodeName
        2 - NodeName
        (1, 2) - ControlFlowEdge
         */

//        String resourcePathControlFlow = getResourceControlFlow(paths, entryAcmNode, cg, cfg, st);
//        epFeatures.put("resCFG", resourcePathControlFlow);


        // ToDo: DataFlowFeatures - need to do another analysis

        /*
        DataFlowGraph
        1 - NodeName
        2 - NodeName
        (1, 2) - DataFlowEdge
         */


        return epFeatures;

    }

    private static String getResourceControlFlow(HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> paths, AcmNode entryNode, CallGraph cg, SSACFG cfg, SymbolTable st) {

        String ResourceControlFlowEdges = "ResourceControlFlowEdges\n";
        return ResourceControlFlowEdges;


    }

    private static String getEPControlFlowEdges(DefaultEntrypoint concreteEp, CallGraph cg, SSACFG cfg, SymbolTable st) {

        HashSet<String> nodeMap = new HashSet<>();
        HashSet<String> edgeMap = new HashSet<>();

        ISSABasicBlock start = cfg.entry();
        HashSet<ISSABasicBlock> visited = new HashSet<>();
        Stack<ISSABasicBlock> dfsStack = new Stack<>();

//        dfsStack.add(start);

        for (ISSABasicBlock next : cfg.getNormalSuccessors(start)) {
            dfsStack.add(next);
        }


        while (!dfsStack.isEmpty()) {
            // get the top element
            ISSABasicBlock top = dfsStack.pop();

            if (top.isExitBlock() || visited.contains(top)) {
                continue;
            }

            visited.add(top);
            // process the top block
            ArrayList<SSAInstruction> instructions = new ArrayList<>();


            // filtering acecss checks and other useless methods.
            for (Iterator<SSAInstruction> it = top.iterator(); it.hasNext(); ) {
                SSAInstruction s = it.next();
                if (s instanceof SSAAbstractInvokeInstruction) {
                    if (!AccessControlUtils.isAccessCheckInvoke(((SSAAbstractInvokeInstruction) s)) &&
                            !IClassUtils.isExclusionMethod((SSAAbstractInvokeInstruction) s) &&
                            !IClassUtils.isClassExclusionSuper(((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getDeclaringClass().getName().toString())) {
                        instructions.add(s);
                    }
                } else {
                    instructions.add(s);
                }
            }

            String prevHash = "000";
            String prevNode = prevHash + " =  FalseNode";

            if (!instructions.isEmpty()) {
                SSAInstruction first = instructions.get(0);


                prevHash = Integer.toString(first.hashCode());
                prevNode = prevHash + " = " + first.toString(st);
                nodeMap.add(prevNode);

                try {
                    for (SSAInstruction n : instructions.subList(1, instructions.size() - 1)) {
                        String curHash = Integer.toString(n.hashCode());
                        String curNode = curHash + " = " + n.toString(st);

                        String edge = prevNode + " -> " + curNode;
                        nodeMap.add(curNode);
                        edgeMap.add(edge);

                        prevHash = curHash;
                        prevNode = curNode;
                    }
                } catch (Exception e) {
                    // not enough instruction ... just pass
                }

            }

            // process the children
            for (ISSABasicBlock next : cfg.getNormalSuccessors(top)) {

                // add all edges from prev to the next blocks
                SSAInstruction firstInstruction = null;
                for (Iterator<SSAInstruction> it = next.iterator(); it.hasNext(); ) {
                    SSAInstruction i = it.next();
                    firstInstruction = i;
                    break;
                }

                if (firstInstruction != null) {
                    if (firstInstruction instanceof SSAAbstractInvokeInstruction) {
//                        Filtering access checks and other useless methods
                        if (!AccessControlUtils.isAccessCheckInvoke(((SSAAbstractInvokeInstruction) firstInstruction)) &&
                                !IClassUtils.isExclusionMethod((SSAAbstractInvokeInstruction) firstInstruction) &&
                                !IClassUtils.isClassExclusionSuper(((SSAAbstractInvokeInstruction) firstInstruction).getDeclaredTarget().getDeclaringClass().getName().toString())) {

                            String curHash = Integer.toString(firstInstruction.hashCode());
                            String curNode = curHash + " = " + firstInstruction.toString(st);

                            String edge = prevHash + " -> " + curHash;
                            edgeMap.add(edge);
                            nodeMap.add(curNode);
                        }
                    } else {
                        String curHash = Integer.toString(firstInstruction.hashCode());
                        String curNode = curHash + " = " + firstInstruction.toString(st);

                        String edge = prevHash + " -> " + curHash;
                        edgeMap.add(edge);
                        nodeMap.add(curNode);
                    }

                }
                dfsStack.add(next);
            }

        }

        String controlFlowEdges = "ControlFlowGraph\n";
        for (String node : nodeMap) {
            controlFlowEdges += node + "\n";
        }
        for (String edge : edgeMap) {
            controlFlowEdges += edge + "\n";
        }
        return controlFlowEdges;

    }

    private static int getResourceAccessControl(ArrayList<AcmNode> protection) {
        ArrayList<Integer> _pathProtection = new ArrayList<>();
        for (AcmNode node : protection) {
            int p = getProtectionLevelFromNode(node);
            _pathProtection.add(p);
        }
        return resolvePathProtectionAND(_pathProtection);
    }

    private static int getProtectionForEntryPathWrapper(HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> graph, AcmNode entryAcmNode) {
        ArrayList<ArrayList<AcmNode>> paths = graph.get(entryAcmNode);
        ArrayList<Integer> pathProtections = new ArrayList<>();
        HashSet<AcmNode> visited = new HashSet<>();

        for (ArrayList<AcmNode> path : paths) {
            int p = getProtectionForEntryPath(graph, path, visited);
            pathProtections.add(p);
        }

        return resolvePathProtectionOR(pathProtections);
    }

    private static int getProtectionForEntryPath(HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> graph, ArrayList<AcmNode> path, HashSet<AcmNode> visited) {

        // get Protection along each path
        ArrayList<Integer> accessControlPath = new ArrayList<>();

        for (AcmNode n : path) {
            // mark as visited to avoid cycles
            if(visited.contains(n)){
                continue;
            }

            visited.add(n);
            if (graph.containsKey(n)) {
                if (n instanceof AccessControlNode) {
                    // adding curAccess if the current node is access control node
                    accessControlPath.add(getProtectionLevelFromNode(n));
                }

                ArrayList<ArrayList<AcmNode>> _paths = graph.get(n);
                ArrayList<Integer> pathProtections = new ArrayList<>();
                for (ArrayList<AcmNode> _path : _paths) {
                    int p = getProtectionForEntryPath(graph, _path, visited);
                    pathProtections.add(p);
                }
                int pathProtectionAccess = resolvePathProtectionOR(pathProtections);
                accessControlPath.add(pathProtectionAccess);

            } else {
                if (n instanceof AccessControlNode) {
                    accessControlPath.add(getProtectionLevelFromNode(n));
                }
            }

        }

        return resolvePathProtectionAND(accessControlPath);
    }

    private static int resolvePathProtectionOR(ArrayList<Integer> accessControlPath) {

        int temp = -1;
        for (Integer i : accessControlPath) {
            temp = FrameworkAccessControlDetector.OR(temp, i);
        }
        return temp;
    }

    private static int resolvePathProtectionAND(ArrayList<Integer> accessControlPath) {

        int temp = -1;
        for (Integer i : accessControlPath) {
            temp = FrameworkAccessControlDetector.AND(temp, i);
        }
        return temp;
    }

    private static int getProtectionLevelFromNode(AcmNode n) {

        if (n instanceof AccessControlNode) {
            return ((AccessControlNode) n).accessLevel;
        }
        return -1;
    }


    public static HashMap<AcmNode, ArrayList<AcmNode>> getProtection(HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> graph, AcmNode node) {
        HashMap<AcmNode, ArrayList<AcmNode>> res = new HashMap<>();

        ArrayList<ArrayList<AcmNode>> paths = graph.get(node);
        ArrayList<AcmNode> parentProtection = new ArrayList<>();
        for (ArrayList<AcmNode> path : paths) {
            HashMap<AcmNode, ArrayList<AcmNode>> protections = getProtectionHelper(graph, path, parentProtection);
            res.putAll(protections);
        }
        return res;
    }

    private static HashMap<AcmNode, ArrayList<AcmNode>> getProtectionHelper(HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> graph, ArrayList<AcmNode> path, ArrayList<AcmNode> parentProtection) {
        HashMap<AcmNode, ArrayList<AcmNode>> res = new HashMap<>();
        for (AcmNode node : path) {
            if (node instanceof AccessControlNode) {
                parentProtection.add(node);
                if (node.nodeType == AcmNode.NodeTypes.AccessControlPotential) {
                    if (graph.containsKey(node)) {
                        ArrayList<ArrayList<AcmNode>> paths = graph.get(node);
                        for (ArrayList<AcmNode> _path : paths) {
                            HashMap<AcmNode, ArrayList<AcmNode>> protections = getProtectionHelper(graph, _path, parentProtection);
                            res.putAll(protections);
                        }
                    }
                }
            } else if (node instanceof ResourceNode) {
                res.put(node, parentProtection);
                if (graph.containsKey(node)) {
                    ArrayList<ArrayList<AcmNode>> paths = graph.get(node);
                    for (ArrayList<AcmNode> _path : paths) {
                        HashMap<AcmNode, ArrayList<AcmNode>> protections = getProtectionHelper(graph, _path, parentProtection);
                        res.putAll(protections);
                    }
                }
            }
        }
        return res;
    }

    public static HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> combinePaths(HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> graph, AcmNode node) {
        if (!graph.containsKey(node)) {
            // Base case: if the node has no outgoing edges, return its path
            HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> result = new HashMap<>();
            ArrayList<ArrayList<AcmNode>> paths = new ArrayList<>();
            paths.add(new ArrayList<>());
            result.put(node, paths);
            return result;
        }
        HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> result = new HashMap<>();
        ArrayList<ArrayList<AcmNode>> combinedPaths = new ArrayList<>();
        for (ArrayList<AcmNode> childPath : graph.get(node)) {
            // Recursively combine the paths from each child node
            AcmNode child = childPath.get(0);
            ArrayList<ArrayList<AcmNode>> childPaths = combinePaths(graph, child).get(child);
            for (ArrayList<AcmNode> path : childPaths) {
                ArrayList<AcmNode> combinedPath = new ArrayList<>(path);
                combinedPath.addAll(childPath.subList(1, childPath.size()));
                combinedPaths.add(combinedPath);
            }
        }
        // Combine the paths that share a common intermediate node
        ArrayList<ArrayList<AcmNode>> paths = new ArrayList<>();
        for (ArrayList<AcmNode> path : combinedPaths) {
            ArrayList<AcmNode> combinedPath = new ArrayList<>();
            AcmNode lastNode = null;
            for (AcmNode n : path) {
                if (lastNode != null && graph.get(lastNode).size() == 1 && graph.get(lastNode).get(0).get(0) == n) {
                    combinedPath.set(combinedPath.size() - 1, n);
                } else {
                    combinedPath.add(n);
                }
                lastNode = n;
            }
            paths.add(combinedPath);
        }
        // Combine all the paths into a single path starting at the entry node
        ArrayList<AcmNode> combinedPath = new ArrayList<>();
        for (ArrayList<AcmNode> path : paths) {
            combinedPath.addAll(path);
        }
        result.put(node, new ArrayList<>(Arrays.asList(combinedPath)));
        return result;
    }

    public static HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> getPaths(DefaultEntrypoint ep, AcmNode entryAcmNode, CallGraph cg, SSACFG cfg, InterproceduralCFG icfg, ISSABasicBlock start) throws Exception {
        HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> paths = new HashMap<>();
        HashSet<Integer> visited = new HashSet<>();
        HashSet<DefaultEntrypoint> visitedNodes = new HashSet<>();
        ArrayList<AcmNode> path = new ArrayList<>();

        visitedNodes.add(ep);
        dfsPaths(cg, cfg, icfg, start, visited, visitedNodes, path, paths, entryAcmNode, ep);
        return paths;
    }

    // Need the resource Nodes to have more features.
    private static void dfsPaths(CallGraph cg,
                                 SSACFG cfg,
                                 InterproceduralCFG icfg,
                                 ISSABasicBlock start,
                                 HashSet<Integer> visited,
                                 HashSet<DefaultEntrypoint> visitedNodes,
                                 ArrayList<AcmNode> path,
                                 HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> paths,
                                 AcmNode parentNode, DefaultEntrypoint ep) throws Exception {
        // mark visited
        if(visited.contains(start.getNumber())){
            return;
        }
        visited.add(start.getNumber());

        CGNode node = CallGraphUtils.getNodeFromEntryPoint(cg, ep);
        BasicBlockInContext<ISSABasicBlock> bb = new BasicBlockInContext<ISSABasicBlock>(node, start);
        SymbolTable st = node.getIR().getSymbolTable();
        DefUse du = node.getDU();

        if (!paths.containsKey(parentNode)) {
            paths.put(parentNode, new ArrayList<ArrayList<AcmNode>>());
        }


        // add all instructions from the block
        for (Iterator<SSAInstruction> it = start.iterator(); it.hasNext(); ) {
            SSAInstruction s = it.next();

            if (s instanceof SSAConditionalBranchInstruction) {
                Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> pe = FrameworkAccessControlDetector.analyzeConditionalBranchInstruction(s, icfg, bb, st, du);
                if (pe == null) {
                    // no access check just a conditional branch statement
                    // skip
                } else {
                    AccessControlNode acNode = new AccessControlNode(s, AcmNode.NodeTypes.AccessControl, parentNode.methodName, parentNode.className, pe.snd);


                    if (pe.snd instanceof PotentialMethodNameAccessControl) {

                        acNode = new AccessControlNode(s, AcmNode.NodeTypes.AccessControlPotential, parentNode.methodName, parentNode.className, pe.snd);
                        // add this node for analysis
                        try {
                            DefaultEntrypoint _ep = DataCollection.getEntryFromMethodReference(((SSAAbstractInvokeInstruction) s).getDeclaredTarget());
//                            if(_ep != null && !visitedNodes.contains(_ep)){
                            if (_ep != null) {
//                                visitedNodes.add(_ep);

                                ArrayList<DefaultEntrypoint> singlePoint = new ArrayList<>();
                                singlePoint.add(_ep);

                                CallGraph _cg = CallGraphUtils.buildCallGraph(CallGraphUtils.CGType.V01CFA, FrameworkAnalyzer.scope,
                                        FrameworkAnalyzer.cha, singlePoint,
                                        AnalysisOptions.ReflectionOptions.NONE, null);

                                InterproceduralCFG _icfg = new InterproceduralCFG(_cg);

                                CGNode _node = CallGraphUtils.getNodeFromEntryPoint(cg, _ep);

                                if (_node != null) {
                                    SSACFG _cfg = _node.getIR().getControlFlowGraph();
                                    ISSABasicBlock _start = _cfg.entry();
                                    dfsPathsPotential(_cg, _cfg, _icfg, _start, visited, visitedNodes, new ArrayList<AcmNode>(), paths, acNode, _ep);
                                }

                            }
                        } catch (Exception e) {
                            // pass
                        }

                    }

                    path.add(acNode);
                }

            }
            else if (s instanceof SSAFieldAccessInstruction) {

                Pair<HashMap<String, Boolean>, HashMap<String, String>> features = getFeaturesForResourceNode(s, icfg, cfg, parentNode, ep, cg);
                String fieldType = "";

                // ToDo: Can we check if the field is arraytype or int type or string type etc. Do we want to?

                if (((SSAFieldAccessInstruction) s).getDeclaredFieldType().getClassLoader().getName().toString().toLowerCase().contains("primordial")) {
                    fieldType = "Primordial";
                } else {
                    fieldType = ((SSAFieldAccessInstruction) s).getDeclaredFieldType().getName().toString();
                }

                AcmNode resNode = new ResourceNode(s, AcmNode.NodeTypes.FieldAccessResource, parentNode.methodName, parentNode.className, fieldType + " " + ((SSAFieldAccessInstruction) s).getDeclaredField().getName().toString(), features.fst, features.snd);

                path.add(resNode);

            }
            else if (s instanceof SSAAbstractInvokeInstruction) {

                // check if access control or resource
                if (IClassUtils.isExclusionMethod((SSAAbstractInvokeInstruction) s) ||
                        IClassUtils.isClassExclusionSuper(((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getDeclaringClass().getName().toString())) {
                    continue;
                }

                Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> pe = FrameworkAccessControlDetector.analyzeInvokeInstruction(((SSAAbstractInvokeInstruction) s), du, icfg, bb, st);

                if (pe == null) {
                    // not an access control so has to be resource

                    // if we cannot get the entry point from the method
                    try {
                        DefaultEntrypoint _ep = DataCollection.getEntryFromMethodReference(((SSAAbstractInvokeInstruction) s).getDeclaredTarget());
                        Pair<HashMap<String, Boolean>, HashMap<String, String>> features = getFeaturesForResourceNode(s, icfg, cfg, parentNode, ep, cg);


                        if (_ep != null && !visitedNodes.contains(_ep)) {
//                        if(_ep != null){
//                            visitedNodes.add(_ep);

                            if (!IClassUtils.isClassExclusionSuper(_ep.getMethod().getDeclaringClass().getName().toString())) {
                                AcmNode resNode = new ResourceNode(s, AcmNode.NodeTypes.InvokeResource, parentNode.methodName, parentNode.className, _ep.getMethod().getDeclaringClass().getName().toString() + " " + _ep.getMethod().getName().toString(), features.fst, features.snd);
                                ArrayList<DefaultEntrypoint> singlePoint = new ArrayList<>();
                                singlePoint.add(_ep);

                                CallGraph _cg = CallGraphUtils.buildCallGraph(CallGraphUtils.CGType.V01CFA, FrameworkAnalyzer.scope,
                                        FrameworkAnalyzer.cha, singlePoint,
                                        AnalysisOptions.ReflectionOptions.NONE, null);

                                InterproceduralCFG _icfg = new InterproceduralCFG(_cg);


                                CGNode _node = CallGraphUtils.getNodeFromEntryPoint(cg, _ep);
                                if (_node != null) {
                                    SSACFG _cfg = _node.getIR().getControlFlowGraph();
                                    ISSABasicBlock _start = _cfg.entry();

                                    dfsPaths(_cg, _cfg, _icfg, _start, visited, visitedNodes, new ArrayList<AcmNode>(), paths, resNode, _ep);

                                }

                                path.add(resNode);

                            }


                        }


                    } catch (Exception e) {
                        // just add the resource node
                        Pair<HashMap<String, Boolean>, HashMap<String, String>> features = getFeaturesForResourceNode(s, icfg, cfg, parentNode, ep, cg);
                        AcmNode resNode = new ResourceNode(s, AcmNode.NodeTypes.InvokeResource, parentNode.methodName, parentNode.className, ((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getSignature().toString(), features.fst, features.snd);
                        path.add(resNode);
                    }

                } else {
                    // pe != null
                    // so there is an access control in the block.
                    // get that access control
                    AccessControlNode acNode = new AccessControlNode(s, AcmNode.NodeTypes.AccessControl, parentNode.methodName, parentNode.className, pe.snd);

                    if (pe.snd instanceof PotentialMethodNameAccessControl) {
                        acNode = new AccessControlNode(s, AcmNode.NodeTypes.AccessControlPotential, parentNode.methodName, parentNode.className, pe.snd);
                        // add this node for analysis


                        DefaultEntrypoint _ep = DataCollection.getEntryFromMethodReference(((SSAAbstractInvokeInstruction) s).getDeclaredTarget());

                        if (_ep != null && !visitedNodes.contains(_ep)) {
//                            if(_ep != null){
//                            visitedNodes.add(_ep);

                            ArrayList<DefaultEntrypoint> singlePoint = new ArrayList<>();
                            singlePoint.add(_ep);

                            CallGraph _cg = CallGraphUtils.buildCallGraph(CallGraphUtils.CGType.V01CFA, FrameworkAnalyzer.scope,
                                    FrameworkAnalyzer.cha, singlePoint,
                                    AnalysisOptions.ReflectionOptions.NONE, null);

                            InterproceduralCFG _icfg = new InterproceduralCFG(_cg);

                            CGNode _node = CallGraphUtils.getNodeFromEntryPoint(cg, _ep);

                            if (_node != null) {
                                SSACFG _cfg = _node.getIR().getControlFlowGraph();
                                ISSABasicBlock _start = _cfg.entry();


                                // replace with a different method for access check finding ...

                                dfsPathsPotential(_cg, _cfg, _icfg, _start, visited, visitedNodes, new ArrayList<AcmNode>(), paths, acNode, _ep);

//                                    dfsPaths(_cg, _cfg, _icfg, _start, visited, visitedNodes, new ArrayList<AcmNode>(), paths, acNode, _ep);
                            }

                        }


                    }

                    path.add(acNode);
                }

            }
        }

        for (ISSABasicBlock block : cfg.getNormalSuccessors(start)) {
            if (block.isExitBlock()) {
                if (!paths.get(parentNode).contains(path)) {
                    paths.get(parentNode).add(path);
                }

            } else if (!visited.contains(block.getNumber())) {
                dfsPaths(cg, cfg, icfg, block, visited, visitedNodes, path, paths, parentNode, ep);
            }
        }

        // why would you remove start from visited?

        visited.remove(start.getNumber());
    }

    private static void dfsPathsPotential(CallGraph cg, SSACFG cfg, InterproceduralCFG icfg, ISSABasicBlock start, HashSet<Integer> visited, HashSet<DefaultEntrypoint> visitedNodes, ArrayList<AcmNode> path, HashMap<AcmNode, ArrayList<ArrayList<AcmNode>>> paths, AccessControlNode parentNode, DefaultEntrypoint ep) throws Exception {

        if(visitedNodes.contains(ep)){
            return;
        }
        visitedNodes.add(ep);

        if (!paths.containsKey(parentNode)) {
            paths.put(parentNode, new ArrayList<ArrayList<AcmNode>>());
        }

        CGNode node = CallGraphUtils.getNodeFromEntryPoint(cg, ep);
//        BasicBlockInContext<ISSABasicBlock> bb = new BasicBlockInContext<ISSABasicBlock>(node, start);
        SymbolTable st = node.getIR().getSymbolTable();
        DefUse du = node.getDU();

        SSAInstruction[] _ins = cfg.getInstructions();
        for (SSAInstruction s: _ins){
            if(s instanceof SSAAbstractInvokeInstruction) {
                BasicBlockInContext<ISSABasicBlock> bb = InstructionUtils.getBBForInstruction(icfg, s);
                Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> pe = FrameworkAccessControlDetector.analyzeInvokeInstruction(((SSAAbstractInvokeInstruction) s), du, icfg, bb, st);

                if (pe != null) {
                        AccessControlNode acNode = new AccessControlNode(s, AcmNode.NodeTypes.AccessControl, parentNode.methodName, parentNode.className, pe.snd);
//                        TODO: Handle Other Access Control Scenarios
                        if (pe.snd instanceof PotentialMethodNameAccessControl) {
                            acNode = new AccessControlNode(s, AcmNode.NodeTypes.AccessControlPotential, parentNode.methodName, parentNode.className, pe.snd);
                            // add this node for analysis

                            DefaultEntrypoint _ep = DataCollection.getEntryFromMethodReference(((SSAAbstractInvokeInstruction) s).getDeclaredTarget());
                            if (_ep != null && !visitedNodes.contains(_ep)) {
        //                            if(_ep != null){
//                                visitedNodes.add(_ep);

                                ArrayList<DefaultEntrypoint> singlePoint = new ArrayList<>();
                                singlePoint.add(_ep);

                                CallGraph _cg = CallGraphUtils.buildCallGraph(CallGraphUtils.CGType.V01CFA, FrameworkAnalyzer.scope,
                                        FrameworkAnalyzer.cha, singlePoint,
                                        AnalysisOptions.ReflectionOptions.NONE, null);

                                InterproceduralCFG _icfg = new InterproceduralCFG(_cg);

                                CGNode _node = CallGraphUtils.getNodeFromEntryPoint(cg, _ep);

                                if (_node != null) {
                                    SSACFG _cfg = _node.getIR().getControlFlowGraph();
                                    ISSABasicBlock _start = _cfg.entry();

                                    // replace with a different method for access check finding ...

                                    dfsPathsPotential(_cg, _cfg, _icfg, _start, visited, visitedNodes, new ArrayList<AcmNode>(), paths, acNode, _ep);
                                }

                            }


                    }
                    path.add(acNode);
                    if(!paths.get(parentNode).contains(path)){
                        paths.get(parentNode).add(path);
                    }

                }

            }else if(s instanceof SSAConditionalBranchInstruction){
                BasicBlockInContext<ISSABasicBlock> bb = InstructionUtils.getBBForInstruction(icfg, s);
                Pair<BasicBlockInContext<ISSABasicBlock>, AccessControlEnforcement> pe = FrameworkAccessControlDetector.analyzeConditionalBranchInstruction(((SSAConditionalBranchInstruction) s), icfg, bb, st, du);


                if (pe != null) {
                    AccessControlNode acNode = new AccessControlNode(s, AcmNode.NodeTypes.AccessControl, parentNode.methodName, parentNode.className, pe.snd);
                    if (pe.snd instanceof PotentialMethodNameAccessControl) {
                        acNode = new AccessControlNode(s, AcmNode.NodeTypes.AccessControlPotential, parentNode.methodName, parentNode.className, pe.snd);
                        // add this node for analysis


                        DefaultEntrypoint _ep = DataCollection.getEntryFromMethodReference(((SSAAbstractInvokeInstruction) s).getDeclaredTarget());
                        if (_ep != null && !visitedNodes.contains(_ep)) {
                            //                            if(_ep != null){
//                            visitedNodes.add(_ep);

                            ArrayList<DefaultEntrypoint> singlePoint = new ArrayList<>();
                            singlePoint.add(_ep);

                            CallGraph _cg = CallGraphUtils.buildCallGraph(CallGraphUtils.CGType.V01CFA, FrameworkAnalyzer.scope,
                                    FrameworkAnalyzer.cha, singlePoint,
                                    AnalysisOptions.ReflectionOptions.NONE, null);

                            InterproceduralCFG _icfg = new InterproceduralCFG(_cg);

                            CGNode _node = CallGraphUtils.getNodeFromEntryPoint(cg, _ep);

                            if (_node != null) {
                                SSACFG _cfg = _node.getIR().getControlFlowGraph();
                                ISSABasicBlock _start = _cfg.entry();


                                // replace with a different method for access check finding ...

                                dfsPathsPotential(_cg, _cfg, _icfg, _start, visited, visitedNodes, new ArrayList<AcmNode>(), paths, acNode, _ep);
                            }

                        }


                    }
                    path.add(acNode);
                    if(!paths.get(parentNode).contains(path)){
                        paths.get(parentNode).add(path);
                    }
                }
            }
        }

    }

    private static Pair<HashMap<String, Boolean>, HashMap<String, String>> getFeaturesForResourceNode(SSAInstruction s, InterproceduralCFG icfg, SSACFG cfg, AcmNode parent, DefaultEntrypoint ep, CallGraph cg) throws InvalidClassFileException {

        boolean parameterDataflow = isParameterDataFlow(s, cfg, ep, cg);
        boolean returnDataflow = isReturnDataFlow(s, cfg, ep, cg);

        HashMap<String, Boolean> booleanFeatures = new HashMap<>();
        HashMap<String, String> stringFeature = new HashMap<>();

        stringFeature.put("id_hash", Integer.toString(s.hashCode()));
        stringFeature.put("parentMethod", parent.methodName);
        stringFeature.put("parentClass", parent.className);

        booleanFeatures.put("ParameterDataFlow", parameterDataflow);
        booleanFeatures.put("ReturnDataFlow", returnDataflow);

        // is there a control flow from the param to the instruction?
        if (s instanceof SSAReturnInstruction) {
            SSAReturnInstruction _temp = ((SSAReturnInstruction) s);
            // skip this return statements
            if (_temp.returnsVoid()) {
                // do nothing
            } else {

            }


        } else if (s instanceof SSAAbstractInvokeInstruction) {
            MethodReference m = ((SSAAbstractInvokeInstruction) s).getDeclaredTarget();

            try {
                IMethod method = DataCollection.getEntryFromMethodReference(m).getMethod();

                String name = ((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getName().toString();
                String returnType = ((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getReturnType().getName().toString();
                if (((SSAAbstractInvokeInstruction) s).getDeclaredTarget().getReturnType().getClassLoader().getName().toString().toLowerCase().contains("primordial")) {
                    returnType = "primordial";
                }
                int numParams = m.getNumberOfParameters();
                String paramTypes = "";
                for (int i = 0; i < numParams; i++) {
                    String _type = m.getParameterType(i).getName().toString();
                    if (m.getParameterType(i).getClassLoader().getName().toString().contains("primordial")) {
                        _type = "primordial";
                    }
                    paramTypes += _type;
                    paramTypes += " ";
                }

                booleanFeatures.put("isInvoke", true);
                booleanFeatures.put("isFieldAccess", false);
                booleanFeatures.put("isAbstract", method.isAbstract());
                booleanFeatures.put("isAnnotation", method.isAnnotation());
                booleanFeatures.put("isBridge", method.isBridge());
                booleanFeatures.put("isClinit", method.isClinit());
                booleanFeatures.put("isFinal", method.isFinal());
                booleanFeatures.put("isInit", method.isInit());
                booleanFeatures.put("isModule", method.isModule());
                booleanFeatures.put("isNative", method.isNative());
                booleanFeatures.put("isPrivate", method.isPrivate());
                booleanFeatures.put("isProtected", method.isProtected());
                booleanFeatures.put("isPublic", method.isPublic());
                booleanFeatures.put("isSynchronized", method.isSynchronized());
                booleanFeatures.put("isSynthetic", method.isSynthetic());
                booleanFeatures.put("isEnum", method.isEnum());
                booleanFeatures.put("isStatic", method.isStatic());
                booleanFeatures.put("hasExceptionHandler", method.hasExceptionHandler());

                stringFeature.put("invokeName", name);
                stringFeature.put("returnType", returnType);
                stringFeature.put("numParams", Integer.toString(numParams));
                stringFeature.put("paramType", paramTypes);

                try {
                    TypeReference[] exc = method.getDeclaredExceptions();
                    String _typeRef = "";

                    for (TypeReference t : exc) {
                        _typeRef += t.getName().toString() + " ";
                    }

                    if (_typeRef == "") {
                        _typeRef = "NONE";
                    }

                    stringFeature.put("exceptions", _typeRef);
                } catch (Exception e) {
                    stringFeature.put("exceptions", "NONE");
                }

                stringFeature.put("selector", method.getSelector().toString());
                stringFeature.put("paramType", paramTypes);

            } catch (Exception e) {
                // pass
            }

        } else if (s instanceof SSAFieldAccessInstruction) {
            String _accessType = "";
            String fieldType = "";
            String fieldName = ((SSAFieldAccessInstruction) s).getDeclaredField().getName().toString();

            SSAFieldAccessInstruction fieldIns = ((SSAFieldAccessInstruction) s);

            if (s instanceof SSAGetInstruction) {
                _accessType = "get";
            } else if (s instanceof SSAPutInstruction) {
                _accessType = "put";
            }

            if (((SSAFieldAccessInstruction) s).getDeclaredFieldType().getClassLoader().getName().toString().toLowerCase().contains("primordial")) {
                fieldType = "Primordial";
            } else {
                fieldType = ((SSAFieldAccessInstruction) s).getDeclaredFieldType().getName().toString();
            }

            booleanFeatures.put("isInvoke", false);
            booleanFeatures.put("isFieldAccess", true);

            booleanFeatures.put("isPEI", fieldIns.isPEI());
            booleanFeatures.put("isStatic", fieldIns.isStatic());
            booleanFeatures.put("hasDef", fieldIns.hasDef());
            booleanFeatures.put("isFallThrough", fieldIns.isFallThrough());


            stringFeature.put("fieldType", fieldType);
            stringFeature.put("accessType", _accessType);
            stringFeature.put("fieldName", fieldName);
            try {
                ArrayList<TypeReference> exc = (ArrayList<TypeReference>) fieldIns.getExceptionTypes();
                String _typeRef = "";

                for (TypeReference t : exc) {
                    _typeRef += t.getName().toString() + " ";
                }

                if (_typeRef == "") {
                    _typeRef = "NONE";
                }

                stringFeature.put("exceptions", _typeRef);

            } catch (Exception e) {
                stringFeature.put("exceptions", "NONE");
            }
            stringFeature.put("numDefs", Integer.toString(fieldIns.getNumberOfDefs()));
            stringFeature.put("numUses", Integer.toString(fieldIns.getNumberOfUses()));
        }

        return Pair.make(booleanFeatures, stringFeature);
    }

    private static boolean isReturnDataFlow(SSAInstruction s, SSACFG cfg, DefaultEntrypoint ep, CallGraph cg) {
        int nDefs = s.getNumberOfDefs();
        HashSet<Integer> defDeps = new HashSet<>();

        CGNode node = CallGraphUtils.getNodeFromEntryPoint(cg, ep);

        SymbolTable st = node.getIR().getSymbolTable();


        for (int d = 0; d < nDefs; d++) {
            int def = s.getDef(d);
            defDeps.add(def);
        }

        ISSABasicBlock start = cfg.entry();

        HashSet<ISSABasicBlock> visited = new HashSet<>();
        Stack<ISSABasicBlock> dfsStack = new Stack<>();

        dfsStack.push(start);

        while (!dfsStack.isEmpty()) {

            ISSABasicBlock top = dfsStack.pop();
            visited.add(top);

            for (Iterator<SSAInstruction> it = top.iterator(); it.hasNext(); ) {
                // is it the same instruction?
                boolean isDependent = false;
                SSAInstruction i = it.next();
                int nUses = i.getNumberOfUses();
                for (int k = 0; k < nUses; k++) {
                    int _temp = i.getUse(k);
                    if (defDeps.contains(_temp)) {

                        isDependent = true;

                        int _nDefs = i.getNumberOfDefs();
                        for (int j = 0; j < _nDefs; j++) {
                            defDeps.add(i.getDef(j));
                        }
                    }
                }

                if (i instanceof SSAReturnInstruction) {
                    if (isDependent) {
                        return true;
                    }
                    return false;
                }
            }

            for (ISSABasicBlock block : cfg.getNormalSuccessors(top)) {
                if (visited.contains(block) || block.isExitBlock()) {
                    continue;
                }
                dfsStack.add(block);
            }
        }
        return false;
    }


    private static boolean isParameterDataFlow(SSAInstruction s, SSACFG cfg, DefaultEntrypoint ep, CallGraph cg) {

        HashSet<Integer> paramDeps = new HashSet<>();


        CGNode node = CallGraphUtils.getNodeFromEntryPoint(cg, ep);

        SymbolTable st = node.getIR().getSymbolTable();

        int[] paramNumbers = st.getParameterValueNumbers();

        for (int p : paramNumbers) {
            paramDeps.add(p);
        }

        ISSABasicBlock start = cfg.entry();

        HashSet<ISSABasicBlock> visited = new HashSet<>();
        Stack<ISSABasicBlock> dfsStack = new Stack<>();

        dfsStack.push(start);

        while (!dfsStack.isEmpty()) {
            ISSABasicBlock top = dfsStack.pop();
            visited.add(top);
            for (Iterator<SSAInstruction> it = top.iterator(); it.hasNext(); ) {
                // is it the same instruction?
                boolean isDependent = false;
                SSAInstruction i = it.next();
                int nUses = i.getNumberOfUses();
                for (int k = 0; k < nUses; k++) {
                    int _temp = i.getUse(k);
                    if (paramDeps.contains(_temp)) {

                        isDependent = true;

                        int nDefs = i.getNumberOfDefs();
                        for (int j = 0; j < nDefs; j++) {
                            paramDeps.add(i.getDef(j));
                        }
                    }
                }

                if (i == s) {
                    if (isDependent) {
                        return true;
                    }
                    return false;
                }
            }

            for (ISSABasicBlock block : cfg.getNormalSuccessors(top)) {
                if (visited.contains(block) || block.isExitBlock()) {
                    continue;
                }
                dfsStack.add(block);
            }
        }
        return false;
    }


    private static String joinAccessPath(ArrayList<AcmNode> protections) {
        ArrayList<String> stringProtections = new ArrayList<>();
        for (AcmNode node : protections) {
            if (node instanceof AccessControlNode) {
                stringProtections.add(((AccessControlNode) node).getAccessControlString());
            }
        }
        return joinStrings(stringProtections, " ^ ");
    }

    public static String joinStrings(ArrayList<String> list, String delimiter) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }



}




