package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What stops a {@code down} run before anything is rolled back.
 *
 * <p>A run is stopped by at most one of these: {@link DownService#plan} reports the first it finds
 * and does not look further. The wording is built by {@link DownPlanFormatter} so that every front
 * end refuses in the same words.
 */
public sealed interface DownBlocker {

    /**
     * The requested node is itself one that cannot be rolled back.
     *
     * @param target the node that was asked for, carried whole so a report can quote the reason its
     *     author declared
     */
    record IrreversibleTarget(FrozenMigration target) implements DownBlocker {}

    /**
     * A migration that cannot come down, together with the row that applied it.
     *
     * <p>Both are needed to say <em>why</em>. The row holds the payload that is missing, the reason
     * the author declared, and the token that says whether the row can be read at face value at
     * all; the node holds what the definitions say about the same migration, which is what tells a
     * forgotten rollback from one the history simply never kept. Reporting from the node alone told
     * an operator their task file declared no rollback while it plainly declared one.
     *
     * @param node the migration as it would have been rolled back
     * @param appliedRow the row that applied it, or {@code null} when the history holds none
     */
    record FrozenMigration(MigrationNode node, @Nullable ExecutionRecord appliedRow) {}

    /**
     * The requested node can only be removed by breaking something that has to stay.
     *
     * @param target the node that was asked for
     * @param holders the applied nodes that have no down migration and stand on it
     */
    record HeldTarget(NodeId target, List<NodeId> holders) implements DownBlocker {}

    /**
     * A {@code --all} run cannot be satisfied, because something applied cannot come down.
     *
     * <p>{@code --all} means the whole of the database, and part of it is not the whole. Rolling
     * back everything else first would leave a shape nobody asked for, out of an operation whose
     * entire meaning is "all" — so the run refuses before removing anything. Naming a single node
     * is a different request: it is satisfiable or it is not, and {@link IrreversibleTarget} and
     * {@link HeldTarget} answer that one.
     *
     * @param frozenAppliedCount how many applied migrations cannot come down in total — the ones
     *     with no rollback plus the applied ones they hold down
     * @param irreversible the migrations with no rollback of their own, carried whole so the report
     *     can quote the reason each one has for having none
     */
    record FrozenAll(int frozenAppliedCount, List<FrozenMigration> irreversible)
            implements DownBlocker {}

    /**
     * Applied rows naming a target the project no longer configures.
     *
     * <p>There is no connection to reconstruct, so those migrations cannot come down — but their
     * objects are real and the history says so, and running around them would be the one silence
     * the tool does not permit: the rest of the rollback would remove what they still stand on. The
     * rows are named rather than skipped, and nothing is rolled back.
     *
     * @param rows the applied rows whose {@code target_id} does not resolve, ordered by node id
     */
    record UnresolvableTargets(List<ExecutionRecord> rows) implements DownBlocker {}

    /**
     * Applied rows carrying no fingerprint, so nothing they record can be read.
     *
     * <p>The refusal is the whole run rather than the migrations concerned, and it is what makes
     * the rest of this command's reasoning safe: a row that cannot say what it stands on cannot
     * order a cascade either, so refusing per node would leave {@code down} planning around edges
     * that are not there — and taking a migration out from under something still standing on it is
     * the one outcome no rollback may produce.
     *
     * @param nodes the migrations whose applied row carries no fingerprint
     */
    record IncompleteHistory(Set<NodeId> nodes) implements DownBlocker {}

    /**
     * Migrations whose row cannot be read and whose definition cannot say what it would record.
     *
     * <p>The same state {@link IncompleteHistory} covers, minus the repair. Naming {@code amend}
     * for it would send the operator to a command that skips the row and back to this refusal, so
     * it is reported as the plugin fault it is. {@code up} and {@code rebuild} split it the same
     * way, from the same answer, because three commands describing one row differently is the thing
     * that made an operator run in a circle.
     *
     * @param nodes the migrations whose plugin cannot report a token
     */
    record UnreadableHistory(Set<NodeId> nodes) implements DownBlocker {}
}
