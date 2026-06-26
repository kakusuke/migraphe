package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import java.util.List;

/**
 * A single level within an {@link ExecutionPlan}.
 *
 * <p>All nodes in one level are mutually independent — none depends on another within the same
 * level — so they may be executed in parallel. Levels carry an ordinal ({@code levelNumber}) that
 * fixes their position relative to other levels, which must be processed in ascending order.
 *
 * @param levelNumber the zero-based position of this level within the plan; lower numbers run first
 * @param nodes the nodes belonging to this level, all safely executable in parallel; defensively
 *     copied to an immutable list
 */
public record ExecutionLevel(int levelNumber, List<MigrationNode> nodes) {

    /**
     * Canonical constructor that defensively copies {@code nodes} into an immutable list.
     *
     * @param levelNumber the zero-based position of this level within the plan
     * @param nodes the nodes belonging to this level
     */
    public ExecutionLevel {
        nodes = List.copyOf(nodes);
    }

    /**
     * Returns the number of nodes in this level.
     *
     * @return the count of nodes, i.e. the parallelism width of this level
     */
    public int size() {
        return nodes.size();
    }
}
