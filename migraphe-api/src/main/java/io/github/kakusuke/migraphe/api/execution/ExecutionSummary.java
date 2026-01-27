package io.github.kakusuke.migraphe.api.execution;

import io.github.kakusuke.migraphe.api.task.ExecutionDirection;

/** 実行結果のサマリー。 */
public record ExecutionSummary(
        ExecutionDirection direction,
        int totalNodes,
        int executedCount,
        int skippedCount,
        int failedCount,
        boolean success) {

    /** 成功サマリーを作成する。 */
    public static ExecutionSummary success(
            ExecutionDirection direction, int total, int executed, int skipped) {
        return new ExecutionSummary(direction, total, executed, skipped, 0, true);
    }

    /** 失敗サマリーを作成する。 */
    public static ExecutionSummary failure(
            ExecutionDirection direction, int total, int executed, int skipped) {
        return new ExecutionSummary(direction, total, executed, skipped, 1, false);
    }
}
