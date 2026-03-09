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
    @DisplayName(
            "単一ノードのグラフで rootStream() がノードを含み、forkNode() が null、childStreams() と nonTreeEdges()"
                    + " が空である")
    void shouldBuildSingleNodeTreeWithEmptyForkAndEdges() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = node("a").build();
        graph.addNode(nodeA);

        LayoutSort.LayoutOrder order = LayoutSort.sort(graph);
        LayoutTree tree = LayoutTree.build(graph, order);

        assertThat(tree.rootStream()).isNotNull();
        assertThat(tree.rootStream().nodes()).containsExactly(nodeA);
        assertThat(tree.rootStream().forkNode()).isNull();
        assertThat(tree.rootStream().childStreams()).isEmpty();
        assertThat(tree.nonTreeEdges()).isEmpty();
    }

    @Test
    @DisplayName(
            "A→B→C の線形チェーンで rootStream() が全3ノードを含み、forkNode() が null、childStreams() と"
                    + " nonTreeEdges() が空である")
    void shouldBuildLinearChainWithAllNodesInRootStream() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").dependencies(NodeId.of("a")).build();
        MigrationNode nodeC = node("c").dependencies(NodeId.of("b")).build();
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        LayoutSort.LayoutOrder order = LayoutSort.sort(graph);
        LayoutTree tree = LayoutTree.build(graph, order);

        assertThat(tree.rootStream().nodes()).containsExactly(nodeA, nodeB, nodeC);
        assertThat(tree.rootStream().forkNode()).isNull();
        assertThat(tree.rootStream().childStreams()).isEmpty();
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

        LayoutStream rootStream = tree.rootStream();
        LayoutStream childStream = rootStream.childStreams().get(0);

        assertThat(tree.streamOf(NodeId.of("a"))).isSameAs(rootStream);
        assertThat(tree.streamOf(NodeId.of("b"))).isSameAs(rootStream);
        assertThat(tree.streamOf(NodeId.of("c"))).isSameAs(childStream);
        assertThat(tree.streamOf(NodeId.of("d"))).isSameAs(childStream);
    }

    @Test
    @DisplayName(
            "ダイヤモンドグラフ A→B, A→C, B→D, C→D で rootStream が [A,B]、childStream が [C,D] を含み、nonTreeEdges"
                    + " に (D→B) が1件ある")
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

        LayoutStream rootStream = tree.rootStream();
        assertThat(rootStream.nodes()).containsExactly(nodeA, nodeB);
        assertThat(rootStream.forkNode()).isNull();
        assertThat(rootStream.childStreams()).hasSize(1);

        LayoutStream childStream = rootStream.childStreams().get(0);
        assertThat(childStream.forkNode()).isEqualTo(NodeId.of("a"));
        assertThat(childStream.nodes()).containsExactly(nodeC, nodeD);
        assertThat(childStream.childStreams()).isEmpty();

        assertThat(tree.nonTreeEdges()).hasSize(1);
        assertThat(tree.nonTreeEdges().get(0))
                .isEqualTo(new NonTreeEdge(NodeId.of("d"), NodeId.of("b")));
    }
}
