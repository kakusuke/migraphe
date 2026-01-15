package io.github.kakusuke.migraphe.core.graph;

import static io.github.kakusuke.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.common.ValidationResult;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
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
        NodeId id3 = NodeId.of("node-3");

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

        // Add nodes without dependencies first
        MigrationNode node1 = node("node-1").build();
        MigrationNode node2 = node("node-2").build();
        MigrationNode node3 = node("node-3").build();

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);

        // Create a cycle: node1 -> node2 -> node3 -> node1
        graph.addDependency(id1, id2);
        graph.addDependency(id2, id3);
        graph.addDependency(id3, id1);

        // when & then
        assertThat(graph.hasCycle()).isTrue();
    }

    @Test
    void shouldNotDetectCycleInAcyclicGraph() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("node-1");
        NodeId id2 = NodeId.of("node-2");

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
        // given
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("node-1");
        NodeId id2 = NodeId.of("node-2");

        MigrationNode node1 = node("node-1").build();
        MigrationNode node2 = node("node-2").build();

        graph.addNode(node1);
        graph.addNode(node2);

        // Create a cycle
        graph.addDependency(id1, id2);
        graph.addDependency(id2, id1);

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
    void shouldThrowExceptionWhenDependencyDoesNotExist() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        NodeId nonExistent = NodeId.of("non-existent");
        MigrationNode node = node("node-1").dependencies(nonExistent).build();

        // when & then
        assertThatThrownBy(() -> graph.addNode(node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dependency node does not exist");
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
    void shouldAddDependencyBetweenExistingNodes() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("node-1");
        NodeId id2 = NodeId.of("node-2");

        MigrationNode node1 = node("node-1").build();
        MigrationNode node2 = node("node-2").build();

        graph.addNode(node1);
        graph.addNode(node2);

        // when
        graph.addDependency(id2, id1);

        // then
        assertThat(graph.getDependencies(id2)).contains(id1);
    }
}
