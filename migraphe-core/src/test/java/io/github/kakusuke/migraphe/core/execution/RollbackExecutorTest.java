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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RollbackExecutor")
class RollbackExecutorTest {

    private MigrationGraph graph;
    private InMemoryHistoryRepository historyRepo;
    private MockExecutionListener listener;
    private RollbackExecutor executor;
    private Environment testEnv;

    @BeforeEach
    void setUp() {
        graph = MigrationGraph.create();
        historyRepo = new InMemoryHistoryRepository();
        listener = new MockExecutionListener();
        testEnv = SimpleEnvironment.create(EnvironmentId.of("test"), "Test Environment");
    }

    @Nested
    @DisplayName("ロールバック対象決定")
    class DetermineRollbackTargets {

        @Test
        @DisplayName("全ロールバック時は実行済みノードを返す")
        void shouldReturnAllExecutedNodesForAllRollback() {
            // Given
            MigrationNode nodeA = createNode("a", "Node A");
            MigrationNode nodeB = createNode("b", "Node B");
            graph.addNode(nodeA);
            graph.addNode(nodeB);

            // nodeA のみ実行済み
            historyRepo.record(
                    ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "Node A", null, 100L));

            executor = new RollbackExecutor(graph, historyRepo, listener);

            // When
            Set<NodeId> targets = executor.determineRollbackTargets(null, true);

            // Then
            assertThat(targets).containsExactly(NodeId.of("a"));
        }

        @Test
        @DisplayName("未実行ノードは返さない")
        void shouldNotReturnPendingNodes() {
            // Given
            MigrationNode nodeA = createNode("a", "Node A");
            graph.addNode(nodeA);
            // 実行履歴なし

            executor = new RollbackExecutor(graph, historyRepo, listener);

            // When
            Set<NodeId> targets = executor.determineRollbackTargets(null, true);

            // Then
            assertThat(targets).isEmpty();
        }

        @Test
        @DisplayName("ターゲット指定時、2-hop 先の推移的 dependent も含む (A → B → C で A 指定 → {A,B,C})")
        void shouldIncludeTransitiveDependentsForTargetedRollback() {
            // Given: A -> B -> C (chain)
            MigrationNode nodeA = createNode("a", "Node A");
            MigrationNode nodeB = createNode("b", "Node B", Set.of(NodeId.of("a")));
            MigrationNode nodeC = createNode("c", "Node C", Set.of(NodeId.of("b")));
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            graph.addNode(nodeC);

            // 全て実行済み
            historyRepo.record(
                    ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "Node A", null, 100L));
            historyRepo.record(
                    ExecutionRecord.upSuccess(NodeId.of("b"), testEnv.id(), "Node B", null, 100L));
            historyRepo.record(
                    ExecutionRecord.upSuccess(NodeId.of("c"), testEnv.id(), "Node C", null, 100L));

            executor = new RollbackExecutor(graph, historyRepo, listener);

            // When: A を指定
            Set<NodeId> targets = executor.determineRollbackTargets(NodeId.of("a"), false);

