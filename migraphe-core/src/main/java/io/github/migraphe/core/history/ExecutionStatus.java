package io.github.migraphe.core.history;

/** マイグレーション実行ステータス。 */
public enum ExecutionStatus {
    SUCCESS, // 実行成功
    FAILURE, // 実行失敗
    SKIPPED // スキップ（既に実行済みなど）
}
