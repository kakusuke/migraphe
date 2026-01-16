package io.github.kakusuke.migraphe.core.graph;

import static io.github.kakusuke.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.Set;
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

    @Test
    void shouldCreateReverseExecutionPlanForLinearDependencies() {
        // given: V001 <- V002 <- V003 (V003 は V002 に依存, V002 は V001 に依存)
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

        // when: V002, V003 を対象に逆順プラン生成
        ExecutionPlan plan = TopologicalSort.createReverseExecutionPlanFor(graph, Set.of(id2, id3));

        // then: V003 → V002 の順で実行（依存されている側からロールバック）
        assertThat(plan.levelCount()).isEqualTo(2);
        assertThat(plan.levels().get(0).nodes()).extracting(MigrationNode::id).containsExactly(id3);
        assertThat(plan.levels().get(1).nodes()).extracting(MigrationNode::id).containsExactly(id2);
    }

    @Test
    void shouldCreateReverseExecutionPlanForSubsetOfNodes() {
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

        // when: V003, V004 のみを対象
        ExecutionPlan plan = TopologicalSort.createReverseExecutionPlanFor(graph, Set.of(id3, id4));

        // then: V004 → V003 の順
        assertThat(plan.levelCount()).isEqualTo(2);
        assertThat(plan.levels().get(0).nodes()).extracting(MigrationNode::id).containsExactly(id4);
        assertThat(plan.levels().get(1).nodes()).extracting(MigrationNode::id).containsExactly(id3);
    }

    @Test
    void shouldCreateReverseExecutionPlanWithParallelNodes() {
        // given: V001 <- V002, V001 <- V003 (V002, V003 は並列)
        MigrationGraph graph = MigrationGraph.create();
        NodeId id1 = NodeId.of("V001");
        NodeId id2 = NodeId.of("V002");
        NodeId id3 = NodeId.of("V003");

        MigrationNode node1 = node("V001").build();
        MigrationNode node2 = node("V002").dependencies(id1).build();
        MigrationNode node3 = node("V003").dependencies(id1).build();

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);

        // when: V002, V003 を対象に逆順プラン生成
        ExecutionPlan plan = TopologicalSort.createReverseExecutionPlanFor(graph, Set.of(id2, id3));

        // then: V002, V003 は同一レベル（並列実行可能）
        assertThat(plan.levelCount()).isEqualTo(1);
        assertThat(plan.levels().get(0).nodes())
                .extracting(MigrationNode::id)
                .containsExactlyInAnyOrder(id2, id3);
    }

    @Test
    void shouldReturnEmptyPlanForEmptyTargetNodes() {
        // given
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode node1 = node("V001").build();
        graph.addNode(node1);

        // when
        ExecutionPlan plan = TopologicalSort.createReverseExecutionPlanFor(graph, Set.of());

        // then
        assertThat(plan.levelCount()).isZero();
        assertThat(plan.totalNodes()).isZero();
    }
}