            // Then: A, B, C すべてを含む (C は B 経由の推移依存)
            assertThat(targets)
                    .containsExactlyInAnyOrder(NodeId.of("a"), NodeId.of("b"), NodeId.of("c"));
        }
    }

    @Nested
    @DisplayName("sample/cli DAG ロールバック対象決定 (推移的 dependents)")
    class SampleCliRollbackTargets {

        @BeforeEach
        void buildSampleCliGraph() {
            graph = MigrationGraph.create();
            historyRepo = new InMemoryHistoryRepository();
            listener = new MockExecutionListener();

            addSampleNode("mysql/01_common/001_currencies");
            addSampleNode("mysql/01_common/002_locales");
            addSampleNode("mysql/02_catalog/001_categories");
            addSampleNode("mysql/02_catalog/002_brands");
            addSampleNode(
                    "mysql/02_catalog/003_products",
                    "mysql/02_catalog/001_categories",
                    "mysql/02_catalog/002_brands");
            addSampleNode(
                    "mysql/02_catalog/004_variants",
                    "mysql/02_catalog/003_products",
                    "mysql/01_common/001_currencies");
            addSampleNode("mysql/02_catalog/005_images", "mysql/02_catalog/003_products");
            addSampleNode(
                    "pg/02_users/001_users",
                    "mysql/01_common/001_currencies",
                    "mysql/01_common/002_locales");
            addSampleNode(
                    "mysql/03_reviews/001_reviews",
                    "mysql/02_catalog/003_products",
                    "pg/02_users/001_users");
            addSampleNode(
                    "mysql/04_indexes/001_product_indexes",
                    "mysql/02_catalog/003_products",
                    "mysql/02_catalog/004_variants");
            addSampleNode("mysql/04_indexes/002_review_indexes", "mysql/03_reviews/001_reviews");
            addSampleNode("pg/02_users/002_profiles", "pg/02_users/001_users");
            addSampleNode("pg/02_users/003_addresses", "pg/02_users/001_users");
            addSampleNode(
                    "pg/05_orders/001_orders",
                    "pg/02_users/001_users",
                    "pg/02_users/003_addresses",
                    "mysql/01_common/001_currencies");
            addSampleNode(
                    "pg/05_orders/002_order_items",
                    "pg/05_orders/001_orders",
                    "mysql/02_catalog/004_variants");
            addSampleNode("pg/06_payments/001_payment_methods", "pg/02_users/001_users");
            addSampleNode(
                    "pg/06_payments/002_payments",
                    "pg/05_orders/001_orders",
                    "pg/06_payments/001_payment_methods");
            addSampleNode(
                    "pg/07_indexes/001_user_indexes",
                    "pg/02_users/001_users",
                    "pg/02_users/002_profiles");
            addSampleNode(
                    "pg/07_indexes/002_order_indexes",
                    "pg/05_orders/001_orders",
                    "pg/05_orders/002_order_items",
                    "pg/06_payments/002_payments");

            for (MigrationNode node : graph.allNodes()) {
                historyRepo.record(
                        ExecutionRecord.upSuccess(
                                node.id(), node.environment().id(), node.name(), null, 100L));
            }

            executor = new RollbackExecutor(graph, historyRepo, listener);
        }

        private void addSampleNode(String id, String... deps) {
            Set<NodeId> depSet = new HashSet<>();
            for (String d : deps) {
                depSet.add(NodeId.of(d));
            }
            graph.addNode(createNode(id, id, depSet));
        }

        @Test
        @DisplayName("変種 (mysql/02_catalog/004_variants) を指定: pg/07_indexes/002_order_indexes も含む")
        void shouldIncludeTransitiveDependentsForVariantsTarget() {
            Set<NodeId> rollback =
                    executor.determineRollbackTargets(
                            NodeId.of("mysql/02_catalog/004_variants"), false);

            assertThat(rollback)
                    .containsExactlyInAnyOrder(
                            NodeId.of("mysql/02_catalog/004_variants"),
                            NodeId.of("mysql/04_indexes/001_product_indexes"),
                            NodeId.of("pg/05_orders/002_order_items"),
                            NodeId.of("pg/07_indexes/002_order_indexes"));
        }

        @Test
        @DisplayName("各ノードを target に指定すると {target} ∪ getAllDependents(target) が返る")
        void shouldIncludeAllTransitiveDependentsForEveryNode() {
            for (MigrationNode node : graph.allNodes()) {
                NodeId target = node.id();
                Set<NodeId> expected = new HashSet<>();
                expected.add(target);
                expected.addAll(graph.getAllDependents(target));

                Set<NodeId> actual = executor.determineRollbackTargets(target, false);

                assertThat(actual)
                        .as(
                                "rollback set for %s must contain itself + all transitive"
                                        + " dependents",
                                target.value())
                        .containsExactlyInAnyOrderElementsOf(expected);
            }
        }
    }

    @Nested
    @DisplayName("ロールバック実行")
    class Execute {

        @Test
        @DisplayName("単一ノードをロールバックできる")
        void shouldRollbackSingleNode() {
            // Given
            MigrationNode nodeA = createNode("a", "Node A");
            graph.addNode(nodeA);

            // 実行済みとして記録
            historyRepo.record(
                    ExecutionRecord.upSuccess(
                            NodeId.of("a"), testEnv.id(), "Node A", "DROP TABLE;", 100L));

            executor = new RollbackExecutor(graph, historyRepo, listener);

            // When
            ExecutionResult result = executor.execute(Set.of(NodeId.of("a")));

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.summary().executedCount()).isEqualTo(1);
            assertThat(listener.succeededNodes).containsExactly(NodeId.of("a"));
        }

        @Test
        @DisplayName("依存順（逆順）でロールバックされる")
        void shouldRollbackInReverseOrder() {
            // Given: A -> B (ロールバックは B -> A の順)
            MigrationNode nodeA = createNode("a", "Node A");
            MigrationNode nodeB = createNode("b", "Node B", Set.of(NodeId.of("a")));
            graph.addNode(nodeA);
            graph.addNode(nodeB);

            // 両方実行済み
            historyRepo.record(
                    ExecutionRecord.upSuccess(
                            NodeId.of("a"), testEnv.id(), "Node A", "DROP A;", 100L));
            historyRepo.record(
                    ExecutionRecord.upSuccess(
                            NodeId.of("b"), testEnv.id(), "Node B", "DROP B;", 100L));

            executor = new RollbackExecutor(graph, historyRepo, listener);

            // When
            ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

            // Then
            assertThat(result.success()).isTrue();
            // B が先にロールバックされる（逆順）
            assertThat(listener.succeededNodes).containsExactly(NodeId.of("b"), NodeId.of("a"));
        }

        @Test
        @DisplayName("リスナーに通知される")
        void shouldNotifyListener() {
            // Given
            MigrationNode nodeA = createNode("a", "Node A");
            graph.addNode(nodeA);

            historyRepo.record(
                    ExecutionRecord.upSuccess(
                            NodeId.of("a"), testEnv.id(), "Node A", "DROP TABLE;", 100L));

            executor = new RollbackExecutor(graph, historyRepo, listener);

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
