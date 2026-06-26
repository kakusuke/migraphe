package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks which migration nodes are ready to execute as a direction-aware DAG is traversed.
 *
 * <p>This is the dispatch driver behind {@link DagExecutor}. It maintains a per-node in-degree
 * counter restricted to the {@code targetNodes} set: a node's in-degree is the number of its
 * predecessors (in the configured {@link ExecutionDirection}) that are also targets. A node becomes
 * ready when its in-degree drops to zero. {@link #initialReadyNodes} returns the nodes that start
 * ready, and {@link #markCompleted} decrements the in-degrees of a completed node's successors and
 * returns any that became newly ready.
 *
 * <p>The direction inverts the graph's notion of predecessor/successor: for {@link
 * ExecutionDirection#UP} a node's predecessors are its dependencies and successors are its
 * dependents; for {@link ExecutionDirection#DOWN} this is reversed, so rollbacks proceed from
 * dependents toward dependencies.
 *
 * <p>{@link #markCompleted} is {@code synchronized}, making it safe to call from the multiple
 * virtual threads that {@link DagExecutor} dispatches concurrently.
 */
public final class ReadyNodeTracker {

    private final MigrationGraph graph;
    private final Set<NodeId> targetNodes;
    private final Map<NodeId, Integer> inDegrees;
    private final ExecutionDirection direction;

    /**
     * Creates a tracker for an UP traversal.
     *
     * @param graph the migration graph whose edges define dependency order
     * @param targetNodes the set of nodes participating in this run; in-degree counting is confined
     *     to this set
     */
    public ReadyNodeTracker(MigrationGraph graph, Set<NodeId> targetNodes) {
        this(graph, targetNodes, ExecutionDirection.UP);
    }

    /**
     * Creates a tracker for the given traversal direction.
     *
     * @param graph the migration graph whose edges define dependency order
     * @param targetNodes the set of nodes participating in this run; in-degree counting is confined
     *     to this set
     * @param direction the traversal direction that determines predecessor/successor orientation
     */
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

    /**
     * Returns the target nodes that are ready to execute before any completion is reported.
     *
     * <p>These are the target nodes whose direction-aware in-degree is zero (no predecessors within
     * the target set).
     *
     * @return the set of initially ready node IDs
     */
    public Set<NodeId> initialReadyNodes() {
        Set<NodeId> ready = new HashSet<>();
        for (Map.Entry<NodeId, Integer> entry : inDegrees.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }
        return ready;
    }

    /**
     * Records completion of a node and returns its successors that have just become ready.
     *
     * <p>For each successor (in the configured direction) that is within the target set, its
     * in-degree is decremented; any whose in-degree reaches zero is returned as newly ready. This
     * method is {@code synchronized} and safe to call concurrently.
     *
     * @param nodeId the node that has just completed
     * @return the set of node IDs that became ready as a result of this completion
     */
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

    /** Returns the direction-aware predecessor set used for in-degree counting. */
    private Set<NodeId> predecessors(NodeId nodeId) {
        return direction == ExecutionDirection.DOWN
                ? graph.getDependents(nodeId)
                : graph.getDependencies(nodeId);
    }

    /**
     * Returns the direction-aware successor set used to compute newly ready nodes after completion.
     */
    private Set<NodeId> successors(NodeId nodeId) {
        return direction == ExecutionDirection.DOWN
                ? graph.getDependencies(nodeId)
                : graph.getDependents(nodeId);
    }
}
