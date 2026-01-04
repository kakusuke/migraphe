package io.github.migraphe.core.graph;

import static io.github.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.migraphe.api.graph.MigrationNode;
import io.github.migraphe.api.graph.NodeId;
import org.junit.jupiter.api.Test;

class TopologicalSortTest {

    @Test
    void shouldCreateExecutionPlanForSingleNode() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode node = node("node-1").build();
        graph.addNode(node);

        // when
        ExecutionPlan plan = TopologicalSort.createExecutionPlan(graph);

        // then
        assertThat(plan.levelCount()).isEqualTo(1);
        assertThat(plan.totalNodes()).isEqualTo(1);
        assertThat(plan.levels().get(0).nodes()).containsExactly(node);
    }

    @Test
    void shouldCreateExecutionPlanForLinearDependencies() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("node-1");
        NodeId id2 = NodeId.of("node-2");
        NodeId id3 = NodeId.of("node-3");

        MigrationNode node1 = node("node-1").build();
        MigrationNode node2 = node("node-2").dependencies(id1).build();
        MigrationNode node3 = node("node-3").dependencies(id2).build();

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);

        // when
        ExecutionPlan plan = TopologicalSort.createExecutionPlan(graph);

        // then
        assertThat(plan.levelCount()).isEqualTo(3);
        assertThat(plan.levels().get(0).nodes()).containsExactly(node1);
        assertThat(plan.levels().get(1).nodes()).containsExactly(node2);
        assertThat(plan.levels().get(2).nodes()).containsExactly(node3);
    }

    @Test
    void shouldCreateExecutionPlanForParallelNodes() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode node1 = node("node-1").build();
        MigrationNode node2 = node("node-2").build();
        MigrationNode node3 = node("node-3").build();

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);

        // when
        ExecutionPlan plan = TopologicalSort.createExecutionPlan(graph);

        // then
        assertThat(plan.levelCount()).isEqualTo(1);
        assertThat(plan.levels().get(0).nodes()).containsExactlyInAnyOrder(node1, node2, node3);
        assertThat(plan.maxParallelism()).isEqualTo(3);
    }

    @Test
    void shouldCreateExecutionPlanForDiamondDependencies() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("node-1");
        NodeId id2 = NodeId.of("node-2");
        NodeId id3 = NodeId.of("node-3");
        NodeId id4 = NodeId.of("node-4");

        // Diamond: node1 -> {node2, node3} -> node4
        MigrationNode node1 = node("node-1").build();
        MigrationNode node2 = node("node-2").dependencies(id1).build();
        MigrationNode node3 = node("node-3").dependencies(id1).build();
        MigrationNode node4 = node("node-4").dependencies(id2, id3).build();

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);
        graph.addNode(node4);

        // when
        ExecutionPlan plan = TopologicalSort.createExecutionPlan(graph);

        // then
        assertThat(plan.levelCount()).isEqualTo(3);
        assertThat(plan.levels().get(0).nodes()).containsExactly(node1);
        assertThat(plan.levels().get(1).nodes()).containsExactlyInAnyOrder(node2, node3);
        assertThat(plan.levels().get(2).nodes()).containsExactly(node4);
    }

    @Test
    void shouldThrowExceptionForGraphWithCycle() {
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

        // when & then
        assertThatThrownBy(() -> TopologicalSort.createExecutionPlan(graph))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void shouldCreateExecutionPlanForEmptyGraph() {
        // given
        MigrationGraph graph = MigrationGraph.create();

        // when
        ExecutionPlan plan = TopologicalSort.createExecutionPlan(graph);

        // then
        assertThat(plan.levelCount()).isZero();
        assertThat(plan.totalNodes()).isZero();
    }
}
