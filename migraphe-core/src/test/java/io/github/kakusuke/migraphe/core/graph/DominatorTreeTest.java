package io.github.kakusuke.migraphe.core.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import org.junit.jupiter.api.Test;

class DominatorTreeTest {

    @Test
    void shouldBeConstructedWithEmptyNodeList() {
        assertThatCode(() -> new DominatorTree(List.of(), false)).doesNotThrowAnyException();
    }

    @Test
    void shouldBeConstructedWithSingleNode() {
        MigrationNode node = TestHelpers.node("a").name("A").build();
        assertThatCode(() -> new DominatorTree(List.of(node), false)).doesNotThrowAnyException();
    }

    @Test
    void nodeMapContainsAllNodes() {
        MigrationNode nodeA = TestHelpers.node("a").name("A").build();
        MigrationNode nodeB = TestHelpers.node("b").name("B").build();

        DominatorTree tree = new DominatorTree(List.of(nodeA, nodeB), false);

        assertThat(tree.nodeMap)
                .containsEntry(NodeId.of("a"), nodeA)
                .containsEntry(NodeId.of("b"), nodeB)
                .hasSize(2);
    }

    @Test
    void findNonDomEdgesReturnsEmptyForLinearChain() {
        MigrationNode nodeA = TestHelpers.node("a").name("A").build();
        MigrationNode nodeB = TestHelpers.node("b").name("B").dependencies(NodeId.of("a")).build();
        MigrationNode nodeC = TestHelpers.node("c").name("C").dependencies(NodeId.of("b")).build();

        DominatorTree tree = new DominatorTree(List.of(nodeA, nodeB, nodeC), false);

        assertThat(tree.findNonDomEdges()).isEmpty();
    }

    @Test
    void trunkChildShouldBeTopoLastChildRegardlessOfSubtreeDepth() {
        MigrationNode nodeA = TestHelpers.node("a").name("A").build();
        MigrationNode nodeB = TestHelpers.node("b").name("B").dependencies(NodeId.of("a")).build();
        MigrationNode nodeC = TestHelpers.node("c").name("C").dependencies(NodeId.of("a")).build();
        MigrationNode nodeE = TestHelpers.node("e").name("E").dependencies(NodeId.of("b")).build();

        DominatorTree tree = new DominatorTree(List.of(nodeA, nodeB, nodeC, nodeE), false);

        assertThat(tree.trunkChild.get(NodeId.of("a"))).isEqualTo(NodeId.of("c"));
    }

    @Test
    void findNonDomEdgesReturnsDiamondEdge() {
        MigrationNode nodeA = TestHelpers.node("a").name("A").build();
        MigrationNode nodeB = TestHelpers.node("b").name("B").dependencies(NodeId.of("a")).build();
        MigrationNode nodeC = TestHelpers.node("c").name("C").dependencies(NodeId.of("a")).build();
        MigrationNode nodeD =
                TestHelpers.node("d")
                        .name("D")
                        .dependencies(NodeId.of("b"), NodeId.of("c"))
                        .build();

        DominatorTree tree = new DominatorTree(List.of(nodeA, nodeB, nodeC, nodeD), false);

        // A→{B,C}→D: idom(D)=A, so both B→D and C→D are non-dom edges
        List<NonDomEdge> edges = tree.findNonDomEdges();
        assertThat(edges).hasSize(2);
        assertThat(edges).allMatch(e -> e.target().equals(NodeId.of("d")));
    }
}
