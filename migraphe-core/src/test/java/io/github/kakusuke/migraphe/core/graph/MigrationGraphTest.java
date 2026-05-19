package io.github.kakusuke.migraphe.core.graph;

import static io.github.kakusuke.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.core.common.ValidationResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MigrationGraphTest {

    @Test
    void shouldCreateEmptyGraph() {
        // when
        MigrationGraph graph = MigrationGraph.create();

        // then
        assertThat(graph.size()).isZero();
        assertThat(graph.allNodes()).isEmpty();
        assertThat(graph.getRoots()).isEmpty();
    }

    @Test
    void shouldAddNode() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode node = node("node-1").build();

        // when
        graph.addNode(node);

        // then
        assertThat(graph.size()).isEqualTo(1);
        assertThat(graph.allNodes()).containsExactly(node);
    }

    @Test
    void shouldGetRootNodes() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("node-1");
        NodeId id2 = NodeId.of("node-2");

        MigrationNode root1 = node("node-1").build();
        MigrationNode root2 = node("node-2").build();
        MigrationNode dependent = node("node-3").dependencies(id1, id2).build();

        // when
        graph.addNode(root1);
        graph.addNode(root2);
        graph.addNode(dependent);

        // then
        assertThat(graph.getRoots()).containsExactlyInAnyOrder(root1, root2);
    }

    @Test
    void shouldGetDependencies() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("node-1");
        NodeId id2 = NodeId.of("node-2");

        MigrationNode node1 = node("node-1").build();
        MigrationNode node2 = node("node-2").dependencies(id1).build();

        graph.addNode(node1);
        graph.addNode(node2);

        // when & then
        assertThat(graph.getDependencies(id2)).containsExactly(id1);
        assertThat(graph.getDependencies(id1)).isEmpty();
    }

    @Test
    void shouldGetDependents() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("node-1");
        NodeId id2 = NodeId.of("node-2");
        NodeId id3 = NodeId.of("node-3");

        MigrationNode node1 = node("node-1").build();
        MigrationNode node2 = node("node-2").dependencies(id1).build();
        MigrationNode node3 = node("node-3").dependencies(id1).build();

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);

        // when
        Set<NodeId> dependents = graph.getDependents(id1);

        // then
        assertThat(dependents).containsExactlyInAnyOrder(id2, id3);
    }

    @Test
    void shouldDetectCycle() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("node-1");
        NodeId id2 = NodeId.of("node-2");
        NodeId id3 = NodeId.of("node-3");

        // Create a cycle: node1 -> node2 -> node3 -> node1
        MigrationNode node1 = node("node-1").dependencies(id2).build();
        MigrationNode node2 = node("node-2").dependencies(id3).build();
        MigrationNode node3 = node("node-3").dependencies(id1).build();

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);

        // when & then
        assertThat(graph.hasCycle()).isTrue();
    }

    @Test
    void shouldNotDetectCycleInAcyclicGraph() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("node-1");

        MigrationNode node1 = node("node-1").build();
        MigrationNode node2 = node("node-2").dependencies(id1).build();

        graph.addNode(node1);
        graph.addNode(node2);

        // when & then
        assertThat(graph.hasCycle()).isFalse();
    }

    @Test
    void shouldValidateGraph() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode node = node("node-1").build();
        graph.addNode(node);

        // when
        ValidationResult result = graph.validate();

        // then
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldFailValidationWhenGraphHasCycle() {
        // given: cycle node1 -> node2 -> node1
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode node1 = node("node-1").dependencies(NodeId.of("node-2")).build();
        MigrationNode node2 = node("node-2").dependencies(NodeId.of("node-1")).build();
        graph.addNode(node1);
        graph.addNode(node2);

        // when
        ValidationResult result = graph.validate();

        // then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("cycle"));
    }

    @Test
    void shouldThrowExceptionWhenAddingDuplicateNode() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode node = node("node-1").build();

        // when
        graph.addNode(node);

        // then
        assertThatThrownBy(() -> graph.addNode(node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Node already exists");
    }

    @Test
    void shouldDetectDanglingDependencyViaValidate() {
        // given: addNode no longer validates dependency existence; validate() catches it instead
        MigrationGraph graph = MigrationGraph.create();
        NodeId nonExistent = NodeId.of("non-existent");
        MigrationNode node = node("node-1").dependencies(nonExistent).build();

        // when
        graph.addNode(node); // no longer throws

        // then
        ValidationResult result = graph.validate();
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("depends on non-existent node"));
    }

    @Test
    void fromNodesUpWithDanglingDependency_doesNotThrow() {
        // given
        MigrationNode node = node("node-1").dependencies(NodeId.of("missing")).build();

        // when & then: fromNodesUp must not throw on dangling deps (validate() handles it)
        MigrationGraph graph = MigrationGraph.fromNodesUp(List.of(node));
        assertThat(graph.size()).isEqualTo(1);
        assertThat(graph.validate().isValid()).isFalse();
    }

    @Test
    void shouldGetNodeById() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        NodeId id = NodeId.of("node-1");
        MigrationNode node = node("node-1").build();
        graph.addNode(node);

        // when & then
        assertThat(graph.getNode(id)).hasValue(node);
        assertThat(graph.getNode(NodeId.of("non-existent"))).isEmpty();
    }

    @Test
    void shouldGetAllDependentsRecursively() {
        // given: V001 <- V002 <- V003 <- V004
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("V001");
        NodeId id2 = NodeId.of("V002");
        NodeId id3 = NodeId.of("V003");
        NodeId id4 = NodeId.of("V004");

        MigrationNode node1 = node("V001").build();
        MigrationNode node2 = node("V002").dependencies(id1).build();
        MigrationNode node3 = node("V003").dependencies(id2).build();
        MigrationNode node4 = node("V004").dependencies(id3).build();

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);
        graph.addNode(node4);

        // when
        Set<NodeId> allDependents = graph.getAllDependents(id1);

        // then: V001 に依存する全ノードは V002, V003, V004
        assertThat(allDependents).containsExactlyInAnyOrder(id2, id3, id4);
    }

    @Test
    void shouldGetAllDependentsForMiddleNode() {
        // given: V001 <- V002 <- V003
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("V001");
        NodeId id2 = NodeId.of("V002");
        NodeId id3 = NodeId.of("V003");

        MigrationNode node1 = node("V001").build();
        MigrationNode node2 = node("V002").dependencies(id1).build();
        MigrationNode node3 = node("V003").dependencies(id2).build();

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);

        // when
        Set<NodeId> allDependents = graph.getAllDependents(id2);

        // then: V002 に依存するのは V003 のみ
        assertThat(allDependents).containsExactly(id3);
    }

    @Test
    void shouldReturnEmptySetWhenNoDependents() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("V001");

        MigrationNode node1 = node("V001").build();
        graph.addNode(node1);

        // when
        Set<NodeId> allDependents = graph.getAllDependents(id1);

        // then
        assertThat(allDependents).isEmpty();
    }

    @Test
    void shouldGetAllDependentsWithBranchingGraph() {
        // given: V001 <- V002, V001 <- V003, V002 <- V004
        //        (V003 は V002 に依存しない)
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("V001");
        NodeId id2 = NodeId.of("V002");
        NodeId id4 = NodeId.of("V004");

        MigrationNode node1 = node("V001").build();
        MigrationNode node2 = node("V002").dependencies(id1).build();
        MigrationNode node3 = node("V003").dependencies(id1).build();
        MigrationNode node4 = node("V004").dependencies(id2).build();

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);
        graph.addNode(node4);

        // when: V002 の全依存ノード
        Set<NodeId> dependentsOfV002 = graph.getAllDependents(id2);

        // then: V002 に依存するのは V004 のみ（V003 は含まない）
        assertThat(dependentsOfV002).containsExactly(id4);
    }

    @Test
    void shouldGetAllDependenciesRecursively() {
        // given: V001 <- V002 <- V003 <- V004
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("V001");
        NodeId id2 = NodeId.of("V002");
        NodeId id3 = NodeId.of("V003");
        NodeId id4 = NodeId.of("V004");

        MigrationNode node1 = node("V001").build();
        MigrationNode node2 = node("V002").dependencies(id1).build();
        MigrationNode node3 = node("V003").dependencies(id2).build();
        MigrationNode node4 = node("V004").dependencies(id3).build();

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);
        graph.addNode(node4);

        // when: V004 が依存する全ノード
        Set<NodeId> allDependencies = graph.getAllDependencies(id4);

        // then: V004 が依存するのは V001, V002, V003
        assertThat(allDependencies).containsExactlyInAnyOrder(id1, id2, id3);
    }

    @Test
    void shouldGetAllDependenciesForMiddleNode() {
        // given: V001 <- V002 <- V003
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("V001");
        NodeId id2 = NodeId.of("V002");

        MigrationNode node1 = node("V001").build();
        MigrationNode node2 = node("V002").dependencies(id1).build();
        MigrationNode node3 = node("V003").dependencies(id2).build();

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);

        // when: V002 が依存する全ノード
        Set<NodeId> allDependencies = graph.getAllDependencies(id2);

        // then: V002 が依存するのは V001 のみ
        assertThat(allDependencies).containsExactly(id1);
    }

    @Test
    void shouldReturnEmptySetWhenNoDependencies() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("V001");

        MigrationNode node1 = node("V001").build();
        graph.addNode(node1);

        // when: V001 が依存する全ノード（ルートノードなので依存なし）
        Set<NodeId> allDependencies = graph.getAllDependencies(id1);

        // then
        assertThat(allDependencies).isEmpty();
    }

    @Test
    void fromNodesDown_twoNodesWithDependency_reversesAdjacency() {
        // given: B depends on A
        MigrationNode nodeA = node("node-a").build();
        MigrationNode nodeB = node("node-b").dependencies(NodeId.of("node-a")).build();

        // when
        MigrationGraph graph = MigrationGraph.fromNodesDown(List.of(nodeA, nodeB));

        // then: reversed adjacency — A's deps contain B, B's deps are empty
        assertThat(graph.getDependencies(NodeId.of("node-a"))).containsExactly(NodeId.of("node-b"));
        assertThat(graph.getDependencies(NodeId.of("node-b"))).isEmpty();
    }

    @Test
    void fromNodesDown_singleNodeWithoutDependencies_returnsGraphWithSizeOne() {
        // given
        MigrationNode node = node("node-1").build();

        // when
        MigrationGraph graph = MigrationGraph.fromNodesDown(List.of(node));

        // then
        assertThat(graph.size()).isEqualTo(1);
        assertThat(graph.allNodes()).containsExactly(node);
    }

    @Test
    void fromNodesUp_singleNodeWithoutDependencies_returnsGraphWithSizeOne() {
        // given
        MigrationNode node = node("node-1").build();

        // when
        MigrationGraph graph = MigrationGraph.fromNodesUp(List.of(node));

        // then
        assertThat(graph.size()).isEqualTo(1);
        assertThat(graph.allNodes()).containsExactly(node);
    }

    @Test
    void fromNodesDown_getRoots_returnsLeafOfOriginalGraph() {
        // given: B depends on A (A is root, B is leaf in UP graph)
        MigrationNode nodeA = node("node-a").build();
        MigrationNode nodeB = node("node-b").dependencies(NodeId.of("node-a")).build();

        // when: DOWN graph reverses edges — A's adjacency = {B}, B's adjacency = {}
        MigrationGraph graph = MigrationGraph.fromNodesDown(List.of(nodeA, nodeB));

        // then: getRoots() should reflect adjacency (B has empty adjacency = root of DOWN graph)
        assertThat(graph.getRoots()).containsExactlyInAnyOrder(nodeB);
    }

    @Test
    void shouldGetAllDependenciesWithDiamondDependencies() {
        // given: V001 <- V002, V001 <- V003, V002+V003 <- V004 (ダイヤモンド)
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("V001");
        NodeId id2 = NodeId.of("V002");
        NodeId id3 = NodeId.of("V003");
        NodeId id4 = NodeId.of("V004");

        MigrationNode node1 = node("V001").build();
        MigrationNode node2 = node("V002").dependencies(id1).build();
        MigrationNode node3 = node("V003").dependencies(id1).build();
        MigrationNode node4 = node("V004").dependencies(id2, id3).build();

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);
        graph.addNode(node4);

        // when: V004 が依存する全ノード
        Set<NodeId> allDependencies = graph.getAllDependencies(id4);

        // then: V004 は V001, V002, V003 に依存（V001 は重複しない）
        assertThat(allDependencies).containsExactlyInAnyOrder(id1, id2, id3);
    }
}
