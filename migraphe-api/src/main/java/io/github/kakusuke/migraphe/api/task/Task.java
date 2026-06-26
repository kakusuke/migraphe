package io.github.kakusuke.migraphe.api.task;

import io.github.kakusuke.migraphe.api.common.Result;

/**
 * The executable unit of work for a single migration step.
 *
 * <p>A {@link io.github.kakusuke.migraphe.api.graph.MigrationNode} exposes one {@code Task} for the
 * forward (up) direction and, optionally, one for the reverse (down) direction. Migraphe invokes
 * {@link #execute()} when the owning node is scheduled to run.
 *
 * <p>Plugins implement this interface to define concrete migration logic (for example, running a
 * SQL statement). Implementations are responsible for their own transaction management ({@code
 * BEGIN}/{@code COMMIT}/{@code ROLLBACK}); Migraphe does not wrap the call in a transaction. An
 * implementation must report success or failure through the returned {@link Result} rather than by
 * throwing, so that the orchestrator can record history and continue fail-soft.
 *
 * @see TaskResult
 * @see Result
 * @see io.github.kakusuke.migraphe.api.graph.MigrationNode
 */
public interface Task {

    /**
     * Executes this task.
     *
     * <p>The implementation performs its own transaction management ({@code BEGIN}/{@code
     * COMMIT}/{@code ROLLBACK}). On success for an up task, the returned {@link TaskResult} should
     * carry the serialized down task needed to roll the step back later.
     *
     * @return an {@link Result#ok ok} result wrapping the {@link TaskResult} on success, or an
     *     {@link Result#err err} result wrapping an error message on failure
     */
    Result<TaskResult, String> execute();

    /**
     * Returns a human-readable description of what this task does.
     *
     * @return the task description
     */
    String description();
}
