package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.Fingerprinter;
import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.core.common.ValidationResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.HexFormat;

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

    /**
     * Returns the transitive closure of what the nodes <em>declare</em>, in a stable order.
     *
     * <p>The order is ascending by {@link NodeId#value()}, so that a caller which folds the closure
     * into a persisted value — a fingerprint — gets the same value for the same graph on every run.
     * A {@link java.util.HashSet} iteration order would not survive that.
     *
     * <p>Unlike {@link #getAllDependencies(NodeId)} this walks {@link MigrationNode#dependencies()}
     * rather than the adjacency list, for the same reason {@link #unresolvedDependencies()} does:
     * {@link #fromNodesUp}/{@link #fromNodesDown} narrow the adjacency to the supplied subset, and
     * a fingerprint describes the definition, not the subset it was loaded with.
     *
     * <p>A declared dependency whose node the graph does not contain is therefore kept, but the
     * walk stops there — what that node declared went with its task file. Deleting a task file
     * shrinks a dependent's closure by whatever stood behind it and is not also reachable along
     * another declared path, and that dependent then compares as changed although nobody edited it.
     * Nothing in the graph recovers it; the information left with the file.
     *
     * @param nodeId the identifier of the node whose transitive dependencies are requested
     * @return the declared transitive dependencies sorted by identifier, possibly empty
     */
    public List<NodeId> canonicalTransitiveDependencies(NodeId nodeId) {
        Set<NodeId> collected = new HashSet<>();
        collectDeclaredDependencies(nodeId, collected);
        return collected.stream().sorted(Comparator.comparing(NodeId::value)).toList();
    }

    /**
     * Returns the {@link Fingerprinter} for one node, holding the closure that node stands on.
     *
     * <p>The closure never reaches the node, and that is deliberate. Handing one over has been the
     * shape of the same defect repeatedly: a caller that passes an empty list instead compiles and
     * fails nothing, because a node folding an empty closure still produces a token. Here there is
     * nothing for a caller to pass.
     *
     * <p>The closure is {@link #canonicalTransitiveDependencies(NodeId)} — declared dependencies,
     * ascending by identifier — so a token is the same on every run for the same graph.
     *
     * <p>The same reasoning covers the three attributes core folds without asking the plugin —
     * {@link MigrationNode#name()}, {@link MigrationNode#noWayBack()} and the target's id. They are
     * looked up here, from the id already supplied, so a plugin cannot leave one out and no caller
     * gains an argument to get wrong. Core is what writes those three to their history columns, so
     * core is what signs them.
     *
     * @param nodeId the node whose fingerprint will be folded; must be in this graph
     * @return a fingerprinter for that node
     * @throws NullPointerException if this graph holds no node with that identifier
     */
    public Fingerprinter fingerprinterFor(NodeId nodeId) {
        MigrationNode node =
                Objects.requireNonNull(
                        nodes.get(nodeId), () -> "No node to fingerprint: " + nodeId.value());
        List<NodeId> closure = canonicalTransitiveDependencies(nodeId);
        return signatures -> fold(node, signatures, closure);
    }

    /**
     * Frames what core reads off the node, the signatures and the closure into one pre-image and
     * digests it.
     *
     * <p>The order is {@code name}, {@code noWayBack}, target id, the signatures, {@code '/'}, the
     * closure. The three core reads itself come <strong>first</strong>, at a fixed count, so they
     * are read before anything of variable length; put last they would have to be counted back from
     * the end of the closure. The separator only says where the signatures stop and the closure
     * starts — nothing has to scan for it, because every part states its own length.
     *
     * <p>Every part is written as {@code <length>:<text>} so that concatenation cannot lose a
     * boundary: without it one signature of {@code "ab"} and two of {@code "a"} and {@code "b"}
     * would build the same pre-image, and so would a closure of {@code [ab]} and one of {@code [a,
     * b]}.
     *
     * <p>An absent {@code noWayBack} is the single byte {@code '-'}. A fixed position cannot use
     * arity to say "not declared" the way the signature count says "no rollback", and a length
     * always begins with a digit, so the two cannot collide. A reason declared empty frames as
     * {@code 0:} and is a different token — those are different edits.
     */
    private static String fold(MigrationNode node, String[] signatures, List<NodeId> closure) {
        StringBuilder preimage = new StringBuilder();
        appendLengthPrefixed(preimage, node.name());
        String noWayBack = node.noWayBack();
        if (noWayBack == null) {
            preimage.append('-');
        } else {
            appendLengthPrefixed(preimage, noWayBack);
        }
        appendLengthPrefixed(preimage, node.target().id().value());
        for (String signature : signatures) {
            appendLengthPrefixed(preimage, signature);
        }
        preimage.append('/');
        for (NodeId dependency : closure) {
            appendLengthPrefixed(preimage, dependency.value());
        }
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(preimage.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    private static void appendLengthPrefixed(StringBuilder target, String part) {
        target.append(part.length()).append(':').append(part);
    }

    /**
     * Returns each node's declared dependencies with the redundant ones removed.
     *
     * <p>An edge {@code x -> p} is redundant when {@code p} is already reachable from another of
     * {@code x}'s declared dependencies: declaring it changes nothing about what {@code x} stands
     * on. What is left is the transitive reduction, which for a DAG is unique — so two graphs whose
     * dependencies differ only in redundant edges reduce to the same thing, and comparing the
     * reduced forms cannot report a difference the transitive closure does not have.
     *
     * <p>Like {@link #canonicalTransitiveDependencies(NodeId)} this reads what the nodes
     * <em>declare</em> rather than the adjacency list, and for the same reason. A declared
     * dependency whose node the graph does not contain is kept: nothing here can show it redundant,
     * because what that node declared went with its task file.
     *
     * <p>The graph is not modified. The result is a fresh map, and a node that declares nothing
     * maps to an empty set rather than being absent.
     *
     * @return each node in this graph mapped to its reduced set of direct dependencies
     */
    public Map<NodeId, Set<NodeId>> transitiveReduction() {
        Map<NodeId, Set<NodeId>> reduced = new HashMap<>();
        for (MigrationNode node : nodes.values()) {
            Set<NodeId> declared = node.dependencies();
            Set<NodeId> keep = new HashSet<>();
            for (NodeId candidate : declared) {
                if (!reachableFromAnother(declared, candidate)) {
                    keep.add(candidate);
                }
            }
            reduced.put(node.id(), Set.copyOf(keep));
        }
        return Map.copyOf(reduced);
    }

    /** Whether {@code candidate} lies behind one of {@code declared}'s other members. */
    private boolean reachableFromAnother(Set<NodeId> declared, NodeId candidate) {
        for (NodeId other : declared) {
            if (other.equals(candidate)) {
                continue;
            }
            Set<NodeId> behindOther = new HashSet<>();
            collectDeclaredDependencies(other, behindOther);
            if (behindOther.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void collectDeclaredDependencies(NodeId nodeId, Set<NodeId> collected) {
        MigrationNode node = nodes.get(nodeId);
        if (node == null) {
            return;
        }
        for (NodeId dependency : node.dependencies()) {
            if (collected.add(dependency)) {
                collectDeclaredDependencies(dependency, collected);
            }
        }
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
     * Returns the nodes that lie on a cycle, sorted by identifier.
     *
     * <p>{@link #hasCycle()} answers whether one exists; a refusal has to say which migrations are
     * involved, and on the side of the tool where the evidence is rows in a table there is no
     * command an operator could run to find out for themselves.
     *
     * <p>A node lies on a cycle when it is reachable from itself, so this is {@link
     * #canonicalTransitiveDependencies} asked about the node itself — which means it reads what the
     * nodes <strong>declare</strong>, like the reduction this check protects, rather than the
     * adjacency list {@link #hasCycle()} walks. The two agree about whether a cycle exists; this
     * one costs a closure walk per node, so it answers a refusal's question, not a hot path's.
     *
     * @return the identifiers of every node on a cycle, sorted, or an empty list when the graph is
     *     a DAG
     */
    public List<NodeId> nodesOnCycles() {
        return nodes.keySet().stream()
                .filter(nodeId -> canonicalTransitiveDependencies(nodeId).contains(nodeId))
                .sorted(Comparator.comparing(NodeId::value))
                .toList();
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
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();

        if (hasCycle()) {
            errors.add("Graph contains a cycle (circular dependency)");
        }

        for (Map.Entry<NodeId, Set<NodeId>> entry : unresolvedDependencies().entrySet()) {
            for (NodeId depId : entry.getValue()) {
                errors.add("Node " + entry.getKey() + " depends on non-existent node: " + depId);
            }
        }

        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }

    /**
     * Returns the declared dependencies that name no node in this graph.
     *
     * <p>A cycle is a defect in the graph: no order satisfies it, so nothing can run. An unresolved
     * dependency is only incompleteness — the description points outside itself, which is what
     * deleting a task file leaves behind, and the node it names may well be sitting in the history
     * as applied. So this is reported rather than fatal: a project in this state must still be able
     * to say what happened to it. What stops is applying something whose ground is not described.
     *
     * <p>{@code node.dependencies()} is consulted directly rather than the adjacency list, because
     * {@link #fromNodesUp}/{@link #fromNodesDown} narrow the adjacency to the supplied subset — the
     * node itself is the source of truth for what it declared.
     *
     * @return each node with unresolved dependencies mapped to the ids it names, empty when every
     *     declared dependency resolves
     */
    public Map<NodeId, Set<NodeId>> unresolvedDependencies() {
        Map<NodeId, Set<NodeId>> unresolved = new LinkedHashMap<>();
        for (MigrationNode node : nodes.values()) {
            Set<NodeId> missing = new LinkedHashSet<>();
            for (NodeId depId : node.dependencies()) {
                if (!nodes.containsKey(depId)) {
                    missing.add(depId);
                }
            }
            if (!missing.isEmpty()) {
                unresolved.put(node.id(), missing);
            }
        }
        return unresolved;
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
