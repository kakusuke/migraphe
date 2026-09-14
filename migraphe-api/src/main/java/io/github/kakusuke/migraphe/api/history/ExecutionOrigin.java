package io.github.kakusuke.migraphe.api.history;

/**
 * Whether a history row was written because a migration ran, or because someone claimed it had.
 *
 * <p>Writing an up row for a migration that never ran is exactly what the maintenance command that
 * makes the history agree with the definitions is for. But the history is an audit log: an operator
 * asking "did migraphe ever run this DDL" has to be able to answer it, so the row says which of the
 * two it is.
 *
 * <p>This is a <strong>separate axis from {@link
 * io.github.kakusuke.migraphe.api.task.ExecutionDirection}</strong>, not a value of it. A claimed
 * row still says the node is now up or now down, so folding the two together would need {@code
 * AMEND_UP} and {@code AMEND_DOWN} and every place that branches on direction would have to spell
 * out both — and a place that forgot would read the node as never applied and re-run its DDL.
 * Keeping them apart costs one column and changes no existing branch.
 *
 * <p><strong>One thing branches on this</strong>, and only one: the integrity check in {@link
 * HistoryRepository#latestApplies} tells a claim apart from an execution. A second {@link
 * #EXECUTED} apply of a migration that already stands is a history no run writes, so it is refused;
 * an {@link #AMENDED} one is the maintenance command stating what the definition says now, so it
 * supersedes. Nothing else reads it — not what is applied, not what a rollback runs — so it stays
 * cheap.
 *
 * @see ExecutionRecord
 */
public enum ExecutionOrigin {
    /** The migration ran, and this row records what happened. */
    EXECUTED,

    /**
     * The row states what the definition says, without anything having been executed.
     *
     * <p>A row written before this distinction existed reads as {@link #EXECUTED}: every version
     * that could write one wrote it by running something.
     */
    AMENDED
}
