package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides what {@code rebuild} would take out of the database and put back.
 *
 * <p>Stated as a DAG difference: the migrations whose recorded state no longer matches the
 * definitions come out, in reverse dependency order, and then everything is applied. This class
 * answers only the first half — which migrations differ — and performs no mutation.
 *
 * <p><strong>A rebuild is partly a permanent removal.</strong> What comes out is everything the
 * history holds that the definitions do not, and part of that is migrations the task files no
 * longer declare — they cannot be defined out of the difference, because the history-only side
 * <em>is</em> where they live. What the definitions still declare goes straight back up; what they
 * have lost does not, because there is nothing left to put back. That is the wanted behaviour for a
 * development-time command and worth knowing before running one.
 *
 * <p>Content and edges are one question, not two. The fingerprint covers the transitive
 * dependencies as well as the SQL, so a rewired edge moves the token exactly as an edited statement
 * does. The history's {@code dependencies} column exists for <em>ordering</em> a rollback through
 * nodes the definitions may no longer contain, which a hash cannot do — not for detecting the
 * difference.
 */
public final class RebuildService {

    private final MigrationGraph graph;
    private final HistoryRepository historyRepository;

    /**
     * Creates a rebuild service over a graph and its history.
     *
     * @param graph the migration graph whose nodes are inspected
     * @param historyRepository the repository consulted for what was applied
     */
    public RebuildService(MigrationGraph graph, HistoryRepository historyRepository) {
        this.graph = graph;
        this.historyRepository = historyRepository;
    }

    /**
     * Works out what rebuilding would touch, without touching anything.
     *
     * @param configuredTargets every target the project configures, used to resolve the connection
     *     each recorded row names — the rollback phase is {@code down}'s, and {@code down} takes
     *     objects out of where they were put rather than where the definitions point today
     * @return the plan
     */
    public RebuildPlan plan(Collection<Target> configuredTargets) {
        DownService downService = new DownService(graph, historyRepository);

        // Before anything is compared, because an unreadable row is what the comparison would be
        // reading. `down` refuses the same state in the same words, and this asks it the same
        // question rather than working out a second answer.
        //
        // Rows whose target no longer resolves are carried through, because a front end reports
        // those first and both states have to be cleared anyway. What is *not* carried is anything
        // the comparison would have produced: a plugin whose accessor throws is reported only once
        // the history can be read, so an operator holding both states sees them one run apart.
        // That is the accepted cost of not running a comparison over rows that cannot be read.
        UnusableAppliedRows unusable = UnusableAppliedRows.of(graph, historyRepository);
        if (unusable.any()) {
            return new RebuildPlan(
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    List.of(),
                    unusable.incomplete(),
                    graph,
                    downService.rowsWithNoTarget(configuredTargets),
                    unusable.unreadable());
        }

        GraphDifference difference =
                GraphDifference.between(
                        graph, RecordedGraph.of(historyRepository, configuredTargets).graph());

        MigrationGraph runGraph = downService.rollbackGraph(configuredTargets);

        // What H holds that D does not: a migration the definitions no longer declare, and one
        // whose recorded content is not what they say now. Both come down — and what comes down
        // with them is `down`'s question, asked rather than worked out again here.
        Set<NodeId> comingDown = new LinkedHashSet<>(difference.contentDiffers());
        comingDown.addAll(difference.onlyInHistory());
        Set<NodeId> toRollBack = downService.withAppliedDependents(comingDown, runGraph);

        DownService.RollbackBlockers blockers = downService.rollbackBlockers(runGraph);
        Set<NodeId> frozen = new LinkedHashSet<>();
        for (NodeId node : toRollBack) {
            if (blockers.frozen().contains(node)) {
                frozen.add(node);
            }
        }
        // What is stuck, and what is stuck *because of it*, are different answers: a migration
        // holding others down is the one an operator can act on, and the rest are held.
        List<DownBlocker.FrozenMigration> irreversible =
                downService.frozenMigrationsOf(blockers.irreversible(), runGraph);

        return new RebuildPlan(
                Set.copyOf(difference.contentDiffers()),
                Set.copyOf(toRollBack),
                Set.copyOf(frozen),
                irreversible,
                Set.copyOf(difference.cannotCompare()),
                runGraph,
                downService.rowsWithNoTarget(configuredTargets),
                Set.copyOf(difference.unreadable()));
    }

    /**
     * What rebuilding would do.
     *
     * <p>A node that is only in the definitions was never applied and is {@code up}'s job, not a
     * rebuild's; a node whose comparison cannot be made — a row predating the fingerprint column, a
     * plugin that supplies none — is deliberately not a difference, because including it would mean
     * the first rebuild after an upgrade tore down and re-created most of the database. Clearing
     * that state is {@code upgrade}'s job.
     *
     * @param toRebuild the migrations whose recorded content no longer matches the definitions.
     *     What is only in the history is not among them: it comes down and does not go back up,
     *     because the definitions no longer say what it was
     * @param toRollBack what has to come out of the database first: everything H holds that D does
     *     not — the migrations whose content differs <em>and</em> the ones the definitions no
     *     longer declare — plus every applied migration standing on one of those. Rolling back a
     *     migration means rolling back what was built on top of it, and what stands on what is read
     *     off {@code graph}, since a migration only H holds is in no declaration to be a dependent
     *     of
     * @param irreversible the subset of {@code frozen} that cannot come down <em>on its own
     *     account</em>, each paired with the row that applied it so a refusal can say why. The rest
     *     of {@code frozen} is held down by these, and telling their authors to write a rollback
     *     would send them to a file that already has one
     * @param frozen the migrations in {@code toRollBack} that cannot come down — a rebuild
     *     involving any of them stops there, which is the case nothing can do anything about. The
     *     rule is {@link DownService}'s, asked rather than copied
     * @param incomplete the applied migrations whose comparison cannot be made because the history
     *     never recorded the attribute. They are not a difference — decision 7 keeps them out, so
     *     the first rebuild after an upgrade does not tear down the world — but a rebuild cannot
     *     proceed around them either: "not known to differ" is not "known to agree", and acting on
     *     the former as if it were the latter is the guess a caller must not make. One {@code amend
     *     upgrade} away
     * @param graph the graph the rollback phase has to execute against — the declarations, with
     *     every node the history placed against another target replaced by the row that placed it.
     *     The apply phase that follows runs over the definitions, because that is what it is
     *     putting back
     * @param unresolvableRows applied rows naming a target the project no longer configures. There
     *     is no connection to take those migrations out through, and running around them would
     *     remove what they still stand on — so a rebuild stops, for the reason and in the words
     *     {@code down} stops for
     * @param unreadable the migrations whose comparison threw. Not evidence of agreement, so a
     *     rebuild stops for the same reason it stops on {@code incomplete} — but a different state
     *     and a different next move: an absent token is an upgrade to finish, an accessor that
     *     fails is a fault in the plugin, and no repair the operator runs turns the second into the
     *     first
     */
    public record RebuildPlan(
            Set<NodeId> toRebuild,
            Set<NodeId> toRollBack,
            Set<NodeId> frozen,
            List<DownBlocker.FrozenMigration> irreversible,
            Set<NodeId> incomplete,
            MigrationGraph graph,
            List<ExecutionRecord> unresolvableRows,
            Set<NodeId> unreadable) {}
}
