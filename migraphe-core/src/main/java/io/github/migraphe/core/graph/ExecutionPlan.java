package io.github.migraphe.core.graph;

import java.util.List;

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
}
