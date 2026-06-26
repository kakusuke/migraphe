package io.github.kakusuke.migraphe.core.generator.tree;

import java.util.List;

/**
 * A serializable snapshot of a migration graph.
 *
 * <p>Produced by {@link MigrationTreeSourcePlugin} and consumed by output plugins (such as JSON
 * rendering), it is a plain data holder describing each node and its dependencies.
 *
 * @param nodes the node entries of the graph, typically sorted by id for stable output
 */
public record MigrationTreeData(List<NodeEntry> nodes) {

    /**
     * A single migration node together with its dependencies and execution status.
     *
     * @param id the node's unique identifier
     * @param name the node's human-readable name
     * @param target the identifier of the target environment the node runs against
     * @param status the execution status, {@code "executed"} or {@code "pending"}
     * @param dependencies the identifiers of the nodes this node directly depends on, typically
     *     sorted
     */
    public record NodeEntry(
            String id, String name, String target, String status, List<String> dependencies) {}
}
