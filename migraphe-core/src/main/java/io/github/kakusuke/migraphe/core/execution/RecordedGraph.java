package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The graph the <em>history</em> describes, as opposed to the one the definitions do.
 *
 * <p>A rebuild is the difference between two DAGs, not a cascade through the current declarations.
 * Both sides are a {@link MigrationGraph}, so every traversal the tool already has — dependents,
 * closures, reverse topological order — works on either without knowing which it holds.
 *
 * <p>Only what the history says is <strong>applied</strong> is in it. A migration that was rolled
 * back is not part of the state the database is in, and a row that only ever failed never put
 * anything there.
 *
 * <p>Edges are recorded <strong>as the row states them</strong>, without checking that their
 * targets are also in the graph. That is the point of building this side at all: the history can
 * name a dependency the definitions have deleted, and dropping the edge would hide exactly the case
 * the difference exists to find.
 *
 * @param graph the applied migrations, carrying the fingerprints, dependencies and rollback
 *     payloads their rows recorded
 * @param rowsWithNoTarget applied rows whose {@code target_id} matches no configured target. They
 *     are not in the graph — there is no connection to reconstruct, so they cannot be rolled back —
 *     but the objects they describe are real and the history says so, which is why they are handed
 *     back rather than dropped
 */
record RecordedGraph(MigrationGraph graph, List<ExecutionRecord> rowsWithNoTarget) {

    /**
     * Reads the history and builds the graph of what it says is applied.
     *
     * <p><strong>The rows drive this, not the targets.</strong> Asking the history what it holds,
     * rather than asking it about targets already known from the definitions, is what makes an
     * orphan visible at all: deleting the last task that names a target must not hide that target's
     * rows. The configured targets are a lookup table for the {@code target_id} each row carries,
     * never a list of places to go looking.
     *
     * <p>The rows are the repository's {@link HistoryRepository#latestApplies}, never a fold of
     * this class's own. The latest record of <em>any</em> kind would be the wrong row: a failed
     * rollback is the newest thing a node has, and it carries neither the fingerprint nor the
     * dependencies nor the rollback payload, all of which are written on an apply alone.
     *
     * @param historyRepository the repository consulted for what was applied
     * @param configuredTargets every target the project configures, used to resolve the {@code
     *     target_id} a row names
     * @return the applied graph, together with the rows whose target no longer resolves
     * @throws IllegalStateException if the recorded edges form a cycle, which the reduction the
     *     comparison applies is not defined on
     */
    static RecordedGraph of(
            HistoryRepository historyRepository, Collection<Target> configuredTargets) {
        Map<TargetId, Target> targetsById = new HashMap<>();
        for (Target configured : configuredTargets) {
            targetsById.put(configured.id(), configured);
        }

        MigrationGraph graph = MigrationGraph.create();
        Map<NodeId, ExecutionRecord> rowOf = new HashMap<>();
        List<ExecutionRecord> rowsWithNoTarget = new ArrayList<>();
        Set<NodeId> appliedNow = Set.copyOf(historyRepository.executedNodes());
        for (ExecutionRecord apply : historyRepository.latestApplies()) {
            if (!appliedNow.contains(apply.nodeId())) {
                continue;
            }
            Target target = targetsById.get(apply.targetId());
            if (target == null) {
                rowsWithNoTarget.add(apply);
                continue;
            }
            rowOf.put(apply.nodeId(), apply);
            graph.addNode(RecordedNode.of(apply, target));
        }
        List<NodeId> onCycles = graph.nodesOnCycles();
        if (!onCycles.isEmpty()) {
            throw recordedEdgesFormACycle(onCycles, rowOf);
        }
        return new RecordedGraph(graph, List.copyOf(rowsWithNoTarget));
    }

    /**
     * Refuses to answer about a history whose recorded edges form a cycle.
     *
     * <p>It is refused rather than worked around because the transitive reduction the comparison
     * applies is defined only on a DAG: two parents on a common cycle each justify dropping the
     * other, so a node above the cycle would silently lose every edge and read as a root.
     *
     * <p>The message names the rows and stops. It does not say how they came to disagree, because
     * migraphe can write this itself: {@code amend} records the dependencies the definition
     * declares <em>now</em>, so amending one node after reversing an edge in the task files leaves
     * its row naming a migration whose row still names it back.
     */
    private static IllegalStateException recordedEdgesFormACycle(
            List<NodeId> onCycles, Map<NodeId, ExecutionRecord> rowOf) {
        return new IllegalStateException(
                "The dependencies recorded for these migrations place each of them on a cycle, so"
                        + " the history cannot be read as a graph — no order satisfies it:\n  "
                        + onCycles.stream()
                                .map(id -> describe(id, rowOf.get(id)))
                                .collect(Collectors.joining("\n  "))
                        + "\nmigraphe cannot choose which of these edges to disregard.");
    }

    /**
     * One migration on the cycle, in the terms an operator can look its row up by.
     *
     * <p>A node on a cycle came from a row and declares at least one dependency, so neither absence
     * below is reachable. They are stated rather than defaulted: a refusal that quietly dropped the
     * evidence it exists to carry would be worse than one that failed loudly.
     */
    private static String describe(NodeId nodeId, @Nullable ExecutionRecord row) {
        Objects.requireNonNull(row, () -> nodeId.value() + " is on a cycle but came from no row");
        List<NodeId> dependencies =
                Objects.requireNonNull(
                        row.dependencies(),
                        () -> nodeId.value() + " is on a cycle but recorded no dependencies");
        return nodeId.value()
                + "  row="
                + row.id()
                + "  target="
                + row.targetId().value()
                + "  stands on "
                + dependencies.stream().map(NodeId::value).collect(Collectors.joining(", "));
    }
}
