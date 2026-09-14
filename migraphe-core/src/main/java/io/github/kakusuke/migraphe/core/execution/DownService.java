package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.target.DownTaskRestorer;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Decides what a {@code down} run would roll back, and what stops it.
 *
 * <p>This is the whole decision a {@code down} front end makes: every check that can refuse a run,
 * and the set of nodes it would roll back. The CLI command and the Gradle task both call it and do
 * nothing beyond rendering the answer, prompting, and choosing an exit code — a guard added here
 * therefore reaches both, which is what a guard living in one of them did not.
 *
 * <p>It reads state from a {@link HistoryRepository} but performs no mutation; the run itself is
 * still {@link DagExecutor}'s.
 */
public final class DownService {

    /**
     * Recency, the way the whole family defines it: {@code executedAt}, then the identifier.
     *
     * <p>Identifiers are time-ordered, and a driver that drops sub-second precision leaves two rows
     * of one migration sharing an instant.
     */
    private static final Comparator<ExecutionRecord> BY_RECENCY =
            Comparator.comparing(ExecutionRecord::executedAt).thenComparing(ExecutionRecord::id);

    private final MigrationGraph graph;
    private final HistoryRepository historyRepository;

    /**
     * Creates a down service over a graph and its history.
     *
     * @param graph the migration graph whose nodes are inspected
     * @param historyRepository the repository consulted for applied state
     */
    public DownService(MigrationGraph graph, HistoryRepository historyRepository) {
        this.graph = graph;
        this.historyRepository = historyRepository;
    }

    /**
     * Resolves the connection a row names, or {@code null} when the project no longer configures
     * it.
     *
     * <p>From the <em>configured</em> targets, never from a declared node that happens to share
     * one. The objects to remove are where they were put, which is what the row records; reading
     * the connection off a sibling node made an orphan unreachable the moment the last task
     * pointing at its target was deleted — the case a rollback most needs to reach.
     *
     * <p>A row whose {@code target_id} the project no longer configures at all cannot be rolled
     * back: there is no connection to reconstruct. That is a limit of the configuration, not of
     * this lookup.
     */
    private static @Nullable Target targetOf(
            TargetId targetId, Collection<Target> configuredTargets) {
        for (Target configured : configuredTargets) {
            if (configured.id().equals(targetId)) {
                return configured;
            }
        }
        return null;
    }

    /**
     * The row that speaks for each node, across every declared target, and the ids none does.
     *
     * <p>The walk follows edges a node declared, and nothing says a dependency lives in the same
     * target as its dependent — so resolving one against a single target's rows would read a
     * cross-target edge as "cannot say" and refuse every rollback that crosses a target boundary.
     *
     * <p>The history can hold rows for one id under several targets — a node id comes from its file
     * path and does not move when the task's {@code target:} is edited — but {@link
     * HistoryRepository#latestApplies} answers with one row per identifier, the newest successful
     * one, and only when it is an apply. So a migration rolled back and re-applied elsewhere yields
     * one row rather than two, and an id with nothing standing under it yields none — which is what
     * makes the walk refuse at a withdrawn intermediate rather than read its edges. The merge below
     * is a guard against a repository that answers otherwise, not a rule this class applies.
     */
    private Map<NodeId, ExecutionRecord> appliedRows() {
        Map<NodeId, ExecutionRecord> byNode = new HashMap<>();
        for (ExecutionRecord apply : historyRepository.latestApplies()) {
            byNode.merge(
                    apply.nodeId(),
                    apply,
                    (existing, incoming) ->
                            BY_RECENCY.compare(incoming, existing) > 0 ? incoming : existing);
        }
        return byNode;
    }

