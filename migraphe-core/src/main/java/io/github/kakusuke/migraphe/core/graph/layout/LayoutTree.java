package io.github.kakusuke.migraphe.core.graph.layout;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Decomposes a DAG into a spanning tree of {@link LayoutStream}s plus the leftover non-tree edges.
 *
 * <p>This is the second stage of the ASCII layout pipeline ({@code MigrationGraph -> LayoutSort ->
 * LayoutTree -> GridCanvas -> ExecutionGraphView}). Given a topological {@link
 * LayoutSort.LayoutOrder}, it attaches each node to a single chosen parent — either extending that
 * parent's stream trunk or forking a new child stream — and collects every remaining parent edge as
 * a {@link NonTreeEdge}. All streams hang off a synthetic {@link VirtualNode virtual root}, so the
 * resulting tree always has exactly one root stream.
 *
 * <p>Instances are immutable and created only through {@link #build(MigrationGraph,
 * LayoutSort.LayoutOrder)}.
 */
public final class LayoutTree {

    private final LayoutStream rootStream;
    private final List<NonTreeEdge> nonTreeEdges;
    private final Map<NodeId, LayoutStream> nodeToStream;

    private LayoutTree(
            LayoutStream rootStream,
            List<NonTreeEdge> nonTreeEdges,
            Map<NodeId, LayoutStream> nodeToStream) {
        this.rootStream = rootStream;
        this.nonTreeEdges = List.copyOf(nonTreeEdges);
        this.nodeToStream = Map.copyOf(nodeToStream);
    }

    /**
     * Returns the single root stream, whose trunk holds only the synthetic virtual root node.
     *
     * @return the root {@link LayoutStream} of this tree
     */
    public LayoutStream rootStream() {
        return rootStream;
    }

    /**
     * Returns the DAG edges that could not be represented as tree edges.
     *
     * <p>The list is sorted so that shorter, lower edges are drawn first; it is rendered by {@link
     * GridCanvas#addNonTreeEdge(NodeId, NodeId)}.
     *
     * @return an immutable list of non-tree edges
     */
    public List<NonTreeEdge> nonTreeEdges() {
        return nonTreeEdges;
    }

    /**
     * Returns the stream that contains the given node.
     *
     * @param nodeId the node to look up
     * @return the {@link LayoutStream} whose trunk contains the node
     * @throws IllegalArgumentException if the node is not part of this tree
     */
    public LayoutStream streamOf(NodeId nodeId) {
        LayoutStream stream = nodeToStream.get(nodeId);
        if (stream == null) {
            throw new IllegalArgumentException("Node not found in tree: " + nodeId);
        }
        return stream;
    }

    /**
     * Builds the stream tree by a single forward pass over the topological order.
     *
     * <p>Each node is processed in order and attached in one of three ways: (1) if it has no
     * parents, a new root stream is created directly under the virtual root; (2) if its chosen
     * parent is the tail of that parent's stream, the node extends the trunk; (3) otherwise it
     * forks a new child stream off the chosen parent. The chosen parent is the parent with the
     * greatest current DFS draw position, so the node is drawn below all of its parents. Every
     * other parent edge becomes a {@link NonTreeEdge}.
     *
     * <p>Invariant: for every dependency {@code parent -> child}, the final rendering satisfies
     * {@code row(parent) < row(child)}. This guarantees no non-tree edge ever points upward, so the
     * upward-skip guard in {@link GridCanvas#addNonTreeEdge(NodeId, NodeId)} is never structurally
     * triggered.
     *
     * @param graph the migration graph being laid out
     * @param order the topological layout order produced by {@link LayoutSort#sort(MigrationGraph)}
     * @return the constructed layout tree
     */
    public static LayoutTree build(MigrationGraph graph, LayoutSort.LayoutOrder order) {
        MigrationNode virtualRoot = new VirtualNode();
        if (order.nodes().isEmpty()) {
            return new LayoutTree(
                    new LayoutStream(null, List.of(virtualRoot), List.of()), List.of(), Map.of());
        }

        Map<NodeId, MutableStream> nodeToMutable = new HashMap<>();
        List<MutableStream> rootChildren = new ArrayList<>();
        List<NonTreeEdge> nonTreeEdges = new ArrayList<>();

        for (MigrationNode node : order.nodes()) {
            Set<NodeId> parents = graph.getDependencies(node.id());

            if (parents.isEmpty()) {
                MutableStream s = new MutableStream(virtualRoot.id());
                s.appendTrunk(node);
                rootChildren.add(s);
                nodeToMutable.put(node.id(), s);
                continue;
            }

            // Compute DFS draw positions in the current tree and pick the latest parent as the
            // attach target. This way the node is drawn below its last parent and the remaining
            // parents never point upward.
            Map<NodeId, Integer> dfsPos = computeDfsPositions(rootChildren);
            NodeId chosenParent =
                    parents.stream()
                            .max(Comparator.comparingInt(p -> dfsPos.getOrDefault(p, -1)))
                            .orElseThrow();

            MutableStream chosenStream =
                    Objects.requireNonNull(
                            nodeToMutable.get(chosenParent),
                            "parent stream missing for attach target");

            if (chosenStream.tail().equals(chosenParent)) {
                // If the parent is the tail of the stream, extend its trunk
                chosenStream.appendTrunk(node);
                nodeToMutable.put(node.id(), chosenStream);
            } else {
                // If the parent is not the tail, fork a new child stream at the parent
                MutableStream newStream = new MutableStream(chosenParent);
                newStream.appendTrunk(node);
                chosenStream.children.add(newStream);
                nodeToMutable.put(node.id(), newStream);
            }

            for (NodeId p : parents) {
                if (!p.equals(chosenParent)) {
                    nonTreeEdges.add(new NonTreeEdge(p, node.id()));
                }
            }
        }

        Map<NodeId, LayoutStream> nodeToStream = new HashMap<>();
        List<LayoutStream> frozenRootChildren = new ArrayList<>();
        for (MutableStream m : rootChildren) {
            frozenRootChildren.add(m.freeze(nodeToStream));
        }

        LayoutStream vrStream = new LayoutStream(null, List.of(virtualRoot), frozenRootChildren);

        nonTreeEdges.sort(
                Comparator.<NonTreeEdge>comparingInt(
                                e ->
                                        order.rankMap().getOrDefault(e.source(), 0)
                                                - order.rankMap().getOrDefault(e.target(), 0))
                        .thenComparingInt(e -> -order.rankMap().getOrDefault(e.source(), 0)));

        return new LayoutTree(vrStream, nonTreeEdges, nodeToStream);
    }

