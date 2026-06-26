package io.github.kakusuke.migraphe.core.plugin;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Simple, immutable reference implementation of {@link Task}.
 *
 * <p>This task performs no real work: {@link #execute()} always succeeds, returning a {@link
 * TaskResult} whose message echoes the task description and which carries the optionally configured
 * serialized down task. It is used by the {@code noop} plugin and serves as a baseline that plugin
 * developers can study when writing their own {@link Task}. Instances are created via the {@code
 * of} and {@code withDownTask} factory methods.
 *
 * @see Task
 * @see TaskResult
 */
public final class SimpleTask implements Task {
    private final String description;
    private final @Nullable String serializedDownTask;

    private SimpleTask(String description, @Nullable String serializedDownTask) {
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.serializedDownTask = serializedDownTask;
    }

    /**
     * Executes this no-op task.
     *
     * <p>This implementation performs no work and always succeeds, returning a successful {@link
     * Result} whose {@link TaskResult} message is {@code "Executed: " + description} and which
     * carries the configured serialized down task (or {@code null} if none was configured).
     *
     * @return a successful {@link Result} wrapping the produced {@link TaskResult}
     */
    @Override
    public Result<TaskResult, String> execute() {
        // Simple implementation: always returns success.
        return Result.ok(new TaskResult("Executed: " + description, serializedDownTask));
    }

    @Override
    public String description() {
        return description;
    }

    /**
     * Creates a task with the given description and no serialized down task.
     *
     * @param description a human-readable description of the task
     * @return the constructed task
     * @throws NullPointerException if {@code description} is {@code null}
     */
    public static SimpleTask of(String description) {
        return new SimpleTask(description, null);
    }

    /**
     * Creates a task that carries a serialized down task for rollback.
     *
     * @param description a human-readable description of the task
     * @param serializedDownTask the serialized down task to include in the produced {@link
     *     TaskResult}
     * @return the constructed task
     * @throws NullPointerException if {@code description} is {@code null}
     */
    public static SimpleTask withDownTask(String description, String serializedDownTask) {
        return new SimpleTask(description, serializedDownTask);
    }
}