    /**
     * Works out what rolling back would do, without rolling anything back.
     *
     * <p>The checks run in a fixed order and the first one that refuses wins: a plan carrying a
     * {@linkplain DownPlan#blocker() blocker} carries no target nodes. What cannot be rolled back,
     * and what is currently applied, are each computed once and read by every question that depends
     * on them, so no question is asked of the history twice within a step.
     *
     * @param requestedNode the node to roll back together with everything depending on it, or
     *     {@code null} to defer to {@code allMigrations}
     * @param allMigrations {@code true} to roll back every applied migration that can be rolled
     *     back
     * @param configuredTargets every target the project configures, used to resolve the connection
     *     a recorded row names — the objects to remove are where they were put, and only the
     *     configuration can turn that name back into a connection
     * @return the plan
     */
    public DownPlan plan(
            @Nullable NodeId requestedNode,
            boolean allMigrations,
            Collection<Target> configuredTargets) {
        List<ExecutionRecord> unresolvable = rowsWithNoTarget(configuredTargets);
        if (!unresolvable.isEmpty()) {
            return new DownPlan(new DownBlocker.UnresolvableTargets(unresolvable), Set.of(), graph);
        }

        UnusableAppliedRows unusable = UnusableAppliedRows.of(graph, historyRepository);
        if (!unusable.unreadable().isEmpty()) {
            return new DownPlan(
                    new DownBlocker.UnreadableHistory(unusable.unreadable()), Set.of(), graph);
        }
        if (!unusable.incomplete().isEmpty()) {
            return new DownPlan(
                    new DownBlocker.IncompleteHistory(unusable.incomplete()), Set.of(), graph);
        }

        MigrationGraph runGraph = rollbackGraph(configuredTargets);
        Map<NodeId, ExecutionRecord> appliedRows = appliedRows();
        RollbackBlockers blockers = rollbackBlockers(runGraph);

        if (requestedNode != null && blockers.frozen().contains(requestedNode)) {
            return new DownPlan(
                    blockerForTarget(requestedNode, blockers, runGraph, appliedRows),
                    Set.of(),
                    runGraph);
        }

        if (!allMigrations) {
            return new DownPlan(null, nodesToRollBack(requestedNode, blockers, runGraph), runGraph);
        }

        // Asked once and read twice: what to roll back, and how much of what has to stay behind
        // is a migration anybody actually applied.
        Set<NodeId> applied = appliedNodes(runGraph);
        int frozenAppliedCount = (int) applied.stream().filter(blockers.frozen()::contains).count();
        if (frozenAppliedCount > 0) {
            return new DownPlan(
                    new DownBlocker.FrozenAll(
                            frozenAppliedCount,
                            frozenMigrationsOf(blockers.irreversible(), runGraph, appliedRows)),
                    Set.of(),
                    runGraph);
        }

        return new DownPlan(null, notFrozen(applied, blockers), runGraph);
    }

    /**
     * The graph the rollback runs over: the history's, with nothing borrowed from the definitions.
     *
     * <p>Which nodes, the order, and the connection all come from the rows. The objects to remove
     * are where they were put and stand on what they stood on <em>when they ran</em>; what the
     * definitions say today is where the migration would go and what it would stand on if it were
     * applied now. Both differ from the database the moment a task file is edited, and a rollback
     * planned from them removes the wrong things in the wrong order — running a {@code down:}
     * against a re-pointed target leaves the old objects standing, and following a re-declared
     * dependency takes a migration out from under one still built on it.
     *
     * <p>Assembling it from the definitions and substituting only what visibly moved was the same
     * mistake in a smaller place: a node whose {@code dependencies:} was edited has not moved, so
     * it kept its declaration's edges, and the cascade followed a shape the database never had.
     *
     * <p>Package-visible because {@code rebuild} delegates its rollback phase to this command and
     * must connect the same way: where a rollback connects is {@code down}'s question, answered
     * from the history in one place. A second copy of the rule would drift, and the way it would
     * drift is by keeping the definition's target — the thing this exists to stop.
     *
     * @param configuredTargets every target the project configures
     * @return the graph a rollback has to execute against
     */
    MigrationGraph rollbackGraph(Collection<Target> configuredTargets) {
        return RecordedGraph.of(historyRepository, configuredTargets).graph();
    }

    /**
     * The applied rows naming a target the project no longer configures, ordered by node id.
     *
     * <p>Read before anything else a rollback decides. Such a row cannot become a node — there is
     * no connection to bind it to — and dropping it silently is what let a run remove the
     * migrations underneath it and report success. Whether the definitions still declare the
     * identifier makes no difference: it is the row that says where the objects are.
     *
     * <p>Package-visible because {@code rebuild} has to give the same answer: it takes the same
     * things out, so a row it cannot connect to stops it for the same reason, in the same words.
     *
     * @param configuredTargets every target the project configures
     * @return the rows no connection can be reconstructed for
     */
    List<ExecutionRecord> rowsWithNoTarget(Collection<Target> configuredTargets) {
        return appliedRows().values().stream()
                .filter(row -> targetOf(row.targetId(), configuredTargets) == null)
                .sorted(Comparator.comparing(row -> row.nodeId().value()))
                .toList();
    }

    /**
     * The applied nodes a rollback of {@code requestedNode} would take out: that node and every
     * applied migration standing on it, minus anything no rollback may touch.
     *
     * <p>Only the named request comes here. A {@code --all} run is answered in {@link #plan}
     * directly, because it either takes out everything applied or refuses — there is no set to
     * compute between those two.
     */
    private Set<NodeId> nodesToRollBack(
            @Nullable NodeId requestedNode, RollbackBlockers blockers, MigrationGraph runGraph) {
        if (requestedNode == null) {
            return Set.of();
        }
        return withAppliedDependents(Set.of(requestedNode), runGraph).stream()
                .filter(id -> !blockers.frozen().contains(id))
                .collect(Collectors.toSet());
    }

