package io.github.kakusuke.migraphe.api.history;

/**
 * The outcome status recorded for a single migration execution.
 *
 * @see ExecutionRecord
 */
public enum ExecutionStatus {
    /** The execution completed successfully. */
    SUCCESS,

    /** The execution failed; the record carries an error message. */
    FAILURE,

    /** The execution was skipped, for example because the node had already been applied. */
    SKIPPED
}
