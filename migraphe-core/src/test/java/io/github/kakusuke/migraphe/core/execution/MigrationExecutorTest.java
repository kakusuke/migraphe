package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MigrationExecutor")
class MigrationExecutorTest {

    private MigrationGraph graph;
    private InMemoryHistoryRepository historyRepo;
    private MockExecutionListener listener;
    private MigrationExecutor executor;
    private Environment testEnv;

    @BeforeEach
    void setUp() {
        graph = MigrationGraph.create();
        historyRepo = new InMemoryHistoryRepository();
        listener = new MockExecutionListener();
        testEnv = SimpleEnvironment.create(EnvironmentId.of("test"), "Test Environment");
    }

    @Nested
    @DisplayName("対象ノード決定")
    class DetermineTargetNodes {

        @Test
        @DisplayName("ターゲット指定なしで全未実行ノードを返す")
        void shouldReturnAllPendingNodesWhenNoTarget() {
            // Given
            MigrationNode nodeA = createNode("a", "Node A");
            MigrationNode nodeB = createNode("b", "Node B");
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            executor = new MigrationExecutor(graph, historyRepo, listener);

            // When
            Set<NodeId> targets = executor.determineTargetNodes(null);

            // Then
            assertThat(targets).containsExactlyInAnyOrder(NodeId.of("a"), NodeId.of("b"));
        }

        @Test
        @DisplayName("実行済みノードは除外される")
        void shouldExcludeExecutedNodes() {
            // Given
            MigrationNode nodeA = createNode("a", "Node A");
            MigrationNode nodeB = createNode("b", "Node B");
            graph.addNode(nodeA);
            graph.addNode(nodeB);

            // nodeA を実行済みとして記録
            historyRepo.record(
                    ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "Node A", null, 100L));

            executor = new MigrationExecutor(graph, historyRepo, listener);

            // When
            Set<NodeId> targets = executor.determineTargetNodes(null);

            // Then
            assertThat(targets).containsExactly(NodeId.of("b"));
        }

        @Test
        @DisplayName("ターゲット指定でターゲットと依存先を返す")
        void shouldReturnTargetAndDependencies() {
            // Given: A -> B -> C
            MigrationNode nodeA = createNode("a", "Node A");
            MigrationNode nodeB = createNode("b", "Node B", Set.of(NodeId.of("a")));
            MigrationNode nodeC = createNode("c", "Node C", Set.of(NodeId.of("b")));
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            graph.addNode(nodeC);
            executor = new MigrationExecutor(graph, historyRepo, listener);

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
            MigrationNode nodeA = createNode("a", "Node A");
            graph.addNode(nodeA);
            executor = new MigrationExecutor(graph, historyRepo, listener);

            // When
            ExecutionResult result = executor.execute(Set.of(NodeId.of("a")));

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.summary().executedCount()).isEqualTo(1);
            assertThat(historyRepo.wasExecuted(NodeId.of("a"), testEnv.id())).isTrue();
            assertThat(listener.succeededNodes).containsExactly(NodeId.of("a"));
        }

        @Test
        @DisplayName("複数ノードを依存順に実行できる")
        void shouldExecuteNodesInOrder() {
            // Given: A -> B
            MigrationNode nodeA = createNode("a", "Node A");
            MigrationNode nodeB = createNode("b", "Node B", Set.of(NodeId.of("a")));
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            executor = new MigrationExecutor(graph, historyRepo, listener);

            // When
            ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.summary().executedCount()).isEqualTo(2);
            assertThat(listener.succeededNodes)
                    .containsExactly(NodeId.of("a"), NodeId.of("b")); // 順序を確認
        }

        @Test
        @DisplayName("実行済みノードはスキップされる")
        void shouldSkipExecutedNodes() {
            // Given
            MigrationNode nodeA = createNode("a", "Node A");
            MigrationNode nodeB = createNode("b", "Node B");
            graph.addNode(nodeA);
            graph.addNode(nodeB);

            // nodeA を実行済みとして記録
            historyRepo.record(
                    ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "Node A", null, 100L));

            executor = new MigrationExecutor(graph, historyRepo, listener);

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
            MigrationNode nodeA = createNode("a", "Node A");
            graph.addNode(nodeA);
            executor = new MigrationExecutor(graph, historyRepo, listener);

            // When
            executor.execute(Set.of(NodeId.of("a")));

            // Then
            assertThat(listener.startedNodes).containsExactly(NodeId.of("a"));
            assertThat(listener.succeededNodes).containsExactly(NodeId.of("a"));
            assertThat(listener.completedCalled).isTrue();
        }
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

    /** テスト用の ExecutionListener 実装 */
    static class MockExecutionListener implements ExecutionListener {
        final List<NodeId> startedNodes = new ArrayList<>();
        final List<NodeId> succeededNodes = new ArrayList<>();
        final List<NodeId> skippedNodes = new ArrayList<>();
        final List<NodeId> failedNodes = new ArrayList<>();
        boolean completedCalled = false;

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
}
