package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import java.util.List;

/** 実行レベル - 同じレベルのノードは並列実行可能 */
public record ExecutionLevel(int levelNumber, List<MigrationNode> nodes) {

    public ExecutionLevel {
        nodes = List.copyOf(nodes);
    }

    public int size() {
        return nodes.size();
    }
}
