package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Decides what an {@code up} run would apply, and what stops it.
 *
 * <p>This is the whole decision an {@code up} front end makes: every check that can refuse a run,
 * and the set of nodes a run would execute. The CLI command and the Gradle task both call it and do
 * nothing beyond rendering the answer, prompting, and choosing an exit code — a guard added here
 * therefore reaches both, which is what a guard living in one of them did not.
 *
 * <p>It reads state from a {@link HistoryRepository} but performs no mutation; the run itself is
 * still {@link DagExecutor}'s.
 */
public final class UpService {

    private final MigrationGraph graph;
    private final HistoryRepository historyRepository;

    /**
     * Creates an up service over a graph and its history.
     *
     * @param graph the migration graph whose nodes are inspected
     * @param historyRepository the repository consulted for what has already been applied
     */
    public UpService(MigrationGraph graph, HistoryRepository historyRepository) {
        this.graph = graph;
        this.historyRepository = historyRepository;
    }

    /**
     * Works out what applying would do, without applying anything.
     *
     * <p>The checks run in a fixed order and the first one that refuses wins: a plan carrying a
     * {@linkplain UpPlan#blocker() blocker} carries no target nodes, because a graph that cannot be
     * applied has no meaningful set to offer.
     *
     * @param targetId the highest node to migrate up to and including, or {@code null} to consider
     *     every node
     * @return the plan
     */
    public UpPlan plan(@Nullable NodeId targetId) {
        Map<NodeId, Set<NodeId>> unresolved = graph.unresolvedDependencies();
        if (!unresolved.isEmpty()) {
            return new UpPlan(new UpBlocker.UnresolvedDependencies(unresolved), Set.of());
        }

        Set<NodeId> undeclared = graph.undeclaredIrreversibleNodes();
        if (!undeclared.isEmpty()) {
            return new UpPlan(new UpBlocker.UndeclaredIrreversible(undeclared), Set.of());
        }

        return new UpPlan(null, targetNodes(targetId));
    }

    /**
     * Returns the nodes an UP run would execute, ignoring anything that would refuse the run.
     *
     * <p>When {@code targetId} is supplied, the candidate set is that node plus all of its
     * transitive dependencies; otherwise every node in the graph is a candidate. The candidates are
     * then filtered to those not yet successfully applied, so already-applied nodes are excluded.
     *
     * @param targetId a specific target node to migrate up to, or {@code null} to consider all
     *     nodes
     * @return the set of not-yet-executed node IDs to run
     */
    public Set<NodeId> targetNodes(@Nullable NodeId targetId) {
        Set<NodeId> candidates;

        if (targetId != null) {
            candidates = new HashSet<>(graph.getAllDependencies(targetId));
            candidates.add(targetId);
        } else {
            candidates =
                    graph.allNodes().stream().map(MigrationNode::id).collect(Collectors.toSet());
        }

        return candidates.stream()
                .filter(
                        id -> {
                            MigrationNode node = graph.getNode(id).orElse(null);
                            if (node == null) {
                                return false;
                            }
                            return !historyRepository.wasExecuted(id, node.environment().id());
                        })
                .collect(Collectors.toSet());
    }

    /**
     * What applying would do.
     *
     * @param blocker what refuses the run, or {@code null} when nothing does
     * @param targetNodes the nodes a run would execute; always empty when {@code blocker} is
     *     non-{@code null}
     */
    public record UpPlan(@Nullable UpBlocker blocker, Set<NodeId> targetNodes) {}
}
