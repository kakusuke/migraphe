package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.execution.ExecutionListener;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** 仮想スレッドを使用した並列 UP マイグレーション実行サービス。 */
public final class ParallelMigrationExecutor implements Executor {

    private final MigrationGraph graph;
    private final HistoryRepository historyRepository;
    private final ExecutionListener listener;
    private final int maxParallelism;

    public ParallelMigrationExecutor(
            MigrationGraph graph, HistoryRepository historyRepository, ExecutionListener listener) {
        this(graph, historyRepository, listener, 0);
    }

    /**
     * 並列度制限付きコンストラクタ。
     *
     * @param graph マイグレーショングラフ
     * @param historyRepository 履歴リポジトリ
     * @param listener 実行リスナー
     * @param maxParallelism 最大並列数（0 = 無制限）
     */
    public ParallelMigrationExecutor(
            MigrationGraph graph,
            HistoryRepository historyRepository,
            ExecutionListener listener,
            int maxParallelism) {
        this.graph = graph;
        this.historyRepository = historyRepository;
        this.listener = listener;
        this.maxParallelism = maxParallelism;
    }

    /**
     * 実行対象ノードを決定する。
     *
     * @param targetId 特定のターゲットID（null の場合は全ノード）
     * @return 未実行のノードIDセット
     */
    @Override
    public Set<NodeId> determineTargetNodes(@Nullable NodeId targetId) {
        Set<NodeId> candidates;

        if (targetId != null) {
            candidates = new HashSet<>(graph.getAllDependencies(targetId));
            candidates.add(targetId);
        } else {
            candidates =
                    graph.allNodes().stream().map(MigrationNode::id).collect(Collectors.toSet());
        }

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
     * マイグレーションを並列実行する。
     *
     * @param targetNodes 実行対象ノード
     * @return 実行結果
     */
    @Override
    public ExecutionResult execute(Set<NodeId> targetNodes) {
        if (targetNodes.isEmpty()) {
            ExecutionSummary summary = ExecutionSummary.success(ExecutionDirection.UP, 0, 0, 0);
            listener.onCompleted(summary);
            return ExecutionResult.success(summary);
        }

        ExecutionPlan plan = TopologicalSort.createExecutionPlanFor(graph, targetNodes);
        int totalNodes = plan.totalNodes();

        // トポロジカル順序でのポジションマップを構築
        Map<NodeId, Integer> positionMap = new HashMap<>();
        int pos = 0;
        for (ExecutionLevel level : plan.levels()) {
            for (MigrationNode node : level.nodes()) {
                positionMap.put(node.id(), pos++);
            }
        }

        Comparator<MigrationNode> orderComparator =
                Comparator.comparingInt(n -> positionMap.getOrDefault(n.id(), Integer.MAX_VALUE));

        ReadyNodeTracker tracker = new ReadyNodeTracker(graph, targetNodes);
        PriorityBlockingQueue<MigrationNode> readyQueue =
                new PriorityBlockingQueue<>(Math.max(1, targetNodes.size()), orderComparator);

        // 初期の実行可能ノードをキューに追加
        for (NodeId readyId : tracker.initialReadyNodes()) {
            graph.getNode(readyId).ifPresent(readyQueue::put);
        }

        CountDownLatch latch = new CountDownLatch(totalNodes);
        AtomicBoolean failureDetected = new AtomicBoolean(false);
        AtomicInteger executedCount = new AtomicInteger(0);
        int skippedCount = 0;
        @Nullable Semaphore semaphore = maxParallelism > 0 ? new Semaphore(maxParallelism) : null;

        try {
            for (int i = 0; i < totalNodes; i++) {
                MigrationNode node = readyQueue.take();

                if (failureDetected.get()) {
                    latch.countDown();
                    continue;
                }

                // 実行済みチェック
                if (historyRepository.wasExecuted(node.id(), node.environment().id())) {
                    listener.onNodeSkipped(node, ExecutionDirection.UP, "already executed");
                    skippedCount++;
                    processCompletion(node.id(), tracker, readyQueue);
                    latch.countDown();
                    continue;
                }

                // セマフォで並列度を制限（セマフォが設定されている場合）
                if (semaphore != null) {
                    semaphore.acquire();
                }

                // 仮想スレッドで実行
                @Nullable Semaphore sem = semaphore;
                Thread.startVirtualThread(
                        () -> {
                            try {
                                executeNode(
                                        node, failureDetected, executedCount, tracker, readyQueue);
                            } finally {
                                if (sem != null) {
                                    sem.release();
                                }
                                latch.countDown();
                            }
                        });
            }

            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ExecutionSummary summary =
                    ExecutionSummary.failure(
                            ExecutionDirection.UP, totalNodes, executedCount.get(), skippedCount);
            listener.onCompleted(summary);
            return ExecutionResult.failure(summary);
        }

        if (failureDetected.get()) {
            ExecutionSummary summary =
                    ExecutionSummary.failure(
                            ExecutionDirection.UP, totalNodes, executedCount.get(), skippedCount);
            listener.onCompleted(summary);
            return ExecutionResult.failure(summary);
        }

        ExecutionSummary summary =
                ExecutionSummary.success(
                        ExecutionDirection.UP, totalNodes, executedCount.get(), skippedCount);
        listener.onCompleted(summary);
        return ExecutionResult.success(summary);
    }

    private void executeNode(
            MigrationNode node,
            AtomicBoolean failureDetected,
            AtomicInteger executedCount,
            ReadyNodeTracker tracker,
            PriorityBlockingQueue<MigrationNode> readyQueue) {

        listener.onNodeStarted(node, ExecutionDirection.UP);

        long startTime = System.currentTimeMillis();
        Result<TaskResult, String> result = node.upTask().execute();
        long duration = System.currentTimeMillis() - startTime;

        if (result.isOk()) {
            listener.onNodeSucceeded(node, ExecutionDirection.UP, duration);

            TaskResult taskResult = result.value();
            String serializedDownTask = taskResult != null ? taskResult.serializedDownTask() : null;

            ExecutionRecord record =
                    ExecutionRecord.upSuccess(
                            node.id(),
                            node.environment().id(),
                            node.name(),
                            serializedDownTask,
                            duration);
            historyRepository.record(record);

            executedCount.incrementAndGet();
        } else {
            String errorMsg = result.error();
            String message = errorMsg != null ? errorMsg : "Unknown error";
            String sqlContent = null;
            Task upTask = node.upTask();
            if (upTask instanceof SqlContentProvider sqlProvider) {
                sqlContent = sqlProvider.sqlContent();
            }

            listener.onNodeFailed(node, ExecutionDirection.UP, sqlContent, message);

            ExecutionRecord failureRecord =
                    ExecutionRecord.failure(
                            node.id(),
                            node.environment().id(),
                            ExecutionDirection.UP,
                            node.name(),
                            message);
            historyRepository.record(failureRecord);

            failureDetected.set(true);
        }

        processCompletion(node.id(), tracker, readyQueue);
    }

    private void processCompletion(
            NodeId nodeId,
            ReadyNodeTracker tracker,
            PriorityBlockingQueue<MigrationNode> readyQueue) {
        Set<NodeId> newlyReady = tracker.markCompleted(nodeId);
        for (NodeId readyId : newlyReady) {
            graph.getNode(readyId).ifPresent(readyQueue::put);
        }
    }
}
