package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryFingerprintUpdater;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.execution.StatusService.NodeStatus;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Works out which nodes the {@code amend} command would record a fingerprint for.
 *
 * <p>Amend resolves drift in the history's favour: it declares that what was applied is what the
 * task files currently define, and writes only to the history. Which nodes qualify is decided by
 * {@link StatusService} rather than recomputed here, so that what {@code status} displays and what
 * {@code amend} acts on cannot drift apart.
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
     * Works out what amending would record, without writing anything.
     *
     * @return the plan
     */
    public AmendPlan plan() {
        List<AmendEntry> toRecord = new ArrayList<>();

        for (NodeStatus status : statusService.getStatus().nodes()) {
            if (isDrifted(status.upContentState())) {
                toRecord.add(entryFor(status));
            }
        }

        return new AmendPlan(List.copyOf(toRecord));
    }

    /**
     * Writes the planned fingerprints to the history.
     *
     * @param plan the plan to carry out
     * @return how many records were revised
     */
    public int apply(AmendPlan plan) {
        if (!(historyRepository instanceof HistoryFingerprintUpdater updater)) {
            throw new IllegalStateException(
                    "This history repository cannot revise a recorded fingerprint: "
                            + historyRepository.getClass().getName());
        }

        int written = 0;
        for (AmendEntry entry : plan.toRecord()) {
            if (updater.updateFingerprint(entry.recordId(), entry.fingerprint())) {
                written++;
            }
        }
        return written;
    }

    /**
     * Reports whether a state is one that amending resolves.
     *
     * <p>Deliberately switches without a {@code default} arm: adding a state to {@link
     * UpContentState} should stop this compiling until someone decides whether amending covers it.
     */
    private static boolean isDrifted(UpContentState state) {
        return switch (state) {
            case UNKNOWN, CHANGED -> true;
            case NOT_APPLICABLE, UNCHANGED, UNREADABLE -> false;
        };
    }

    /**
     * Builds the entry for a node whose state {@link #isDrifted} accepts.
     *
     * <p>Both drifted states already establish the two values this needs: each is only reached when
     * the node has a latest record and its own fingerprint is readable and non-null.
     */
    private static AmendEntry entryFor(NodeStatus status) {
        ExecutionRecord record =
                Objects.requireNonNull(status.latestRecord(), "drift implies a latest record");
        String fingerprint =
                Objects.requireNonNull(
                        status.node().fingerprint(), "drift implies a node fingerprint");
        return new AmendEntry(status.node(), record.id(), fingerprint);
    }

    /**
     * One fingerprint that amending would record.
     *
     * @param node the node whose content the fingerprint describes
     * @param recordId the history record to revise
     * @param fingerprint the fingerprint to store
     */
    public record AmendEntry(MigrationNode node, String recordId, String fingerprint) {}

    /**
     * What amending would do.
     *
     * @param toRecord the fingerprints that would be written
     */
    public record AmendPlan(List<AmendEntry> toRecord) {}
}
