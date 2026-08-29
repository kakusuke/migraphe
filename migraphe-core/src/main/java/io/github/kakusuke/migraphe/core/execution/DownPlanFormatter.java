package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.core.execution.DownService.DownPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders what a {@code down} run refused, or had to leave behind, as the lines a front end
 * reports.
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
     * @return the lines to report
     */
    public static List<String> format(DownBlocker blocker) {
        return switch (blocker) {
            case DownBlocker.Orphans orphans -> orphanLines(orphans.orphans());
            case DownBlocker.IrreversibleTarget target ->
                    List.of(
                            "Error: "
                                    + target.target().id().value()
                                    + " cannot be rolled back — "
                                    + why(target.target()));
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
     * Formats what a {@code --all} run had to leave behind, or nothing when it left nothing.
     *
     * <p>{@code --all} cannot mean all while something has no down migration, so the run says which
     * nodes have none and how many others they hold down. The count is of applied migrations only:
     * a frozen dependency that was never applied is not a migration anyone has to deal with.
     *
     * @param plan the plan to read the remainder from
     * @return the lines to report before the rollback runs, empty when nothing was left behind
     */
    public static List<String> formatFrozen(DownPlan plan) {
        if (!plan.leftFrozen()) {
            return List.of();
        }
        int held = plan.frozenAppliedCount() - plan.irreversible().size();
        List<String> lines = new ArrayList<>();
        lines.add(
                "Error: "
                        + plan.frozenAppliedCount()
                        + " applied migrations cannot be rolled back"
                        + (held > 0 ? " (" + held + " of them held down by the rest):" : ":"));
        for (MigrationNode node : plan.irreversible()) {
            lines.add("  " + node.id().value() + " — " + why(node));
        }
        return List.copyOf(lines);
    }

    private static List<String> orphanLines(List<StatusService.OrphanStatus> orphans) {
        List<String> lines = new ArrayList<>();
        lines.add(
                "Error: "
                        + orphans.size()
                        + " applied migration(s) are no longer defined. What they stand on is not"
                        + " recorded, so rolling anything back could remove it. Nothing was rolled"
                        + " back:");
        orphans.stream()
                .map(orphan -> orphan.nodeId().value())
                .sorted()
                .forEach(id -> lines.add("  " + id));
        return List.copyOf(lines);
    }

    /**
     * Says why a node has no rollback, in the author's words when they left any.
     *
     * <p>A declared reason and a forgotten {@code down:} look identical from the outside, and the
     * operator's next move differs: respect the decision, or go write the rollback.
     */
    private static String why(MigrationNode node) {
        String declared = node.noWayBack();
        return declared != null
                ? "no way back: " + declared
                : "it has no down migration, and none was declared";
    }
}
