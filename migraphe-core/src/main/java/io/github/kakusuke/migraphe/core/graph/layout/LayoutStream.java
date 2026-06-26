package io.github.kakusuke.migraphe.core.graph.layout;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A contiguous run of nodes ("trunk") in the layout tree, together with the streams that fork off
 * from it.
 *
 * <p>A {@code LayoutStream} is the immutable unit produced by {@link LayoutTree}: its {@link
 * #nodes} form a single vertical column segment, and each child in {@link #childStreams} branches
 * to the right at the trunk node identified by that child's own {@link #forkNode}. The {@link
 * GridCanvas} walks this structure to place {@link Cell} values on the grid.
 *
 * @param forkNode the trunk node of the parent stream from which this stream forks, or {@code null}
 *     for a root stream (one attached directly to the virtual root)
 * @param nodes the ordered, immutable list of nodes that make up this stream's trunk
 * @param childStreams the ordered, immutable list of streams that fork off this trunk
 */
public record LayoutStream(
        @Nullable NodeId forkNode, List<MigrationNode> nodes, List<LayoutStream> childStreams) {

    /**
     * Canonical constructor that defensively copies the node and child-stream lists so the record
     * stays immutable.
     */
    public LayoutStream {
        nodes = List.copyOf(nodes);
        childStreams = List.copyOf(childStreams);
    }
}
