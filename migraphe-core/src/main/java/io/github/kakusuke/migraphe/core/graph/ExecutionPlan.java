package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 並列実行のための実行プラン。 レベルごとにノードをグループ化し、各レベルは並列実行可能。 */
public record ExecutionPlan(List<ExecutionLevel> levels) {

    public ExecutionPlan {
        levels = List.copyOf(levels);
    }

    /** プラン内の全マイグレーション数 */
    public int totalNodes() {
        return levels.stream().mapToInt(ExecutionLevel::size).sum();
    }

    /** 最大並列度（最も大きいレベルのサイズ） */
    public int maxParallelism() {
        return levels.stream().mapToInt(ExecutionLevel::size).max().orElse(0);
    }

    /** レベル数 */
    public int levelCount() {
        return levels.size();
    }

    /**
     * プラン内のノードを、指定されたリストの順序でフィルタして返す。
     *
     * <p>プランに含まれるノードのみを、allNodes の順序を維持して返す。
     *
     * @param allNodes フィルタ元のノードリスト（DFS 順など）
     * @return プランに含まれるノードのみを allNodes の順序で返したリスト
     */
    public List<MigrationNode> filterNodesInOrder(List<MigrationNode> allNodes) {
        Set<NodeId> planNodeIds = new HashSet<>();
        for (ExecutionLevel level : levels) {
            for (MigrationNode n : level.nodes()) {
                planNodeIds.add(n.id());
            }
        }
        List<MigrationNode> filtered = new ArrayList<>();
        for (MigrationNode node : allNodes) {
            if (planNodeIds.contains(node.id())) {
                filtered.add(node);
            }
        }
        return filtered;
    }
}
