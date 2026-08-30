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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * Unified DAG-based migration executor covering UP/DOWN and sequential/parallel execution.
 *
 * <p>This is the single executor used for every migration run in Migraphe. A single instance is
 * bound to one traversal {@link ExecutionDirection}: {@link ExecutionDirection#UP} walks the graph
 * in dependency order and runs each node's {@link MigrationNode#upTask()}, while {@link
 * ExecutionDirection#DOWN} walks it in reverse and runs each node's {@link
 * MigrationNode#downTask()}. The same code path serves both sequential and parallel runs — the
 * degree of concurrency is controlled solely by the {@code maxParallelism} constructor argument.
 *
 * <h2>Concurrency model</h2>
 *
 * <p>Execution is driven by a single coordinator loop on the calling thread plus one virtual thread
 * per executing node:
 *
 * <ul>
 *   <li>A {@link ReadyNodeTracker} (constructed for this {@code direction}) tracks the
 *       direction-aware in-degree of every target node and reports nodes that have become ready.
 *   <li>Ready nodes are placed into a {@link PriorityBlockingQueue} ordered by their position in
 *       the {@link ExecutionPlan}, so that — even under parallelism — nodes are dispatched in a
 *       stable, plan-consistent order.
 *   <li>The coordinator loop polls the queue and, for each ready node, acquires a permit from a
 *       {@link Semaphore} sized to {@code maxParallelism} and dispatches the node on a new virtual
 *       thread ({@link Thread#startVirtualThread}). When {@code maxParallelism == 1} the semaphore
 *       effectively serializes execution. When {@code maxParallelism <= 0} no semaphore is created
 *       and dispatch is unbounded.
 *   <li>A {@link CountDownLatch} initialized to the total node count tracks outstanding work; the
 *       coordinator loop runs until the latch reaches zero, then awaits it before summarizing.
 *   <li>On task completion the node is reported back to the tracker, releasing any newly ready
 *       successors into the queue.
 * </ul>
 *
 * <p>To keep the supplied {@link HistoryRepository} and {@link ExecutionListener} safe to call from
 * many concurrent virtual threads, the constructor wraps each in a synchronizing decorator ({@link
 * SynchronizedHistoryRepository}, {@link SynchronizedExecutionListener}) unless the supplied
 * instance is already of that wrapper type (avoiding double wrapping).
 *
 * <h2>Failure handling (fail-soft)</h2>
 *
 * <p>A node failure does not abort the whole run. The failed node is recorded, its transitive
 * successors (dependents for UP, dependencies for DOWN) within the target set are marked skipped
 * via {@link #propagateFailure}, and any independent branches keep running. The final {@link
 * ExecutionResult} reports failure whenever at least one node failed.
 *
 * @see Executor
 * @see ReadyNodeTracker
 * @see ExecutionPlan
 */
public final class DagExecutor implements Executor {

    private final MigrationGraph graph;
    private final HistoryRepository history;
    private final ExecutionListener listener;
    private final ExecutionDirection direction;
    private final int maxParallelism;

    /**
     * Creates an executor bound to a graph, persistence, listener, direction, and parallelism.
     *
     * <p>The {@code history} and {@code listener} are automatically wrapped in synchronizing
     * decorators for thread safety unless they already are such wrappers.
     *
     * @param graph the migration graph to traverse
     * @param history the history repository used to read prior state and record results; wrapped in
     *     a {@link SynchronizedHistoryRepository} unless already synchronized
     * @param listener the listener notified of execution events; wrapped in a {@link
     *     SynchronizedExecutionListener} unless already synchronized
     * @param direction the traversal direction; {@link ExecutionDirection#UP} runs up tasks in
     *     dependency order, {@link ExecutionDirection#DOWN} runs down tasks in reverse order
     * @param maxParallelism the maximum number of nodes executed concurrently; {@code 1} serializes
     *     execution and a value {@code <= 0} disables the bounding semaphore (unbounded dispatch)
     */
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
     * Determines the set of nodes to execute for an UP run.
     *
     * <p>Delegates to {@link UpService#targetNodes}, which is where the whole apply decision lives
     * so that every front end asks the same question of it.
     *
     * @param targetId a specific target node to migrate up to, or {@code null} to consider all
     *     nodes
     * @return the set of not-yet-executed node IDs to run
     */
    @Override
    public Set<NodeId> determineTargetNodes(@Nullable NodeId targetId) {
        return new UpService(graph, history).targetNodes(targetId);
    }

    /**
     * Determines the set of nodes to roll back for a DOWN run.
     *
     * <p>Delegates to {@link DownService#rollbackTargets}, which is where the whole rollback
     * decision lives so that every front end asks the same question of it.
     *
     * @param targetVersion the node to roll back (together with its dependents), or {@code null} to
     *     defer to {@code allMigrations}
     * @param allMigrations when {@code true}, selects all currently applied nodes regardless of
     *     {@code targetVersion}
     * @return the set of currently applied node IDs to roll back
     */
    public Set<NodeId> determineRollbackTargets(
            @Nullable NodeId targetVersion, boolean allMigrations) {
        return new DownService(graph, history).rollbackTargets(targetVersion, allMigrations);
    }

    /**
     * Executes the given target nodes in this executor's direction.
     *
     * <p>An empty target set completes immediately with a success summary. Otherwise an {@link
     * ExecutionPlan} is built for the targets, ready nodes are dispatched on virtual threads
     * bounded by {@code maxParallelism}, and the coordinator awaits completion of all nodes.
     * Execution is fail-soft: a node failure marks its transitive successors (within the target
     * set) as skipped but allows independent branches to continue. Per-node lifecycle events are
     * emitted to the listener and outcomes are persisted to the history repository.
     *
     * <p>If the coordinator thread is interrupted while awaiting work, the interrupt flag is
     * restored and a failure result is returned.
     *
     * @param targetNodes the set of node IDs to execute; typically the result of {@link
     *     #determineTargetNodes} or {@link #determineRollbackTargets}
     * @return a success {@link ExecutionResult} if no node failed, otherwise a failure result; the
     *     embedded {@link ExecutionSummary} carries the executed/skipped/failed counts
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

                if (failedNodes.contains(node.id())) {
                    if (semaphore != null) {
                        semaphore.release();
                    }
                    continue;
                }

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
                                if (semaphore != null) {
                                    semaphore.release();
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

        try {
            if (result.isOk()) {
                listener.onNodeSucceeded(node, direction, duration);

                TaskResult taskResult = result.value();
                history.record(recordSuccess(node, duration, taskResult));

                executedCount.incrementAndGet();
                processCompletion(node.id(), tracker, readyQueue);
            } else {
                String errorMsg = result.error();
                String message = errorMsg != null ? errorMsg : "Unknown error";

                listener.onNodeFailed(node, direction, sqlContentOf(task), message);

                history.record(
                        ExecutionRecord.failure(
                                node.id(),
                                node.environment().id(),
                                direction,
                                node.name(),
                                message));

                failedNodes.add(node.id());
                failureCount.incrementAndGet();
                propagateFailure(node.id(), failedNodes, targetNodes, skippedCount, latch);
            }
        } catch (RuntimeException e) {
            String message =
                    (result.isOk()
                                    ? "applied, but recording the result failed: "
                                    : "recording the failure failed: ")
                            + e;

            listener.onNodeFailed(node, direction, sqlContentOf(task), message);

            if (failedNodes.add(node.id())) {
                failureCount.incrementAndGet();
            }
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

    /**
     * Returns the task for this direction; may be {@code null} for DOWN when no down task exists.
     */
    private @Nullable Task taskFor(MigrationNode node) {
        return direction == ExecutionDirection.DOWN ? node.downTask() : node.upTask();
    }

    /**
     * Returns the SQL the task would report to a failure listener, or {@code null} if it has none.
     */
    private static @Nullable String sqlContentOf(Task task) {
        return task instanceof SqlContentProvider sqlProvider ? sqlProvider.sqlContent() : null;
    }

    /**
     * Returns the transitive successor set used for failure propagation: {@code getAllDependents}
     * for UP, {@code getAllDependencies} for DOWN.
     */
    private Set<NodeId> transitiveSuccessorsOf(NodeId nodeId) {
        return direction == ExecutionDirection.DOWN
                ? graph.getAllDependencies(nodeId)
                : graph.getAllDependents(nodeId);
    }

    /** Builds the execution plan for this direction (forward for UP, reverse for DOWN). */
    private ExecutionPlan createPlanFor(Set<NodeId> targetNodes) {
        return direction == ExecutionDirection.DOWN
                ? TopologicalSort.createReverseExecutionPlanFor(graph, targetNodes)
                : TopologicalSort.createExecutionPlanFor(graph, targetNodes);
    }

    /** Builds the success {@link ExecutionRecord} for a completed node in this direction. */
    private ExecutionRecord recordSuccess(
            MigrationNode node, long duration, @Nullable TaskResult taskResult) {
        if (direction == ExecutionDirection.DOWN) {
            return ExecutionRecord.downSuccess(
                    node.id(), node.environment().id(), node.name(), duration);
        }
        String serializedDownTask = taskResult != null ? taskResult.serializedDownTask() : null;
        return ExecutionRecord.upSuccess(
                node.id(),
                node.environment().id(),
                node.name(),
                serializedDownTask,
                duration,
                fingerprintOf(node));
    }

    /**
     * Returns the node's fingerprint, or {@code null} when the plugin's accessor throws.
     *
     * <p>The node's task has already been applied by the time this is called, so a broken accessor
     * must not cost the success record: without it the migration is applied again on the next run.
     * {@code null} is what {@link MigrationNode#fingerprint(java.util.List)} already defines as
     * "unknown".
     */
    private @Nullable String fingerprintOf(MigrationNode node) {
        try {
            return node.fingerprint(graph.canonicalTransitiveDependencies(node.id()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Reports whether the node is already in its target history state and can be skipped: for UP,
     * skip when already executed; for DOWN, skip when not yet executed.
     */
    private boolean isAlreadyInRequiredState(MigrationNode node) {
        boolean wasExecuted = history.wasExecuted(node.id(), node.environment().id());
        return direction == ExecutionDirection.DOWN ? !wasExecuted : wasExecuted;
    }

    /** Returns the skip-reason string used when a node is skipped due to its history state. */
    private String requiredHistorySkipReason() {
        return direction == ExecutionDirection.DOWN ? "not executed" : "already executed";
    }
}
