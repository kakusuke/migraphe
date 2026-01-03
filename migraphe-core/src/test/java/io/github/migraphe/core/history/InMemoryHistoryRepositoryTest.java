package io.github.migraphe.core.history;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.migraphe.core.environment.EnvironmentId;
import io.github.migraphe.core.graph.NodeId;
import io.github.migraphe.core.task.ExecutionDirection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryHistoryRepositoryTest {

    private final EnvironmentId envId = EnvironmentId.of("dev");
    private final EnvironmentId stagingEnvId = EnvironmentId.of("staging");
    private final NodeId node1 = NodeId.of("node-1");
    private final NodeId node2 = NodeId.of("node-2");

    private HistoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryHistoryRepository();
        repository.initialize();
    }

    @Test
    void shouldStartWithNoRecords() {
        // when & then
        assertThat(repository.allRecords(envId)).isEmpty();
        assertThat(repository.executedNodes(envId)).isEmpty();
    }

    @Test
    void shouldRecordExecution() {
        // given
        ExecutionRecord record =
                ExecutionRecord.upSuccess(node1, envId, "Create table", Optional.empty(), 100);

        // when
        repository.record(record);

        // then
        assertThat(repository.allRecords(envId)).containsExactly(record);
    }

    @Test
    void shouldCheckIfNodeWasExecuted() {
        // given
        ExecutionRecord record =
                ExecutionRecord.upSuccess(node1, envId, "Create table", Optional.empty(), 100);

        // when
        repository.record(record);

        // then
        assertThat(repository.wasExecuted(node1, envId)).isTrue();
        assertThat(repository.wasExecuted(node2, envId)).isFalse();
    }

    @Test
    void shouldReturnExecutedNodes() {
        // given
        ExecutionRecord record1 =
                ExecutionRecord.upSuccess(node1, envId, "Create table", Optional.empty(), 100);
        ExecutionRecord record2 =
                ExecutionRecord.upSuccess(node2, envId, "Add column", Optional.empty(), 50);

        // when
        repository.record(record1);
        repository.record(record2);

        // then
        List<NodeId> executed = repository.executedNodes(envId);
        assertThat(executed).containsExactly(node1, node2);
    }

    @Test
    void shouldFindLatestRecordForNode() {
        // given
        ExecutionRecord record1 =
                ExecutionRecord.upSuccess(node1, envId, "First execution", Optional.empty(), 100);
        ExecutionRecord record2 = ExecutionRecord.downSuccess(node1, envId, "Rollback", 50);

        // when
        repository.record(record1);
        repository.record(record2);

        // then
        Optional<ExecutionRecord> latest = repository.findLatestRecord(node1, envId);
        assertThat(latest).isPresent();
        assertThat(latest.get().direction()).isEqualTo(ExecutionDirection.DOWN);
    }

    @Test
    void shouldNotConsiderFailedExecutionAsExecuted() {
        // given
        ExecutionRecord failedRecord =
                ExecutionRecord.failure(node1, envId, ExecutionDirection.UP, "Failed", "Error");

        // when
        repository.record(failedRecord);

        // then
        assertThat(repository.wasExecuted(node1, envId)).isFalse();
    }

    @Test
    void shouldNotConsiderSkippedExecutionAsExecuted() {
        // given
        ExecutionRecord skippedRecord =
                ExecutionRecord.skipped(node1, envId, "Skipped", "Already applied");

        // when
        repository.record(skippedRecord);

        // then
        assertThat(repository.wasExecuted(node1, envId)).isFalse();
    }

    @Test
    void shouldReturnAllRecordsInOrder() {
        // given
        ExecutionRecord record1 =
                ExecutionRecord.upSuccess(node1, envId, "First", Optional.empty(), 100);
        ExecutionRecord record2 =
                ExecutionRecord.upSuccess(node2, envId, "Second", Optional.empty(), 50);

        // when
        repository.record(record1);
        repository.record(record2);

        // then
        assertThat(repository.allRecords(envId)).containsExactly(record1, record2);
    }

    @Test
    void shouldIsolateRecordsBetweenEnvironments() {
        // given
        ExecutionRecord devRecord =
                ExecutionRecord.upSuccess(node1, envId, "Dev migration", Optional.empty(), 100);
        ExecutionRecord stagingRecord =
                ExecutionRecord.upSuccess(
                        node2, stagingEnvId, "Staging migration", Optional.empty(), 50);

        // when
        repository.record(devRecord);
        repository.record(stagingRecord);

        // then
        assertThat(repository.allRecords(envId)).containsExactly(devRecord);
        assertThat(repository.allRecords(stagingEnvId)).containsExactly(stagingRecord);
        assertThat(repository.wasExecuted(node1, envId)).isTrue();
        assertThat(repository.wasExecuted(node1, stagingEnvId)).isFalse();
        assertThat(repository.wasExecuted(node2, envId)).isFalse();
        assertThat(repository.wasExecuted(node2, stagingEnvId)).isTrue();
    }

    @Test
    void shouldReturnEmptyForNonExistentNode() {
        // when
        Optional<ExecutionRecord> latest = repository.findLatestRecord(node1, envId);

        // then
        assertThat(latest).isEmpty();
    }
}
