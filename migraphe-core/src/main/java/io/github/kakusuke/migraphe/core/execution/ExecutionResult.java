package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.execution.ExecutionSummary;

/** マイグレーション実行結果。 */
public record ExecutionResult(ExecutionSummary summary, boolean success) {

    /** 成功結果を作成する。 */
    public static ExecutionResult success(ExecutionSummary summary) {
        return new ExecutionResult(summary, true);
    }

    /** 失敗結果を作成する。 */
    public static ExecutionResult failure(ExecutionSummary summary) {
        return new ExecutionResult(summary, false);
    }
}
