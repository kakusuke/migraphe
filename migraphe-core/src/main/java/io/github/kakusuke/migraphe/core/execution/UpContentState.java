package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;

/**
 * How a node's current content compares with the content that was applied.
 *
 * <p>Reported by {@link StatusService.NodeStatus#upContentState()}. What counts as the content is
 * whatever {@link MigrationNode#fingerprint(java.util.List)} covers, which is the plugin's choice;
 * this only compares two tokens.
 */
public enum UpContentState {

    /**
     * The comparison does not apply: either the node has never been applied, or its plugin supplies
     * no fingerprint and has thereby declared that it is not to be judged by one.
     *
     * <p>{@link StatusService.NodeStatus#executed()} tells the two apart.
     */
    NOT_APPLICABLE,

    /**
     * The plugin supplies a fingerprint but the applied record carries none, so whether the content
     * changed cannot be answered. Rows written before the {@code fingerprint} column existed read
     * this way.
     */
    UNKNOWN,

    /** Both fingerprints are known and equal. */
    UNCHANGED,

    /** Both fingerprints are known and differ: what is defined now is not what was applied. */
    CHANGED,

    /**
     * {@link MigrationNode#fingerprint(java.util.List)} threw, so the current content could not be
     * read. Unlike {@link #NOT_APPLICABLE} this is a fault in the plugin rather than a declared
     * opt-out: a plugin that does not implement fingerprints inherits the interface default, which
     * returns {@code null} and cannot throw.
     */
    UNREADABLE
}
