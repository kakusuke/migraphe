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

/** DAG をストリームツリーに分解する。 */
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

    /**
     * トポロジカル順に走査してストリームツリーをフォワード構築する。
     *
     * <p>各ノードを順に処理し、(1) 親なしなら VR 直下に新規ルートストリーム、(2) 親 stream の末尾なら trunk extension、(3) そうでなければ親
     * stream の child stream として fork、を選ぶ。残った親エッジは非ツリーエッジ。
     *
     * <p>不変条件: 全ての依存 parent → child について、最終的な描画上で row(parent) &lt; row(child) を満たす。これにより {@code
     * GridCanvas.addNonTreeEdge} の上向きスキップガードに引っかかるエッジが構造的に発生しない。
     *
     * @param graph マイグレーショングラフ
     * @param order レイアウト用トポロジカルソート結果
     * @return LayoutTree
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

            // 現時点のツリーで DFS 順位置を計算し、もっとも後ろの親を attach 先に選ぶ。
            // これにより node は最後の親より後ろに描画され、残りの親は上向きにならない。
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
                // 親が stream の末尾なら trunk 拡張
                chosenStream.appendTrunk(node);
                nodeToMutable.put(node.id(), chosenStream);
            } else {
                // 親が末尾でなければ、親で新規 child stream を fork
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
     * 現時点のツリーを DFS 走査して各ノードの描画順位置を返す。{@link GridCanvas} の drawStream と同じ traversal: trunk 内の
     * ノードを順番に出し、各ノードの直後にその fork-children を順に再帰展開する。
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

    /** 構築中の可変ストリーム表現。{@link #freeze} で {@link LayoutStream} に変換される。 */
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
