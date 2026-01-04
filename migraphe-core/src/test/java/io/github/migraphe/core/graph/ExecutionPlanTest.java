package io.github.migraphe.core.graph;

import static io.github.migraphe.core.graph.TestHelpers.node;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.migraphe.api.graph.MigrationNode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExecutionPlanTest {

    @Test
    void shouldCreateExecutionPlan() {
        // given
        MigrationNode node1 = node("node-1").build();
        MigrationNode node2 = node("node-2").build();

        ExecutionLevel level1 = new ExecutionLevel(0, Set.of(node1));
        ExecutionLevel level2 = new ExecutionLevel(1, Set.of(node2));

        // when
        ExecutionPlan plan = new ExecutionPlan(List.of(level1, level2));

        // then
        assertThat(plan.levels()).hasSize(2);
        assertThat(plan.levelCount()).isEqualTo(2);
        assertThat(plan.totalNodes()).isEqualTo(2);
    }

    @Test
    void shouldCalculateMaxParallelism() {
        // given
        MigrationNode node1 = node("node-1").build();
        MigrationNode node2 = node("node-2").build();
        MigrationNode node3 = node("node-3").build();

        ExecutionLevel level1 = new ExecutionLevel(0, Set.of(node1));
        ExecutionLevel level2 = new ExecutionLevel(1, Set.of(node2, node3)); // 2 parallel nodes

        // when
        ExecutionPlan plan = new ExecutionPlan(List.of(level1, level2));

        // then
        assertThat(plan.maxParallelism()).isEqualTo(2);
    }

    @Test
    void shouldReturnImmutableLevels() {
        // given
        MigrationNode node = node("node-1").build();
        ExecutionLevel level = new ExecutionLevel(0, Set.of(node));
        ExecutionPlan plan = new ExecutionPlan(List.of(level));

        // when & then
        assertThat(plan.levels()).isUnmodifiable();
    }

    @Test
    void shouldCalculateTotalNodes() {
        // given
        MigrationNode node1 = node("node-1").build();
        MigrationNode node2 = node("node-2").build();
        MigrationNode node3 = node("node-3").build();

        ExecutionLevel level1 = new ExecutionLevel(0, Set.of(node1, node2));
        ExecutionLevel level2 = new ExecutionLevel(1, Set.of(node3));

        // when
        ExecutionPlan plan = new ExecutionPlan(List.of(level1, level2));

        // then
        assertThat(plan.totalNodes()).isEqualTo(3);
    }
}
