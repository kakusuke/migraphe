package io.github.kakusuke.migraphe.core.plugin;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final Object content;
    private final @Nullable String serializedDownTask;

    private SimpleTask(Object content, @Nullable String serializedDownTask) {
        this.content = Objects.requireNonNull(content, "content must not be null");
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
        return Result.ok(new TaskResult("Executed: " + description(), serializedDownTask, null));
    }

    @Override
    public String description() {
        return content.toString();
    }

    /**
     * The content this task was built from, rendered so that the same content reads the same way
     * twice.
     *
     * <p>Rendered here rather than when the task is built: a shape with no stable rendering then
     * stops a comparison rather than stopping the task from existing.
     *
     * @return the signature of this task's content
     * @throws IllegalArgumentException if the content has no stable rendering
     */
    @Override
    public String signature() {
        return serialize(content);
    }

    /**
     * Renders content as text that reads the same on every run.
     *
     * <p>Scalars are written as they read; a {@link List} keeps its order because a list's order is
     * part of what it says; a {@link Set} and a {@link Map} are sorted, because theirs is not — a
     * {@code HashSet}'s iteration order is not something recorded on one machine that can be relied
     * on when it is read on another. Nesting is followed, so a map of lists says what its lists
     * say.
     *
     * <p>Anything else is refused rather than rendered. {@code toString()} would produce something,
     * but nothing guarantees it produces the same something across JVM versions — least of all for
     * the proxies a configuration library hands back for a nested mapping. Whoever holds content of
     * a shape this does not cover knows how to write it down; this does not.
     */
    private static String serialize(Object value) {
        StringBuilder rendered = new StringBuilder();
        switch (value) {
            case String s -> appendLengthPrefixed(rendered, s.strip());
            case Number n -> appendLengthPrefixed(rendered, n.toString());
            case Boolean b -> appendLengthPrefixed(rendered, b.toString());
            case List<?> list -> {
                rendered.append('[');
                for (Object element : list) {
                    rendered.append(serialize(element));
                }
                rendered.append(']');
            }
            case Set<?> set -> {
                rendered.append('{');
                set.stream().map(SimpleTask::serialize).sorted().forEach(rendered::append);
                rendered.append('}');
            }
            case Map<?, ?> map -> {
                rendered.append('<');
                map.entrySet().stream()
                        .map(e -> serialize(e.getKey()) + serialize(e.getValue()))
                        .sorted()
                        .forEach(rendered::append);
                rendered.append('>');
            }
            default ->
                    throw new IllegalArgumentException(
                            "Cannot render "
                                    + value.getClass().getName()
                                    + " the same way twice: pass text, a list, a set or a map");
        }
        return rendered.toString();
    }

    private static void appendLengthPrefixed(StringBuilder target, String part) {
        target.append(part.length()).append(':').append(part);
    }

    /**
     * Creates a task from the given content, with no serialized down task.
     *
     * @param content what the task was defined by — text, or the list or map a structured
     *     definition binds to. It is also what {@link #description()} reads
     * @return the constructed task
     * @throws NullPointerException if {@code content} is {@code null}
     */
    public static SimpleTask of(Object content) {
        return new SimpleTask(content, null);
    }

    /**
     * Creates a task that carries a serialized down task for rollback.
     *
     * @param content what the task was defined by, on the same terms as {@link #of(Object)}
     * @param serializedDownTask the serialized down task to include in the produced {@link
     *     TaskResult}
     * @return the constructed task
     * @throws NullPointerException if {@code content} is {@code null}
     */
    public static SimpleTask withDownTask(Object content, String serializedDownTask) {
        return new SimpleTask(content, serializedDownTask);
    }
}
