package io.github.kakusuke.migraphe.api.task;

import org.jspecify.annotations.Nullable;

/**
 * Optional capability of a {@link Task}: reporting the rollback payload it would record, without
 * running anything.
 *
 * <p>The two values are the ones a successful execution puts in its {@link TaskResult}, and they
 * carry the same contract there: both are the plugin's, and core stores and returns them without
 * looking inside either one.
 *
 * <p>The caller is a command that has to make the history's record of a node agree with the node's
 * current definition <em>without applying it</em>. A real execution supplies the payload as a side
 * effect of succeeding; such a command has no execution to take it from, and the alternatives are
 * both wrong — leaving the recorded rollback stale while claiming agreement, or having core
 * assemble a payload whose format belongs to the plugin.
 *
 * <p>Implementations return what an execution of this same task would record. Nothing enforces
 * that, so a task whose payload depends on what the run observes should not implement this
 * capability at all; detection is by {@code instanceof}, so declining is expressible.
 *
 * @see Task
 * @see TaskResult
 * @see SqlContentProvider
 */
public interface RollbackPayloadProvider {

    /**
     * The serialized rollback this task would record.
     *
     * @return the serialized down task, or {@code null} if this migration records no rollback
     */
    @Nullable String serializedDownTask();

    /**
     * The plugin's own record of an execution of this task.
     *
     * @return the plugin metadata, or {@code null} if this task records none
     */
    @Nullable String pluginMetadata();
}
