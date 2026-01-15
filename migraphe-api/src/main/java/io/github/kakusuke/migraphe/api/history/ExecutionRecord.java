package io.github.kakusuke.migraphe.api.history;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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
        @Nullable String serializedDownTask, // シリアライズされたDownTask（UP実行時のみ）
        long durationMs, // 実行時間（ミリ秒）
        @Nullable String errorMessage // エラーメッセージ（失敗時のみ）
        ) {
    public ExecutionRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(environmentId, "environmentId must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(executedAt, "executedAt must not be null");
        Objects.requireNonNull(description, "description must not be null");

        if (status == ExecutionStatus.FAILURE && errorMessage == null) {
            throw new IllegalArgumentException("Failure status requires error message");
        }

        // UP実行時のみDownTaskを持つべき
        if (direction == ExecutionDirection.DOWN && serializedDownTask != null) {
            throw new IllegalArgumentException("DOWN execution should not have serializedDownTask");
        }
    }

    /** UP成功記録を作成 */
    public static ExecutionRecord upSuccess(
            NodeId nodeId,
            EnvironmentId environmentId,
            String description,
            @Nullable String serializedDownTask,
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
                null);
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
                null, // DOWNはserializedDownTaskを持たない
                durationMs,
                null);
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
                null,
                0L,
                errorMessage);
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
                null,
                0L,
                reason);
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
