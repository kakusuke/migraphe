package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** DAG をストリームツリーに分解する。 */
public final class LayoutTree {

    private final LayoutStream rootStream;
    private final List<NonTreeEdge> nonTreeEdges;
    private final Map<NodeId, LayoutStream> nodeToStream;

    private LayoutTree(LayoutStream rootStream, List<NonTreeEdge> nonTreeEdges) {
        this.rootStream = rootStream;
        this.nonTreeEdges = List.copyOf(nonTreeEdges);
        Map<NodeId, LayoutStream> map = new HashMap<>();
        populateNodeToStream(rootStream, map);
        this.nodeToStream = Map.copyOf(map);
    }

    public LayoutStream rootStream() {
        return rootStream;
    }

    public List<NonTreeEdge> nonTreeEdges() {
        return nonTreeEdges;
    }

    public LayoutStream streamOf(NodeId nodeId) {
        LayoutStream stream = nodeToStream.get(nodeId);
        if (stream == null) {
            throw new IllegalArgumentException("Node not found in tree: " + nodeId);
        }
        return stream;
    }

    private static void populateNodeToStream(LayoutStream stream, Map<NodeId, LayoutStream> map) {
        for (MigrationNode node : stream.nodes()) {
            map.put(node.id(), stream);
        }
        for (LayoutStream child : stream.childStreams()) {
            populateNodeToStream(child, map);
        }
    }

    /**
     * グラフとレイアウト順序からストリームツリーを構築する。
     *
     * @param graph マイグレーショングラフ
     * @param order レイアウト用トポロジカルソート結果
     * @return LayoutTree
     */
    public static LayoutTree build(MigrationGraph graph, LayoutSort.LayoutOrder order) {
        // nodeId -> which stream it belongs to
        Map<NodeId, LayoutStream> assignedStream = new HashMap<>();
        // stream -> mutable child list (built up as children are discovered)
        Map<LayoutStream, List<LayoutStream>> childrenBuilder = new HashMap<>();
        // collected non-tree edges
        List<NonTreeEdge> nonTreeEdges = new ArrayList<>();

        // unassigned nodes in topo order
        List<MigrationNode> remaining = new ArrayList<>(order.nodes());

        LayoutStream root = null;

        while (!remaining.isEmpty()) {
            // pick the first unassigned node that has all parents already assigned,
            // using min rank (topo order guarantees the first eligible is the min-rank one)
            MigrationNode startNode = remaining.get(0);
            remaining.remove(0);

            // determine forkNode: the assigned parent with the highest rank
            NodeId forkNode = chooseForkParent(startNode.id(), graph, order, assignedStream);

            // collect non-tree edges: parents other than forkNode
            for (NodeId parentId : graph.getDependencies(startNode.id())) {
                if (!parentId.equals(forkNode)) {
                    nonTreeEdges.add(new NonTreeEdge(startNode.id(), parentId));
                }
            }

            // build the stream greedily
            List<MigrationNode> streamNodes = new ArrayList<>();
            streamNodes.add(startNode);

            MigrationNode tail = startNode;
            while (true) {
                // find a dependent of tail that can continue this stream:
                // it must be unassigned and all its parents must be assigned
                // (after we tentatively "assign" the current stream nodes)
                MigrationNode next =
                        findContinuation(
                                tail, graph, order, remaining, assignedStream, streamNodes);
                if (next == null) {
                    break;
                }
                remaining.remove(next);

                // non-tree edges for next's extra parents
                NodeId continuationFork =
                        chooseForkParent(next.id(), graph, order, assignedStream, streamNodes);
                for (NodeId parentId : graph.getDependencies(next.id())) {
                    if (!parentId.equals(continuationFork) && !parentId.equals(tail.id())) {
                        nonTreeEdges.add(new NonTreeEdge(next.id(), parentId));
                    }
                }

                streamNodes.add(next);
                tail = next;
            }

            // create the stream (childStreams filled in later)
            LayoutStream stream = new LayoutStream(forkNode, streamNodes, List.of());
            childrenBuilder.put(stream, new ArrayList<>());

            // register assignments
            for (MigrationNode n : streamNodes) {
                assignedStream.put(n.id(), stream);
            }

            // attach to parent stream
            if (forkNode != null) {
                LayoutStream parentStream = assignedStream.get(forkNode);
                if (parentStream != null) {
                    List<LayoutStream> siblings = childrenBuilder.get(parentStream);
                    if (siblings != null) {
                        siblings.add(stream);
                    }
                }
            } else if (root == null) {
                root = stream;
            }
        }

        if (root == null) {
            // empty graph
            root = new LayoutStream(null, List.of(), List.of());
        }

        // rebuild streams with proper childStreams lists
        root = rebuildWithChildren(root, childrenBuilder);

        return new LayoutTree(root, nonTreeEdges);
    }

