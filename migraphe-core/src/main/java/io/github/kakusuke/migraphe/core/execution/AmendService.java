package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.task.RollbackPayloadProvider;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.core.execution.StatusService.NodeStatus;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Works out which nodes the {@code amend} command would re-record the definition of.
 *
 * <p>Amend resolves drift in the history's favour: it declares that what was applied is what the
 * task files currently define, and writes only to the history. Which nodes qualify is decided by
 * {@link StatusService} rather than recomputed here, so that what {@code status} displays and what
 * {@code amend} acts on cannot drift apart.
 *
 * <p><strong>There is one way to plan: by naming a migration.</strong> {@link #plan(NodeId)} asks
 * only whether the definitions declare it. If they do, the row appended says it is applied —
 * whatever the history said first, including nothing at all, a token that already agrees, or a
 * rollback. If they do not and the history holds it, it is an <strong>orphan</strong> and the row
 * says it is <em>not</em> applied. Either way the claim replaces what the history reports, which is
 * why it is made deliberately, node by node.
 *
 * <p>There used to be a bulk form for filling every row that carried no fingerprint. That is not
 * amend's job: it is what a history an older release wrote looks like, and completing it carries no
 * decision about the migrations, so it belongs to {@code upgrade}. Keeping it here also meant a
 * flag whose selection was <em>this</em> release's shape — a later one adding a column would leave
 * rows that have a fingerprint and lack the new thing, which the flag would not select.
 *
 * <p><strong>Amending is not the same as filling in what is absent.</strong> The row is built from
 * the definition, so the history reports the current definition for the whole node — not only the
 * part that was missing. That is the difference from {@code upgrade}, which touches only columns
 * that carry nothing. {@link AmendNotice} is what the front ends say about it.
 *
 * <p>Planning is separate from applying so that {@code --preview} and the confirmation prompt can
 * show exactly the set that would be written.
 */
public final class AmendService {

    private final StatusService statusService;
    private final HistoryRepository historyRepository;

    /**
     * Creates an amend service over a graph and its history.
     *
     * @param graph the migration graph whose nodes are inspected
     * @param historyRepository the repository consulted for applied state and latest records
     */
    public AmendService(MigrationGraph graph, HistoryRepository historyRepository) {
        this.statusService = new StatusService(graph, historyRepository);
        this.historyRepository = historyRepository;
    }

    /**
     * Works out what amending one named node would record, without writing anything.
     *
     * <p>A node argument is required for this form because a claim that replaces what the history
     * reported before is one the operator has to make deliberately, node by node. What the history
     * said first is not a condition on it: a node it has never recorded appends the first row,
     * which is how a database that already has the schema is brought under migraphe; a node whose
     * row already agrees appends the same sentence again; and a node it holds as rolled back is
     * claimed applied, which is what naming one is for.
     *
     * @param nodeId the node to amend
     * @return the plan, carrying a blocker when the migration cannot be claimed at all
     */
    public AmendPlan plan(NodeId nodeId) {
        StatusService.StatusInfo status = statusService.getStatus();

        AmendBlocker blocker = blockerFor(nodeId, status);
        if (blocker != null) {
            return new AmendPlan(blocker, List.of(), List.of());
        }

        for (StatusService.OrphanStatus orphan : status.orphans()) {
            if (orphan.nodeId().equals(nodeId)) {
                return new AmendPlan(null, List.of(), List.of(nodeId));
            }
        }

        List<AmendEntry> toRecord = new ArrayList<>();
        for (NodeStatus nodeStatus : status.nodes()) {
            if (!nodeStatus.node().id().equals(nodeId)) {
                continue;
            }
            if (!isClaimable(nodeStatus)) {
                return new AmendPlan(new AmendBlocker.NoFingerprint(nodeId), List.of(), List.of());
            }
            AmendBlocker cannotReport = cannotReportRollbackPayload(nodeStatus);
            if (cannotReport != null) {
                return new AmendPlan(cannotReport, List.of(), List.of());
            }
            toRecord.add(entryFor(nodeStatus));
        }

        return new AmendPlan(null, List.copyOf(toRecord), List.of());
    }

    /**
     * Reports why the named migration cannot be amended, or {@code null} when it can be considered.
     *
     * <p>This answers only "is there such a migration at all". What the definitions declare is
     * considered; what only the history holds is an orphan and is withdrawn rather than refused; an
     * identifier neither side knows is the error. Whether the considered node can then be claimed
     * is decided after this, and refused there.
     */
    private static @Nullable AmendBlocker blockerFor(
            NodeId nodeId, StatusService.StatusInfo status) {
        for (NodeStatus nodeStatus : status.nodes()) {
            if (nodeStatus.node().id().equals(nodeId)) {
                return null;
            }
        }
        for (StatusService.OrphanStatus orphan : status.orphans()) {
            if (orphan.nodeId().equals(nodeId)) {
                return null;
            }
        }
        return new AmendBlocker.NoSuchMigration(nodeId);
    }

    /**
     * Appends one row per planned entry, each built from the definition.
     *
     * <p>An append, not an update. A node accumulates rows as it is applied, rolled back, retried
     * and re-applied, and there is no principled answer to "which of them do I edit"; nor is a
     * deletion right, because the history records what happened and amending did not un-happen
     * anything. Appending keeps when the migration was really applied readable next to the claim
     * that replaced it, and it works because applied-ness is read from the latest applied row.
     *
     * <p>No capability is needed for this: {@link HistoryRepository#record} is on every
     * implementation.
     *
     * @param plan the plan to carry out
     * @return how many rows of each kind were appended
     */
    public AmendOutcome apply(AmendPlan plan) {
        int withdrawn = 0;
        for (NodeId orphan : plan.toWithdraw()) {
            ExecutionRecord applied = latestAppliedRowOf(orphan);
            if (applied == null) {
                continue;
            }
            historyRepository.record(
                    ExecutionRecord.amendedDown(orphan, applied.targetId(), applied.description()));
            withdrawn++;
        }

        int recorded = 0;
        for (AmendEntry entry : plan.toRecord()) {
            MigrationNode node = entry.node();
            historyRepository.record(
                    ExecutionRecord.amendedUp(
                            node.id(),
                            node.target().id(),
                            node.name(),
                            entry.serializedDownTask(),
                            entry.fingerprint(),
                            entry.pluginMetadata(),
                            entry.dependencies(),
                            node.noWayBack()));
            recorded++;
        }
        return new AmendOutcome(recorded, withdrawn);
    }

    /** The row saying the orphan was applied, which is where its target and name come from. */
    private @Nullable ExecutionRecord latestAppliedRowOf(NodeId nodeId) {
        for (StatusService.OrphanStatus orphan : statusService.getStatus().orphans()) {
            if (orphan.nodeId().equals(nodeId)) {
                return orphan.appliedRecord();
            }
        }
        return null;
    }

    /**
     * Whether the named migration can be claimed: it folds a token, and that is the whole
     * condition.
     *
     * <p>Whether the history already agrees is not part of it. The command states what the
     * definitions say, and it says the same thing whether or not the row already said it — so
     * asking about drift would make a deliberate, node-by-node claim conditional on a comparison
     * the operator did not ask about. Nor is having a row at all: a migration the history has never
     * recorded is the case that brings an existing database under migraphe, and it is the same
     * append with nothing to disagree with.
     *
     * <p>The token is required, because a row appended without one would read as a row whose
     * definition-side values cannot be trusted — which is the opposite of what this writes.
     */
    private static boolean isClaimable(NodeStatus status) {
        return readableFingerprint(status) != null;
    }

    /** The node's token, or {@code null} when it has none or its accessor threw. */
    private static @Nullable String readableFingerprint(NodeStatus status) {
        try {
            return status.node().fingerprint(status.fingerprinter());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Refuses a node whose up task cannot say what rollback it would record, or {@code null}.
     *
     * <p>Checked before an entry is built rather than inside {@link #entryFor}, so a plan carrying
     * entries is one every entry could be built for.
     */
    private static @Nullable AmendBlocker cannotReportRollbackPayload(NodeStatus status) {
        Task upTask = status.node().upTask();
        if (upTask instanceof RollbackPayloadProvider) {
            return null;
        }
        return new AmendBlocker.CannotReportRollbackPayload(
                status.node().id(), upTask.getClass().getName());
    }

    /**
     * Builds the entry for a node {@link #isClaimable} accepts.
     *
     * <p>That guard establishes the one value this needs: a claimable node folds a readable,
     * non-null fingerprint. It does <strong>not</strong> establish a latest record — a node the
     * history has never recorded, or has only rolled back, is claimable and has none. Nothing here
     * reads one; the row is built from the definition.
     */
    private static AmendEntry entryFor(NodeStatus status) {
        String fingerprint =
                Objects.requireNonNull(
                        readableFingerprint(status), "a claimable node folds a fingerprint");
        RollbackPayloadProvider provider = (RollbackPayloadProvider) status.node().upTask();
        String serializedDownTask = provider.serializedDownTask();
        String pluginMetadata = provider.pluginMetadata();
        return new AmendEntry(
                status.node(),
                fingerprint,
                status.node().dependencies().stream()
                        .sorted(Comparator.comparing(NodeId::value))
                        .toList(),
                serializedDownTask,
                pluginMetadata,
                status.upContentState());
    }

    /**
     * One record's worth of what amending would write.
     *
     * @param node the node whose content the fingerprint describes
     * @param fingerprint the fingerprint to store
     * @param dependencies the node's declared direct dependencies, which is what the column holds.
     *     Not what the fingerprint was folded over — that is the closure, and the two answer
     *     different questions
     * @param serializedDownTask the rollback to record, or {@code null} for none. Always the
     *     definition's: the row is built from scratch, so there is nothing to leave alone. A node
     *     whose up task cannot report it never reaches here — the plan refuses instead, because
     *     carrying the superseded row's value over would put into a row claiming to say what the
     *     definition says something the definition never produced
     * @param pluginMetadata the plugin's own record of the execution, on the same terms
     * @param from the state the node is in now, so a report can say what it is moving away from.
     *     {@link UpContentState#CHANGED} supersedes a known-different token, {@link
     *     UpContentState#UNKNOWN} supersedes a row that carries none, and {@link
     *     UpContentState#NOT_APPLICABLE} means the history reports the migration as not applied at
     *     all — never recorded, rolled back, or only ever failed
     */
    public record AmendEntry(
            MigrationNode node,
            String fingerprint,
            List<NodeId> dependencies,
            @Nullable String serializedDownTask,
            @Nullable String pluginMetadata,
            UpContentState from) {}

    /**
     * How many rows of each kind an {@link #apply} appended.
     *
     * <p>Two counts rather than one total, because the two are not the same sentence to a reader: a
     * recorded row says the definition is now what the history reports, and a withdrawn one says a
     * migration nobody defines any more is no longer applied. Reporting a single number forces the
     * caller to guess which it is from the plan, and a plan carrying both kinds would make that
     * guess wrong.
     *
     * @param recorded rows appended for entries whose definition was claimed
     * @param withdrawn rows appended saying an orphan is no longer applied
     */
    public record AmendOutcome(int recorded, int withdrawn) {}

    /**
     * What amending would do.
     *
     * @param blocker why the named migration cannot be amended, or {@code null} when it can — both
     *     lists empty with no blocker means there was simply nothing to do
     * @param toRecord the migrations to be claimed applied, one entry each, built from the
     *     definitions
     * @param toWithdraw the orphans to be claimed <em>not</em> applied. They carry no entry because
     *     the definitions describe nothing for one to hold; the appended row says only that
     */
    public record AmendPlan(
            @Nullable AmendBlocker blocker, List<AmendEntry> toRecord, List<NodeId> toWithdraw) {}
}
