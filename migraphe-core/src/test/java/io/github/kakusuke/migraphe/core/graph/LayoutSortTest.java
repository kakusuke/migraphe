package io.github.kakusuke.migraphe.core.graph;

import static io.github.kakusuke.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LayoutSort")
class LayoutSortTest {

    @Test
    @DisplayName("単一ノードのグラフで nodes() が1件、rank() が0を返す")
    void shouldReturnSingleNodeWithRankZero() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = node("a").build();
        graph.addNode(nodeA);

        LayoutSort.LayoutOrder order = LayoutSort.sort(graph);

        assertThat(order.nodes()).hasSize(1);
        assertThat(order.rank(NodeId.of("a"))).isEqualTo(0);
    }

    @Test
    @DisplayName("A→B→C の線形チェーンで nodes() が順序 [A,B,C]、rank() が 0,1,2 を返す")
    void shouldReturnLinearChainInOrderWithCorrectRanks() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeB = node("b").dependencies(NodeId.of("a")).build();
        MigrationNode nodeC = node("c").dependencies(NodeId.of("b")).build();
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        LayoutSort.LayoutOrder order = LayoutSort.sort(graph);

        assertThat(order.nodes()).containsExactly(nodeA, nodeB, nodeC);
        assertThat(order.rank(NodeId.of("a"))).isEqualTo(0);
        assertThat(order.rank(NodeId.of("b"))).isEqualTo(1);
        assertThat(order.rank(NodeId.of("c"))).isEqualTo(2);
    }

    @Test
    @DisplayName("独立した3ノードを非アルファベット順 (b,a,c) で追加しても nodes() が id 昇順 [a,b,c] を返す")
    void shouldReturnIndependentNodesInIdAscendingOrder() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeB = node("b").build();
        MigrationNode nodeA = node("a").build();
        MigrationNode nodeC = node("c").build();
        graph.addNode(nodeB);
        graph.addNode(nodeA);
        graph.addNode(nodeC);

        LayoutSort.LayoutOrder order = LayoutSort.sort(graph);

        assertThat(order.nodes()).containsExactly(nodeA, nodeB, nodeC);
    }

    @Test
    @DisplayName("ダイヤモンド形グラフ A→B, A→C, B→D, C→D で nodes() が [A,B,C,D]、rank() が 0,1,2,3 を返す")
    void shouldReturnDiamondGraphInOrderWithCorrectRanks() {
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

        assertThat(order.nodes()).containsExactly(nodeA, nodeB, nodeC, nodeD);
        assertThat(order.rank(NodeId.of("a"))).isEqualTo(0);
        assertThat(order.rank(NodeId.of("b"))).isEqualTo(1);
        assertThat(order.rank(NodeId.of("c"))).isEqualTo(2);
        assertThat(order.rank(NodeId.of("d"))).isEqualTo(3);
    }
}
