package io.github.kakusuke.migraphe.core.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionOrigin;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.ExecutionStatus;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionRecordTest {

    @Test
    void shouldCreateUpSuccessRecord() {
        // given
        NodeId nodeId = NodeId.of("node-1");
        TargetId targetId = TargetId.of("dev");
        String description = "Create users table";
        String serializedDownTask = "{\"rollback\":\"drop table\"}";
        long durationMs = 100;

        // when
        ExecutionRecord record =
                ExecutionRecord.upSuccess(
                        nodeId, targetId, description, serializedDownTask, durationMs);

        // then
        assertThat(record.nodeId()).isEqualTo(nodeId);
        assertThat(record.targetId()).isEqualTo(targetId);
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
    void anEmptyDependencyListIsNotTheSameAsAnUnrecordedOne() {
        ExecutionRecord standingOnTwo =
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/003_c"),
                        TargetId.of("dev"),
                        "Add index",
                        "DROP INDEX i",
                        100,
                        null,
                        null,
                        List.of(NodeId.of("db1/001_a"), NodeId.of("db1/002_b")));
        ExecutionRecord standingOnNothing =
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/001_a"),
                        TargetId.of("dev"),
                        "Create users",
                        "DROP TABLE users",
                        100,
                        null,
                        null,
                        List.of());
        ExecutionRecord writtenBeforeTheColumn =
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/001_a"),
                        TargetId.of("dev"),
                        "Create users",
                        "DROP TABLE users",
                        100);

        assertThat(standingOnTwo.dependencies())
                .containsExactly(NodeId.of("db1/001_a"), NodeId.of("db1/002_b"));
        assertThat(standingOnNothing.dependencies()).isEmpty();
        assertThat(writtenBeforeTheColumn.dependencies()).isNull();
    }

    @Test
    void pluginMetadataIsCarriedOpaquelyAndIsNotTiedToTheUpDirection() {
        ExecutionRecord withMetadata =
                ExecutionRecord.upSuccess(
                        NodeId.of("node-1"),
                        TargetId.of("dev"),
                        "Create users table",
                        "DROP TABLE users",
                        100,
                        "fingerprint-token",
                        "autocommit.down=true\n");
        ExecutionRecord withoutMetadata =
                ExecutionRecord.upSuccess(
                        NodeId.of("node-1"),
                        TargetId.of("dev"),
                        "Create users table",
                        "DROP TABLE users",
                        100);
        ExecutionRecord rolledBack =
                new ExecutionRecord(
                        "id-1",
                        NodeId.of("node-1"),
                        TargetId.of("dev"),
                        ExecutionDirection.DOWN,
                        ExecutionStatus.SUCCESS,
                        Instant.now(),
                        "Drop users table",
                        null,
                        50,
                        null,
                        null,
                        "autocommit.down=true\n",
                        null,
                        ExecutionOrigin.EXECUTED,
                        null);

        assertThat(withMetadata.pluginMetadata()).isEqualTo("autocommit.down=true\n");
        assertThat(withoutMetadata.pluginMetadata()).isNull();
        assertThat(rolledBack.pluginMetadata()).isEqualTo("autocommit.down=true\n");
    }

    @Test
    void shouldCreateDownSuccessRecord() {
        // given
        NodeId nodeId = NodeId.of("node-1");
        TargetId targetId = TargetId.of("dev");
        String description = "Drop users table";
        long durationMs = 50;

        // when
        ExecutionRecord record =
                ExecutionRecord.downSuccess(nodeId, targetId, description, durationMs);

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
        TargetId targetId = TargetId.of("staging");
        String description = "Failed migration";
        String errorMessage = "Connection timeout";

        // when
        ExecutionRecord record =
                ExecutionRecord.failure(
                        nodeId, targetId, ExecutionDirection.UP, description, errorMessage);

        // then
        assertThat(record.status()).isEqualTo(ExecutionStatus.FAILURE);
        assertThat(record.errorMessage()).isEqualTo(errorMessage);
        assertThat(record.durationMs()).isZero();
    }

    @Test
    void shouldCreateSkippedRecord() {
        // given
        NodeId nodeId = NodeId.of("node-1");
        TargetId targetId = TargetId.of("prod");
        String description = "Already executed";
        String reason = "Migration already applied";

        // when
        ExecutionRecord record = ExecutionRecord.skipped(nodeId, targetId, description, reason);

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
                                        TargetId.of("dev"),
                                        ExecutionDirection.UP,
                                        ExecutionStatus.FAILURE,
                                        java.time.Instant.now(),
                                        "desc",
                                        null,
                                        100L,
                                        null,
                                        null,
                                        null,
                                        null,
                                        ExecutionOrigin.EXECUTED,
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
                                        TargetId.of("dev"),
                                        ExecutionDirection.DOWN,
                                        ExecutionStatus.SUCCESS,
                                        java.time.Instant.now(),
                                        "desc",
                                        "serialized",
                                        100L,
                                        null,
                                        null,
                                        null,
                                        null,
                                        ExecutionOrigin.EXECUTED,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DOWN execution should not have serializedDownTask");
    }

    @Test
    void shouldAllowNullSerializedDownTaskForUp() {
        // when
        ExecutionRecord record =
                ExecutionRecord.upSuccess(
                        NodeId.of("node-1"), TargetId.of("dev"), "desc", null, 100);

        // then
        assertThat(record.serializedDownTask()).isNull();
    }

    @Test
    void everyFactoryRecordsThatTheMigrationActuallyRan() {
        NodeId nodeId = NodeId.of("node-1");
        TargetId targetId = TargetId.of("dev");

        assertThat(ExecutionRecord.upSuccess(nodeId, targetId, "d", null, 1L).origin())
                .isEqualTo(ExecutionOrigin.EXECUTED);
        assertThat(ExecutionRecord.upSuccess(nodeId, targetId, "d", null, 1L, "token").origin())
                .isEqualTo(ExecutionOrigin.EXECUTED);
        assertThat(
                        ExecutionRecord.upSuccess(nodeId, targetId, "d", null, 1L, "token", "meta")
                                .origin())
                .isEqualTo(ExecutionOrigin.EXECUTED);
        assertThat(
                        ExecutionRecord.upSuccess(
                                        nodeId, targetId, "d", null, 1L, "token", "meta", List.of())
                                .origin())
                .isEqualTo(ExecutionOrigin.EXECUTED);
        assertThat(ExecutionRecord.downSuccess(nodeId, targetId, "d", 1L).origin())
                .isEqualTo(ExecutionOrigin.EXECUTED);
        assertThat(
                        ExecutionRecord.failure(
                                        nodeId, targetId, ExecutionDirection.UP, "d", "boom")
                                .origin())
                .isEqualTo(ExecutionOrigin.EXECUTED);
        assertThat(ExecutionRecord.skipped(nodeId, targetId, "d", "already applied").origin())
                .isEqualTo(ExecutionOrigin.EXECUTED);
    }
}
