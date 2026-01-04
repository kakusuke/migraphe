package io.github.migraphe.api.task;

import io.github.migraphe.api.common.Result;

/**
 * マイグレーションタスクのインターフェース。 プラグインがこのインターフェースを実装して、具体的な実行ロジックを定義する。
 *
 * <p>トランザクション管理は実装側で行う（BEGIN/COMMIT/ROLLBACK）。
 */
public interface Task {

    /**
     * タスクを実行する。 トランザクション管理（BEGIN/COMMIT/ROLLBACK）は実装側で行う。
     *
     * @return 実行結果（成功時はシリアライズされたDownTaskを含む）
     */
    Result<TaskResult, String> execute();

    /** タスクの説明 */
    String description();
}
