package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.execution.ExecutionListener;
import io.github.kakusuke.migraphe.api.execution.ExecutionSummary;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.target.DownTaskRestorer;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
 *       <strong>Exactly one countdown per node</strong>, held by whoever claims it first: too few
 *       and the run hangs, too many and it reports itself over while a migration is still inside
 *       {@code execute()}.
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
 * <p>Propagation walks the <em>transitive</em> cone while the tracker gates on <em>direct</em>
 * predecessors, so a cone member can already be running: its only declared predecessor may sit
 * outside the run — applied by an earlier one — which makes it ready alongside the node that then
 * fails above it. Such a node is left to its own completion; propagation reports and counts only
 * the ones nobody has claimed.
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
     * The row that applied each node, read once when a rollback run starts.
     *
     * <p>Empty for an UP run, which never asks. A DOWN run writes only DOWN rows, so no apply can
     * appear while it is running and one read answers for every node — where asking per node made
     * {@link HistoryRepository#latestApplies} fold the whole history once per migration, since the
     * shipped JDBC repository derives it from {@code allRecords()}.
     *
     * <p>Assigned at the top of {@link #execute} before any worker starts, so the workers see it.
     */
    private Map<NodeId, ExecutionRecord> appliedRows = Map.of();

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
     * <p>Delegates to {@link UpService#selectedNodes}, which is where the whole apply decision
     * lives so that every front end asks the same question of it.
     *
     * @param requestedNode a specific target node to migrate up to, or {@code null} to consider all
     *     nodes
     * @return the set of not-yet-executed node IDs to run
     */
    @Override
    public Set<NodeId> determineSelectedNodes(@Nullable NodeId requestedNode) {
        return new UpService(graph, history).selectedNodes(requestedNode);
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
     * @param selectedNodes the set of node IDs to execute; typically the result of {@link
     *     #determineSelectedNodes} or, for a rollback, {@link DownService.DownPlan#selectedNodes}
     * @return a success {@link ExecutionResult} if no node failed, otherwise a failure result; the
     *     embedded {@link ExecutionSummary} carries the executed/skipped/failed counts
     */
    @Override
    public ExecutionResult execute(Set<NodeId> selectedNodes) {
        if (selectedNodes.isEmpty()) {
            ExecutionSummary summary = ExecutionSummary.success(direction, 0, 0, 0);
            listener.onCompleted(summary);
            return ExecutionResult.success(summary);
        }

        appliedRows = direction == ExecutionDirection.DOWN ? appliedRowsForThisRun() : Map.of();

        ExecutionPlan plan = createPlanFor(selectedNodes);
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

        ReadyNodeTracker tracker = new ReadyNodeTracker(graph, selectedNodes, direction);
        PriorityBlockingQueue<MigrationNode> readyQueue =
                new PriorityBlockingQueue<>(Math.max(1, selectedNodes.size()), orderComparator);

        for (NodeId readyId : tracker.initialReadyNodes()) {
            graph.getNode(readyId).ifPresent(readyQueue::put);
        }

        CountDownLatch latch = new CountDownLatch(totalNodes);
        // Who owns a node's single countdown. Every node is counted exactly once or the run either
        // hangs or reports itself over: the coordinator claims a node before starting it, and
        // failure propagation may only count the ones nobody has claimed.
        Set<NodeId> claimed = ConcurrentHashMap.newKeySet();
        Set<NodeId> failedNodes = ConcurrentHashMap.newKeySet();
        // Every thread this run started, so a cancellation can wait for the tasks already in
        // flight. Waiting on the latch instead would never return: it still holds counts for
        // nodes that were never dispatched.
        List<Thread> dispatched = new CopyOnWriteArrayList<>();
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
                    if (!claimed.add(node.id())) {
                        continue;
                    }
                    listener.onNodeSkipped(node, direction, requiredHistorySkipReason());
                    skippedCount.incrementAndGet();
                    processCompletion(node.id(), tracker, readyQueue);
                    latch.countDown();
                    continue;
                }

                if (semaphore != null) {
                    semaphore.acquire();
                }

                if (!claimed.add(node.id())) {
                    if (semaphore != null) {
                        semaphore.release();
                    }
                    continue;
                }

                dispatched.add(
                        Thread.startVirtualThread(
                                () -> {
                                    try {
                                        executeNode(
                                                node,
                                                failedNodes,
                                                claimed,
                                                executedCount,
                                                skippedCount,
                                                failureCount,
                                                tracker,
                                                readyQueue,
                                                latch,
                                                selectedNodes);
                                    } finally {
                                        if (semaphore != null) {
                                            semaphore.release();
                                        }
                                        latch.countDown();
                                    }
                                }));
            }

            latch.await();
        } catch (InterruptedException e) {
            awaitDispatched(dispatched);
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

    /**
     * Waits for the tasks already in flight, whatever the interrupt was for.
     *
     * <p>A cancelled run must not return while a statement is executing: the threads are virtual,
     * hence daemon, so the JVM may exit inside it and leave the database in a state no history row
     * describes. What is <em>not</em> waited for is the rest of the graph — nothing further is
     * dispatched once the loop is left, and the latch cannot be awaited because it still holds
     * counts for nodes that never started.
     *
     * <p>Interrupts that arrive while waiting are absorbed and re-raised on the way out, because
     * there is nothing left to interrupt: the choice was already made to stop dispatching, and the
     * only thing still running is work that cannot be abandoned safely.
     */
    private static void awaitDispatched(List<Thread> dispatched) {
        boolean interrupted = false;
        for (Thread thread : dispatched) {
            while (true) {
                try {
                    thread.join();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void executeNode(
            MigrationNode node,
            Set<NodeId> failedNodes,
            Set<NodeId> claimed,
            AtomicInteger executedCount,
            AtomicInteger skippedCount,
            AtomicInteger failureCount,
            ReadyNodeTracker tracker,
            PriorityBlockingQueue<MigrationNode> readyQueue,
            CountDownLatch latch,
            Set<NodeId> selectedNodes) {

        String refusal = rollbackRefusalFor(node);
        if (refusal != null) {
            listener.onNodeFailed(node, direction, null, refusal);
            failedNodes.add(node.id());
            failureCount.incrementAndGet();
            propagateFailure(node.id(), failedNodes, claimed, selectedNodes, skippedCount, latch);
            return;
        }

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
                                node.id(), node.target().id(), direction, node.name(), message));

                failedNodes.add(node.id());
                failureCount.incrementAndGet();
                propagateFailure(
                        node.id(), failedNodes, claimed, selectedNodes, skippedCount, latch);
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
            propagateFailure(node.id(), failedNodes, claimed, selectedNodes, skippedCount, latch);
        }
    }

    private void propagateFailure(
            NodeId failedId,
            Set<NodeId> failedNodes,
            Set<NodeId> claimed,
            Set<NodeId> selectedNodes,
            AtomicInteger skippedCount,
            CountDownLatch latch) {
        Set<NodeId> cone = transitiveSuccessorsOf(failedId);
        for (NodeId skipId : cone) {
            if (!selectedNodes.contains(skipId)) {
                continue;
            }
            if (!failedNodes.add(skipId)) {
                continue;
            }
            if (!claimed.add(skipId)) {
                // Already running, or already skipped by the coordinator. Its own completion owns
                // the countdown; counting it here as well drops the latch to zero while its task
                // is still inside execute(), and the run reports itself over mid-migration.
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
     * Returns the task for this direction; {@code null} for DOWN when the history has no rollback
     * to run.
     *
     * <p>A rollback runs what the history recorded, and <strong>only</strong> that. There is no
     * fallback to {@link MigrationNode#downTask()}: the definition may have moved on — or be gone —
     * since the node was applied, and the objects standing in the database were made by the version
     * that ran, so the task file describes something else. Every state in which the recorded
     * rollback cannot be rebuilt is a refusal instead, reported by {@link #rollbackRefusalFor}
     * before this is asked.
     *
     * <p>What is left for {@code null} to mean here is a node the history holds no apply for, which
     * a plan does not select.
     */
    private @Nullable Task taskFor(MigrationNode node) {
        if (direction != ExecutionDirection.DOWN) {
            return node.upTask();
        }
        return recordedRollbackFor(node);
    }

    /**
     * Rebuilds the rollback the history kept for this node, or {@code null} when it cannot.
     *
     * <p>Read from the row that <strong>applied</strong> the node, not from its latest row of any
     * kind. A payload is written on an apply alone, so a node whose newest row is a failed rollback
     * has none there — and reading the newest row hid what was applied the moment a rollback failed
     * once, leaving every retry on the current definition instead of replaying what actually ran.
     *
     * <p>{@code null} means one of three things, and none of them lets the definition stand in for
     * the row: the plugin's target does not implement {@link DownTaskRestorer}, the history records
     * no apply of this node against this target, or that apply carried no payload. The first and
     * the third are refusals; the second is a node nothing selected.
     */
    private @Nullable Task recordedRollbackFor(MigrationNode node) {
        if (!(node.target() instanceof DownTaskRestorer restorer)) {
            return null;
        }
        ExecutionRecord applied = appliedRowFor(node);
        if (applied == null || applied.serializedDownTask() == null) {
            return null;
        }
        return restorer.restoreDownTask(applied.serializedDownTask(), applied.pluginMetadata());
    }

    /**
     * The row that applied this node against its own target, or {@code null} when there is none.
     */
    private @Nullable ExecutionRecord appliedRowFor(MigrationNode node) {
        ExecutionRecord applied = appliedRows.get(node.id());
        return applied != null && applied.targetId().equals(node.target().id()) ? applied : null;
    }

    /**
     * The applied rows this run may be asked about, keyed by node.
     *
     * <p>Keyed by node alone because {@link HistoryRepository#latestApplies} is: an identifier is
     * unique across the project, so it answers with one row per migration and there is no second
     * row for a key to tell apart. {@link #appliedRowFor} still checks the target, so a row naming
     * somewhere this run is not rolling back is declined rather than replayed.
     */
    private Map<NodeId, ExecutionRecord> appliedRowsForThisRun() {
        Map<NodeId, ExecutionRecord> rows = new HashMap<>();
        for (ExecutionRecord apply : history.latestApplies()) {
            rows.put(apply.nodeId(), apply);
        }
        return rows;
    }

    /**
     * Why this node's rollback cannot run, or {@code null} when it can be attempted.
     *
     * <p>The history recording no rollback is a refusal, not a reason to run the definition's. The
     * recorded SQL is what matches the objects that exist; substituting what the definitions say
     * now would be answering with D where H was asked, which is the judgement this tool does not
     * make.
     *
     * <p>Four refusals, because the operator's next move differs, and they are asked in that order.
     * A row naming a {@code no_way_back:} reason is quoting its author, so the reason is quoted
     * back — first, since a row can carry the reason and no fingerprint at once. A row carrying no
     * fingerprint cannot speak for its own contents <em>at all</em>, whatever else it holds, so
     * that is asked before the payload and answered in the words {@code down}'s own plan uses;
     * {@code upgrade} fills it from the definitions in one pass. A complete row that still kept no
     * payload has either come from a plugin whose up task reports no rollback payload, or been
     * edited — the row cannot tell those apart, so the message does not pretend to. And a payload
     * the target cannot rebuild is a plugin that cannot read back what it wrote.
     *
     * <p><strong>Nothing is recorded.</strong> A refusal is not an attempt: a DOWN failure row
     * would say a rollback ran and failed, and — since {@code amend} reads the latest record — it
     * would take the remedy this message names away from the operator it was named to.
     *
     * <p>Two states stay outside this: a node the history has no apply for, and a complete row
     * whose payload the target can rebuild. Neither is the history saying it kept no rollback.
     *
     * <p>The restorer arm was once outside it, to spare {@code noop}, whose provider kept the
     * rollback on the node rather than in the row; that was the plugin being wrong, and it is
     * fixed. {@code up} now refuses a definition whose declared rollback would not be recorded, so
     * no new row of that shape can appear, and the reference target rebuilds what its task
     * recorded.
     */
    private @Nullable String rollbackRefusalFor(MigrationNode node) {
        if (direction != ExecutionDirection.DOWN) {
            return null;
        }
        ExecutionRecord applied = appliedRowFor(node);
        if (applied == null) {
            return null;
        }
        String id = node.id().value();
        String reason = applied.noWayBack();
        if (reason != null) {
            return id
                    + " was applied one-way: "
                    + reason
                    + ". The history recorded no rollback for it.";
        }
        if (applied.fingerprint() == null) {
            // Both halves, because neither reaches every row: upgrade fills a row from the
            // definition that names it, and a node standing for a row no definition names — which
            // is exactly what reaches this executor as a rollback — is repaired by naming it.
            return id
                    + ": the row that applied it carries no fingerprint, so what it recorded cannot"
                    + " be read at face value; run 'migraphe upgrade-history', or 'migraphe"
                    + " amend "
                    + id
                    + "' if no task file declares it any more";
        }
        if (applied.serializedDownTask() == null) {
            return id
                    + ": the history recorded no rollback for it and no reason for having none."
                    + " Either the plugin that applied it does not report rollback payloads, or the"
                    + " row was edited.";
        }
        return recordedRollbackFor(node) != null
                ? null
                : id
                        + ": the history recorded a rollback for it, but the target "
                        + node.target().id().value()
                        + " cannot rebuild one — only the plugin that wrote the payload can read it"
                        + " back.";
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
    private ExecutionPlan createPlanFor(Set<NodeId> selectedNodes) {
        return direction == ExecutionDirection.DOWN
                ? TopologicalSort.createReverseExecutionPlanFor(graph, selectedNodes)
                : TopologicalSort.createExecutionPlanFor(graph, selectedNodes);
    }

    /** Builds the success {@link ExecutionRecord} for a completed node in this direction. */
    private ExecutionRecord recordSuccess(
            MigrationNode node, long duration, @Nullable TaskResult taskResult) {
        if (direction == ExecutionDirection.DOWN) {
            return ExecutionRecord.downSuccess(
                    node.id(), node.target().id(), node.name(), duration);
        }
        String serializedDownTask = taskResult != null ? taskResult.serializedDownTask() : null;
        String pluginMetadata = taskResult != null ? taskResult.pluginMetadata() : null;
        return ExecutionRecord.upSuccess(
                node.id(),
                node.target().id(),
                node.name(),
                serializedDownTask,
                duration,
                fingerprintOf(node),
                pluginMetadata,
                canonicalDirectDependencies(node),
                node.noWayBack());
    }

    /**
     * What the node declares it stands on directly, ordered so the column reads the same each run.
     */
    private static List<NodeId> canonicalDirectDependencies(MigrationNode node) {
        return node.dependencies().stream().sorted(Comparator.comparing(NodeId::value)).toList();
    }

    /**
     * Returns the node's fingerprint, or {@code null} when the plugin's accessor throws.
     *
     * <p>The node's task has already been applied by the time this is called, so a broken accessor
     * must not cost the success record: without it the migration is applied again on the next run.
     * {@code null} is what {@link MigrationNode#fingerprint} already defines as "unknown".
     */
    private @Nullable String fingerprintOf(MigrationNode node) {
        try {
            return node.fingerprint(graph.fingerprinterFor(node.id()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Reports whether the node is already in its target history state and can be skipped: for UP,
     * skip when already executed; for DOWN, skip when not yet executed.
     */
    private boolean isAlreadyInRequiredState(MigrationNode node) {
        boolean wasExecuted = history.wasExecuted(node.id());
        return direction == ExecutionDirection.DOWN ? !wasExecuted : wasExecuted;
    }

    /** Returns the skip-reason string used when a node is skipped due to its history state. */
    private String requiredHistorySkipReason() {
        return direction == ExecutionDirection.DOWN ? "not executed" : "already executed";
    }
}
