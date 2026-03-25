package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.execution.ExecutionListener;
import io.github.kakusuke.migraphe.api.execution.ExecutionPlanInfo;
import io.github.kakusuke.migraphe.api.execution.ExecutionSummary;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ParallelMigrationExecutor")
class ParallelMigrationExecutorTest {

    private MigrationGraph graph;
    private InMemoryHistoryRepository historyRepo;
    private HistoryRepository syncHistoryRepo;
    private MockExecutionListener listener;
    private ParallelMigrationExecutor executor;
    private Environment testEnv;

    @BeforeEach
    void setUp() {
        graph = MigrationGraph.create();
        historyRepo = new InMemoryHistoryRepository();
        syncHistoryRepo = new SynchronizedHistoryRepository(historyRepo);
        listener = new MockExecutionListener();
        testEnv = SimpleEnvironment.create(EnvironmentId.of("test"), "Test Environment");
    }

    @Test
    @DisplayName("単一ノードを実行できる")
    void shouldExecuteSingleNode() {
        // Given
        MigrationNode nodeA = createNode("a", "Node A");
        graph.addNode(nodeA);
        executor = new ParallelMigrationExecutor(graph, syncHistoryRepo, listener);

        // When
        ExecutionResult result = executor.execute(Set.of(NodeId.of("a")));

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.summary().executedCount()).isEqualTo(1);
        assertThat(syncHistoryRepo.wasExecuted(NodeId.of("a"), testEnv.id())).isTrue();
        assertThat(listener.succeededNodes).containsExactly(NodeId.of("a"));
    }

    @Test
    @DisplayName("A→B チェーンで順序保証")
    void shouldExecuteChainInOrder() {
        // Given: A -> B
        MigrationNode nodeA = createNode("a", "Node A");
        MigrationNode nodeB = createNode("b", "Node B", Set.of(NodeId.of("a")));
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        executor = new ParallelMigrationExecutor(graph, syncHistoryRepo, listener);

        // When
        ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.summary().executedCount()).isEqualTo(2);
        assertThat(listener.succeededNodes).containsExactly(NodeId.of("a"), NodeId.of("b"));
    }

    @Test
    @DisplayName("独立ノードが両方実行される")
    void shouldExecuteIndependentNodes() {
        // Given: A and B are independent
        MigrationNode nodeA = createNode("a", "Node A");
        MigrationNode nodeB = createNode("b", "Node B");
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        executor = new ParallelMigrationExecutor(graph, syncHistoryRepo, listener);

        // When
        ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.summary().executedCount()).isEqualTo(2);
        assertThat(syncHistoryRepo.wasExecuted(NodeId.of("a"), testEnv.id())).isTrue();
        assertThat(syncHistoryRepo.wasExecuted(NodeId.of("b"), testEnv.id())).isTrue();
    }

    @Test
    @DisplayName("fail-fast — 失敗時に依存ノードは実行されない")
    void shouldNotExecuteDependentsOnFailure() {
        // Given: A -> B, A fails
        Task failingTask = ControllableTask.failing("task failed");
        MigrationNode nodeA =
                SimpleMigrationNode.builder()
                        .id(NodeId.of("a"))
                        .name("Node A")
                        .environment(testEnv)
                        .dependencies(Set.of())
                        .upTask(failingTask)
                        .downTask(SimpleTask.of("DOWN: A"))
                        .build();
        MigrationNode nodeB = createNode("b", "Node B", Set.of(NodeId.of("a")));
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        executor = new ParallelMigrationExecutor(graph, syncHistoryRepo, listener);

        // When
        ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

        // Then
        assertThat(result.success()).isFalse();
        assertThat(listener.failedNodes).containsExactly(NodeId.of("a"));
        assertThat(listener.succeededNodes).doesNotContain(NodeId.of("b"));
    }

    @Test
    @DisplayName("実行済みノードはスキップされる")
    void shouldSkipAlreadyExecutedNodes() {
        // Given
        MigrationNode nodeA = createNode("a", "Node A");
        graph.addNode(nodeA);

        // nodeA を実行済みとして記録
        syncHistoryRepo.record(
                ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "Node A", null, 100L));

        executor = new ParallelMigrationExecutor(graph, syncHistoryRepo, listener);

        // When
        ExecutionResult result = executor.execute(Set.of(NodeId.of("a")));

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.summary().skippedCount()).isEqualTo(1);
        assertThat(result.summary().executedCount()).isEqualTo(0);
        assertThat(listener.skippedNodes).containsExactly(NodeId.of("a"));
    }

    @Test
    @DisplayName("リスナーに完了が通知される")
    void shouldNotifyListenerOnCompletion() {
        // Given
        MigrationNode nodeA = createNode("a", "Node A");
        graph.addNode(nodeA);
        executor = new ParallelMigrationExecutor(graph, syncHistoryRepo, listener);

        // When
        executor.execute(Set.of(NodeId.of("a")));

        // Then
        assertThat(listener.completedCalled).isTrue();
    }

    @Test
    @DisplayName("maxParallelism=1 で逐次実行される")
    void shouldExecuteSequentiallyWithMaxParallelismOne() throws InterruptedException {
        // Given: 独立した3ノード A, B, C を maxParallelism=1 で実行
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        AtomicInteger currentConcurrent = new AtomicInteger(0);
        CountDownLatch allDone = new CountDownLatch(3);

        Task concurrencyTrackingTask =
                new Task() {
                    @Override
                    public Result<TaskResult, String> execute() {
                        int current = currentConcurrent.incrementAndGet();
                        maxConcurrent.updateAndGet(max -> Math.max(max, current));
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        currentConcurrent.decrementAndGet();
                        allDone.countDown();
                        return Result.ok(TaskResult.withoutDownTask("done"));
                    }

                    @Override
                    public String description() {
                        return "ConcurrencyTrackingTask";
                    }
                };

        MigrationNode nodeA = createNodeWithTask("a", "Node A", Set.of(), concurrencyTrackingTask);
        MigrationNode nodeB = createNodeWithTask("b", "Node B", Set.of(), concurrencyTrackingTask);
        MigrationNode nodeC = createNodeWithTask("c", "Node C", Set.of(), concurrencyTrackingTask);
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        executor = new ParallelMigrationExecutor(graph, syncHistoryRepo, listener, 1);

        // When
        ExecutionResult result =
                executor.execute(Set.of(NodeId.of("a"), NodeId.of("b"), NodeId.of("c")));

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.summary().executedCount()).isEqualTo(3);
        assertThat(maxConcurrent.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("maxParallelism=2 で同時実行数が2以下に制限される")
    void shouldLimitConcurrencyToMaxParallelism() throws InterruptedException {
        // Given: 独立した4ノードを maxParallelism=2 で実行
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        AtomicInteger currentConcurrent = new AtomicInteger(0);

        Task concurrencyTrackingTask =
                new Task() {
                    @Override
                    public Result<TaskResult, String> execute() {
                        int current = currentConcurrent.incrementAndGet();
                        maxConcurrent.updateAndGet(max -> Math.max(max, current));
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        currentConcurrent.decrementAndGet();
                        return Result.ok(TaskResult.withoutDownTask("done"));
                    }

                    @Override
                    public String description() {
                        return "ConcurrencyTrackingTask";
                    }
                };

        MigrationNode nodeA = createNodeWithTask("a", "Node A", Set.of(), concurrencyTrackingTask);
        MigrationNode nodeB = createNodeWithTask("b", "Node B", Set.of(), concurrencyTrackingTask);
        MigrationNode nodeC = createNodeWithTask("c", "Node C", Set.of(), concurrencyTrackingTask);
        MigrationNode nodeD = createNodeWithTask("d", "Node D", Set.of(), concurrencyTrackingTask);
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);
        graph.addNode(nodeD);

        executor = new ParallelMigrationExecutor(graph, syncHistoryRepo, listener, 2);

        // When
        ExecutionResult result =
                executor.execute(
                        Set.of(NodeId.of("a"), NodeId.of("b"), NodeId.of("c"), NodeId.of("d")));

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.summary().executedCount()).isEqualTo(4);
        assertThat(maxConcurrent.get()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("maxParallelism=0 で無制限に並列実行される")
    void shouldExecuteUnlimitedWithMaxParallelismZero() {
        // Given: 独立した2ノードを maxParallelism=0 で実行
        MigrationNode nodeA = createNode("a", "Node A");
        MigrationNode nodeB = createNode("b", "Node B");
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        executor = new ParallelMigrationExecutor(graph, syncHistoryRepo, listener, 0);

        // When
        ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.summary().executedCount()).isEqualTo(2);
    }

    private MigrationNode createNodeWithTask(
            String id, String name, Set<NodeId> dependencies, Task upTask) {
        return SimpleMigrationNode.builder()
                .id(NodeId.of(id))
                .name(name)
                .environment(testEnv)
                .dependencies(dependencies)
                .upTask(upTask)
                .downTask(SimpleTask.of("DOWN: " + name))
                .build();
    }

    private MigrationNode createNode(String id, String name) {
        return createNode(id, name, Set.of());
    }

    private MigrationNode createNode(String id, String name, Set<NodeId> dependencies) {
        Task upTask = SimpleTask.of("UP: " + name);
        Task downTask = SimpleTask.of("DOWN: " + name);
        return SimpleMigrationNode.builder()
                .id(NodeId.of(id))
                .name(name)
                .environment(testEnv)
                .dependencies(dependencies)
                .upTask(upTask)
                .downTask(downTask)
                .build();
    }

    /** テスト用の制御可能な Task 実装 */
    static class ControllableTask implements Task {
        private final Result<TaskResult, String> result;

        ControllableTask(Result<TaskResult, String> result) {
            this.result = result;
        }

        static ControllableTask succeeding() {
            return new ControllableTask(Result.ok(TaskResult.withoutDownTask("success")));
        }

        static ControllableTask failing(String error) {
            return new ControllableTask(Result.err(error));
        }

        @Override
        public Result<TaskResult, String> execute() {
            return result;
        }

        @Override
        public String description() {
            return "ControllableTask";
        }
    }

    /** テスト用の ExecutionListener 実装 */
    static class MockExecutionListener implements ExecutionListener {
        final List<NodeId> startedNodes = Collections.synchronizedList(new ArrayList<>());
        final List<NodeId> succeededNodes = Collections.synchronizedList(new ArrayList<>());
        final List<NodeId> skippedNodes = Collections.synchronizedList(new ArrayList<>());
        final List<NodeId> failedNodes = Collections.synchronizedList(new ArrayList<>());
        volatile boolean completedCalled = false;

        @Override
        public void onPlanCreated(ExecutionPlanInfo plan) {}

        @Override
        public void onNodeStarted(MigrationNode node, ExecutionDirection direction) {
            startedNodes.add(node.id());
        }

        @Override
        public void onNodeSucceeded(
                MigrationNode node, ExecutionDirection direction, long durationMs) {
            succeededNodes.add(node.id());
        }

        @Override
        public void onNodeSkipped(MigrationNode node, ExecutionDirection direction, String reason) {
            skippedNodes.add(node.id());
        }

        @Override
        public void onNodeFailed(
                MigrationNode node,
                ExecutionDirection direction,
                @Nullable String sqlContent,
                String errorMessage) {
            failedNodes.add(node.id());
        }

        @Override
        public void onCompleted(ExecutionSummary summary) {
            completedCalled = true;
        }
    }

    /** スレッドセーフな HistoryRepository ラッパー */
    static class SynchronizedHistoryRepository implements HistoryRepository {
        private final HistoryRepository delegate;

        SynchronizedHistoryRepository(HistoryRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized void initialize() {
            delegate.initialize();
        }

        @Override
        public synchronized void record(ExecutionRecord record) {
            delegate.record(record);
        }

        @Override
        public synchronized boolean wasExecuted(NodeId nodeId, EnvironmentId environmentId) {
            return delegate.wasExecuted(nodeId, environmentId);
        }

        @Override
        public synchronized List<NodeId> executedNodes(EnvironmentId environmentId) {
            return delegate.executedNodes(environmentId);
        }

        @Override
        public synchronized @Nullable ExecutionRecord findLatestRecord(
                NodeId nodeId, EnvironmentId environmentId) {
            return delegate.findLatestRecord(nodeId, environmentId);
        }

        @Override
        public synchronized List<ExecutionRecord> allRecords(EnvironmentId environmentId) {
            return delegate.allRecords(environmentId);
        }
    }
}
