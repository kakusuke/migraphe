package io.github.migraphe.core.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.migraphe.api.environment.EnvironmentId;
import io.github.migraphe.api.graph.NodeId;
import io.github.migraphe.api.history.ExecutionRecord;
import io.github.migraphe.api.history.ExecutionStatus;
import io.github.migraphe.api.task.ExecutionDirection;
import org.junit.jupiter.api.Test;

class ExecutionRecordTest {

    @Test
    void shouldCreateUpSuccessRecord() {
        // given
        NodeId nodeId = NodeId.of("node-1");
        EnvironmentId envId = EnvironmentId.of("dev");
        String description = "Create users table";
        String serializedDownTask = "{\"rollback\":\"drop table\"}";
        long durationMs = 100;

        // when
        ExecutionRecord record =
                ExecutionRecord.upSuccess(
                        nodeId, envId, description, serializedDownTask, durationMs);

        // then
        assertThat(record.nodeId()).isEqualTo(nodeId);
        assertThat(record.environmentId()).isEqualTo(envId);
        assertThat(record.direction()).isEqualTo(ExecutionDirection.UP);
        assertThat(record.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(record.description()).isEqualTo(description);
        assertThat(record.serializedDownTask()).isEqualTo(serializedDownTask);
        assertThat(record.durationMs()).isEqualTo(durationMs);
        assertThat(record.errorMessage()).isNull();
        assertThat(record.id()).isNotBlank();
        assertThat(record.executedAt()).isNotNull();
        assertThat(record.isUp()).isTrue();
        assertThat(record.isDown()).isFalse();
    }

    @Test
    void shouldCreateDownSuccessRecord() {
        // given
        NodeId nodeId = NodeId.of("node-1");
        EnvironmentId envId = EnvironmentId.of("dev");
        String description = "Drop users table";
        long durationMs = 50;

        // when
        ExecutionRecord record =
                ExecutionRecord.downSuccess(nodeId, envId, description, durationMs);

        // then
        assertThat(record.direction()).isEqualTo(ExecutionDirection.DOWN);
        assertThat(record.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(record.serializedDownTask()).isNull();
        assertThat(record.errorMessage()).isNull();
        assertThat(record.isUp()).isFalse();
        assertThat(record.isDown()).isTrue();
    }

    @Test
    void shouldCreateFailureRecord() {
        // given
        NodeId nodeId = NodeId.of("node-1");
        EnvironmentId envId = EnvironmentId.of("staging");
        String description = "Failed migration";
        String errorMessage = "Connection timeout";

        // when
        ExecutionRecord record =
                ExecutionRecord.failure(
                        nodeId, envId, ExecutionDirection.UP, description, errorMessage);

        // then
        assertThat(record.status()).isEqualTo(ExecutionStatus.FAILURE);
        assertThat(record.errorMessage()).isEqualTo(errorMessage);
        assertThat(record.durationMs()).isZero();
    }

    @Test
    void shouldCreateSkippedRecord() {
        // given
        NodeId nodeId = NodeId.of("node-1");
        EnvironmentId envId = EnvironmentId.of("prod");
        String description = "Already executed";
        String reason = "Migration already applied";

        // when
        ExecutionRecord record = ExecutionRecord.skipped(nodeId, envId, description, reason);

        // then
        assertThat(record.status()).isEqualTo(ExecutionStatus.SKIPPED);
        assertThat(record.direction()).isEqualTo(ExecutionDirection.UP);
        assertThat(record.errorMessage()).isEqualTo(reason);
        assertThat(record.serializedDownTask()).isNull();
    }

    @Test
    void shouldThrowExceptionWhenFailureStatusWithoutErrorMessage() {
        // when & then
        assertThatThrownBy(
                        () ->
                                new ExecutionRecord(
                                        "id-1",
                                        NodeId.of("node-1"),
                                        EnvironmentId.of("dev"),
                                        ExecutionDirection.UP,
                                        ExecutionStatus.FAILURE,
                                        java.time.Instant.now(),
                                        "desc",
                                        null,
                                        100L,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failure status requires error message");
    }

    @Test
    void shouldThrowExceptionWhenDownExecutionHasSerializedDownTask() {
        // when & then
        assertThatThrownBy(
                        () ->
                                new ExecutionRecord(
                                        "id-1",
                                        NodeId.of("node-1"),
                                        EnvironmentId.of("dev"),
                                        ExecutionDirection.DOWN,
                                        ExecutionStatus.SUCCESS,
                                        java.time.Instant.now(),
                                        "desc",
                                        "serialized",
                                        100L,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DOWN execution should not have serializedDownTask");
    }

    @Test
    void shouldAllowNullSerializedDownTaskForUp() {
        // when
        ExecutionRecord record =
                ExecutionRecord.upSuccess(
                        NodeId.of("node-1"), EnvironmentId.of("dev"), "desc", null, 100);

        // then
        assertThat(record.serializedDownTask()).isNull();
    }
}
