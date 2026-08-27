package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;

/**
 * How a node's current UP content compares with the content that was applied.
 *
 * <p>Reported by {@link StatusService.NodeStatus#upContentState()}. Only the UP content is covered,
 * so editing a rollback definition or an apply-mode flag does not show up here.
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

    /** Both fingerprints are known and differ: the definition was edited after it was applied. */
    CHANGED,

    /**
     * {@link MigrationNode#fingerprint()} threw, so the current content could not be read. Unlike
     * {@link #NOT_APPLICABLE} this is a fault in the plugin rather than a declared opt-out: a
     * plugin that does not implement fingerprints inherits the interface default, which returns
     * {@code null} and cannot throw.
     */
    UNREADABLE
}
