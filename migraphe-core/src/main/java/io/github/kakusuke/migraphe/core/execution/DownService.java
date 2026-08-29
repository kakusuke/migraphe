package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Decides what a {@code down} run would roll back, and what stops it.
 *
 * <p>This is the whole decision a {@code down} front end makes: every check that can refuse a run,
 * what a {@code --all} run has to leave behind, and the set of nodes it would roll back. The CLI
 * command and the Gradle task both call it and do nothing beyond rendering the answer, prompting,
 * and choosing an exit code — a guard added here therefore reaches both, which is what a guard
 * living in one of them did not.
 *
 * <p>It reads state from a {@link HistoryRepository} but performs no mutation; the run itself is
 * still {@link DagExecutor}'s.
 */
public final class DownService {

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
     * Works out what rolling back would do, without rolling anything back.
     *
     * <p>The checks run in a fixed order and the first one that refuses wins: a plan carrying a
     * {@linkplain DownPlan#blocker() blocker} carries no target nodes. What cannot be rolled back,
     * and what is currently applied, are each computed once and read by every question that depends
     * on them, so planning costs one pass over the history rather than one per question.
     *
     * @param targetVersion the node to roll back together with everything depending on it, or
     *     {@code null} to defer to {@code allMigrations}
     * @param allMigrations {@code true} to roll back every applied migration that can be rolled
     *     back
     * @return the plan
     */
    public DownPlan plan(@Nullable NodeId targetVersion, boolean allMigrations) {
        List<StatusService.OrphanStatus> orphans =
                new StatusService(graph, historyRepository).getStatus().orphans();
        if (!orphans.isEmpty()) {
            return new DownPlan(new DownBlocker.Orphans(orphans), List.of(), 0, Set.of());
        }

        RollbackBlockers blockers = rollbackBlockers();

        if (targetVersion != null && blockers.frozen().contains(targetVersion)) {
            return new DownPlan(blockerForTarget(targetVersion, blockers), List.of(), 0, Set.of());
        }

        if (!allMigrations) {
            return new DownPlan(
                    null, List.of(), 0, rollbackTargets(targetVersion, false, blockers));
        }

        // Asked once and read twice: what to roll back, and how much of what has to stay behind
        // is a migration anybody actually applied.
        Set<NodeId> applied = appliedNodes();
        int frozenAppliedCount = (int) applied.stream().filter(blockers.frozen()::contains).count();

        return new DownPlan(
                null,
                nodesOf(blockers.irreversible()),
                frozenAppliedCount,
                notFrozen(applied, blockers));
    }

    /**
     * Returns the nodes a DOWN run would roll back, ignoring anything that would refuse the run.
     *
     * <p>If {@code allMigrations} is {@code true}, every currently applied node is selected.
     * Otherwise, if {@code targetVersion} is supplied, the selection is that node plus all of its
     * transitive dependents, filtered to nodes that are currently applied. If neither applies, an
     * empty set is returned.
     *
     * <p>Either way the frozen nodes are then removed: a node with no down task, and everything it
     * stands on. Rolling those back is impossible, and rolling back only part of them is what used
     * to leave the history describing a node as applied while its dependencies were not.
     *
     * @param targetVersion the node to roll back (together with its dependents), or {@code null} to
     *     defer to {@code allMigrations}
     * @param allMigrations when {@code true}, selects all currently applied nodes regardless of
     *     {@code targetVersion}
     * @return the set of currently applied node IDs to roll back
     */
    public Set<NodeId> rollbackTargets(@Nullable NodeId targetVersion, boolean allMigrations) {
        return rollbackTargets(targetVersion, allMigrations, rollbackBlockers());
    }

    private Set<NodeId> rollbackTargets(
            @Nullable NodeId targetVersion, boolean allMigrations, RollbackBlockers blockers) {
        if (allMigrations) {
            return notFrozen(appliedNodes(), blockers);
        }

        if (targetVersion != null) {
            Set<NodeId> targets = new HashSet<>();
            targets.add(targetVersion);
            targets.addAll(graph.getAllDependents(targetVersion));

            return targets.stream()
                    .filter(
                            id -> {
                                MigrationNode node = graph.getNode(id).orElse(null);
                                if (node == null) {
                                    return false;
                                }
                                return historyRepository.wasExecuted(id, node.environment().id());
                            })
                    .filter(id -> !blockers.frozen().contains(id))
                    .collect(Collectors.toSet());
        }

        return Set.of();
    }

    /**
     * Returns the applied nodes that cannot be rolled back, together with everything they stand on.
     *
     * <p>A node with no down task cannot be removed, so neither can anything it depends on —
     * removing a dependency would break the node that has to stay. This propagates downwards only:
     * a node that depends on it can still be removed, because removing it leaves the frozen node
     * untouched. The remainder is therefore closed under dependents, which is what makes rolling it
     * back in reverse order safe.
     */
    private RollbackBlockers rollbackBlockers() {
        Set<NodeId> irreversible = new HashSet<>();
        Set<NodeId> frozen = new HashSet<>();
        for (MigrationNode node : graph.allNodes()) {
            if (node.downTask() != null
                    || !historyRepository.wasExecuted(node.id(), node.environment().id())) {
                continue;
            }
            irreversible.add(node.id());
            frozen.add(node.id());
            frozen.addAll(graph.getAllDependencies(node.id()));
        }
        return new RollbackBlockers(Set.copyOf(irreversible), Set.copyOf(frozen));
    }

    /** Names what refuses a rollback of {@code target}, which is known to be frozen. */
    private DownBlocker blockerForTarget(NodeId target, RollbackBlockers blockers) {
        if (blockers.irreversible().contains(target)) {
            return new DownBlocker.IrreversibleTarget(
                    Objects.requireNonNull(
                            graph.getNode(target).orElse(null),
                            "irreversible ids are read off graph nodes"));
        }
        List<NodeId> holders =
                blockers.irreversible().stream()
                        .filter(id -> graph.getAllDependencies(id).contains(target))
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
    private Set<NodeId> appliedNodes() {
        return graph.allNodes().stream()
                .filter(node -> historyRepository.wasExecuted(node.id(), node.environment().id()))
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

    /** Resolves ids to nodes, ordered by id so that a report reads the same way twice. */
    private List<MigrationNode> nodesOf(Set<NodeId> ids) {
        List<MigrationNode> nodes = new ArrayList<>();
        ids.stream()
                .sorted(Comparator.comparing(NodeId::value))
                .forEach(id -> graph.getNode(id).ifPresent(nodes::add));
        return List.copyOf(nodes);
    }

    /**
     * What no rollback may touch.
     *
     * @param irreversible applied nodes that have no down task
     * @param frozen {@code irreversible} together with everything those nodes transitively depend
     *     on
     */
    private record RollbackBlockers(Set<NodeId> irreversible, Set<NodeId> frozen) {}

    /**
     * What rolling back would do.
     *
     * @param blocker what refuses the run, or {@code null} when nothing does
     * @param irreversible the applied nodes with no down migration that a {@code --all} run has to
     *     leave behind, ordered by id; empty when nothing is left behind
     * @param frozenAppliedCount how many applied migrations a {@code --all} run has to leave behind
     *     in total — the {@code irreversible} nodes plus the applied ones they hold down
     * @param targetNodes the nodes a run would roll back; always empty when {@code blocker} is
     *     non-{@code null}
     */
    public record DownPlan(
            @Nullable DownBlocker blocker,
            List<MigrationNode> irreversible,
            int frozenAppliedCount,
            Set<NodeId> targetNodes) {

        /**
         * Reports whether the run has to leave applied migrations behind.
         *
         * @return {@code true} when something applied cannot be rolled back
         */
        public boolean leftFrozen() {
            return frozenAppliedCount > 0;
        }
    }
}
