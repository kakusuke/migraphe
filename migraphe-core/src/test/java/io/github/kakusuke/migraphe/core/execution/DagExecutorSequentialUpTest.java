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
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import io.github.kakusuke.migraphe.core.execution.support.MockExecutionListener;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DagExecutor (Sequential UP)")
class DagExecutorSequentialUpTest {

    private final Environment testEnv = SimpleEnvironment.create(EnvironmentId.of("env"), "env");

    @Test
    @DisplayName("UP / max=1 でインスタンス化できる")
    void shouldInstantiate() {
        MigrationGraph graph = MigrationGraph.create();
        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        DagExecutor executor =
                new DagExecutor(graph, history, NoopListener.INSTANCE, ExecutionDirection.UP, 1);
        assertThat(executor).isNotNull();
    }

    @Test
    @DisplayName("A→B チェーンを順に実行し成功する")
    void shouldExecuteAbChainInOrder() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createNode("a", Set.of());
        MigrationNode nodeB = createNode("b", Set.of(NodeId.of("a")));
        graph.addNode(nodeA);
        graph.addNode(nodeB);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        MockExecutionListener listener = new MockExecutionListener();

        DagExecutor executor = new DagExecutor(graph, history, listener, ExecutionDirection.UP, 1);
        ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

        assertThat(result.success()).isTrue();
        assertThat(listener.startedNodes).containsExactly(NodeId.of("a"), NodeId.of("b"));
        assertThat(listener.succeededNodes).containsExactly(NodeId.of("a"), NodeId.of("b"));
        assertThat(listener.completedCalled).isTrue();
        assertThat(history.wasExecuted(NodeId.of("a"), EnvironmentId.of("env"))).isTrue();
        assertThat(history.wasExecuted(NodeId.of("b"), EnvironmentId.of("env"))).isTrue();
    }

    @Nested
    @DisplayName("対象ノード決定")
    class DetermineTargetNodes {

        @Test
        @DisplayName("ターゲット指定なしで全未実行ノードを返す")
        void shouldReturnAllPendingNodesWhenNoTarget() {
            // Given
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createNode("a", Set.of());
            MigrationNode nodeB = createNode("b", Set.of());
            graph.addNode(nodeA);
            graph.addNode(nodeB);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            MockExecutionListener listener = new MockExecutionListener();
            DagExecutor executor =
                    new DagExecutor(graph, history, listener, ExecutionDirection.UP, 1);

            // When
            Set<NodeId> targets = executor.determineTargetNodes(null);

            // Then
            assertThat(targets).containsExactlyInAnyOrder(NodeId.of("a"), NodeId.of("b"));
        }

        @Test
        @DisplayName("実行済みノードは除外される")
        void shouldExcludeExecutedNodes() {
            // Given
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createNode("a", Set.of());
            MigrationNode nodeB = createNode("b", Set.of());
            graph.addNode(nodeA);
            graph.addNode(nodeB);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            // nodeA を実行済みとして記録
            history.record(
                    ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 100L));

            MockExecutionListener listener = new MockExecutionListener();
            DagExecutor executor =
                    new DagExecutor(graph, history, listener, ExecutionDirection.UP, 1);

            // When
            Set<NodeId> targets = executor.determineTargetNodes(null);

            // Then
            assertThat(targets).containsExactly(NodeId.of("b"));
        }

        @Test
        @DisplayName("ターゲット指定でターゲットと依存先を返す")
        void shouldReturnTargetAndDependencies() {
            // Given: A -> B -> C
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createNode("a", Set.of());
            MigrationNode nodeB = createNode("b", Set.of(NodeId.of("a")));
            MigrationNode nodeC = createNode("c", Set.of(NodeId.of("b")));
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            graph.addNode(nodeC);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            MockExecutionListener listener = new MockExecutionListener();
            DagExecutor executor =
                    new DagExecutor(graph, history, listener, ExecutionDirection.UP, 1);

            // When: C をターゲットに指定
            Set<NodeId> targets = executor.determineTargetNodes(NodeId.of("c"));

            // Then: A, B, C が対象
            assertThat(targets)
                    .containsExactlyInAnyOrder(NodeId.of("a"), NodeId.of("b"), NodeId.of("c"));
        }
    }

    @Nested
    @DisplayName("実行")
    class Execute {

        @Test
        @DisplayName("単一ノードを実行できる")
        void shouldExecuteSingleNode() {
            // Given
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createNode("a", Set.of());
            graph.addNode(nodeA);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            MockExecutionListener listener = new MockExecutionListener();
            DagExecutor executor =
                    new DagExecutor(graph, history, listener, ExecutionDirection.UP, 1);

            // When
            ExecutionResult result = executor.execute(Set.of(NodeId.of("a")));

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.summary().executedCount()).isEqualTo(1);
            assertThat(history.wasExecuted(NodeId.of("a"), testEnv.id())).isTrue();
            assertThat(listener.succeededNodes).containsExactly(NodeId.of("a"));
        }

        @Test
        @DisplayName("UP 成功はノードの fingerprint を履歴に残し、DOWN 成功は残さない")
        void shouldRecordFingerprintOnUpOnly() {
            // Given
            MigrationGraph upGraph = MigrationGraph.create();
            MigrationNode fingerprinted =
                    new FingerprintedNode(createNode("a", Set.of()), "abc123");
            upGraph.addNode(fingerprinted);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            new DagExecutor(upGraph, history, new MockExecutionListener(), ExecutionDirection.UP, 1)
                    .execute(Set.of(NodeId.of("a")));

            // Then
            ExecutionRecord afterUp = history.findLatestRecord(NodeId.of("a"), testEnv.id());
            assertThat(afterUp).isNotNull();
            assertThat(afterUp.fingerprint()).isEqualTo("abc123");

            // When rolled back
            MigrationGraph downGraph = MigrationGraph.create();
            downGraph.addNode(fingerprinted);
            new DagExecutor(
                            downGraph,
                            history,
                            new MockExecutionListener(),
                            ExecutionDirection.DOWN,
                            1)
                    .execute(Set.of(NodeId.of("a")));

            // Then
            ExecutionRecord afterDown = history.findLatestRecord(NodeId.of("a"), testEnv.id());
            assertThat(afterDown).isNotNull();
            assertThat(afterDown.direction()).isEqualTo(ExecutionDirection.DOWN);
            assertThat(afterDown.fingerprint()).isNull();
        }

        @Test
        @DisplayName("実行済みノードはスキップされる")
        void shouldSkipExecutedNodes() {
            // Given
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createNode("a", Set.of());
            MigrationNode nodeB = createNode("b", Set.of());
            graph.addNode(nodeA);
            graph.addNode(nodeB);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            // nodeA を実行済みとして記録
            history.record(
                    ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 100L));

            MockExecutionListener listener = new MockExecutionListener();
            DagExecutor executor =
                    new DagExecutor(graph, history, listener, ExecutionDirection.UP, 1);

            // When
            ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.summary().executedCount()).isEqualTo(1);
            assertThat(result.summary().skippedCount()).isEqualTo(1);
            assertThat(listener.skippedNodes).containsExactly(NodeId.of("a"));
        }

        @Test
        @DisplayName("リスナーに通知される")
        void shouldNotifyListener() {
            // Given
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createNode("a", Set.of());
            graph.addNode(nodeA);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            MockExecutionListener listener = new MockExecutionListener();
            DagExecutor executor =
                    new DagExecutor(graph, history, listener, ExecutionDirection.UP, 1);

            // When
            executor.execute(Set.of(NodeId.of("a")));

            // Then
            assertThat(listener.startedNodes).containsExactly(NodeId.of("a"));
            assertThat(listener.succeededNodes).containsExactly(NodeId.of("a"));
            assertThat(listener.completedCalled).isTrue();
        }

        @Test
        @DisplayName("fail-soft — 失敗ノードの推移的依存ノードは dependency failed reason でスキップされる")
        void shouldSkipTransitiveDependentsWithReasonOnFailure() {
            // Given: A -> B -> C, A が失敗
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createFailingNode("a", "boom", Set.of());
            MigrationNode nodeB = createNode("b", Set.of(NodeId.of("a")));
            MigrationNode nodeC = createNode("c", Set.of(NodeId.of("b")));
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            graph.addNode(nodeC);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            MockExecutionListener listener = new MockExecutionListener();
            DagExecutor executor =
                    new DagExecutor(graph, history, listener, ExecutionDirection.UP, 1);

            // When
            ExecutionResult result =
                    executor.execute(Set.of(NodeId.of("a"), NodeId.of("b"), NodeId.of("c")));

            // Then: B と C は "dependency failed: ..." reason でスキップ
            assertThat(result.success()).isFalse();
            assertThat(listener.failedNodes).containsExactly(NodeId.of("a"));
            assertThat(listener.succeededNodes).isEmpty();
            assertThat(listener.skippedNodes)
                    .containsExactlyInAnyOrder(NodeId.of("b"), NodeId.of("c"));
            assertThat(listener.skipReasons.get(NodeId.of("b"))).isEqualTo("dependency failed: a");
            // C が B 経由で skip された場合と A 経由で skip された場合の両方を許容する。
            // (推移的伝播の経路は内部実装の詳細であり、いずれでも fail-soft の意味は満たされる)
            assertThat(listener.skipReasons.get(NodeId.of("c")))
                    .isIn("dependency failed: a", "dependency failed: b");
        }

        @Test
        @DisplayName("fail-soft — 失敗ノードと依存圏外の独立ノードは引き続き実行される")
        void shouldContinueExecutingIndependentNodesAfterFailure() {
            // Given: A (failing) と B (independent, no deps)
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createFailingNode("a", "boom", Set.of());
            MigrationNode nodeB = createNode("b", Set.of());
            graph.addNode(nodeA);
            graph.addNode(nodeB);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            MockExecutionListener listener = new MockExecutionListener();
            DagExecutor executor =
                    new DagExecutor(graph, history, listener, ExecutionDirection.UP, 1);

            // When
            ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

            // Then: failure result だが B は完走する
            assertThat(result.success()).isFalse();
            assertThat(listener.failedNodes).containsExactly(NodeId.of("a"));
            assertThat(listener.succeededNodes).containsExactly(NodeId.of("b"));
            assertThat(history.wasExecuted(NodeId.of("b"), testEnv.id())).isTrue();
        }
    }

    /** Wraps a node so it reports a fingerprint, which {@link SimpleMigrationNode} never does. */
    private record FingerprintedNode(MigrationNode delegate, String fingerprint)
            implements MigrationNode {
        @Override
        public NodeId id() {
            return delegate.id();
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public @Nullable String description() {
            return delegate.description();
        }

        @Override
        public Environment environment() {
            return delegate.environment();
        }

        @Override
        public Set<NodeId> dependencies() {
            return delegate.dependencies();
        }

        @Override
        public Task upTask() {
            return delegate.upTask();
        }

        @Override
        public @Nullable Task downTask() {
            return delegate.downTask();
        }
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

    private enum NoopListener implements ExecutionListener {
        INSTANCE;

        @Override
        public void onPlanCreated(ExecutionPlanInfo plan) {}

        @Override
        public void onNodeStarted(MigrationNode node, ExecutionDirection direction) {}

        @Override
        public void onNodeSucceeded(
                MigrationNode node, ExecutionDirection direction, long durationMs) {}

        @Override
        public void onNodeSkipped(
                MigrationNode node, ExecutionDirection direction, String reason) {}

        @Override
        public void onNodeFailed(
                MigrationNode node,
                ExecutionDirection direction,
                @Nullable String sqlContent,
                String errorMessage) {}

        @Override
        public void onCompleted(ExecutionSummary summary) {}
    }
}
