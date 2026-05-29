package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
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
    private final ExecutionDirection direction;

    public ReadyNodeTracker(MigrationGraph graph, Set<NodeId> targetNodes) {
        this(graph, targetNodes, ExecutionDirection.UP);
    }

    public ReadyNodeTracker(
            MigrationGraph graph, Set<NodeId> targetNodes, ExecutionDirection direction) {
        this.graph = graph;
        this.targetNodes = Set.copyOf(targetNodes);
        this.inDegrees = new HashMap<>();
        this.direction = direction;

        for (NodeId nodeId : targetNodes) {
            int count = 0;
            for (NodeId dep : predecessors(nodeId)) {
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
        for (NodeId dependent : successors(nodeId)) {
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

    /** direction に応じた「先行ノード」集合を返す (in-degree 計算用)。 */
    private Set<NodeId> predecessors(NodeId nodeId) {
        return direction == ExecutionDirection.DOWN
                ? graph.getDependents(nodeId)
                : graph.getDependencies(nodeId);
    }

    /** direction に応じた「後続ノード」集合を返す (markCompleted 後の ready 計算用)。 */
    private Set<NodeId> successors(NodeId nodeId) {
        return direction == ExecutionDirection.DOWN
                ? graph.getDependencies(nodeId)
                : graph.getDependents(nodeId);
    }
}
