package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.core.common.ValidationResult;
import java.util.*;

/**
 * A directed acyclic graph (DAG) of migration nodes.
 *
 * <p>This is the mutable, core-side implementation of {@link MigrationGraphView} and acts as the
 * aggregate root that owns the structure of a migration run. It stores the set of {@link
 * MigrationNode nodes} together with an adjacency list mapping each node to the identifiers of the
 * nodes it depends on, and provides the traversal, cycle-detection, and validation primitives that
 * the execution-planning code ({@link TopologicalSort}, {@link ExecutionPlan}) builds on.
 *
 * <p>A graph can be assembled incrementally with {@link #create()} plus {@link
 * #addNode(MigrationNode)}, or built in one shot from a node list with {@link #fromNodesUp(List)}
 * (forward dependency edges) or {@link #fromNodesDown(List)} (reversed edges, for rollback).
 * Structural integrity — the absence of cycles and of dangling dependency references — is not
 * enforced on mutation; call {@link #validate()} (or {@link #hasCycle()}) before planning
 * execution.
 *
 * <p>Instances are not thread-safe.
 */
public final class MigrationGraph implements MigrationGraphView {
    private final Map<NodeId, MigrationNode> nodes;
    private final Map<NodeId, Set<NodeId>> adjacencyList; // node -> nodes it depends on

    private MigrationGraph() {
        this.nodes = new HashMap<>();
        this.adjacencyList = new HashMap<>();
    }

    /**
     * Adds a node to the graph.
     *
     * <p>The node's declared dependencies are recorded as outgoing edges as-is; whether those
     * dependency targets actually exist in the graph is not checked here but later by {@link
     * #validate()}.
     *
     * @param node the migration node to add; its {@link MigrationNode#id() id} must not already be
     *     present in the graph
     * @throws IllegalArgumentException if a node with the same identifier already exists
     */
    public void addNode(MigrationNode node) {
        if (nodes.containsKey(node.id())) {
            throw new IllegalArgumentException("Node already exists: " + node.id());
        }

        nodes.put(node.id(), node);
        adjacencyList.put(node.id(), new HashSet<>(node.dependencies()));
    }

