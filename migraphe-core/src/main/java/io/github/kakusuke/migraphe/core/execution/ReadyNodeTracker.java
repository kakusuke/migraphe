package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 実行可能状態のノードを追跡するクラス。 */
public final class ReadyNodeTracker {

    private final MigrationGraph graph;
    private final Set<NodeId> targetNodes;
    private final Map<NodeId, Integer> inDegrees;

    public ReadyNodeTracker(MigrationGraph graph, Set<NodeId> targetNodes) {
        this.graph = graph;
        this.targetNodes = Set.copyOf(targetNodes);
        this.inDegrees = new HashMap<>();

        for (NodeId nodeId : targetNodes) {
            int count = 0;
            for (NodeId dep : graph.getDependencies(nodeId)) {
                if (targetNodes.contains(dep)) {
                    count++;
                }
            }
            inDegrees.put(nodeId, count);
        }
    }

    public Set<NodeId> initialReadyNodes() {
        Set<NodeId> ready = new HashSet<>();
        for (Map.Entry<NodeId, Integer> entry : inDegrees.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }
        return ready;
    }

    public synchronized Set<NodeId> markCompleted(NodeId nodeId) {
        Set<NodeId> newlyReady = new HashSet<>();
        for (NodeId dependent : graph.getDependents(nodeId)) {
            if (!targetNodes.contains(dependent)) {
                continue;
            }
            int newCount = inDegrees.merge(dependent, -1, Integer::sum);
            if (newCount == 0) {
                newlyReady.add(dependent);
            }
        }
        return newlyReady;
    }
}
