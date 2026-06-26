package io.github.kakusuke.migraphe.api.graph;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * A read-only view over a migration graph.
 *
 * <p>This interface exposes the structure of a directed acyclic graph (DAG) of {@link MigrationNode
 * nodes} without permitting mutation. It is the abstraction handed to generators and other
 * consumers that need to inspect node relationships (for example, to render the graph or extract
 * the migration tree) but must not alter it.
 *
 * @see MigrationNode
 * @see NodeId
 */
public interface MigrationGraphView {

    /**
     * Returns all nodes contained in the graph.
     *
     * @return a collection of every {@link MigrationNode} in the graph
     */
    Collection<MigrationNode> allNodes();

    /**
     * Returns the identifiers of the nodes that the given node directly depends on (its
     * predecessors).
     *
     * @param nodeId the identifier of the node whose dependencies are requested
     * @return the set of direct dependency identifiers, possibly empty
     */
    Set<NodeId> getDependencies(NodeId nodeId);

    /**
     * Returns the identifiers of the nodes that directly depend on the given node (its successors).
     *
     * @param nodeId the identifier of the node whose dependents are requested
     * @return the set of direct dependent identifiers, possibly empty
     */
    Set<NodeId> getDependents(NodeId nodeId);

    /**
     * Looks up a node by its identifier.
     *
     * @param nodeId the identifier of the node to retrieve
     * @return an {@link Optional} containing the matching {@link MigrationNode}, or {@link
     *     Optional#empty()} if no node with that identifier exists
     */
    Optional<MigrationNode> getNode(NodeId nodeId);

    /**
     * Returns the root nodes of the graph (those with no dependencies).
     *
     * @return the set of root {@link MigrationNode nodes}, possibly empty
     */
    Set<MigrationNode> getRoots();

    /**
     * Returns the number of nodes in the graph.
     *
     * @return the total node count
     */
    int size();
}
