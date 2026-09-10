package io.github.kakusuke.migraphe.api.target;

import io.github.kakusuke.migraphe.api.task.Task;
import org.jspecify.annotations.Nullable;

/**
 * Optional capability of a {@link Target}: turning a rollback the history recorded back into an
 * executable {@link io.github.kakusuke.migraphe.api.task.Task Task}.
 *
 * <p>It is the missing half of a contract that already existed. A plugin's UP task hands core a
 * serialized rollback in {@link io.github.kakusuke.migraphe.api.task.TaskResult}, core stores it
 * without understanding it, and until now nothing could turn it back — so the column was written
 * and never read. Only the plugin that wrote the payload can interpret it, and only the target
 * holds what executing it needs, which is why the capability lives here.
 *
 * <p>Kept separate from {@link Target} rather than added as a {@code default} method: a plugin that
 * cannot reconstruct a task simply does not implement this, and callers detect that with {@code
 * instanceof} instead of being handed a method that throws. The price is that the check happens at
 * run time, so "this plugin cannot roll back a migration it no longer defines" is not something the
 * compiler reports.
 *
 * <p>The caller for this is a rollback of a migration the definitions no longer contain: there is
 * no {@link io.github.kakusuke.migraphe.api.graph.MigrationNode} left to ask for a down task, and
 * the history row is all that remains.
 *
 * @see Target
 * @see io.github.kakusuke.migraphe.api.history.ExecutionRecord
 */
public interface DownTaskRestorer {

    /**
     * Rebuilds the rollback described by one execution record.
     *
     * <p>Both arguments come from the record verbatim, and how to read them is entirely this
     * plugin's business — core neither produced nor inspected either one.
     *
     * <p>An absent {@code pluginMetadata} is not an error — every row written before the column
     * existed carries none. What to do about it is the implementation's decision, and the
     * implementation is the only party that can make it: it chose what to record, so it is the only
     * one that knows whether the missing value changes what the rollback does.
     *
     * @param serializedDownTask the record's serialized rollback; never {@code null}, because a
     *     record without one describes a migration that cannot be rolled back at all
     * @param pluginMetadata the record's plugin metadata, or {@code null} when the row carries none
     *     — including every row written before the column existed
     * @return a task that performs the recorded rollback
     * @throws RuntimeException if the payload cannot be interpreted; the caller reports that rather
     *     than executing something it does not understand
     */
    Task restoreDownTask(String serializedDownTask, @Nullable String pluginMetadata);
}
