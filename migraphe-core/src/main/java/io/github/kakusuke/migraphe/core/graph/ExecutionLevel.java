package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import java.util.Set;

/** 実行レベル - 同じレベルのノードは並列実行可能 */
public record ExecutionLevel(int levelNumber, Set<MigrationNode> nodes) {

    public ExecutionLevel {
        nodes = Set.copyOf(nodes);
    }

    public int size() {
        return nodes.size();
    }
}
