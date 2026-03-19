package io.github.kakusuke.migraphe.core.graph;

import static io.github.kakusuke.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LayoutTree")
class LayoutTreeTest {

    @Test
    @DisplayName("単一ノードのグラフで VR がルートストリーム、実ノードが子ストリームに含まれる")
    void shouldBuildSingleNodeTreeWithVirtualRoot() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = node("a").build();
        graph.addNode(nodeA);

        LayoutSort.LayoutOrder order = LayoutSort.sort(graph);
        LayoutTree tree = LayoutTree.build(graph, order);

        // rootStream は VR
        assertThat(tree.rootStream().nodes()).hasSize(1);
        assertThat(tree.rootStream().nodes().get(0)).isInstanceOf(LayoutTree.VirtualNode.class);
        assertThat(tree.rootStream().forkNode()).isNull();

        // 実ノードは子ストリーム
        assertThat(tree.rootStream().childStreams()).hasSize(1);
        LayoutStream realStream = tree.rootStream().childStreams().get(0);
        assertThat(realStream.nodes()).containsExactly(nodeA);
        assertThat(realStream.childStreams()).isEmpty();
        assertThat(tree.nonTreeEdges()).isEmpty();
    }

    @Test
    @DisplayName("A→B→C の線形チェーンで VR がルート、実ノードが子ストリームに含まれる")
    void shouldBuildLinearChainWithVirtualRoot() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").dependencies(NodeId.of("a")).build();
        MigrationNode nodeC = node("c").dependencies(NodeId.of("b")).build();
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        LayoutSort.LayoutOrder order = LayoutSort.sort(graph);
        LayoutTree tree = LayoutTree.build(graph, order);

        // rootStream は VR、子ストリームに [A, B, C]
        assertThat(tree.rootStream().childStreams()).hasSize(1);
        LayoutStream realStream = tree.rootStream().childStreams().get(0);
        assertThat(realStream.nodes()).containsExactly(nodeA, nodeB, nodeC);
        assertThat(realStream.childStreams()).isEmpty();
        assertThat(tree.nonTreeEdges()).isEmpty();
    }

    @Test
    @DisplayName("ダイヤモンドグラフ A→B, A→C, B→D, C→D で streamOf() が各ノードの所属ストリームを返す")
    void streamOfReturnsOwningStreamForEachNodeInDiamondGraph() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").dependencies(NodeId.of("a")).build();
        MigrationNode nodeC = node("c").dependencies(NodeId.of("a")).build();
        MigrationNode nodeD = node("d").dependencies(NodeId.of("b"), NodeId.of("c")).build();
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);
        graph.addNode(nodeD);

        LayoutSort.LayoutOrder order = LayoutSort.sort(graph);
        LayoutTree tree = LayoutTree.build(graph, order);

        // VR の子ストリーム = 実ルートストリーム [A, C, D]（最大ランク continuation）
        LayoutStream realRoot = tree.rootStream().childStreams().get(0);
        LayoutStream childStream = realRoot.childStreams().get(0);

        assertThat(tree.streamOf(NodeId.of("a"))).isSameAs(realRoot);
        assertThat(tree.streamOf(NodeId.of("c"))).isSameAs(realRoot);
        assertThat(tree.streamOf(NodeId.of("d"))).isSameAs(realRoot);
        assertThat(tree.streamOf(NodeId.of("b"))).isSameAs(childStream);
    }

    @Test
    @DisplayName("ダイヤモンドグラフで VR → 実ルート [A,C,D] → 子 [B]、nonTreeEdges に (B→D)")
    void shouldBuildDiamondGraphWithForkStreamAndNonTreeEdge() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").dependencies(NodeId.of("a")).build();
        MigrationNode nodeC = node("c").dependencies(NodeId.of("a")).build();
        MigrationNode nodeD = node("d").dependencies(NodeId.of("b"), NodeId.of("c")).build();
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);
        graph.addNode(nodeD);

        LayoutSort.LayoutOrder order = LayoutSort.sort(graph);
        LayoutTree tree = LayoutTree.build(graph, order);

        // VR の子ストリーム（最大ランク continuation で [A, C, D]）
        assertThat(tree.rootStream().childStreams()).hasSize(1);
        LayoutStream realRoot = tree.rootStream().childStreams().get(0);
        assertThat(realRoot.nodes()).containsExactly(nodeA, nodeC, nodeD);
        assertThat(realRoot.childStreams()).hasSize(1);

        LayoutStream childStream = realRoot.childStreams().get(0);
        assertThat(childStream.forkNode()).isEqualTo(NodeId.of("a"));
        assertThat(childStream.nodes()).containsExactly(nodeB);
        assertThat(childStream.childStreams()).isEmpty();

        assertThat(tree.nonTreeEdges()).hasSize(1);
        assertThat(tree.nonTreeEdges().get(0))
                .isEqualTo(new NonTreeEdge(NodeId.of("b"), NodeId.of("d")));
    }

    @Test
    @DisplayName("A→B, A→C, B→C のグラフでストリーム末尾からフォークが発生しない")
    void shouldNotForkFromLastNodeInStream() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").dependencies(NodeId.of("a")).build();
        MigrationNode nodeC = node("c").dependencies(NodeId.of("a"), NodeId.of("b")).build();
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        LayoutSort.LayoutOrder order = LayoutSort.sort(graph);
        LayoutTree tree = LayoutTree.build(graph, order);

        // 最大ランク continuation で [A, C]、子ストリームに [B]
        LayoutStream realRoot = tree.rootStream().childStreams().get(0);
        assertThat(realRoot.nodes()).containsExactly(nodeA, nodeC);
        assertThat(realRoot.childStreams()).hasSize(1);

        LayoutStream childStream = realRoot.childStreams().get(0);
        assertThat(childStream.forkNode()).isEqualTo(NodeId.of("a"));
        assertThat(childStream.nodes()).containsExactly(nodeB);

        assertThat(tree.nonTreeEdges()).hasSize(1);
        assertThat(tree.nonTreeEdges().get(0))
                .isEqualTo(new NonTreeEdge(NodeId.of("b"), NodeId.of("c")));

        // 末尾フォークが発生しないことを確認（VR は除外）
        assertNoEndFork(realRoot);
    }

    @Test
    @DisplayName(
            "base→tiers, base→users, tiers→m, users→m, tiers→p, users→p で" + "再帰的構築により末尾フォークが発生しない")
    void shouldBuildRecursivelyAvoidingEndForkWithSharedDependents() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode base = node("base").build();
        MigrationNode tiers = node("tiers").dependencies(NodeId.of("base")).build();
        MigrationNode users = node("users").dependencies(NodeId.of("base")).build();
        MigrationNode memberships =
                node("memberships").dependencies(NodeId.of("tiers"), NodeId.of("users")).build();
        MigrationNode points =
                node("points").dependencies(NodeId.of("tiers"), NodeId.of("users")).build();
        graph.addNode(base);
        graph.addNode(tiers);
        graph.addNode(users);
        graph.addNode(memberships);
        graph.addNode(points);

        LayoutSort.LayoutOrder order = LayoutSort.sort(graph);
        LayoutTree tree = LayoutTree.build(graph, order);

        // VR の子ストリームから末尾フォーク検証
        LayoutStream realRoot = tree.rootStream().childStreams().get(0);
        assertNoEndFork(realRoot);
    }

    @Test
    @DisplayName("マルチルートグラフ A(root), X(root), A→B で全ノードがツリーに含まれ streamOf() が正しく返す")
    void shouldBuildMultiRootGraphWithVirtualRoot() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").dependencies(NodeId.of("a")).build();
        MigrationNode nodeX = node("x").build();
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeX);

        LayoutSort.LayoutOrder order = LayoutSort.sort(graph);
        LayoutTree tree = LayoutTree.build(graph, order);

        // VR がルートストリーム
        assertThat(tree.rootStream().nodes().get(0)).isInstanceOf(LayoutTree.VirtualNode.class);

        // 2つの子ストリーム（2つのルート）
        assertThat(tree.rootStream().childStreams()).hasSize(2);

        // streamOf() で全ノードが見つかる
        assertThat(tree.streamOf(NodeId.of("a"))).isNotNull();
        assertThat(tree.streamOf(NodeId.of("b"))).isNotNull();
        assertThat(tree.streamOf(NodeId.of("x"))).isNotNull();

        // A と B は同じストリーム
        assertThat(tree.streamOf(NodeId.of("a"))).isSameAs(tree.streamOf(NodeId.of("b")));
        // X は別のストリーム
        assertThat(tree.streamOf(NodeId.of("x"))).isNotSameAs(tree.streamOf(NodeId.of("a")));

        assertThat(tree.nonTreeEdges()).isEmpty();
    }

    /** ストリームの末尾ノードに子ストリームが分岐していないことを再帰的に検証する。 */
    private void assertNoEndFork(LayoutStream stream) {
        if (!stream.childStreams().isEmpty()) {
            MigrationNode lastNode = stream.nodes().get(stream.nodes().size() - 1);
            for (LayoutStream child : stream.childStreams()) {
                assertThat(child.forkNode())
                        .as("ストリーム末尾ノード %s からフォークが発生してはならない", lastNode.id())
                        .isNotEqualTo(lastNode.id());
            }
        }
        for (LayoutStream child : stream.childStreams()) {
            assertNoEndFork(child);
        }
    }
}