    /** 現在のストリーム先頭ノードの "forkNode" を選ぶ。 割り当て済み親のうち最もランクが高い（最後に処理された）ものを返す。 */
    private static @Nullable NodeId chooseForkParent(
            NodeId nodeId,
            MigrationGraph graph,
            LayoutSort.LayoutOrder order,
            Map<NodeId, LayoutStream> assignedStream) {
        return graph.getDependencies(nodeId).stream()
                .filter(assignedStream::containsKey)
                .max(Comparator.comparingInt(order::rank))
                .orElse(null);
    }

    /** ストリーム延長中の "forkNode" 選択: 割り当て済みノードと現在ストリームのノードも考慮。 */
    private static @Nullable NodeId chooseForkParent(
            NodeId nodeId,
            MigrationGraph graph,
            LayoutSort.LayoutOrder order,
            Map<NodeId, LayoutStream> assignedStream,
            List<MigrationNode> currentStreamNodes) {
        Set<NodeId> currentIds = new HashSet<>();
        for (MigrationNode n : currentStreamNodes) {
            currentIds.add(n.id());
        }
        return graph.getDependencies(nodeId).stream()
                .filter(p -> assignedStream.containsKey(p) || currentIds.contains(p))
                .max(Comparator.comparingInt(order::rank))
                .orElse(null);
    }

    /**
     * tail の後続ノードのうちストリームを継続できるものを返す。 継続条件: remaining に存在し、全親が assignedStream か currentStreamNodes
     * に含まれる。
     */
    private static @Nullable MigrationNode findContinuation(
            MigrationNode tail,
            MigrationGraph graph,
            LayoutSort.LayoutOrder order,
            List<MigrationNode> remaining,
            Map<NodeId, LayoutStream> assignedStream,
            List<MigrationNode> currentStreamNodes) {
        Set<NodeId> currentIds = new HashSet<>();
        for (MigrationNode n : currentStreamNodes) {
            currentIds.add(n.id());
        }
        Set<NodeId> remainingIds = new HashSet<>();
        for (MigrationNode n : remaining) {
            remainingIds.add(n.id());
        }

        return graph.getDependents(tail.id()).stream()
                .filter(remainingIds::contains)
                .filter(
                        depId -> {
                            for (NodeId parentId : graph.getDependencies(depId)) {
                                if (!assignedStream.containsKey(parentId)
                                        && !currentIds.contains(parentId)) {
                                    return false;
                                }
                            }
                            return true;
                        })
                .min(Comparator.comparingInt(order::rank))
                .flatMap(graph::getNode)
                .orElse(null);
    }

    /** childrenBuilder の情報を使って新しい LayoutStream を再帰的に構築する。 */
    private static LayoutStream rebuildWithChildren(
            LayoutStream stream, Map<LayoutStream, List<LayoutStream>> childrenBuilder) {
        List<LayoutStream> children = childrenBuilder.getOrDefault(stream, List.of());
        List<LayoutStream> rebuiltChildren = new ArrayList<>();
        for (LayoutStream child : children) {
            rebuiltChildren.add(rebuildWithChildren(child, childrenBuilder));
        }
        return new LayoutStream(stream.forkNode(), stream.nodes(), rebuiltChildren);
    }
}
