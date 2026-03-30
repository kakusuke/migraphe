package io.github.kakusuke.migraphe.api.graph;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface MigrationGraphView {
    Collection<MigrationNode> allNodes();

    Set<NodeId> getDependencies(NodeId nodeId);

    Set<NodeId> getDependents(NodeId nodeId);

    Optional<MigrationNode> getNode(NodeId nodeId);

    Set<MigrationNode> getRoots();

    int size();
}
