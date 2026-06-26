package io.github.kakusuke.migraphe.api.task;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of a successful {@link Task} execution.
 *
 * <p>When an up task succeeds, the result carries a serialized representation of the corresponding
 * down task. This serialized form is persisted in the execution history so that the step can later
 * be rolled back even if the original migration definition is no longer available. Down tasks (and
 * up tasks that do not support rollback) carry no serialized down task.
 *
 * @param message a human-readable summary of what was executed; must be non-{@code null}
 * @param serializedDownTask the serialized down task to use for rollback, or {@code null} when none
 *     applies (down executions and steps without rollback support)
 * @see Task
 */
public record TaskResult(String message, @Nullable String serializedDownTask) {

    /**
     * Canonical constructor that validates the result.
     *
     * @param message a human-readable summary of what was executed; must be non-{@code null}
     * @param serializedDownTask the serialized down task to use for rollback, or {@code null} when
     *     none applies
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public TaskResult {
        Objects.requireNonNull(message, "message must not be null");
    }

    /**
     * Creates a result with no serialized down task.
     *
     * <p>Use this for down executions or for up steps that do not support rollback.
     *
     * @param message a human-readable summary of what was executed
     * @return a {@code TaskResult} whose {@link #serializedDownTask()} is {@code null}
     */
    public static TaskResult withoutDownTask(String message) {
        return new TaskResult(message, null);
    }

    /**
     * Creates a result that carries the serialized down task used for rollback.
     *
     * <p>Use this for successful up executions.
     *
     * @param message a human-readable summary of what was executed
     * @param serializedDownTask the serialized down task to persist for later rollback; must be
     *     non-{@code null}
     * @return a {@code TaskResult} carrying {@code serializedDownTask}
     * @throws NullPointerException if {@code serializedDownTask} is {@code null}
     */
    public static TaskResult withDownTask(String message, String serializedDownTask) {
        Objects.requireNonNull(serializedDownTask, "serializedDownTask must not be null");
        return new TaskResult(message, serializedDownTask);
    }
}
