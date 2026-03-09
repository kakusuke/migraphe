package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** レイアウト上の連続ノード列。forkNode はこのストリームの分岐元ノード（ルートストリームでは null）。 */
public record LayoutStream(
        @Nullable NodeId forkNode, List<MigrationNode> nodes, List<LayoutStream> childStreams) {

    public LayoutStream {
        nodes = List.copyOf(nodes);
        childStreams = List.copyOf(childStreams);
    }
}
