package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;

/**
 * What stops a {@code down} run before anything is rolled back.
 *
 * <p>A run is stopped by at most one of these: {@link DownService#plan} reports the first it finds
 * and does not look further. The wording is built by {@link DownPlanFormatter} so that every front
 * end refuses in the same words.
 *
 * <p>Note that a {@code --all} run that merely has to <em>leave something behind</em> is not
 * blocked — it rolls back what it can and reports the remainder; see {@link
 * DownService.DownPlan#irreversible()}.
 */
public sealed interface DownBlocker {

    /**
     * Nodes the history says are applied but the definitions no longer declare.
     *
     * <p>An orphan may well stand on something the rollback would remove, and what it stands on is
     * not recorded — its dependencies lived in a task file that is gone. So nothing is rolled back
     * rather than rolling back around it.
     *
     * @param orphans what is applied but no longer defined
     */
    record Orphans(List<StatusService.OrphanStatus> orphans) implements DownBlocker {}

    /**
     * The requested node is itself one that cannot be rolled back.
     *
     * @param target the node that was asked for, carried whole so a report can quote the reason its
     *     author declared
     */
    record IrreversibleTarget(MigrationNode target) implements DownBlocker {}

    /**
     * The requested node can only be removed by breaking something that has to stay.
     *
     * @param target the node that was asked for
     * @param holders the applied nodes that have no down migration and stand on it
     */
    record HeldTarget(NodeId target, List<NodeId> holders) implements DownBlocker {}
}
