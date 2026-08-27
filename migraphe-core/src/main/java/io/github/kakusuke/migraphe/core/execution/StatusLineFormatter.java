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
     * <p>An applied node reads {@code "[✓] <id> - <name> (<duration>, <timestamp>)"}; one that has
     * not been applied reads {@code "[ ] <id> - <name>"}. The parenthesized suffix is present
     * exactly when the status carries a latest record, which {@link StatusService} supplies only
     * for applied nodes.
     *
     * @param status the node status to render
     * @return the rendered line
     */
    public static String format(NodeStatus status) {
        StringBuilder sb = new StringBuilder();
        sb.append(status.executed() ? "[✓] " : "[ ] ");
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
}
