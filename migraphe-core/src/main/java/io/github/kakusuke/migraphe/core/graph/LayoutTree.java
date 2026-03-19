package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.Task;
import java.util.ArrayList;
import java.util.Collections;
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
     * グラフとレイアウト順序からストリームツリーを再帰的に構築する。
     *
     * <p>Virtual Root (VR) を導入し、全実ルートを VR の子ストリームとして統一的に処理する。 トランク優先:
     * ルートからチェーンを最大限延長し、分岐は子ストリームとして再帰的に構築する。
     *
     * @param graph マイグレーショングラフ
     * @param order レイアウト用トポロジカルソート結果
     * @return LayoutTree
     */
    public static LayoutTree build(MigrationGraph graph, LayoutSort.LayoutOrder order) {
        if (order.nodes().isEmpty()) {
            return new LayoutTree(new LayoutStream(null, List.of(), List.of()), List.of());
        }

        // bucket = まだ割り当てられていないノード集合
        Set<NodeId> bucket = new HashSet<>();
        for (MigrationNode node : order.nodes()) {
            bucket.add(node.id());
        }

        // Virtual root 作成
        MigrationNode virtualRoot = new VirtualNode();

        // 全ルート（依存元なし）を VR の子ストリームとして構築
        List<LayoutStream> rootChildren = new ArrayList<>();
        for (MigrationNode node : order.nodes()) {
            if (graph.getDependencies(node.id()).isEmpty() && bucket.contains(node.id())) {
                rootChildren.add(buildStream(node, virtualRoot.id(), graph, order, bucket));
            }
        }

        // 非ルートだが未割り当てのノード（孤立ノード等）も処理
        for (MigrationNode node : order.nodes()) {
            if (bucket.contains(node.id())) {
                rootChildren.add(buildStream(node, virtualRoot.id(), graph, order, bucket));
            }
        }

        LayoutStream vrStream = new LayoutStream(null, List.of(virtualRoot), rootChildren);
        List<NonTreeEdge> nonTreeEdges = collectNonTreeEdges(graph, vrStream);

        return new LayoutTree(vrStream, nonTreeEdges);
    }

    /**
     * バケット方式でストリームを構築する。トランクを先に全構築し、子ストリームを後ろから構築する。
     *
     * @param startNode ストリームの先頭ノード
     * @param forkNode 分岐元ノードID（ルートストリームでは null）
     * @param graph マイグレーショングラフ
     * @param order レイアウト順序
     * @param bucket 未割り当てノードの集合（副作用で更新される）
     * @return 構築されたストリーム
     */
    private static LayoutStream buildStream(
            MigrationNode startNode,
            @Nullable NodeId forkNode,
            MigrationGraph graph,
            LayoutSort.LayoutOrder order,
            Set<NodeId> bucket) {

        // Phase 1: トランク構築（最大ランク continuation を選択）
        List<MigrationNode> trunk = new ArrayList<>();
        trunk.add(startNode);
        bucket.remove(startNode.id());

        MigrationNode current = startNode;
        while (true) {
            List<NodeId> dependents =
                    graph.getDependents(current.id()).stream()
                            .filter(bucket::contains)
                            .sorted(Comparator.comparingInt(order::rank))
                            .toList();

            if (dependents.isEmpty()) {
                break;
            }

            // 最大ランク = 最も下流のノードを continuation として選択
            NodeId continuationId = dependents.getLast();
            MigrationNode continuationNode = graph.getNode(continuationId).orElse(null);
            if (continuationNode == null) {
                break;
            }

            trunk.add(continuationNode);
            bucket.remove(continuationId);
            current = continuationNode;
        }

        // Phase 2: 子ストリーム構築をトランクの後ろから
        List<LayoutStream> childStreams = new ArrayList<>();
        for (int i = trunk.size() - 1; i >= 0; i--) {
            MigrationNode trunkNode = trunk.get(i);
            List<NodeId> children =
                    graph.getDependents(trunkNode.id()).stream()
                            .filter(bucket::contains)
                            .sorted(Comparator.comparingInt(order::rank).reversed())
                            .toList();

            for (NodeId childId : children) {
                if (!bucket.contains(childId)) {
                    continue; // 他のストリームが先に取った
                }
                MigrationNode childNode = graph.getNode(childId).orElse(null);
                if (childNode == null) {
                    continue;
                }
                childStreams.add(buildStream(childNode, trunkNode.id(), graph, order, bucket));
            }
        }
        Collections.reverse(childStreams);

        return new LayoutStream(forkNode, trunk, childStreams);
    }

    /**
     * ツリー構築後にグラフの全エッジをスキャンし、ツリーエッジでないものを非ツリーエッジとして収集する。
     *
     * <p>ツリーエッジとは: (1) ストリーム内の連続ノード間のエッジ、(2) forkNode から子ストリーム先頭ノードへのエッジ。
     */
    private static List<NonTreeEdge> collectNonTreeEdges(
            MigrationGraph graph, LayoutStream rootStream) {
        Set<String> treeEdges = new HashSet<>();
        collectTreeEdges(rootStream, treeEdges);

        List<NonTreeEdge> nonTreeEdges = new ArrayList<>();
        collectNonTreeEdgesFromStream(rootStream, graph, treeEdges, nonTreeEdges);
        return nonTreeEdges;
    }

    /** ツリーエッジを収集する。 */
    private static void collectTreeEdges(LayoutStream stream, Set<String> treeEdges) {
        List<MigrationNode> nodes = stream.nodes();
        // ストリーム内の連続ノード間エッジ
        for (int i = 0; i < nodes.size() - 1; i++) {
            treeEdges.add(edgeKey(nodes.get(i).id(), nodes.get(i + 1).id()));
        }
        // forkNode → 子ストリーム先頭ノードのエッジ
        for (LayoutStream child : stream.childStreams()) {
            if (child.forkNode() != null && !child.nodes().isEmpty()) {
                treeEdges.add(edgeKey(child.forkNode(), child.nodes().get(0).id()));
            }
            collectTreeEdges(child, treeEdges);
        }
    }

    /** 各ノードの親エッジをスキャンし、ツリーエッジでないものを非ツリーエッジとして追加する。 */
    private static void collectNonTreeEdgesFromStream(
            LayoutStream stream,
            MigrationGraph graph,
            Set<String> treeEdges,
            List<NonTreeEdge> nonTreeEdges) {
        for (MigrationNode node : stream.nodes()) {
            for (NodeId parentId : graph.getDependencies(node.id())) {
                if (!treeEdges.contains(edgeKey(parentId, node.id()))) {
                    nonTreeEdges.add(new NonTreeEdge(parentId, node.id()));
                }
            }
        }
        for (LayoutStream child : stream.childStreams()) {
            collectNonTreeEdgesFromStream(child, graph, treeEdges, nonTreeEdges);
        }
    }

    private static String edgeKey(NodeId from, NodeId to) {
        return from.value() + "->" + to.value();
    }

    /** Virtual Root ノード。レイアウトツリーの内部でのみ使用する。 */
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
