package io.github.migraphe.core.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.migraphe.core.environment.EnvironmentId;
import io.github.migraphe.core.graph.NodeId;
import io.github.migraphe.core.task.ExecutionDirection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MigrationHistoryTest {

    private final EnvironmentId envId = EnvironmentId.of("dev");
    private final NodeId node1 = NodeId.of("node-1");
    private final NodeId node2 = NodeId.of("node-2");

    @Test
    void shouldCreateHistoryForEnvironment() {
        // when
        MigrationHistory history = MigrationHistory.forEnvironment(envId);

        // then
        assertThat(history.environmentId()).isEqualTo(envId);
        assertThat(history.recordCount()).isZero();
        assertThat(history.allRecords()).isEmpty();
    }

    @Test
    void shouldRecordExecution() {
        // given
        MigrationHistory history = MigrationHistory.forEnvironment(envId);
        ExecutionRecord record =
                ExecutionRecord.upSuccess(node1, envId, "Create table", Optional.empty(), 100);

        // when
        history.record(record);

        // then
        assertThat(history.recordCount()).isEqualTo(1);
        assertThat(history.allRecords()).containsExactly(record);
    }

    @Test
    void shouldCheckIfNodeWasExecuted() {
        // given
        MigrationHistory history = MigrationHistory.forEnvironment(envId);
        ExecutionRecord record =
                ExecutionRecord.upSuccess(node1, envId, "Create table", Optional.empty(), 100);

        // when
        history.record(record);

        // then
        assertThat(history.wasExecuted(node1)).isTrue();
        assertThat(history.wasExecuted(node2)).isFalse();
    }

    @Test
    void shouldReturnExecutedNodes() {
        // given
        MigrationHistory history = MigrationHistory.forEnvironment(envId);
        ExecutionRecord record1 =
                ExecutionRecord.upSuccess(node1, envId, "Create table", Optional.empty(), 100);
        ExecutionRecord record2 =
                ExecutionRecord.upSuccess(node2, envId, "Add column", Optional.empty(), 50);

        // when
        history.record(record1);
        history.record(record2);

        // then
        List<NodeId> executed = history.executedNodes();
        assertThat(executed).containsExactly(node1, node2);
    }

    @Test
    void shouldFindLatestRecordForNode() {
        // given
        MigrationHistory history = MigrationHistory.forEnvironment(envId);
        ExecutionRecord record1 =
                ExecutionRecord.upSuccess(node1, envId, "First execution", Optional.empty(), 100);
        ExecutionRecord record2 = ExecutionRecord.downSuccess(node1, envId, "Rollback", 50);

        // when
        history.record(record1);
        history.record(record2);

        // then
        Optional<ExecutionRecord> latest = history.findLatestRecord(node1);
        assertThat(latest).isPresent();
        assertThat(latest.get().direction()).isEqualTo(ExecutionDirection.DOWN);
    }

    @Test
    void shouldNotConsiderFailedExecutionAsExecuted() {
        // given
        MigrationHistory history = MigrationHistory.forEnvironment(envId);
        ExecutionRecord failedRecord =
                ExecutionRecord.failure(node1, envId, ExecutionDirection.UP, "Failed", "Error");

        // when
        history.record(failedRecord);

        // then
        assertThat(history.wasExecuted(node1)).isFalse();
    }

    @Test
    void shouldNotConsiderSkippedExecutionAsExecuted() {
        // given
        MigrationHistory history = MigrationHistory.forEnvironment(envId);
        ExecutionRecord skippedRecord =
                ExecutionRecord.skipped(node1, envId, "Skipped", "Already applied");

        // when
        history.record(skippedRecord);

        // then
        assertThat(history.wasExecuted(node1)).isFalse();
    }

    @Test
    void shouldThrowExceptionWhenRecordingMismatchedEnvironment() {
        // given
        MigrationHistory history = MigrationHistory.forEnvironment(envId);
        EnvironmentId otherEnv = EnvironmentId.of("staging");
        ExecutionRecord record =
                ExecutionRecord.upSuccess(node1, otherEnv, "Create table", Optional.empty(), 100);

        // when & then
        assertThatThrownBy(() -> history.record(record))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Record environment mismatch");
    }

    @Test
    void shouldReturnAllRecordsInOrder() {
        // given
        MigrationHistory history = MigrationHistory.forEnvironment(envId);
        ExecutionRecord record1 =
                ExecutionRecord.upSuccess(node1, envId, "First", Optional.empty(), 100);
        ExecutionRecord record2 =
                ExecutionRecord.upSuccess(node2, envId, "Second", Optional.empty(), 50);

        // when
        history.record(record1);
        history.record(record2);

        // then
        assertThat(history.allRecords()).containsExactly(record1, record2);
    }
}
