package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import io.github.kakusuke.migraphe.core.execution.support.MockExecutionListener;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DagExecutor (Parallel UP)")
class DagExecutorParallelUpTest {

    private final Environment testEnv = SimpleEnvironment.create(EnvironmentId.of("env"), "env");

    @Test
    @DisplayName("ダイアモンド DAG を maxParallelism=2 で全ノード成功する")
    void shouldExecuteDiamondDagWithMaxParallelismTwo() {
        // Given: A -> B, A -> C, B -> D, C -> D
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createNode("a", Set.of());
        MigrationNode nodeB = createNode("b", Set.of(NodeId.of("a")));
        MigrationNode nodeC = createNode("c", Set.of(NodeId.of("a")));
        MigrationNode nodeD = createNode("d", Set.of(NodeId.of("b"), NodeId.of("c")));
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);
        graph.addNode(nodeD);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        MockExecutionListener listener = new MockExecutionListener();

        DagExecutor executor = new DagExecutor(graph, history, listener, ExecutionDirection.UP, 2);

        // When
        ExecutionResult result =
                executor.execute(
                        Set.of(NodeId.of("a"), NodeId.of("b"), NodeId.of("c"), NodeId.of("d")));

        // Then
        assertThat(result.success()).isTrue();
        assertThat(listener.succeededNodes)
                .containsExactlyInAnyOrder(
                        NodeId.of("a"), NodeId.of("b"), NodeId.of("c"), NodeId.of("d"));
        assertThat(listener.completedCalled).isTrue();
        assertThat(history.wasExecuted(NodeId.of("a"), EnvironmentId.of("env"))).isTrue();
        assertThat(history.wasExecuted(NodeId.of("b"), EnvironmentId.of("env"))).isTrue();
        assertThat(history.wasExecuted(NodeId.of("c"), EnvironmentId.of("env"))).isTrue();
        assertThat(history.wasExecuted(NodeId.of("d"), EnvironmentId.of("env"))).isTrue();
        List<ExecutionRecord> records = history.allRecords(EnvironmentId.of("env"));
        assertThat(records).hasSize(4);
    }

    @Test
    @DisplayName("単一ノードを実行できる")
    void shouldExecuteSingleNode() {
        // Given
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createNode("a", Set.of());
        graph.addNode(nodeA);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        MockExecutionListener listener = new MockExecutionListener();
        DagExecutor executor = new DagExecutor(graph, history, listener, ExecutionDirection.UP, 0);

        // When
        ExecutionResult result = executor.execute(Set.of(NodeId.of("a")));

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.summary().executedCount()).isEqualTo(1);
        assertThat(history.wasExecuted(NodeId.of("a"), testEnv.id())).isTrue();
        assertThat(listener.succeededNodes).containsExactly(NodeId.of("a"));
    }

    @Test
    @DisplayName("A→B チェーンで順序保証")
    void shouldExecuteChainInOrder() {
        // Given: A -> B
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createNode("a", Set.of());
        MigrationNode nodeB = createNode("b", Set.of(NodeId.of("a")));
        graph.addNode(nodeA);
        graph.addNode(nodeB);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        MockExecutionListener listener = new MockExecutionListener();
        DagExecutor executor = new DagExecutor(graph, history, listener, ExecutionDirection.UP, 0);

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
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createNode("a", Set.of());
        MigrationNode nodeB = createNode("b", Set.of());
        graph.addNode(nodeA);
        graph.addNode(nodeB);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        MockExecutionListener listener = new MockExecutionListener();
        DagExecutor executor = new DagExecutor(graph, history, listener, ExecutionDirection.UP, 0);

        // When
        ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.summary().executedCount()).isEqualTo(2);
        assertThat(history.wasExecuted(NodeId.of("a"), testEnv.id())).isTrue();
        assertThat(history.wasExecuted(NodeId.of("b"), testEnv.id())).isTrue();
    }

    @Test
    @DisplayName("fail-soft — 失敗時に依存ノードはスキップ伝播される")
    void shouldSkipDependentsOnFailure() {
        // Given: A -> B, A fails
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createFailingNode("a", "task failed", Set.of());
        MigrationNode nodeB = createNode("b", Set.of(NodeId.of("a")));
        graph.addNode(nodeA);
        graph.addNode(nodeB);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        MockExecutionListener listener = new MockExecutionListener();
        DagExecutor executor = new DagExecutor(graph, history, listener, ExecutionDirection.UP, 0);

        // When
        ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

        // Then
        assertThat(result.success()).isFalse();
        assertThat(listener.failedNodes).containsExactly(NodeId.of("a"));
        assertThat(listener.succeededNodes).doesNotContain(NodeId.of("b"));
        assertThat(listener.skippedNodes).containsExactly(NodeId.of("b"));
        assertThat(listener.skipReasons.get(NodeId.of("b"))).isEqualTo("dependency failed: a");
    }

    @Test
    @DisplayName("fail-soft — 独立ノードは失敗後も完走する")
    void shouldContinueIndependentNodesAfterFailure() {
        // Given: A (失敗) と B (独立、deps なし)
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createFailingNode("a", "task failed", Set.of());
        MigrationNode nodeB = createNode("b", Set.of());
        graph.addNode(nodeA);
        graph.addNode(nodeB);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        MockExecutionListener listener = new MockExecutionListener();
        DagExecutor executor = new DagExecutor(graph, history, listener, ExecutionDirection.UP, 0);

        // When
        ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

        // Then: failure result だが B は完走する
        assertThat(result.success()).isFalse();
        assertThat(listener.failedNodes).containsExactly(NodeId.of("a"));
        assertThat(listener.succeededNodes).containsExactly(NodeId.of("b"));
        assertThat(history.wasExecuted(NodeId.of("b"), testEnv.id())).isTrue();
    }

    @Test
    @DisplayName("fail-soft — 失敗ノードの兄弟 dependents は全てスキップ伝播される")
    void shouldSkipAllDependentsOnFailure() {
        // Given: A -> B, A -> C (B and C are independent siblings)
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createFailingNode("a", "task failed", Set.of());
        MigrationNode nodeB = createNode("b", Set.of(NodeId.of("a")));
        MigrationNode nodeC = createNode("c", Set.of(NodeId.of("a")));
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        MockExecutionListener listener = new MockExecutionListener();
        DagExecutor executor = new DagExecutor(graph, history, listener, ExecutionDirection.UP, 0);

        // When
        ExecutionResult result =
                executor.execute(Set.of(NodeId.of("a"), NodeId.of("b"), NodeId.of("c")));

        // Then
        assertThat(result.success()).isFalse();
        assertThat(listener.failedNodes).containsExactly(NodeId.of("a"));
        assertThat(listener.succeededNodes).isEmpty();
        assertThat(listener.skippedNodes).containsExactlyInAnyOrder(NodeId.of("b"), NodeId.of("c"));
        assertThat(listener.skipReasons.get(NodeId.of("b"))).isEqualTo("dependency failed: a");
        assertThat(listener.skipReasons.get(NodeId.of("c"))).isEqualTo("dependency failed: a");
    }

    @Test
    @DisplayName("fail-soft — 多依存ノードは親の 1 つでも失敗していればスキップされる")
    void shouldSkipMultiDepNodeIfAnyParentFails() {
        // Given: A -> C, B -> C (C は A,B の両方に依存)。A 失敗、B 成功。
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createFailingNode("a", "task failed", Set.of());
        MigrationNode nodeB = createNode("b", Set.of());
        MigrationNode nodeC = createNode("c", Set.of(NodeId.of("a"), NodeId.of("b")));
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        MockExecutionListener listener = new MockExecutionListener();
        DagExecutor executor = new DagExecutor(graph, history, listener, ExecutionDirection.UP, 0);

        // When
        ExecutionResult result =
                executor.execute(Set.of(NodeId.of("a"), NodeId.of("b"), NodeId.of("c")));

        // Then
        assertThat(result.success()).isFalse();
        assertThat(listener.failedNodes).containsExactly(NodeId.of("a"));
        assertThat(listener.succeededNodes).containsExactly(NodeId.of("b"));
        assertThat(listener.skippedNodes).containsExactly(NodeId.of("c"));
        assertThat(listener.skipReasons.get(NodeId.of("c"))).isEqualTo("dependency failed: a");
    }

    @Test
    @DisplayName("実行済みノードはスキップされる")
    void shouldSkipAlreadyExecutedNodes() {
        // Given
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createNode("a", Set.of());
        graph.addNode(nodeA);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        // nodeA を実行済みとして記録
        history.record(ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 100L));

        MockExecutionListener listener = new MockExecutionListener();
        DagExecutor executor = new DagExecutor(graph, history, listener, ExecutionDirection.UP, 0);

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
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createNode("a", Set.of());
        graph.addNode(nodeA);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        MockExecutionListener listener = new MockExecutionListener();
        DagExecutor executor = new DagExecutor(graph, history, listener, ExecutionDirection.UP, 0);

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

        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createNodeWithTask("a", Set.of(), concurrencyTrackingTask);
        MigrationNode nodeB = createNodeWithTask("b", Set.of(), concurrencyTrackingTask);
        MigrationNode nodeC = createNodeWithTask("c", Set.of(), concurrencyTrackingTask);
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        MockExecutionListener listener = new MockExecutionListener();
        DagExecutor executor = new DagExecutor(graph, history, listener, ExecutionDirection.UP, 1);

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

        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createNodeWithTask("a", Set.of(), concurrencyTrackingTask);
        MigrationNode nodeB = createNodeWithTask("b", Set.of(), concurrencyTrackingTask);
        MigrationNode nodeC = createNodeWithTask("c", Set.of(), concurrencyTrackingTask);
        MigrationNode nodeD = createNodeWithTask("d", Set.of(), concurrencyTrackingTask);
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);
        graph.addNode(nodeD);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        MockExecutionListener listener = new MockExecutionListener();
        DagExecutor executor = new DagExecutor(graph, history, listener, ExecutionDirection.UP, 2);

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
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createNode("a", Set.of());
        MigrationNode nodeB = createNode("b", Set.of());
        graph.addNode(nodeA);
        graph.addNode(nodeB);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        MockExecutionListener listener = new MockExecutionListener();
        DagExecutor executor = new DagExecutor(graph, history, listener, ExecutionDirection.UP, 0);

        // When
        ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.summary().executedCount()).isEqualTo(2);
    }

    private MigrationNode createNode(String id, Set<NodeId> dependencies) {
        Task upTask = SimpleTask.of("UP: " + id);
        Task downTask = SimpleTask.of("DOWN: " + id);
        return SimpleMigrationNode.builder()
                .id(NodeId.of(id))
                .name(id)
                .environment(testEnv)
                .dependencies(dependencies)
                .upTask(upTask)
                .downTask(downTask)
                .build();
    }

    private MigrationNode createNodeWithTask(String id, Set<NodeId> dependencies, Task upTask) {
        return SimpleMigrationNode.builder()
                .id(NodeId.of(id))
                .name(id)
                .environment(testEnv)
                .dependencies(dependencies)
                .upTask(upTask)
                .downTask(SimpleTask.of("DOWN: " + id))
                .build();
    }

    private MigrationNode createFailingNode(String id, String error, Set<NodeId> dependencies) {
        Task upTask =
                new Task() {
                    @Override
                    public Result<TaskResult, String> execute() {
                        return Result.err(error);
                    }

                    @Override
                    public String description() {
                        return "FAIL: " + id;
                    }
                };
        return SimpleMigrationNode.builder()
                .id(NodeId.of(id))
                .name(id)
                .environment(testEnv)
                .dependencies(dependencies)
                .upTask(upTask)
                .downTask(SimpleTask.of("DOWN: " + id))
                .build();
    }
}
