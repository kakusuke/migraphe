package io.github.kakusuke.migraphe.api.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import java.util.Set;

/**
 * 実行プランの情報。
 *
 * <p>Listener への通知用に、実行対象ノードの情報を保持する。
 */
public record ExecutionPlanInfo(
        List<List<MigrationNode>> levels,
        Set<NodeId> executedNodes,
        int totalNodes,
        boolean isDryRun) {

    /** 実行対象ノード数を返す。 */
    public int pendingNodes() {
        return totalNodes - executedNodes.size();
    }
}
