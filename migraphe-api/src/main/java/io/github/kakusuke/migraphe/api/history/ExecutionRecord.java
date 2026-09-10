package io.github.kakusuke.migraphe.api.history;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
 * @param id the unique identifier of this execution record. The {@code static} factories below
 *     generate a time-ordered UUIDv7, so identifiers of records created in sequence also sort in
 *     that order and can break ties between records sharing an {@code executedAt} value; a record
 *     constructed directly may carry any unique string
 * @param nodeId the identifier of the node that was executed
 * @param targetId the target in which the execution took place
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
 * @param fingerprint the node's fingerprint — see {@link
 *     io.github.kakusuke.migraphe.api.graph.MigrationNode#fingerprint} — or {@code null} when none
 *     is known, which is what a record written before this field existed carries. {@code null} does
 *     not mean "unchanged". Written when the node was applied, and rewritten by the maintenance
 *     command that makes the history agree with the definitions, so on a row that has been amended
 *     it describes the definition as of the amend rather than as of the apply
 * @param dependencies the node's declared <strong>direct</strong> dependencies as they stood when
 *     this execution happened, or {@code null} when they were not recorded — which is what a row
 *     written before this field existed carries. An <strong>empty</strong> list is a different
 *     answer: it says the node stood on nothing, which most projects' roots do. Rewritten alongside
 *     {@code fingerprint} for the same reason. Recorded as data rather than only hashed because a
 *     rollback has to be ordered through nodes the definitions may no longer contain, and a hash
 *     cannot be reversed. Edges rather than the closure because the two jobs differ: the
 *     fingerprint <em>detects</em> and needs the closure, while this column <em>reconstructs</em>
 *     and needs the edges — a closure cannot be inverted back into a DAG, so it cannot rebuild the
 *     graph this exists to rebuild, and its width grows with the project rather than with the
 *     node's fan-in. Nothing here is transitive
 * @param pluginMetadata whatever the plugin that ran this node wants recorded alongside it, or
 *     {@code null}. The value is <strong>opaque to core</strong>: it is stored and handed back
 *     verbatim, and only the plugin that wrote it knows its encoding — the same contract {@code
 *     serializedDownTask} already has. Unlike that field it carries no direction constraint,
 *     because what a plugin needs to record is the plugin's business
 * @param origin whether this row records something that ran or something that was claimed. Every
 *     ordinary execution is {@link ExecutionOrigin#EXECUTED}; nothing branches on it
 * @param noWayBack the reason the node's author gave for it being one-way — {@code no_way_back:} —
 *     or {@code null} when none was declared. Core reads this off the node rather than asking the
 *     plugin for it, because it belongs to every plugin alike. Recorded so that a row with no
 *     rollback payload can say which of two things it is: the author declared this one-way, or the
 *     row simply carries nothing. For an orphan there is nowhere else left to look
 * @see HistoryRepository
 * @see ExecutionStatus
 * @see ExecutionDirection
 */
public record ExecutionRecord(
        String id, // unique ID of this execution record
        NodeId nodeId, // ID of the node that was executed
        TargetId targetId, // target the node ran against
        ExecutionDirection direction, // UP or DOWN
        ExecutionStatus status, // SUCCESS, FAILURE, SKIPPED
        Instant executedAt, // timestamp of the execution
        String description, // human-readable task description
        @Nullable String serializedDownTask, // serialized DownTask (only present for UP executions)
        long durationMs, // execution time in milliseconds
        @Nullable String errorMessage, // error message (only present on failure)
        @Nullable String fingerprint, // fingerprint of the applied UP content, or null if unknown
        @Nullable String pluginMetadata, // plugin-owned, opaque to core; see pluginMetadata()
        @Nullable List<NodeId> dependencies, // edges at apply time; empty != null
        ExecutionOrigin origin, // executed, or claimed; see ExecutionOrigin
        @Nullable String noWayBack // the author's reason this cannot be rolled back, or null
        ) {
    /**
     * Canonical constructor that validates the record invariants.
     *
     * @throws NullPointerException if {@code id}, {@code nodeId}, {@code targetId}, {@code
     *     direction}, {@code status}, {@code executedAt}, {@code description}, or {@code origin} is
     *     {@code null}
     * @throws IllegalArgumentException if {@code status} is {@link ExecutionStatus#FAILURE} but
     *     {@code errorMessage} is {@code null}, or if {@code direction} is {@link
     *     ExecutionDirection#DOWN} but {@code serializedDownTask} is non-{@code null}
     */
    public ExecutionRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(executedAt, "executedAt must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(origin, "origin must not be null");

        dependencies = dependencies == null ? null : List.copyOf(dependencies);

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
     * @param targetId the target in which the execution took place
     * @param description a human-readable description of the executed task
     * @param serializedDownTask the serialized down task captured for later rollback, or {@code
     *     null} if the step does not support rollback
     * @param durationMs the execution duration in milliseconds
     * @return a new {@code ExecutionRecord} with status {@link ExecutionStatus#SUCCESS} and
     *     direction {@link ExecutionDirection#UP}, whose {@code fingerprint} is {@code null} —
     *     meaning "unknown", never "unchanged". Use the overload taking one to record it
     */
    public static ExecutionRecord upSuccess(
            NodeId nodeId,
            TargetId targetId,
            String description,
            @Nullable String serializedDownTask,
            long durationMs) {
        return upSuccess(nodeId, targetId, description, serializedDownTask, durationMs, null);
    }

    /**
     * Creates a record for a successful up execution, carrying the fingerprint of the content that
     * was applied.
     *
     * @param nodeId the node that was executed
     * @param targetId the target in which the execution took place
     * @param description a human-readable description of the executed task
     * @param serializedDownTask the serialized down task captured for later rollback, or {@code
     *     null} if the step does not support rollback
     * @param durationMs the execution duration in milliseconds
     * @param fingerprint the node's fingerprint at apply time, or {@code null} when the plugin does
     *     not provide one
     * @return a new {@code ExecutionRecord} with status {@link ExecutionStatus#SUCCESS} and
     *     direction {@link ExecutionDirection#UP}
     */
    public static ExecutionRecord upSuccess(
            NodeId nodeId,
            TargetId targetId,
            String description,
            @Nullable String serializedDownTask,
            long durationMs,
            @Nullable String fingerprint) {
        return upSuccess(
                nodeId, targetId, description, serializedDownTask, durationMs, fingerprint, null);
    }

    /**
     * Creates a record for a successful up execution, carrying both the fingerprint and whatever
     * the plugin wants recorded alongside it.
     *
     * @param nodeId the node that was executed
     * @param targetId the target in which the execution took place
     * @param description a human-readable description of the executed task
     * @param serializedDownTask the serialized down task captured for later rollback, or {@code
     *     null} if the step does not support rollback
     * @param durationMs the execution duration in milliseconds
     * @param fingerprint the node's fingerprint at apply time, or {@code null} when the plugin does
     *     not provide one
     * @param pluginMetadata the plugin's own record of this execution, or {@code null}
     * @return a new {@code ExecutionRecord} with status {@link ExecutionStatus#SUCCESS} and
     *     direction {@link ExecutionDirection#UP}
     */
    public static ExecutionRecord upSuccess(
            NodeId nodeId,
            TargetId targetId,
            String description,
            @Nullable String serializedDownTask,
            long durationMs,
            @Nullable String fingerprint,
            @Nullable String pluginMetadata) {
        return upSuccess(
                nodeId,
                targetId,
                description,
                serializedDownTask,
                durationMs,
                fingerprint,
                pluginMetadata,
                null);
    }

    /**
     * Creates a record for a successful up execution, carrying everything the history keeps about
     * what was applied.
     *
     * @param nodeId the node that was executed
     * @param targetId the target in which the execution took place
     * @param description a human-readable description of the executed task
     * @param serializedDownTask the serialized down task captured for later rollback, or {@code
     *     null} if the step does not support rollback
     * @param durationMs the execution duration in milliseconds
     * @param fingerprint the node's fingerprint at apply time, or {@code null} when the plugin does
     *     not provide one
     * @param pluginMetadata the plugin's own record of this execution, or {@code null}
     * @param dependencies the edges the node stood on when applied, or {@code null} when unrecorded
     * @return a new {@code ExecutionRecord} with status {@link ExecutionStatus#SUCCESS} and
     *     direction {@link ExecutionDirection#UP}
     */
    public static ExecutionRecord upSuccess(
            NodeId nodeId,
            TargetId targetId,
            String description,
            @Nullable String serializedDownTask,
            long durationMs,
            @Nullable String fingerprint,
            @Nullable String pluginMetadata,
            @Nullable List<NodeId> dependencies) {
        return upSuccess(
                nodeId,
                targetId,
                description,
                serializedDownTask,
                durationMs,
                fingerprint,
                pluginMetadata,
                dependencies,
                null);
    }

    /**
     * Creates a record for a successful up execution, carrying everything the history keeps about
     * what was applied, including why the migration cannot be rolled back.
     *
     * @param nodeId the node that was executed
     * @param targetId the target the execution ran against
     * @param description a human-readable description of the executed task
     * @param serializedDownTask the serialized down task captured for later rollback, or {@code
     *     null} if the step does not support rollback
     * @param durationMs the execution duration in milliseconds
     * @param fingerprint the node's fingerprint at apply time, or {@code null} when the plugin does
     *     not provide one
     * @param pluginMetadata the plugin's own record of this execution, or {@code null}
     * @param dependencies the edges the node stood on when applied, or {@code null} when unrecorded
     * @param noWayBack the author's reason the node is one-way, or {@code null} when none was
     *     declared
     * @return a new {@code ExecutionRecord} with status {@link ExecutionStatus#SUCCESS} and
     *     direction {@link ExecutionDirection#UP}
     */
    public static ExecutionRecord upSuccess(
            NodeId nodeId,
            TargetId targetId,
            String description,
            @Nullable String serializedDownTask,
            long durationMs,
            @Nullable String fingerprint,
            @Nullable String pluginMetadata,
            @Nullable List<NodeId> dependencies,
            @Nullable String noWayBack) {
        return new ExecutionRecord(
                RecordIds.newId(),
                nodeId,
                targetId,
                ExecutionDirection.UP,
                ExecutionStatus.SUCCESS,
                Instant.now(),
                description,
                serializedDownTask,
                durationMs,
                null,
                fingerprint,
                pluginMetadata,
                dependencies,
                ExecutionOrigin.EXECUTED,
                noWayBack);
    }

    /**
     * Creates a row that <strong>claims</strong> a node is applied, without anything having run.
     *
     * <p>This is what the maintenance command that makes the history agree with the definitions
     * appends. Every value comes from the definition; the row is built from scratch rather than
     * patched, which is what keeps "every column the definition determines is written" checkable.
     *
     * <p>{@code durationMs} is zero and {@code errorMessage} is {@code null} — both are facts about
     * an execution that did not happen. {@code executedAt} is when the claim was made, which is
     * true of this row and leaves the original apply's timestamp where it is on the row it belongs
     * to.
     *
     * @param nodeId the node being claimed as applied
     * @param targetId the target the definition names
     * @param description the node's name
     * @param serializedDownTask the rollback payload the definition's up task reports, or {@code
     *     null}
     * @param fingerprint the token the current definition folds
     * @param pluginMetadata the plugin's own value for the current definition, or {@code null}
     * @param dependencies the direct dependencies the current definition declares
     * @param noWayBack the reason the definition declares the node one-way, or {@code null}
     * @return a new {@code ExecutionRecord} with direction {@link ExecutionDirection#UP}, status
     *     {@link ExecutionStatus#SUCCESS} and origin {@link ExecutionOrigin#AMENDED}
     */
    public static ExecutionRecord amendedUp(
            NodeId nodeId,
            TargetId targetId,
            String description,
            @Nullable String serializedDownTask,
            @Nullable String fingerprint,
            @Nullable String pluginMetadata,
            @Nullable List<NodeId> dependencies,
            @Nullable String noWayBack) {
        return new ExecutionRecord(
                RecordIds.newId(),
                nodeId,
                targetId,
                ExecutionDirection.UP,
                ExecutionStatus.SUCCESS,
                Instant.now(),
                description,
                serializedDownTask,
                0L,
                null,
                fingerprint,
                pluginMetadata,
                dependencies,
                ExecutionOrigin.AMENDED,
                noWayBack);
    }

    /**
     * Creates a record for a successful down (rollback) execution.
     *
     * <p>The record's identifier is randomly generated and its timestamp is set to the current
     * instant. Down records never carry a serialized down task.
     *
     * @param nodeId the identifier of the executed node
     * @param targetId the target in which the execution took place
     * @param description a human-readable description of the executed task
     * @param durationMs the execution duration in milliseconds
     * @return a new {@code ExecutionRecord} with status {@link ExecutionStatus#SUCCESS} and
     *     direction {@link ExecutionDirection#DOWN}
     */
    public static ExecutionRecord downSuccess(
            NodeId nodeId, TargetId targetId, String description, long durationMs) {
        return new ExecutionRecord(
                RecordIds.newId(),
                nodeId,
                targetId,
                ExecutionDirection.DOWN,
                ExecutionStatus.SUCCESS,
                Instant.now(),
                description,
                null, // DOWN executions never carry a serializedDownTask
                durationMs,
                null,
                null,
                null,
                null,
                ExecutionOrigin.EXECUTED,
                null);
    }

    /**
     * Creates a row that <strong>claims</strong> a migration is no longer applied.
     *
     * <p>What the maintenance command appends for an orphan — a migration the history says is
     * applied and the definitions no longer describe. There is no definition left for a row to
     * agree with, so the only thing it can say is that the migration is not applied, and nothing
     * else is written: no fingerprint, no dependencies, no reason — the definitions supply none.
     *
     * <p>It does <strong>not</strong> remove the objects the migration created. After it migraphe
     * knows nothing about them, so it is right only once they are gone by some other route; rolling
     * the migration back is what removes them and records that it did.
     *
     * @param nodeId the orphaned migration
     * @param targetId the target its own row says it was applied to
     * @param description what that row called it
     * @return a new {@code ExecutionRecord} with direction {@link ExecutionDirection#DOWN}, status
     *     {@link ExecutionStatus#SUCCESS} and origin {@link ExecutionOrigin#AMENDED}
     */
    public static ExecutionRecord amendedDown(
            NodeId nodeId, TargetId targetId, String description) {
        return new ExecutionRecord(
                RecordIds.newId(),
                nodeId,
                targetId,
                ExecutionDirection.DOWN,
                ExecutionStatus.SUCCESS,
                Instant.now(),
                description,
                null,
                0L,
                null,
                null,
                null,
                null,
                ExecutionOrigin.AMENDED,
                null);
    }

    /**
     * Creates a record for a failed execution.
     *
     * <p>The record's identifier is randomly generated, its timestamp is set to the current
     * instant, and its duration is recorded as zero.
     *
     * @param nodeId the identifier of the executed node
     * @param targetId the target in which the execution took place
     * @param direction whether the failed execution was up or down
     * @param description a human-readable description of the executed task
     * @param errorMessage the error message describing the failure; must be non-{@code null}
     * @return a new {@code ExecutionRecord} with status {@link ExecutionStatus#FAILURE}
     */
    public static ExecutionRecord failure(
            NodeId nodeId,
            TargetId targetId,
            ExecutionDirection direction,
            String description,
            String errorMessage) {
        return new ExecutionRecord(
                RecordIds.newId(),
                nodeId,
                targetId,
                direction,
                ExecutionStatus.FAILURE,
                Instant.now(),
                description,
                null,
                0L,
                errorMessage,
                null,
                null,
                null,
                ExecutionOrigin.EXECUTED,
                null);
    }

    /**
     * Creates a record for a skipped execution.
     *
     * <p>The record's identifier is randomly generated, its timestamp is set to the current
     * instant, its direction is recorded as {@link ExecutionDirection#UP}, and its duration is
     * recorded as zero. The skip reason is stored in the error message field.
     *
     * @param nodeId the identifier of the skipped node
     * @param targetId the target in which the skip occurred
     * @param description a human-readable description of the task
     * @param reason the reason the execution was skipped (for example, already applied)
     * @return a new {@code ExecutionRecord} with status {@link ExecutionStatus#SKIPPED}
     */
    public static ExecutionRecord skipped(
            NodeId nodeId, TargetId targetId, String description, String reason) {
        return new ExecutionRecord(
                RecordIds.newId(),
                nodeId,
                targetId,
                ExecutionDirection.UP, // a skip is normally recorded in the UP direction
                ExecutionStatus.SKIPPED,
                Instant.now(),
                description,
                null,
                0L,
                reason,
                null,
                null,
                null,
                ExecutionOrigin.EXECUTED,
                null);
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