    /** {@inheritDoc} A root is a node whose adjacency entry (its dependency set) is empty. */
    @Override
    public Set<MigrationNode> getRoots() {
        return nodes.values().stream()
                .filter(node -> adjacencyList.getOrDefault(node.id(), Set.of()).isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }

    /** {@inheritDoc} */
    @Override
    public Set<NodeId> getDependencies(NodeId nodeId) {
        return Set.copyOf(adjacencyList.getOrDefault(nodeId, Set.of()));
    }

    /** {@inheritDoc} */
    @Override
    public Set<NodeId> getDependents(NodeId nodeId) {
        return adjacencyList.entrySet().stream()
                .filter(entry -> entry.getValue().contains(nodeId))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Returns every node that depends on the given node, directly or transitively.
     *
     * <p>This is the recursive (reflexive-transitive minus self) closure of {@link
     * #getDependents(NodeId)}: it follows dependent edges outward until no new nodes are reached.
     * The starting node itself is not included.
     *
     * @param nodeId the identifier of the node whose transitive dependents are requested
     * @return the set of all directly and indirectly dependent node identifiers, possibly empty
     */
    public Set<NodeId> getAllDependents(NodeId nodeId) {
        Set<NodeId> result = new HashSet<>();
        collectDependents(nodeId, result);
        return result;
    }

    private void collectDependents(NodeId nodeId, Set<NodeId> collected) {
        for (NodeId dependent : getDependents(nodeId)) {
            if (collected.add(dependent)) {
                collectDependents(dependent, collected);
            }
        }
    }

    /**
     * Returns every node that the given node depends on, directly or transitively.
     *
     * <p>This is the recursive (reflexive-transitive minus self) closure of {@link
     * #getDependencies(NodeId)}: it follows dependency edges inward until no new nodes are reached.
     * The starting node itself is not included.
     *
     * @param nodeId the identifier of the node whose transitive dependencies are requested
     * @return the set of all directly and indirectly required node identifiers, possibly empty
     */
    public Set<NodeId> getAllDependencies(NodeId nodeId) {
        Set<NodeId> result = new HashSet<>();
        collectDependencies(nodeId, result);
        return result;
    }

    private void collectDependencies(NodeId nodeId, Set<NodeId> collected) {
        for (NodeId dependency : getDependencies(nodeId)) {
            if (collected.add(dependency)) {
                collectDependencies(dependency, collected);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<MigrationNode> getNode(NodeId nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    /**
     * Returns whether the graph contains a cycle (circular dependency).
     *
     * <p>Detection uses a depth-first traversal over the dependency edges with a recursion stack:
     * re-encountering a node already on the current stack indicates a back edge and therefore a
     * cycle.
     *
     * @return {@code true} if at least one dependency cycle exists, {@code false} otherwise
     */
    public boolean hasCycle() {
        Set<NodeId> visited = new HashSet<>();
        Set<NodeId> recursionStack = new HashSet<>();

        for (NodeId nodeId : nodes.keySet()) {
            if (hasCycleUtil(nodeId, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCycleUtil(NodeId nodeId, Set<NodeId> visited, Set<NodeId> recursionStack) {
        if (recursionStack.contains(nodeId)) {
            return true; // cycle detected
        }

        if (visited.contains(nodeId)) {
            return false;
        }

        visited.add(nodeId);
        recursionStack.add(nodeId);

        for (NodeId dependency : getDependencies(nodeId)) {
            if (hasCycleUtil(dependency, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(nodeId);
        return false;
    }

    /**
     * Validates the structural integrity of the graph.
     *
     * <p>Two conditions are checked: the graph must be acyclic, and every dependency declared by a
     * node (via {@link MigrationNode#dependencies()}) must reference a node that actually exists in
     * the graph. The node's own declared dependencies are used as the source of truth here rather
     * than the adjacency list, because {@link #fromNodesUp(List)}/{@link #fromNodesDown(List)} may
     * filter the adjacency list down to the supplied node subset.
     *
     * @return a {@linkplain ValidationResult#valid() valid} result when no problems are found, or
     *     an {@linkplain ValidationResult#invalid(List) invalid} result listing each cycle and/or
     *     dangling dependency reference
     */
    /**
     * Returns the nodes that neither roll back nor say why they cannot.
     *
     * <p>Applying one of these would put something into the database with no recorded way out and
     * no record of that being a decision — and by then it is too late to ask, because the migration
     * has run. The definition is the only place the question can still be answered.
     *
     * <p>Deliberately not part of {@link #validate()}: that runs on every load, and a project in
     * this state must still be able to report its status. A run that would apply something is what
     * stops.
     *
     * @return the offending node ids, empty when every node declares one or the other
     */
    public Set<NodeId> undeclaredIrreversibleNodes() {
        Set<NodeId> offenders = new HashSet<>();
        for (MigrationNode node : nodes.values()) {
            if (node.downTask() == null && node.noWayBack() == null) {
                offenders.add(node.id());
            }
        }
        return offenders;
    }

    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();

        // cycle check
        if (hasCycle()) {
            errors.add("Graph contains a cycle (circular dependency)");
        }

        // Ensure every dependency target exists. node.dependencies() is consulted directly (rather
        // than the adjacency list) because fromNodesUp/Down may narrow the adjacency list to the
        // supplied node subset, so the node itself is the source of truth.
        for (MigrationNode node : nodes.values()) {
            for (NodeId depId : node.dependencies()) {
                if (!nodes.containsKey(depId)) {
                    errors.add("Node " + node.id() + " depends on non-existent node: " + depId);
                }
            }
        }

        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }

    /** {@inheritDoc} */
    @Override
    public int size() {
        return nodes.size();
    }

    /** {@inheritDoc} */
    @Override
    public Collection<MigrationNode> allNodes() {
        return List.copyOf(nodes.values());
    }

    /**
     * Creates a new, empty graph to be populated with {@link #addNode(MigrationNode)}.
     *
     * @return a fresh, empty {@code MigrationGraph}
     */
    public static MigrationGraph create() {
        return new MigrationGraph();
    }

    /**
     * Builds a graph for forward (up) execution from the given nodes.
     *
     * <p>Each node's dependency edges point at the nodes it depends on, exactly as declared, except
     * that dependencies referring to nodes outside the supplied list are dropped. This yields a
     * self-contained subgraph in which root nodes (no dependencies) are executed first.
     *
     * @param nodes the nodes to include in the graph
     * @return a graph whose edges run from each node to its (in-list) dependencies
     */
    public static MigrationGraph fromNodesUp(List<MigrationNode> nodes) {
        MigrationGraph graph = new MigrationGraph();
        Set<NodeId> nodeIds = new HashSet<>();
        for (MigrationNode node : nodes) {
            nodeIds.add(node.id());
        }
        for (MigrationNode node : nodes) {
            graph.nodes.put(node.id(), node);
            Set<NodeId> filteredDeps = new HashSet<>();
            for (NodeId depId : node.dependencies()) {
                if (nodeIds.contains(depId)) {
                    filteredDeps.add(depId);
                }
            }
            graph.adjacencyList.put(node.id(), filteredDeps);
        }
        return graph;
    }

    /**
     * Builds a graph for reverse (down/rollback) execution from the given nodes.
     *
     * <p>The dependency direction is inverted relative to {@link #fromNodesUp(List)}: for every
     * declared dependency {@code node -> parent}, an edge {@code parent -> node} is added (only
     * when {@code parent} is among the supplied nodes). As a result, a node that nothing depends on
     * becomes a root and is rolled back first, ensuring dependents are undone before the nodes they
     * relied on.
     *
     * @param nodes the nodes to include in the graph
     * @return a graph whose edges are reversed for rollback ordering
     */
    public static MigrationGraph fromNodesDown(List<MigrationNode> nodes) {
        MigrationGraph graph = new MigrationGraph();
        for (MigrationNode node : nodes) {
            graph.nodes.put(node.id(), node);
            graph.adjacencyList.put(node.id(), new HashSet<>());
        }
        for (MigrationNode node : nodes) {
            for (NodeId parentId : node.dependencies()) {
                Set<NodeId> parentAdjacency = graph.adjacencyList.get(parentId);
                if (parentAdjacency != null) {
                    parentAdjacency.add(node.id());
                }
            }
        }
        return graph;
    }
}
