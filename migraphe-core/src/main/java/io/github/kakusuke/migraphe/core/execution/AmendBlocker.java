package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.NodeId;

/**
 * Why {@code amend} cannot act on the migration it was given.
 *
 * <p>Distinct from an empty plan. A migration that is defined and already agrees with its record
 * has nothing to amend, and saying so is an honest success; an id the command cannot act on at all
 * is not, and reporting one as the other is how a typo comes back as "nothing to do".
 *
 * <p>The wording is built by {@link AmendPlanFormatter} so that every front end refuses in the same
 * words.
 */
public sealed interface AmendBlocker {

    /**
     * The id names nothing — neither a defined migration nor a row in the history.
     *
     * @param nodeId what was asked for
     */
    record NoSuchMigration(NodeId nodeId) implements AmendBlocker {}

    /**
     * The node's up task cannot say what rollback it would record, so no row can be built for it.
     *
     * <p>Amending executes nothing, so the rollback payload — the one value a real apply gets back
     * from the plugin's {@code TaskResult} — has to be obtained without running anything, through
     * {@link io.github.kakusuke.migraphe.api.task.RollbackPayloadProvider}. A plugin that does not
     * implement it cannot be amended: the row is built from scratch, so there is no earlier payload
     * to carry over, and core must not invent one because the format is the plugin's. Copying the
     * superseded row's payload would put a value the definition never produced into a row that
     * claims to say what the definition says.
     *
     * @param nodeId the migration that cannot be amended
     * @param upTaskClassName the task that could not report it, which names the plugin
     */
    record CannotReportRollbackPayload(NodeId nodeId, String upTaskClassName)
            implements AmendBlocker {}

    /**
     * The node folds no token, so the row that would be appended could say nothing about itself.
     *
     * <p>The fingerprint is the one marker of a complete row: a row carrying none reads as one
     * whose definition-side values cannot be trusted, which is the opposite of what amending
     * writes. So a migration that cannot fold one cannot be claimed — and that is a refusal, not
     * "nothing to do". Reporting it as success would tell an operator their claim was made when no
     * row was written at all.
     *
     * <p>Two things reach here: a plugin whose node reports no token, and one whose accessor threw.
     * Both are the plugin's, and neither is a state a repair the operator runs will change.
     *
     * @param nodeId the migration that cannot be claimed
     */
    record NoFingerprint(NodeId nodeId) implements AmendBlocker {}
}
