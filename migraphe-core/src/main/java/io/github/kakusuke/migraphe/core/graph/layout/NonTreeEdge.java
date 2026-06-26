package io.github.kakusuke.migraphe.core.graph.layout;

import io.github.kakusuke.migraphe.api.graph.NodeId;

/**
 * A DAG edge that is not part of the layout spanning tree.
 *
 * <p>When {@link LayoutTree} decomposes a {@link
 * io.github.kakusuke.migraphe.core.graph.MigrationGraph} into a tree of {@link LayoutStream
 * streams}, each node is attached to exactly one parent (its trunk extension or fork origin). Every
 * remaining parent edge that could not be represented as a tree edge is captured as a {@code
 * NonTreeEdge} and later rendered by {@link GridCanvas#addNonTreeEdge(NodeId, NodeId)} as a routed
 * merge connector on the grid.
 *
 * @param source the {@link NodeId} of the upstream (dependency) node the edge originates from
 * @param target the {@link NodeId} of the downstream (dependent) node the edge points to
 */
public record NonTreeEdge(NodeId source, NodeId target) {}