    /**
     * Walks the current tree depth-first and returns each node's draw-order position. This uses the
     * same traversal as {@code GridCanvas.drawStream}: emit the trunk nodes in order and,
     * immediately after each node, recursively expand its fork children in order.
     */
    private static Map<NodeId, Integer> computeDfsPositions(List<MutableStream> rootChildren) {
        Map<NodeId, Integer> pos = new HashMap<>();
        int[] counter = {0};
        for (MutableStream s : rootChildren) {
            traverseDfs(s, pos, counter);
        }
        return pos;
    }

    private static void traverseDfs(MutableStream s, Map<NodeId, Integer> pos, int[] counter) {
        Map<NodeId, List<MutableStream>> childByFork = new HashMap<>();
        for (MutableStream c : s.children) {
            childByFork.computeIfAbsent(c.forkNode, k -> new ArrayList<>()).add(c);
        }
        for (MigrationNode n : s.trunk) {
            pos.put(n.id(), counter[0]++);
            List<MutableStream> kids = childByFork.get(n.id());
            if (kids != null) {
                for (MutableStream k : kids) {
                    traverseDfs(k, pos, counter);
                }
            }
        }
    }

    /**
     * Mutable, under-construction representation of a stream, converted to an immutable {@link
     * LayoutStream} by {@link #freeze}.
     */
    private static final class MutableStream {
        final @Nullable NodeId forkNode;
        final List<MigrationNode> trunk = new ArrayList<>();
        final List<MutableStream> children = new ArrayList<>();

        MutableStream(@Nullable NodeId forkNode) {
            this.forkNode = forkNode;
        }

        void appendTrunk(MigrationNode node) {
            trunk.add(node);
        }

        NodeId tail() {
            return trunk.get(trunk.size() - 1).id();
        }

        LayoutStream freeze(Map<NodeId, LayoutStream> nodeToStream) {
            List<LayoutStream> frozenChildren = new ArrayList<>();
            for (MutableStream child : children) {
                frozenChildren.add(child.freeze(nodeToStream));
            }
            LayoutStream frozen = new LayoutStream(forkNode, trunk, frozenChildren);
            for (MigrationNode n : trunk) {
                nodeToStream.put(n.id(), frozen);
            }
            return frozen;
        }
    }

    /**
     * The synthetic virtual root node used only inside the layout tree.
     *
     * <p>Every real root stream is attached beneath this single node so that the tree always has
     * one root; the virtual node carries no real task and is filtered out before rendering by
     * {@link ExecutionGraphView#lines()} and {@link ExecutionGraphView#renderLines}.
     */
    static final class VirtualNode implements MigrationNode {

        private static final NodeId VIRTUAL_ROOT_ID = NodeId.of("__virtual_root__");
        private static final Environment VIRTUAL_ENV =
                new Environment() {
                    @Override
                    public EnvironmentId id() {
                        return EnvironmentId.of("virtual");
                    }

                    @Override
                    public String name() {
                        return "virtual";
                    }
                };

        @Override
        public NodeId id() {
            return VIRTUAL_ROOT_ID;
        }

        @Override
        public String name() {
            return "";
        }

        @Override
        public @Nullable String description() {
            return null;
        }

        @Override
        public Environment environment() {
            return VIRTUAL_ENV;
        }

        @Override
        public Set<NodeId> dependencies() {
            return Set.of();
        }

        /**
         * Always throws, because the virtual root has no executable task.
         *
         * @return never returns normally
         * @throws UnsupportedOperationException always
         */
        @Override
        public Task upTask() {
            throw new UnsupportedOperationException("VirtualNode has no tasks");
        }

        @Override
        public @Nullable Task downTask() {
            return null;
        }
    }
}
