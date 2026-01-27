package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.execution.ExecutionListener;
import io.github.kakusuke.migraphe.api.execution.ExecutionPlanInfo;
import io.github.kakusuke.migraphe.api.execution.ExecutionSummary;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.api.task.SqlContentProvider;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import io.github.kakusuke.migraphe.core.graph.ExecutionLevel;
import io.github.kakusuke.migraphe.core.graph.ExecutionPlan;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.graph.TopologicalSort;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** DOWN マイグレーション（ロールバック）を実行するサービス。 */
public final class RollbackExecutor {

    private final MigrationGraph graph;
    private final HistoryRepository historyRepository;
    private final ExecutionListener listener;

    public RollbackExecutor(
            MigrationGraph graph, HistoryRepository historyRepository, ExecutionListener listener) {
        this.graph = graph;
        this.historyRepository = historyRepository;
        this.listener = listener;
    }

    /**
     * ロールバック対象ノードを決定する。
     *
     * @param targetVersion 特定のターゲットバージョン（null の場合は allMigrations の値に依存）
     * @param allMigrations true の場合は全実行済みノードが対象
     * @return ロールバック対象のノードIDセット
     */
    public Set<NodeId> determineRollbackTargets(
            @Nullable NodeId targetVersion, boolean allMigrations) {
        if (allMigrations) {
            // 全実行済みノードを対象
            return graph.allNodes().stream()
                    .filter(
                            node ->
                                    historyRepository.wasExecuted(
                                            node.id(), node.environment().id()))
                    .map(MigrationNode::id)
                    .collect(Collectors.toSet());
        }

        if (targetVersion != null) {
            // 特定ノードとその依存元（このノードに依存しているノード）を対象
            Set<NodeId> targets = new HashSet<>();
            targets.add(targetVersion);
            // 依存元（dependents）を取得
            targets.addAll(graph.getDependents(targetVersion));

            // 実行済みのもののみフィルタ
            return targets.stream()
                    .filter(
                            id -> {
                                MigrationNode node = graph.getNode(id).orElse(null);
                                if (node == null) return false;
                                return historyRepository.wasExecuted(id, node.environment().id());
                            })
                    .collect(Collectors.toSet());
        }

        // デフォルト: 空セット
        return Set.of();
    }

    /**
     * 実行プランを生成する。
     *
     * @param targetNodes ロールバック対象ノード
     * @param dryRun dry-run モードかどうか
     * @return 実行プラン情報
     */
    public ExecutionPlanInfo createPlan(Set<NodeId> targetNodes, boolean dryRun) {
        ExecutionPlan plan = TopologicalSort.createReverseExecutionPlanFor(graph, targetNodes);

        Set<NodeId> executedNodes = new HashSet<>();
        for (MigrationNode node : graph.allNodes()) {
            if (historyRepository.wasExecuted(node.id(), node.environment().id())) {
                executedNodes.add(node.id());
            }
        }

        List<List<MigrationNode>> levels = new ArrayList<>();
        for (ExecutionLevel level : plan.levels()) {
            levels.add(new ArrayList<>(level.nodes()));
        }

        ExecutionPlanInfo info =
                new ExecutionPlanInfo(levels, executedNodes, plan.totalNodes(), dryRun);
        listener.onPlanCreated(info);
        return info;
    }

    /**
     * ロールバックを実行する。
     *
     * @param targetNodes ロールバック対象ノード
     * @return 実行結果
     */
    public ExecutionResult execute(Set<NodeId> targetNodes) {
        // 逆順の実行プランを生成
        ExecutionPlan plan = TopologicalSort.createReverseExecutionPlanFor(graph, targetNodes);

        int totalNodes = plan.totalNodes();
        int executedCount = 0;
        int skippedCount = 0;

        for (ExecutionLevel level : plan.levels()) {
            for (MigrationNode node : level.nodes()) {
                // 未実行ならスキップ
                if (!historyRepository.wasExecuted(node.id(), node.environment().id())) {
                    listener.onNodeSkipped(node, ExecutionDirection.DOWN, "not executed");
                    skippedCount++;
                    continue;
                }

                // downTask が null の場合はスキップ
                Task downTask = node.downTask();
                if (downTask == null) {
                    listener.onNodeSkipped(node, ExecutionDirection.DOWN, "no down task");
                    skippedCount++;
                    continue;
                }

                // 実行開始を通知
                listener.onNodeStarted(node, ExecutionDirection.DOWN);

                // 実行
                long startTime = System.currentTimeMillis();
                Result<TaskResult, String> result = downTask.execute();
                long duration = System.currentTimeMillis() - startTime;

                if (result.isOk()) {
                    // 成功
                    listener.onNodeSucceeded(node, ExecutionDirection.DOWN, duration);

                    // ロールバック記録を保存
                    ExecutionRecord record =
                            ExecutionRecord.downSuccess(
                                    node.id(), node.environment().id(), node.name(), duration);
                    historyRepository.record(record);

                    executedCount++;
                } else {
                    // 失敗
                    String errorMsg = result.error();
                    String sqlContent = null;
                    if (downTask instanceof SqlContentProvider sqlProvider) {
                        sqlContent = sqlProvider.sqlContent();
                    }

                    listener.onNodeFailed(
                            node,
                            ExecutionDirection.DOWN,
                            sqlContent,
                            errorMsg != null ? errorMsg : "Unknown error");

                    // 失敗記録を保存
                    ExecutionRecord failureRecord =
                            ExecutionRecord.failure(
                                    node.id(),
                                    node.environment().id(),
                                    ExecutionDirection.DOWN,
                                    node.name(),
                                    errorMsg != null ? errorMsg : "Unknown error");
                    historyRepository.record(failureRecord);

                    // 失敗時はサマリーを作成して返す
                    ExecutionSummary summary =
                            ExecutionSummary.failure(
                                    ExecutionDirection.DOWN,
                                    totalNodes,
                                    executedCount,
                                    skippedCount);
                    listener.onCompleted(summary);
                    return ExecutionResult.failure(summary);
                }
            }
        }

        // 成功サマリーを作成
        ExecutionSummary summary =
                ExecutionSummary.success(
                        ExecutionDirection.DOWN, totalNodes, executedCount, skippedCount);
        listener.onCompleted(summary);
        return ExecutionResult.success(summary);
    }
}
