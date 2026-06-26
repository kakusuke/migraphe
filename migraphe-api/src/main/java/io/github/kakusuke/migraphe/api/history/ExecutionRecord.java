package io.github.kakusuke.migraphe.api.history;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An immutable value object describing a single migration execution.
 *
 * <p>One record is persisted per node execution in a {@link HistoryRepository}. For a successful up
 * execution, the record carries the serialized down task, which makes a later rollback possible
 * even if the original migration definition is gone. The canonical constructor enforces a few
 * invariants: a {@link ExecutionStatus#FAILURE} requires an error message, and a {@link
 * ExecutionDirection#DOWN} execution must not carry a serialized down task.
 *
 * @param id the unique identifier of this execution record
 * @param nodeId the identifier of the node that was executed
 * @param environmentId the environment in which the execution took place
 * @param direction whether the execution was {@link ExecutionDirection#UP} or {@link
 *     ExecutionDirection#DOWN}
 * @param status the outcome of the execution
 * @param executedAt the instant at which the execution occurred
 * @param description a human-readable description of the executed task
 * @param serializedDownTask the serialized down task captured during a successful up execution, or
 *     {@code null} otherwise (must be {@code null} for down executions)
 * @param durationMs the execution duration in milliseconds
 * @param errorMessage the error message when the execution failed, or {@code null} otherwise
 *     (required when {@code status} is {@link ExecutionStatus#FAILURE})
 * @see HistoryRepository
 * @see ExecutionStatus
 * @see ExecutionDirection
 */
public record ExecutionRecord(
        String id, // unique ID of this execution record
        NodeId nodeId, // ID of the node that was executed
        EnvironmentId environmentId, // environment the node ran against
        ExecutionDirection direction, // UP or DOWN
        ExecutionStatus status, // SUCCESS, FAILURE, SKIPPED
        Instant executedAt, // timestamp of the execution
        String description, // human-readable task description
        @Nullable String serializedDownTask, // serialized DownTask (only present for UP executions)
        long durationMs, // execution time in milliseconds
        @Nullable String errorMessage // error message (only present on failure)
        ) {
    /**
     * Canonical constructor that validates the record invariants.
     *
     * @throws NullPointerException if {@code id}, {@code nodeId}, {@code environmentId}, {@code
     *     direction}, {@code status}, {@code executedAt}, or {@code description} is {@code null}
     * @throws IllegalArgumentException if {@code status} is {@link ExecutionStatus#FAILURE} but
     *     {@code errorMessage} is {@code null}, or if {@code direction} is {@link
     *     ExecutionDirection#DOWN} but {@code serializedDownTask} is non-{@code null}
     */
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

        // Only UP executions may carry a serialized DownTask
        if (direction == ExecutionDirection.DOWN && serializedDownTask != null) {
            throw new IllegalArgumentException("DOWN execution should not have serializedDownTask");
        }
    }

    /**
     * Creates a record for a successful up execution.
     *
     * <p>The record's identifier is randomly generated and its timestamp is set to the current
     * instant.
     *
     * @param nodeId the identifier of the executed node
     * @param environmentId the environment in which the execution took place
     * @param description a human-readable description of the executed task
     * @param serializedDownTask the serialized down task captured for later rollback, or {@code
     *     null} if the step does not support rollback
     * @param durationMs the execution duration in milliseconds
     * @return a new {@code ExecutionRecord} with status {@link ExecutionStatus#SUCCESS} and
     *     direction {@link ExecutionDirection#UP}
     */
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

    /**
     * Creates a record for a successful down (rollback) execution.
     *
     * <p>The record's identifier is randomly generated and its timestamp is set to the current
     * instant. Down records never carry a serialized down task.
     *
     * @param nodeId the identifier of the executed node
     * @param environmentId the environment in which the execution took place
     * @param description a human-readable description of the executed task
     * @param durationMs the execution duration in milliseconds
     * @return a new {@code ExecutionRecord} with status {@link ExecutionStatus#SUCCESS} and
     *     direction {@link ExecutionDirection#DOWN}
     */
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
                null, // DOWN executions never carry a serializedDownTask
                durationMs,
                null);
    }

    /**
     * Creates a record for a failed execution.
     *
     * <p>The record's identifier is randomly generated, its timestamp is set to the current
     * instant, and its duration is recorded as zero.
     *
     * @param nodeId the identifier of the executed node
     * @param environmentId the environment in which the execution took place
     * @param direction whether the failed execution was up or down
     * @param description a human-readable description of the executed task
     * @param errorMessage the error message describing the failure; must be non-{@code null}
     * @return a new {@code ExecutionRecord} with status {@link ExecutionStatus#FAILURE}
     */
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

    /**
     * Creates a record for a skipped execution.
     *
     * <p>The record's identifier is randomly generated, its timestamp is set to the current
     * instant, its direction is recorded as {@link ExecutionDirection#UP}, and its duration is
     * recorded as zero. The skip reason is stored in the error message field.
     *
     * @param nodeId the identifier of the skipped node
     * @param environmentId the environment in which the skip occurred
     * @param description a human-readable description of the task
     * @param reason the reason the execution was skipped (for example, already applied)
     * @return a new {@code ExecutionRecord} with status {@link ExecutionStatus#SKIPPED}
     */
    public static ExecutionRecord skipped(
            NodeId nodeId, EnvironmentId environmentId, String description, String reason) {
        return new ExecutionRecord(
                UUID.randomUUID().toString(),
                nodeId,
                environmentId,
                ExecutionDirection.UP, // a skip is normally recorded in the UP direction
                ExecutionStatus.SKIPPED,
                Instant.now(),
                description,
                null,
                0L,
                reason);
    }

    /**
     * Indicates whether this record describes an up execution.
     *
     * @return {@code true} if the direction is {@link ExecutionDirection#UP}, {@code false}
     *     otherwise
     */
    public boolean isUp() {
        return direction == ExecutionDirection.UP;
    }

    /**
     * Indicates whether this record describes a down execution.
     *
     * @return {@code true} if the direction is {@link ExecutionDirection#DOWN}, {@code false}
     *     otherwise
     */
    public boolean isDown() {
        return direction == ExecutionDirection.DOWN;
    }
}
