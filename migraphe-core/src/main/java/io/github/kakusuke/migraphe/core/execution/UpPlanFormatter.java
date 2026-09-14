package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders what stopped an {@code up} run as the lines a front end reports.
 *
 * <p>Shared by the CLI command and the Gradle task so that both refuse in the same words; the CLI
 * prints the lines to standard error, the Gradle task joins them into its build failure. This class
 * is not instantiable.
 */
public final class UpPlanFormatter {

    private UpPlanFormatter() {}

    /**
     * Formats what refuses an UP run.
     *
     * <p>Deliberately switches without a {@code default} arm: adding a case to {@link UpBlocker}
     * should stop this compiling until someone writes what it says, rather than silently refusing
     * with no explanation.
     *
     * @param blocker what refuses the run
     * @param repair how this front end is invoked, for the commands the message names
     * @return the lines to report, the first of which names the problem and the rest of which list
     *     the offending tasks
     */
    public static List<String> format(UpBlocker blocker, RepairVocabulary repair) {
        return switch (blocker) {
            case UpBlocker.UnresolvedDependencies unresolved ->
                    unresolvedLines(unresolved.byNode());
            case UpBlocker.UndeclaredIrreversible undeclared -> undeclaredLines(undeclared.nodes());
            case UpBlocker.UnrecordableRollback unrecordable ->
                    unrecordableLines(unrecordable.nodes());
            case UpBlocker.IncompleteHistory incomplete ->
                    HistoryRefusalFormatter.incompleteLines(incomplete.nodes(), repair);
            case UpBlocker.UnreadableContent unreadable ->
                    HistoryRefusalFormatter.unreadableLines(unreadable.nodes(), repair);
            case UpBlocker.EditedSinceApplied edited -> editedLines(edited.nodes(), repair);
        };
    }

    private static List<String> unresolvedLines(Map<NodeId, Set<NodeId>> byNode) {
        List<String> lines = new ArrayList<>();
        lines.add(
                "Error: "
                        + byNode.size()
                        + " task(s) depend on migrations that are not defined. Applying them would"
                        + " build on ground nothing describes:");
        byNode.forEach(
                (nodeId, missing) ->
                        missing.forEach(
                                dep -> lines.add("  " + nodeId.value() + " → " + dep.value())));
        return List.copyOf(lines);
    }

    /**
     * Names the tasks whose declared rollback would not reach the history.
     *
     * <p>The remedy is the plugin's, not the operator's: what gets stored is what the up task
     * reports, so a plugin that keeps the rollback anywhere else records nothing. Saying so is more
     * use than naming a file to edit, because editing the task cannot change where its plugin puts
     * the payload.
     */
    private static List<String> unrecordableLines(Set<NodeId> nodes) {
        List<String> lines = new ArrayList<>();
        lines.add(
                "Error: "
                        + nodes.size()
                        + " task(s) declare a rollback their plugin would not record. What the"
                        + " history keeps is what the up task reports, so applying these would"
                        + " write rows saying they kept no rollback — and nothing could roll them"
                        + " back afterwards:");
        nodes.stream().map(NodeId::value).sorted().forEach(id -> lines.add("  " + id));
        return List.copyOf(lines);
    }

    /**
     * Names the migrations edited since they ran, and both ways out.
     *
     * <p>Every one of them is a repair and none is safe to pick for the operator: which is right
     * depends on the objects, which migraphe cannot read. They are named in the order an operator
     * reaches for them — take the migration out and let it be applied again, or accept what is
     * applied — with the sweep last, because rebuilding everything that differs is the heavier move
     * and takes orphans out permanently along the way.
     */
    private static List<String> editedLines(Set<NodeId> nodes, RepairVocabulary repair) {
        List<String> lines = new ArrayList<>();
        lines.add(
                "Error: "
                        + nodes.size()
                        + " applied migration(s) were edited after they ran, so the database does"
                        + " not match the definitions:");
        nodes.stream().map(NodeId::value).sorted().forEach(id -> lines.add("  [!] " + id));
        lines.add(
                "Applying on top of that builds on ground the task files no longer describe. Take"
                        + " one out with '"
                        + repair.down()
                        + "' and let the next apply put it back as it now reads, or record that"
                        + " what is applied is correct with '"
                        + repair.named()
                        + "'. To do that for every difference at once, run '"
                        + repair.rebuild()
                        + "'.");
        return List.copyOf(lines);
    }

    private static List<String> undeclaredLines(Set<NodeId> nodes) {
        List<String> lines = new ArrayList<>();
        lines.add(
                "Error: "
                        + nodes.size()
                        + " task(s) define neither down: nor no_way_back:. Write the rollback, or"
                        + " state why there is none — once a migration has run it is too late to"
                        + " decide:");
        nodes.stream().map(NodeId::value).sorted().forEach(id -> lines.add("  " + id));
        return List.copyOf(lines);
    }
}
