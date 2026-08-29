package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.ExecutionStatus;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
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
     * <p>The line is {@code "<marker> <id> - <name>"}, followed by an annotation describing the
     * latest record when the status carries one: {@code " (<duration>, <timestamp>)"} for a
     * successful record, or {@code " (rollback failed <timestamp>)"} / {@code " (apply failed
     * <timestamp>)"} for one that failed. The marker is {@code [ ]} for a node that has not been
     * applied; for one that has, it reports its {@link UpContentState} — {@code [!]} for {@code
     * CHANGED}, {@code [?]} for {@code UNKNOWN}, {@code [E]} for {@code UNREADABLE}, and {@code
     * [✓]} when the content matches or the comparison does not apply.
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
            sb.append(" (").append(annotationFor(record)).append(")");
        }
        return sb.toString();
    }

    /**
     * Describes the record the annotation is built from.
     *
     * <p>A successful record established the state the marker reports, so it shows how long it took
     * and when. A failed one did not: presenting it the same way would read as an apply that took
     * no time, so it names what failed instead.
     */
    private static String annotationFor(ExecutionRecord record) {
        if (record.status() == ExecutionStatus.SUCCESS) {
            return FormatUtils.formatDuration(record.durationMs())
                    + ", "
                    + FormatUtils.formatDateTime(record.executedAt());
        }
        String operation = record.direction() == ExecutionDirection.DOWN ? "rollback" : "apply";
        return operation + " failed " + FormatUtils.formatDateTime(record.executedAt());
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
