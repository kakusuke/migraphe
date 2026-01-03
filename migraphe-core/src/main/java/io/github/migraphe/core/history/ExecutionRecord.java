package io.github.migraphe.core.history;

import io.github.migraphe.core.environment.EnvironmentId;
import io.github.migraphe.core.graph.NodeId;
import io.github.migraphe.core.task.ExecutionDirection;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * マイグレーション実行記録（値オブジェクト）。
 *
 * <p>UP実行時には、シリアライズされたDownTaskを含む。 これにより、後でロールバックが可能になる。
 */
public record ExecutionRecord(
        String id, // 実行記録の一意ID
        NodeId nodeId, // 実行されたノードのID
        EnvironmentId environmentId, // 実行環境
        ExecutionDirection direction, // UP or DOWN
        ExecutionStatus status, // SUCCESS, FAILURE, SKIPPED
        Instant executedAt, // 実行日時
        String description, // タスクの説明
        Optional<String> serializedDownTask, // シリアライズされたDownTask（UP実行時のみ）
        long durationMs, // 実行時間（ミリ秒）
        Optional<String> errorMessage // エラーメッセージ（失敗時のみ）
        ) {
    public ExecutionRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(environmentId, "environmentId must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(executedAt, "executedAt must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(serializedDownTask, "serializedDownTask must not be null");
        Objects.requireNonNull(errorMessage, "errorMessage must not be null");

        if (status == ExecutionStatus.FAILURE && errorMessage.isEmpty()) {
            throw new IllegalArgumentException("Failure status requires error message");
        }

        // UP実行時のみDownTaskを持つべき
        if (direction == ExecutionDirection.DOWN && serializedDownTask.isPresent()) {
            throw new IllegalArgumentException("DOWN execution should not have serializedDownTask");
        }
    }

    /** UP成功記録を作成 */
    public static ExecutionRecord upSuccess(
            NodeId nodeId,
            EnvironmentId environmentId,
            String description,
            Optional<String> serializedDownTask,
            long durationMs) {
        return new ExecutionRecord(
                UUID.randomUUID().toString(),
                nodeId,
                environmentId,
                ExecutionDirection.UP,
                ExecutionStatus.SUCCESS,
                Instant.now(),
                description,
                serializedDownTask,
                durationMs,
                Optional.empty());
    }

    /** DOWN成功記録を作成 */
    public static ExecutionRecord downSuccess(
            NodeId nodeId, EnvironmentId environmentId, String description, long durationMs) {
        return new ExecutionRecord(
                UUID.randomUUID().toString(),
                nodeId,
                environmentId,
                ExecutionDirection.DOWN,
                ExecutionStatus.SUCCESS,
                Instant.now(),
                description,
                Optional.empty(), // DOWNはserializedDownTaskを持たない
                durationMs,
                Optional.empty());
    }

    /** 失敗記録を作成 */
    public static ExecutionRecord failure(
            NodeId nodeId,
            EnvironmentId environmentId,
            ExecutionDirection direction,
            String description,
            String errorMessage) {
        return new ExecutionRecord(
                UUID.randomUUID().toString(),
                nodeId,
                environmentId,
                direction,
                ExecutionStatus.FAILURE,
                Instant.now(),
                description,
                Optional.empty(),
                0L,
                Optional.of(errorMessage));
    }

    /** スキップ記録を作成 */
    public static ExecutionRecord skipped(
            NodeId nodeId, EnvironmentId environmentId, String description, String reason) {
        return new ExecutionRecord(
                UUID.randomUUID().toString(),
                nodeId,
                environmentId,
                ExecutionDirection.UP, // スキップは通常UP方向
                ExecutionStatus.SKIPPED,
                Instant.now(),
                description,
                Optional.empty(),
                0L,
                Optional.of(reason));
    }

    /** UP実行かどうか */
    public boolean isUp() {
        return direction == ExecutionDirection.UP;
    }

    /** DOWN実行かどうか */
    public boolean isDown() {
        return direction == ExecutionDirection.DOWN;
    }
}
