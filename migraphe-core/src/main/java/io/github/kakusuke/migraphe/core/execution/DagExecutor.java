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
import io.github.kakusuke.migraphe.core.history.SynchronizedHistoryRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** DAG ベース migration executor (UP/DOWN, sequential/parallel 統合)。 */
public final class DagExecutor implements Executor {

    private final MigrationGraph graph;
    private final HistoryRepository history;
    private final ExecutionListener listener;
    private final ExecutionDirection direction;
    private final int maxParallelism;

    public DagExecutor(
            MigrationGraph graph,
            HistoryRepository history,
            ExecutionListener listener,
            ExecutionDirection direction,
            int maxParallelism) {
        this.graph = graph;
        this.history =
                history instanceof SynchronizedHistoryRepository
                        ? history
                        : new SynchronizedHistoryRepository(history);
        this.listener =
                listener instanceof SynchronizedExecutionListener
                        ? listener
                        : new SynchronizedExecutionListener(listener);
        this.direction = direction;
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
                            return !history.wasExecuted(id, node.environment().id());
                        })
                .collect(Collectors.toSet());
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
            return graph.allNodes().stream()
                    .filter(node -> history.wasExecuted(node.id(), node.environment().id()))
                    .map(MigrationNode::id)
                    .collect(Collectors.toSet());
        }

        if (targetVersion != null) {
            Set<NodeId> targets = new HashSet<>();
            targets.add(targetVersion);
            targets.addAll(graph.getAllDependents(targetVersion));

            return targets.stream()
                    .filter(
                            id -> {
                                MigrationNode node = graph.getNode(id).orElse(null);
                                if (node == null) return false;
                                return history.wasExecuted(id, node.environment().id());
                            })
                    .collect(Collectors.toSet());
        }

        return Set.of();
    }

    /**
     * マイグレーションを実行する。
     *
     * @param targetNodes 実行対象ノード
     * @return 実行結果
     */
    @Override
    public ExecutionResult execute(Set<NodeId> targetNodes) {
        if (targetNodes.isEmpty()) {
            ExecutionSummary summary = ExecutionSummary.success(direction, 0, 0, 0);
            listener.onCompleted(summary);
            return ExecutionResult.success(summary);
        }

        ExecutionPlan plan = createPlanFor(targetNodes);
        int totalNodes = plan.totalNodes();

        Map<NodeId, Integer> positionMap = new HashMap<>();
        int pos = 0;
        for (ExecutionLevel level : plan.levels()) {
            for (MigrationNode node : level.nodes()) {
                positionMap.put(node.id(), pos++);
            }
        }

        Comparator<MigrationNode> orderComparator =
                Comparator.comparingInt(n -> positionMap.getOrDefault(n.id(), Integer.MAX_VALUE));

        ReadyNodeTracker tracker = new ReadyNodeTracker(graph, targetNodes, direction);
        PriorityBlockingQueue<MigrationNode> readyQueue =
                new PriorityBlockingQueue<>(Math.max(1, targetNodes.size()), orderComparator);

        for (NodeId readyId : tracker.initialReadyNodes()) {
            graph.getNode(readyId).ifPresent(readyQueue::put);
        }

        CountDownLatch latch = new CountDownLatch(totalNodes);
        Set<NodeId> failedNodes = ConcurrentHashMap.newKeySet();
        AtomicInteger executedCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        @Nullable Semaphore semaphore = maxParallelism > 0 ? new Semaphore(maxParallelism) : null;

        try {
            while (latch.getCount() > 0) {
                MigrationNode node = readyQueue.poll(100, TimeUnit.MILLISECONDS);
                if (node == null) {
                    continue;
                }

                if (failedNodes.contains(node.id())) {
                    continue;
                }

                if (isAlreadyInRequiredState(node)) {
                    listener.onNodeSkipped(node, direction, requiredHistorySkipReason());
                    skippedCount.incrementAndGet();
                    processCompletion(node.id(), tracker, readyQueue);
                    latch.countDown();
                    continue;
                }

                if (semaphore != null) {
                    semaphore.acquire();
                }

                @Nullable Semaphore sem = semaphore;
                Thread.startVirtualThread(
                        () -> {
                            try {
                                executeNode(
                                        node,
                                        failedNodes,
                                        executedCount,
                                        skippedCount,
                                        failureCount,
                                        tracker,
                                        readyQueue,
                                        latch,
                                        targetNodes);
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
                            direction,
                            totalNodes,
                            executedCount.get(),
                            skippedCount.get(),
                            Math.max(1, failureCount.get()));
            listener.onCompleted(summary);
            return ExecutionResult.failure(summary);
        }

        if (failureCount.get() > 0) {
            ExecutionSummary summary =
                    ExecutionSummary.failure(
                            direction,
                            totalNodes,
                            executedCount.get(),
                            skippedCount.get(),
                            failureCount.get());
            listener.onCompleted(summary);
            return ExecutionResult.failure(summary);
        }

        ExecutionSummary summary =
                ExecutionSummary.success(
                        direction, totalNodes, executedCount.get(), skippedCount.get());
        listener.onCompleted(summary);
        return ExecutionResult.success(summary);
    }

    private void executeNode(
            MigrationNode node,
            Set<NodeId> failedNodes,
            AtomicInteger executedCount,
            AtomicInteger skippedCount,
            AtomicInteger failureCount,
            ReadyNodeTracker tracker,
            PriorityBlockingQueue<MigrationNode> readyQueue,
            CountDownLatch latch,
            Set<NodeId> targetNodes) {

        Task task = taskFor(node);
        if (task == null) {
            listener.onNodeSkipped(node, direction, "no down task");
            skippedCount.incrementAndGet();
            processCompletion(node.id(), tracker, readyQueue);
            return;
        }

        listener.onNodeStarted(node, direction);

        long startTime = System.currentTimeMillis();
        Result<TaskResult, String> result = task.execute();
        long duration = System.currentTimeMillis() - startTime;

        if (result.isOk()) {
            listener.onNodeSucceeded(node, direction, duration);

            TaskResult taskResult = result.value();
            history.record(recordSuccess(node, duration, taskResult));

            executedCount.incrementAndGet();
            processCompletion(node.id(), tracker, readyQueue);
        } else {
            String errorMsg = result.error();
            String message = errorMsg != null ? errorMsg : "Unknown error";
            String sqlContent = null;
            if (task instanceof SqlContentProvider sqlProvider) {
                sqlContent = sqlProvider.sqlContent();
            }

            listener.onNodeFailed(node, direction, sqlContent, message);

            history.record(
                    ExecutionRecord.failure(
                            node.id(), node.environment().id(), direction, node.name(), message));

            failedNodes.add(node.id());
            failureCount.incrementAndGet();
            propagateFailure(node.id(), failedNodes, targetNodes, skippedCount, latch);
        }
    }

    private void propagateFailure(
            NodeId failedId,
            Set<NodeId> failedNodes,
            Set<NodeId> targetNodes,
            AtomicInteger skippedCount,
            CountDownLatch latch) {
        Set<NodeId> cone = transitiveSuccessorsOf(failedId);
        for (NodeId skipId : cone) {
            if (!targetNodes.contains(skipId)) {
                continue;
            }
            if (!failedNodes.add(skipId)) {
                continue;
            }
            MigrationNode skipNode = graph.getNode(skipId).orElse(null);
            if (skipNode == null) {
                latch.countDown();
                continue;
            }
            listener.onNodeSkipped(skipNode, direction, "dependency failed: " + failedId.value());
            skippedCount.incrementAndGet();
            latch.countDown();
        }
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

    /** direction に応じたタスクを返す。DOWN の場合 null 可。 */
    private @Nullable Task taskFor(MigrationNode node) {
        return direction == ExecutionDirection.DOWN ? node.downTask() : node.upTask();
    }

    /** 失敗伝播に使う推移的後続ノード集合。UP: getAllDependents, DOWN: getAllDependencies。 */
    private Set<NodeId> transitiveSuccessorsOf(NodeId nodeId) {
        return direction == ExecutionDirection.DOWN
                ? graph.getAllDependencies(nodeId)
                : graph.getAllDependents(nodeId);
    }

    /** direction に応じた実行プランを生成する。 */
    private ExecutionPlan createPlanFor(Set<NodeId> targetNodes) {
        return direction == ExecutionDirection.DOWN
                ? TopologicalSort.createReverseExecutionPlanFor(graph, targetNodes)
                : TopologicalSort.createExecutionPlanFor(graph, targetNodes);
    }

    /** 成功時の ExecutionRecord を生成する。 */
    private ExecutionRecord recordSuccess(
            MigrationNode node, long duration, @Nullable TaskResult taskResult) {
        if (direction == ExecutionDirection.DOWN) {
            return ExecutionRecord.downSuccess(
                    node.id(), node.environment().id(), node.name(), duration);
        }
        String serializedDownTask = taskResult != null ? taskResult.serializedDownTask() : null;
        return ExecutionRecord.upSuccess(
                node.id(), node.environment().id(), node.name(), serializedDownTask, duration);
    }

    /** 履歴状態が「既に目的の状態」かどうかを判定する。UP: 実行済みならスキップ, DOWN: 未実行ならスキップ。 */
    private boolean isAlreadyInRequiredState(MigrationNode node) {
        boolean wasExecuted = history.wasExecuted(node.id(), node.environment().id());
        return direction == ExecutionDirection.DOWN ? !wasExecuted : wasExecuted;
    }

    /** 履歴状態スキップ時の reason 文字列。 */
    private String requiredHistorySkipReason() {
        return direction == ExecutionDirection.DOWN ? "not executed" : "already executed";
    }
}
