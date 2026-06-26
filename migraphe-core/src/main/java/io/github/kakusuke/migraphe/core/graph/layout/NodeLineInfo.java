package io.github.kakusuke.migraphe.core.graph.layout;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;

/**
 * Per-node placement information produced while rendering the layout grid.
 *
 * <p>Each {@code NodeLineInfo} pairs a {@link MigrationNode} with the grid column at which its
 * {@link Cell.Node} cell was placed, allowing callers to know the horizontal depth (indentation
 * lane) of a node in the rendered ASCII tree. Instances are emitted by {@link
 * GridCanvas#toNodeLineInfos()} and post-processed by {@link ExecutionGraphView#lines()} (which
 * drops the virtual root and shifts columns left by one).
 *
 * @param node the migration node this line describes
 * @param column the zero-based grid column at which the node cell is placed
 */
public record NodeLineInfo(MigrationNode node, int column) {}
