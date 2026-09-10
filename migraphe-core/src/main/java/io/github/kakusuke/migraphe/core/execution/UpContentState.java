package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;

/**
 * How a node's current content compares with the content that was applied.
 *
 * <p>Reported by {@link StatusService.NodeStatus#upContentState()}. What counts as the content is
 * whatever {@link MigrationNode#fingerprint} covers, which is the plugin's choice; this only
 * compares two tokens.
 */
public enum UpContentState {

    /**
     * The comparison does not apply, which for any conforming plugin means the node has never been
     * applied.
     *
     * <p>It has one reading, and only one: the history holds no applied row for this node. A
     * declared node that answers {@code null} is not filed here — that is out of contract and
     * reports as {@link #UNREADABLE} — so nothing has to tell two meanings apart afterwards.
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
     * {@link MigrationNode#fingerprint} could not say what the node holds — it threw, or it
     * answered with none — so the current content cannot be read.
     *
     * <p>A fault in the plugin, not a choice. Supplying a token is the contract: the method has no
     * {@code default} to inherit, and the single null the design keeps belongs to core's own
     * adapter over a history row, which has to report that the row carries none. A declared node
     * answering null is therefore out of contract, and reading it as "nothing to compare" would put
     * the row among the states {@code amend} repairs — which it is not, since there is nothing for
     * a rebuilt row to be built from.
     */
    UNREADABLE
}
