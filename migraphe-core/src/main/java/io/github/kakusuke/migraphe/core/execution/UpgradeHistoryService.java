package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.Fingerprinter;
import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.history.HistoryUpgrade;
import io.github.kakusuke.migraphe.api.history.UpgradeContext;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.List;
import java.util.Objects;

/**
 * Works out which upgrades a history still needs, and applies them.
 *
 * <p>An upgrade brings a history an older release wrote to the shape this version writes. It is not
 * a claim about the migrations: {@code amend} records what the definitions say now because an
 * operator decided that is right, and writes every column they determine; an upgrade completes what
 * an older version wrote incompletely. That is why deleting a {@code down:} and running {@code
 * amend} overwrites the recorded rollback with nothing, and running this cannot.
 *
 * <p>The order the repository declares is kept, because it is load-bearing: a step that fills a
 * column has to run after the step that adds it.
 */
public final class UpgradeHistoryService {

    private final MigrationGraph graph;
    private final HistoryRepository historyRepository;

    /**
     * Creates a service over one project's definitions and history.
     *
     * @param graph the migrations the project declares now, handed to each upgrade that fills
     *     columns from them
     * @param historyRepository the history to upgrade
     */
    public UpgradeHistoryService(MigrationGraph graph, HistoryRepository historyRepository) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
        this.historyRepository =
                Objects.requireNonNull(historyRepository, "historyRepository must not be null");
    }

    /**
     * Returns the lines a command refuses with while this history still needs an upgrade.
     *
     * <p>Static, and taking the repository rather than being an instance method, because the
     * callers are the commands that have <em>not</em> been asked to upgrade anything: they hold a
     * repository and want a yes or no before they touch it. A pending upgrade means the history is
     * not in the shape this version reads, so proceeding produces a SQL error naming a missing
     * column instead of a sentence naming what to run.
     *
     * <p>Every command that opens the history calls this — {@code status} included. The rule that
     * an incomplete history stops every command but {@code status} is about rows whose content
     * cannot be read; this is about a table whose columns are not there to select.
     *
     * @param historyRepository the history the command is about to use
     * @param repair how this front end is invoked, for the command the message names
     * @return the refusal lines, or an empty list when the history is already current
     */
    public static List<String> pendingRefusal(
            HistoryRepository historyRepository, RepairVocabulary repair) {
        List<HistoryUpgrade> pending =
                historyRepository.upgrades().stream().filter(HistoryUpgrade::isPending).toList();
        return pending.isEmpty()
                ? List.of()
                : HistoryRefusalFormatter.pendingUpgradeLines(pending, repair);
    }

    /**
     * Names the upgrades this history still needs, in the order the repository declares them.
     *
     * <p>Each is asked whether it is pending, and one that reports itself already applied is left
     * out — which is what makes running the upgrade command repeatedly safe, with no schema-version
     * bookkeeping anywhere.
     *
     * @return the plan, empty when the history is already in this version's shape
     */
    public UpgradePlan plan() {
        return new UpgradePlan(
                historyRepository.upgrades().stream().filter(HistoryUpgrade::isPending).toList());
    }

    /**
     * Applies every upgrade the plan names, in order.
     *
     * @param plan the plan to apply
     * @return how many upgrades were applied
     */
    public UpgradeOutcome apply(UpgradePlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");
        UpgradeContext context = new GraphUpgradeContext(graph);
        for (HistoryUpgrade upgrade : plan.pending()) {
            upgrade.apply(context);
        }
        return new UpgradeOutcome(plan.pending().size());
    }

    /**
     * The context core lends an upgrade: the project's definitions, and core's own folding.
     *
     * <p>Both come off the one {@link MigrationGraph} the service was built with, so an upgrade
     * filling a row arrives at the token a fresh apply would have written — folded over the same
     * canonical closure, with the same three attributes core signs itself.
     */
    private record GraphUpgradeContext(MigrationGraph graph) implements UpgradeContext {

        @Override
        public MigrationGraphView definitions() {
            return graph;
        }

        @Override
        public Fingerprinter fingerprinterFor(NodeId nodeId) {
            return graph.fingerprinterFor(nodeId);
        }
    }

    /**
     * The upgrades a history still needs.
     *
     * @param pending the outstanding upgrades, in the order they must be applied
     */
    public record UpgradePlan(List<HistoryUpgrade> pending) {

        /**
         * Copies the list, so a repository handing back a mutable one cannot change a plan under
         * the command that is applying it.
         *
         * @param pending the outstanding upgrades, in the order they must be applied
         */
        public UpgradePlan {
            pending = List.copyOf(pending);
        }

        /**
         * Reports whether the history is already in the shape this version writes.
         *
         * @return {@code true} when nothing is outstanding
         */
        public boolean isUpToDate() {
            return pending.isEmpty();
        }
    }

    /**
     * What an upgrade run did.
     *
     * @param applied how many upgrades were applied
     */
    public record UpgradeOutcome(int applied) {}
}
