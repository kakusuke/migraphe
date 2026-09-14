package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders what a {@code down} run refused, as the lines a front end reports.
 *
 * <p>Shared by the CLI command and the Gradle task so that both report a rollback identically; the
 * CLI prints the lines to standard error, the Gradle task logs them and fails the build. This class
 * is not instantiable.
 */
public final class DownPlanFormatter {

    private DownPlanFormatter() {}

    /**
     * Formats what refuses a DOWN run.
     *
     * <p>Deliberately switches without a {@code default} arm: adding a case to {@link DownBlocker}
     * should stop this compiling until someone writes what it says, rather than silently refusing
     * with no explanation.
     *
     * @param blocker what refuses the run
     * @param repair how this front end is invoked, for the commands the message names
     * @return the lines to report
     */
    public static List<String> format(DownBlocker blocker, RepairVocabulary repair) {
        return switch (blocker) {
            case DownBlocker.IrreversibleTarget target ->
                    List.of(
                            "Error: "
                                    + target.target().node().id().value()
                                    + " cannot be rolled back — "
                                    + why(target.target()));
            case DownBlocker.FrozenAll frozen -> frozenAllLines(frozen);
            case DownBlocker.IncompleteHistory incomplete ->
                    HistoryRefusalFormatter.incompleteLines(incomplete.nodes(), repair);
            case DownBlocker.UnreadableHistory unreadable ->
                    HistoryRefusalFormatter.unreadableLines(unreadable.nodes(), repair);
            case DownBlocker.UnresolvableTargets unresolvable ->
                    unresolvableTargetLines(unresolvable.rows());
            case DownBlocker.HeldTarget held ->
                    List.of(
                            "Error: "
                                    + held.target().value()
                                    + " cannot be rolled back while these are applied, because they"
                                    + " have no down migration and stand on it: "
                                    + held.holders().stream()
                                            .map(NodeId::value)
                                            .collect(Collectors.joining(", ")));
        };
    }

    /**
     * Names the rows whose target the project no longer configures, and what that costs.
     *
     * <p>The target is named beside each id because that is the thing to put back: restoring the
     * connection in the configuration is what makes those migrations reachable again, and no edit
     * to a task file will.
     */
    private static List<String> unresolvableTargetLines(List<ExecutionRecord> rows) {
        List<String> lines = new ArrayList<>();
        lines.add(
                "Error: "
                        + rows.size()
                        + " applied migration(s) name a target this project no longer configures,"
                        + " so there is no connection to roll them back through. Nothing was rolled"
                        + " back:");
        for (ExecutionRecord row : rows) {
            lines.add("  " + row.nodeId().value() + " — target " + row.targetId().value());
        }
        return List.copyOf(lines);
    }

    /**
     * Says which migrations a rebuild would have to take out and cannot, and why each one.
     *
     * <p>Public because {@code rebuild} delegates its rollback to {@code down} and must refuse in
     * the same terms. Listing the stuck ids alone said nothing about which of them an operator can
     * act on: a migration held down by a one-way migration above it has a perfectly good rollback,
     * and being told to write one sends its author to a file that is already correct.
     *
     * @param frozenCount how many migrations cannot come down, held-down ones included
     * @param irreversible the ones that cannot come down on their own account, with their rows
     * @return the lines to report
     */
    public static List<String> rebuildFrozenLines(
            int frozenCount, List<DownBlocker.FrozenMigration> irreversible) {
        int held = frozenCount - irreversible.size();
        List<String> lines = new ArrayList<>();
        lines.add(
                "Error: "
                        + frozenCount
                        + " migration(s) have to be rolled back to rebuild, and cannot be"
                        + (held > 0 ? " (" + held + " of them held down by the rest)" : "")
                        + ":");
        for (DownBlocker.FrozenMigration migration : irreversible) {
            lines.add("  " + migration.node().id().value() + " — " + why(migration));
        }
        return List.copyOf(lines);
    }

    /**
     * Names what makes {@code --all} unsatisfiable, and says that nothing was removed.
     *
     * <p>The count is every applied migration that has to stay, including the ones held down by a
     * migration with no rollback; the lines name the ones that have none of their own, because
     * those are where an operator can do something — change the definition, or roll the run back by
     * some other route.
     */
    private static List<String> frozenAllLines(DownBlocker.FrozenAll frozen) {
        int held = frozen.frozenAppliedCount() - frozen.irreversible().size();
        List<String> lines = new ArrayList<>();
        lines.add(
                "Error: --all means all, and "
                        + frozen.frozenAppliedCount()
                        + " applied migration(s) cannot be rolled back"
                        + (held > 0 ? " (" + held + " of them held down by the rest)" : "")
                        + ". Nothing was rolled back:");
        for (DownBlocker.FrozenMigration migration : frozen.irreversible()) {
            lines.add("  " + migration.node().id().value() + " — " + why(migration));
        }
        return List.copyOf(lines);
    }

    /**
     * Says why a migration cannot come down, in the author's words when they left any.
     *
     * <p>The row decides, because the row is what a rollback would run. A task file declaring a
     * {@code down:} says nothing: the payload was written when the migration was applied, and if
     * the row does not carry one there is nothing to run whatever the definitions say now.
     * Reporting from the node alone told an operator to write a rollback they had already written.
     *
     * <p>Five states, and the operator's next move differs in every one. A reason the author
     * declared is quoted and there is no repair. A definition that declares neither a rollback nor
     * a reason is the author's own gap — and only a declared node can be in that state, since a
     * node standing for a row has no definition behind it. A row carrying no fingerprint cannot say
     * anything at all, whatever else it holds, and is one {@code amend} away from being able to —
     * which is why that is asked before the payload. A row that does carry one and still kept no
     * rollback recorded neither, which the definitions cannot produce. And a payload the target
     * cannot rebuild is a plugin that cannot read back what it wrote.
     *
     * <p><strong>The fingerprint arm is unreachable, and no caller exists that would reach
     * it.</strong> {@link DownService#plan} refuses the whole run before any node is frozen, and it
     * is the only thing that builds a frozen migration. The arm stays for the reason the condition
     * behind it stays: {@code DagExecutor} refuses the same row in the same order, and a formatter
     * that had dropped the case would describe the run wrongly the moment a caller hands one over.
     */
    private static String why(DownBlocker.FrozenMigration frozen) {
        MigrationNode node = frozen.node();
        ExecutionRecord row = frozen.appliedRow();
        String reason = row != null && row.noWayBack() != null ? row.noWayBack() : node.noWayBack();
        if (reason != null) {
            return "no way back: " + reason;
        }
        if (!(node instanceof RecordedNode) && node.downTask() == null) {
            return "it has no down migration, and none was declared";
        }
        if (row == null) {
            return "the history holds no row that applied it";
        }
        if (row.fingerprint() == null) {
            return "the row that applied it carries no fingerprint, so what it recorded cannot be"
                    + " read at face value; run 'migraphe upgrade-history', or 'migraphe amend "
                    + node.id().value()
                    + "' if no task file declares it any more";
        }
        if (row.serializedDownTask() == null) {
            return "the history recorded no rollback for it and no reason for having none";
        }
        return "its target " + node.target().id().value() + " cannot rebuild a recorded rollback";
    }
}
