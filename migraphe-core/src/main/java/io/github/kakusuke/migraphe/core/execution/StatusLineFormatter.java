package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.core.execution.StatusService.NodeStatus;
import io.github.kakusuke.migraphe.core.graph.FormatUtils;

/**
 * Renders one node's status as the line the {@code status} command prints.
 *
 * <p>Shared by the CLI command and the Gradle task so that both report a node identically; each
 * passes this as the label function of {@link
 * io.github.kakusuke.migraphe.core.graph.layout.ExecutionGraphView#renderLines}. This class is not
 * instantiable.
 */
public final class StatusLineFormatter {

    private StatusLineFormatter() {}

    /**
     * Formats a node's status as one display line.
     *
     * <p>The line is {@code "<marker> <id> - <name>"}, followed by {@code " (<duration>,
     * <timestamp>)"} when the status carries a latest record. The marker is {@code [ ]} for a node
     * that has not been applied; for one that has, it reports its {@link UpContentState} — {@code
     * [!]} for {@code CHANGED}, {@code [?]} for {@code UNKNOWN}, {@code [E]} for {@code
     * UNREADABLE}, and {@code [✓]} when the content matches or the comparison does not apply.
     *
     * @param status the node status to render
     * @return the rendered line
     */
    public static String format(NodeStatus status) {
        StringBuilder sb = new StringBuilder();
        sb.append(markerFor(status));
        sb.append(status.node().id().value()).append(" - ").append(status.node().name());
        ExecutionRecord record = status.latestRecord();
        if (record != null) {
            sb.append(" (")
                    .append(FormatUtils.formatDuration(record.durationMs()))
                    .append(", ")
                    .append(FormatUtils.formatDateTime(record.executedAt()))
                    .append(")");
        }
        return sb.toString();
    }

    private static String markerFor(NodeStatus status) {
        if (!status.executed()) {
            return "[ ] ";
        }
        return switch (status.upContentState()) {
            case CHANGED -> "[!] ";
            case UNKNOWN -> "[?] ";
            case UNREADABLE -> "[E] ";
            case NOT_APPLICABLE, UNCHANGED -> "[✓] ";
        };
    }
}
