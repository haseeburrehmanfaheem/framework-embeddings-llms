package com.aacr.helpers.detectors.app.componentgraph;

import com.aacr.helpers.accesscontrol.app.Component;
import com.aacr.helpers.detectors.app.componentgraph.models.info.ComponentInfo;
import com.aacr.helpers.detectors.app.componentgraph.models.EdgeLabel;
import com.aacr.helpers.detectors.app.componentgraph.models.Node;
import com.ibm.wala.util.collections.Pair;

import java.util.*;

public class GraphManager implements Iterable<Node> {

    private static GraphManager instance = null;

    public synchronized static GraphManager getInstance() {
        if (instance == null)
            instance = new GraphManager();
        return instance;
    }

    private GraphManager() {}

    // The actual graph of the app components
    private final HashMap<Node, HashSet<Pair<Node, EdgeLabel>>> graph = new HashMap<>();

    // All vertices in the graph that corresponds to an existing component of the app
    private final HashMap<Component, Node> vertices = new HashMap<>();

    // All vertices corresponding to the components defined outside the scope of the app
    private final HashMap<Component, Node> externalVertices = new HashMap<>();

    public void addEdge(Node src, Node dstn, EdgeLabel label) {
        if (!graph.containsKey(src))
            graph.put(src, new HashSet<>());
        graph.get(src).add(Pair.make(dstn, label));
    }

    public Node getNode(Component component) {
        if (vertices.containsKey(component))
            return vertices.get(component);
        return externalVertices.get(component);
    }

    public void addNode(Component component, ComponentInfo miscInfo) {
        if (!vertices.containsKey(component)) {
            vertices.put(component, new Node(component, miscInfo));
        }
    }

    public void addExternalNode(Component component) {
        if (!externalVertices.containsKey(component))
            externalVertices.put(component, new Node(component, null));
    }

    public String toGraphString() {
        StringBuilder finalStr = new StringBuilder();
        for (Map.Entry<Node, HashSet<Pair<Node, EdgeLabel>>> entry : graph.entrySet()) {
            finalStr.append(entry.getKey().toString());
            for(Pair<Node, EdgeLabel> edge : entry.getValue()) {
                Node node = edge.fst;
                EdgeLabel edgeLabel = edge.snd;
                finalStr.append("&");
                if (node.appComponent.getType() == Component.Type.OUT_OF_APP)
                    finalStr.append("OUT OF APP component::");
                finalStr.append(node.appComponent.getClassStr()).append("&").append(edgeLabel).append("\n");
            }
            System.out.println();
        }

        return finalStr.toString();
    }

    @Override
    public Iterator<Node> iterator() {
        return vertices.values().iterator();
    }

}
