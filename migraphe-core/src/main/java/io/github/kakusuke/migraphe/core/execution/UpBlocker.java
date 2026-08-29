package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.Map;
import java.util.Set;

/**
 * What stops an {@code up} run before anything is applied.
 *
 * <p>A run is stopped by at most one of these: {@link UpService#plan} reports the first it finds
 * and does not look further, because the operator's next move is the same either way — go fix the
 * task files. The wording is built by {@link UpPlanFormatter} so that every front end refuses in
 * the same words.
 *
 * <p>Neither of these is fatal to <em>loading</em> a project: a graph in this state must still be
 * able to report its status. What stops is applying something.
 */
public sealed interface UpBlocker {

    /**
     * Tasks whose declared dependencies name nothing in the graph.
     *
     * <p>Applying them would build on ground nothing describes, which is exactly what deleting a
     * task file leaves behind.
     *
     * @param byNode each task with unresolved dependencies mapped to the ids it names, in the
     *     graph's own iteration order
     */
    record UnresolvedDependencies(Map<NodeId, Set<NodeId>> byNode) implements UpBlocker {}

    /**
     * Tasks that define neither a rollback nor a reason there is none.
     *
     * <p>Once a migration has run it is too late to decide, so the choice is demanded before the
     * first apply rather than after.
     *
     * @param nodes the offending task ids
     */
    record UndeclaredIrreversible(Set<NodeId> nodes) implements UpBlocker {}
}
