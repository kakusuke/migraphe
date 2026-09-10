package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.Map;
import java.util.Set;

/**
 * What stops an {@code up} run before anything is applied.
 *
 * <p>A run is stopped by at most one of these: {@link UpService#plan} reports the first it finds
 * and does not look further, because every one of them has to be cleared before anything is
 * applied. Those that read the definitions come first and are fixed in the task files; {@link
 * IncompleteHistory} and {@link UnreadableContent} read the history and the plugin instead, and are
 * fixed by {@code amend} and by the plugin's author respectively — which is why the wording
 * matters. It is built by {@link UpPlanFormatter} so that every front end refuses in the same
 * words.
 *
 * <p>None of these is fatal to <em>loading</em> a project: a graph in this state must still be able
 * to report its status. What stops is applying something.
 */
public sealed interface UpBlocker {

    /**
     * Tasks whose declared dependencies name nothing in the graph.
     *
     * <p>Applying them would build on ground nothing describes, which is exactly what deleting a
     * task file leaves behind.
     *
     * @param byNode each task with unresolved dependencies mapped to the ids it names, in the
     *     graph's own iteration order
     */
    record UnresolvedDependencies(Map<NodeId, Set<NodeId>> byNode) implements UpBlocker {}

    /**
     * Tasks that define neither a rollback nor a reason there is none.
     *
     * <p>Once a migration has run it is too late to decide, so the choice is demanded before the
     * first apply rather than after.
     *
     * @param nodes the offending task ids
     */
    record UndeclaredIrreversible(Set<NodeId> nodes) implements UpBlocker {}

    /**
     * Tasks declaring a rollback their up task would not record.
     *
     * <p>The history's rollback comes from what the up task reports on success, not from the {@code
     * down:} the definition holds — so a task can declare one and still write a row saying it kept
     * none. That row cannot be rolled back later, and by then the migration has run.
     *
     * <p>Checked before anything is applied, and possible to check because {@link
     * io.github.kakusuke.migraphe.api.task.RollbackPayloadProvider} reports what an execution would
     * record without executing. A task that does not implement it is not judged: the capability
     * exists for the tasks that can answer, and declining is how a task whose payload depends on
     * the run says so.
     *
     * @param nodes the offending task ids
     */
    record UnrecordableRollback(Set<NodeId> nodes) implements UpBlocker {}

    /**
     * Migrations whose plugin cannot report what they hold — it threw, or answered with none.
     *
     * <p>{@code up} reads no attribute of a row — it asks only whether an identifier is applied —
     * so it has nothing to guess about. It is stopped anyway, because it builds on the rows it
     * cannot read: applying a migration on top of a foundation whose recorded content cannot be
     * trusted is the same act as rebuilding around one, which was already refused.
     *
     * <p>The refusal is the whole run and counts every applied row, an orphan's included. The
     * objects an unreadable row records are in the database whether or not a task file still
     * declares them, and an {@code up <id>} that avoids them leaves the same project in the same
     * state.
     *
     * @param nodes the migrations whose applied row carries no fingerprint
     */
    record IncompleteHistory(Set<NodeId> nodes) implements UpBlocker {}

    /**
     * Migrations whose row cannot be read and whose definition cannot say what it would record.
     *
     * <p>Separate from {@link IncompleteHistory} because the repair differs, and one of the two
     * does not exist. A row predating the fingerprint column is filled by rebuilding it from the
     * definition that names it; a definition whose accessor throws has nothing to rebuild it from,
     * so no {@code amend} clears it and prescribing one sends the operator round a loop. It is a
     * fault to fix in the plugin. {@code down} and {@code rebuild} split the same answer the same
     * way, from {@link UnusableAppliedRows}: one row cannot mean a repairable state in one command
     * and an unrepairable one in the next, which is what it did until each stopped working the
     * classification out for itself.
     *
     * <p><strong>Both sides of the run are asked.</strong> A node already applied, whose row
     * carries no token and whose definition can no longer supply one, is the state no repair
     * reaches. A node this run is <em>about to</em> apply is asked as well, and before anything
     * runs: the recording path asks after the DDL has gone through, where refusing is no longer
     * available — a broken accessor must not cost the success record, or the next run applies the
     * same migration again. Checking first is what stops a broken plugin from writing the very rows
     * every command then refuses on.
     *
     * @param nodes the migrations whose plugin cannot report a token
     */
    record UnreadableContent(Set<NodeId> nodes) implements UpBlocker {}

    /**
     * Applied migrations whose definition was edited after they ran.
     *
     * <p>The database is known not to match the task files, and applying more on top of it builds
     * on ground they no longer describe. That is a stronger reason to stop than {@link
     * UnreadableContent} or {@link IncompleteHistory}, which stop a run over content that merely
     * cannot be read.
     *
     * <p>Neither remedy is this command's to choose — moving the database to match the definitions
     * and moving the record to match them are both repairs, and only the operator knows which one
     * the objects call for. So the run stops and names both.
     *
     * @param nodes the migrations whose recorded content is not what their definition folds now
     */
    record EditedSinceApplied(Set<NodeId> nodes) implements UpBlocker {}
}
