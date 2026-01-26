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

/** UP マイグレーションを実行するサービス。 */
public final class MigrationExecutor {

    private final MigrationGraph graph;
    private final HistoryRepository historyRepository;
    private final ExecutionListener listener;

    public MigrationExecutor(
            MigrationGraph graph, HistoryRepository historyRepository, ExecutionListener listener) {
        this.graph = graph;
        this.historyRepository = historyRepository;
        this.listener = listener;
    }

    /**
     * 実行対象ノードを決定する。
     *
     * @param targetId 特定のターゲットID（null の場合は全ノード）
     * @return 未実行のノードIDセット
     */
    public Set<NodeId> determineTargetNodes(@Nullable NodeId targetId) {
        Set<NodeId> candidates;

        if (targetId != null) {
            // ID指定: ターゲット + ターゲットの全依存先
            candidates = new HashSet<>(graph.getAllDependencies(targetId));
            candidates.add(targetId);
        } else {
            // 全体: グラフの全ノード
            candidates =
                    graph.allNodes().stream().map(MigrationNode::id).collect(Collectors.toSet());
        }

        // 未実行のノードのみフィルタ
        return candidates.stream()
                .filter(
                        id -> {
                            MigrationNode node = graph.getNode(id).orElse(null);
                            if (node == null) return false;
                            return !historyRepository.wasExecuted(id, node.environment().id());
                        })
                .collect(Collectors.toSet());
    }

    /**
     * 実行プランを生成する。
     *
     * @param targetNodes 実行対象ノード
     * @param dryRun dry-run モードかどうか
     * @return 実行プラン情報
     */
    public ExecutionPlanInfo createPlan(Set<NodeId> targetNodes, boolean dryRun) {
        ExecutionPlan plan = TopologicalSort.createExecutionPlanFor(graph, targetNodes);

        // 実行済みノードを取得
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
     * マイグレーションを実行する。
     *
     * @param targetNodes 実行対象ノード
     * @return 実行結果
     */
    public ExecutionResult execute(Set<NodeId> targetNodes) {
        ExecutionPlan plan = TopologicalSort.createExecutionPlanFor(graph, targetNodes);

        int totalNodes = plan.totalNodes();
        int executedCount = 0;
        int skippedCount = 0;

        for (ExecutionLevel level : plan.levels()) {
            for (MigrationNode node : level.nodes()) {
                // 既に実行済みかチェック
                if (historyRepository.wasExecuted(node.id(), node.environment().id())) {
                    listener.onNodeSkipped(node, ExecutionDirection.UP, "already executed");
                    skippedCount++;
                    continue;
                }

                // 実行開始を通知
                listener.onNodeStarted(node, ExecutionDirection.UP);

                // 実行
                long startTime = System.currentTimeMillis();
                Result<TaskResult, String> result = node.upTask().execute();
                long duration = System.currentTimeMillis() - startTime;

                if (result.isOk()) {
                    // 成功
                    listener.onNodeSucceeded(node, ExecutionDirection.UP, duration);

                    // 実行記録を保存
                    TaskResult taskResult = result.value();
                    String serializedDownTask =
                            taskResult != null ? taskResult.serializedDownTask() : null;

                    ExecutionRecord record =
                            ExecutionRecord.upSuccess(
                                    node.id(),
                                    node.environment().id(),
                                    node.name(),
                                    serializedDownTask,
                                    duration);
                    historyRepository.record(record);

                    executedCount++;
                } else {
                    // 失敗
                    String errorMsg = result.error();
                    String sqlContent = null;
                    Task upTask = node.upTask();
                    if (upTask instanceof SqlContentProvider sqlProvider) {
                        sqlContent = sqlProvider.sqlContent();
                    }

                    listener.onNodeFailed(
                            node,
                            ExecutionDirection.UP,
                            sqlContent,
                            errorMsg != null ? errorMsg : "Unknown error");

                    // 失敗記録を保存
                    ExecutionRecord failureRecord =
                            ExecutionRecord.failure(
                                    node.id(),
                                    node.environment().id(),
                                    ExecutionDirection.UP,
                                    node.name(),
                                    errorMsg != null ? errorMsg : "Unknown error");
                    historyRepository.record(failureRecord);

                    // 失敗時はサマリーを作成して返す
                    ExecutionSummary summary =
                            ExecutionSummary.failure(
                                    ExecutionDirection.UP, totalNodes, executedCount, skippedCount);
                    listener.onCompleted(summary);
                    return ExecutionResult.failure(summary);
                }
            }
        }

        // 成功サマリーを作成
        ExecutionSummary summary =
                ExecutionSummary.success(
                        ExecutionDirection.UP, totalNodes, executedCount, skippedCount);
        listener.onCompleted(summary);
        return ExecutionResult.success(summary);
    }
}
