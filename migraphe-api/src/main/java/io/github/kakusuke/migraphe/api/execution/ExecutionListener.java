package io.github.kakusuke.migraphe.api.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import org.jspecify.annotations.Nullable;

/**
 * マイグレーション実行の進捗を通知するリスナー。
 *
 * <p>CLI や Gradle プラグインがこのインターフェースを実装して出力をカスタマイズする。
 */
public interface ExecutionListener {

    /** 実行プラン確定時に呼ばれる。 */
    void onPlanCreated(ExecutionPlanInfo plan);

    /** ノード実行開始時に呼ばれる。 */
    void onNodeStarted(MigrationNode node, ExecutionDirection direction);

    /** ノード実行成功時に呼ばれる。 */
    void onNodeSucceeded(MigrationNode node, ExecutionDirection direction, long durationMs);

    /** ノードスキップ時に呼ばれる。 */
    void onNodeSkipped(MigrationNode node, ExecutionDirection direction, String reason);

    /** ノード実行失敗時に呼ばれる。 */
    void onNodeFailed(
            MigrationNode node,
            ExecutionDirection direction,
            @Nullable String sqlContent,
            String errorMessage);

    /** 全体完了時に呼ばれる。 */
    void onCompleted(ExecutionSummary summary);
}