    /**
     * The applied migrations taking these out removes: each one, and everything standing on it.
     *
     * <p>Taking a migration out means taking out what was built on top of it, and what stands on
     * what is read off the graph the rollback runs over — so a migration only the history holds is
     * a dependent like any other. A dependent that was never applied has nothing to take out.
     *
     * <p>Package-visible because {@code rebuild} hands its difference to {@code down} rather than
     * working out a cascade of its own: "a dependent that has to come down with a changed node is
     * not {@code rebuild}'s business to work out". What it does <em>not</em> share is the frozen
     * filter above — {@code down} drops those, {@code rebuild} keeps them so it can refuse and name
     * them.
     *
     * @param requested the migrations being taken out
     * @param runGraph the graph the rollback runs over
     * @return those migrations and their applied dependents
     */
    Set<NodeId> withAppliedDependents(Set<NodeId> requested, MigrationGraph runGraph) {
        Set<NodeId> selected = new LinkedHashSet<>();
        for (NodeId node : requested) {
            selected.add(node);
            selected.addAll(runGraph.getAllDependents(node));
        }
        return selected.stream()
                .filter(id -> runGraph.getNode(id).isPresent())
                .filter(historyRepository::wasExecuted)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * The applied migrations that cannot be rolled back, and everything stuck behind them.
     *
     * <p>A node with no rollback cannot be removed, so neither can anything it depends on —
     * removing a dependency would break the node that has to stay. This propagates downwards only:
     * a node that depends on it can still be removed, because removing it leaves the frozen node
     * untouched. The remainder is therefore closed under dependents, which is what makes rolling it
     * back in reverse order safe.
     *
     * <p>Package-visible because {@code rebuild} asks the same question and must get the same
     * answer: what counts as unable to come down is a rule with history behind it — a failed
     * rollback leaves a node applied, a declared one-way migration is not the same as a forgotten
     * rollback — and a second copy of it would drift.
     *
     * @return the irreversible set and the frozen set it drags with it
     */
    RollbackBlockers rollbackBlockers() {
        return rollbackBlockers(graph);
    }

    /**
     * The same question asked of the graph a rollback will actually run over.
     *
     * <p>What can come down is a property of the node that will execute, not of the node the
     * definitions declare. Where the history placed a migration somewhere the definitions no longer
     * do, the node that executes stands for the row, and only the row can say whether a rollback
     * exists — the definition's {@code down:} describes the objects it would create today, not the
     * ones the row put where it put them.
     *
     * @param runGraph the graph the rollback will execute against
     * @return the irreversible set and the frozen set it drags with it
     */
    RollbackBlockers rollbackBlockers(MigrationGraph runGraph) {
        Map<NodeId, ExecutionRecord> applied = appliedRows();
        Set<NodeId> irreversible = new HashSet<>();
        Set<NodeId> frozen = new HashSet<>();
        for (MigrationNode node : runGraph.allNodes()) {
            if (canComeDown(node, applied.get(node.id()))
                    || !historyRepository.wasExecuted(node.id())) {
                continue;
            }
            irreversible.add(node.id());
            frozen.add(node.id());
            frozen.addAll(runGraph.getAllDependencies(node.id()));
        }
        return new RollbackBlockers(Set.copyOf(irreversible), Set.copyOf(frozen));
    }

    /**
     * Whether this node's rollback can actually be run: the row kept a payload, and the target can
     * rebuild one from it.
     *
     * <p>Both halves are the executor's own conditions. Asking the definition for a {@code down:}
     * instead answered a question nobody runs: a rollback executes the payload the row kept, so a
     * task file declaring one says nothing about whether this migration can come down. While this
     * read the definition, a plan listed migrations the run then refused — and refused them one at
     * a time, mid-run, leaving what it had already removed removed.
     *
     * <p>A row carrying <strong>no fingerprint</strong> cannot come down either, whatever else it
     * holds. The fingerprint is the one marker of an incomplete row: a row that carries one was
     * written by a version that writes every attribute the definitions determine, and a row that
     * carries none predates them all, so nothing in it can be read at face value — including the
     * edges a rollback orders itself by.
     *
     * <p><strong>Nothing reaches this clause today.</strong> Both callers of {@link
     * #rollbackBlockers(MigrationGraph)} — {@link #plan} and {@code RebuildService.plan} — refuse
     * the whole run first while any applied row carries no fingerprint, and both draw their rows
     * from the same fold this filter scans. It stays because {@code DagExecutor}'s own refusal asks
     * the same question of the same row, and a plan that answered differently would list a
     * migration the run then declines — the split this family has already produced once, at the
     * cost of a half-removed database.
     *
     * @param node the node as it will execute
     * @param appliedRow the row that applied it, or {@code null} when the history holds none
     */
    private static boolean canComeDown(MigrationNode node, @Nullable ExecutionRecord appliedRow) {
        if (!(node.target() instanceof DownTaskRestorer)) {
            return false;
        }
        return appliedRow != null
                && appliedRow.serializedDownTask() != null
                && appliedRow.fingerprint() != null;
    }

    /**
     * Names what refuses a rollback of {@code target}, which is known to be frozen.
     *
     * <p>The node handed on is the one that will execute, not the one the definitions declare. The
     * refusal quotes the author's reason for the migration being one-way, and where the history
     * placed a migration somewhere the definitions no longer do, that reason lives in the row —
     * reading it off the declaration reports "none was declared" about a task file that declares a
     * rollback, and sends the operator to edit something that is already right.
     */
    private DownBlocker blockerForTarget(
            NodeId target,
            RollbackBlockers blockers,
            MigrationGraph runGraph,
            Map<NodeId, ExecutionRecord> appliedRows) {
        if (blockers.irreversible().contains(target)) {
            return new DownBlocker.IrreversibleTarget(
                    new DownBlocker.FrozenMigration(
                            Objects.requireNonNull(
                                    runGraph.getNode(target).orElse(null),
                                    "irreversible ids are read off the run graph's nodes"),
                            appliedRows.get(target)));
        }
        List<NodeId> holders =
                blockers.irreversible().stream()
                        .filter(id -> runGraph.getAllDependencies(id).contains(target))
                        .sorted(Comparator.comparing(NodeId::value))
                        .toList();
        return new DownBlocker.HeldTarget(target, holders);
    }

    /**
     * Returns every node the history says is currently applied.
     *
     * <p>This is the one place a full pass over the history is paid for, so a caller that needs
     * more than one answer from it should ask once and read the set repeatedly.
     */
    private Set<NodeId> appliedNodes(MigrationGraph runGraph) {
        return runGraph.allNodes().stream()
                .filter(node -> historyRepository.wasExecuted(node.id()))
                .map(MigrationNode::id)
                .collect(Collectors.toSet());
    }

    /**
     * Drops what no rollback may touch.
     *
     * <p>The frozen set holds whatever the irreversible nodes stand on, applied or not — a
     * dependency that was never applied still may not be removed, because removing it would break
     * the node that has to stay.
     */
    private static Set<NodeId> notFrozen(Set<NodeId> ids, RollbackBlockers blockers) {
        return ids.stream()
                .filter(id -> !blockers.frozen().contains(id))
                .collect(Collectors.toSet());
    }

    /**
     * Pairs each id with its node and the row that applied it, ordered by id so that a report reads
     * the same way twice.
     *
     * <p>Package-visible in the arity {@code rebuild} needs, because a refusal that lists ids alone
     * cannot say why any of them is stuck — and the one thing an operator must not be told is to
     * write a rollback for a migration that already declares one.
     */
    List<DownBlocker.FrozenMigration> frozenMigrationsOf(Set<NodeId> ids, MigrationGraph runGraph) {
        return frozenMigrationsOf(ids, runGraph, appliedRows());
    }

    private static List<DownBlocker.FrozenMigration> frozenMigrationsOf(
            Set<NodeId> ids, MigrationGraph runGraph, Map<NodeId, ExecutionRecord> appliedRows) {
        List<DownBlocker.FrozenMigration> frozen = new ArrayList<>();
        ids.stream()
                .sorted(Comparator.comparing(NodeId::value))
                .forEach(
                        id ->
                                runGraph.getNode(id)
                                        .ifPresent(
                                                node ->
                                                        frozen.add(
                                                                new DownBlocker.FrozenMigration(
                                                                        node,
                                                                        appliedRows.get(id)))));
        return List.copyOf(frozen);
    }

    /**
     * What no rollback may touch.
     *
     * @param irreversible applied nodes for which no rollback exists
     * @param frozen {@code irreversible} together with everything those nodes transitively depend
     *     on
     */
    record RollbackBlockers(Set<NodeId> irreversible, Set<NodeId> frozen) {}

    /**
     * What rolling back would do.
     *
     * @param blocker what refuses the run, or {@code null} when nothing does
     * @param selectedNodes the nodes a run would roll back; always empty when {@code blocker} is
     *     non-{@code null}
     * @param graph the graph the run has to execute against: the history's, built from the rows
     *     alone. Every node in it stands for a row — placed in the target that row names, standing
     *     on what that row recorded — so a migration the definitions no longer declare is a node
     *     like any other, and one they declare but never applied is simply not here
     */
    public record DownPlan(
            @Nullable DownBlocker blocker, Set<NodeId> selectedNodes, MigrationGraph graph) {}
}
