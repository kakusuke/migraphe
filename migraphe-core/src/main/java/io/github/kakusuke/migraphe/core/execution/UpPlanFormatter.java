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
     * @return the lines to report, the first of which names the problem and the rest of which list
     *     the offending tasks
     */
    public static List<String> format(UpBlocker blocker) {
        return switch (blocker) {
            case UpBlocker.UnresolvedDependencies unresolved ->
                    unresolvedLines(unresolved.byNode());
            case UpBlocker.UndeclaredIrreversible undeclared -> undeclaredLines(undeclared.nodes());
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
