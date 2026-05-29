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
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DagExecutor (DOWN / maxParallelism=1)")
class DagExecutorRollbackTest {

    private final Environment testEnv = SimpleEnvironment.create(EnvironmentId.of("env"), "env");

    @Test
    @DisplayName("A→B チェーンを逆順 (B→A) にロールバックし成功する")
    void shouldRollbackAbChainInReverseOrder() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createNode("a", Set.of());
        MigrationNode nodeB = createNode("b", Set.of(NodeId.of("a")));
        graph.addNode(nodeA);
        graph.addNode(nodeB);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 100L));
        history.record(ExecutionRecord.upSuccess(NodeId.of("b"), testEnv.id(), "b", null, 100L));

        MockExecutionListener listener = new MockExecutionListener();

        DagExecutor executor =
                new DagExecutor(graph, history, listener, ExecutionDirection.DOWN, 1);
        ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

        assertThat(result.success()).isTrue();
        assertThat(listener.succeededNodes).containsExactly(NodeId.of("b"), NodeId.of("a"));
        assertThat(listener.completedCalled).isTrue();
        assertThat(history.wasExecuted(NodeId.of("a"), testEnv.id())).isFalse();
        assertThat(history.wasExecuted(NodeId.of("b"), testEnv.id())).isFalse();
    }

    @Nested
    @DisplayName("ロールバック対象決定")
    class DetermineRollbackTargets {

        @Test
        @DisplayName("全ロールバック時は実行済みノードを返す")
        void shouldReturnAllExecutedNodesWhenAllMigrationsIsTrue() {
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createNode("a", Set.of());
            MigrationNode nodeB = createNode("b", Set.of(NodeId.of("a")));
            graph.addNode(nodeA);
            graph.addNode(nodeB);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            history.record(
                    ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 100L));

            MockExecutionListener listener = new MockExecutionListener();
            DagExecutor executor =
                    new DagExecutor(graph, history, listener, ExecutionDirection.DOWN, 1);

            Set<NodeId> targets = executor.determineRollbackTargets(null, true);

            assertThat(targets).containsExactly(NodeId.of("a"));
        }

        @Test
        @DisplayName("ターゲット指定時、B + B の推移的依存元のうち実行済みのみ返す")
        void shouldReturnTargetAndExecutedTransitiveDependentsWhenTargetVersionSpecified() {
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createNode("a", Set.of());
            MigrationNode nodeB = createNode("b", Set.of(NodeId.of("a")));
            MigrationNode nodeC = createNode("c", Set.of(NodeId.of("b")));
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            graph.addNode(nodeC);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            history.record(
                    ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 100L));
            history.record(
                    ExecutionRecord.upSuccess(NodeId.of("b"), testEnv.id(), "b", null, 100L));
            history.record(
                    ExecutionRecord.upSuccess(NodeId.of("c"), testEnv.id(), "c", null, 100L));

            MockExecutionListener listener = new MockExecutionListener();
            DagExecutor executor =
                    new DagExecutor(graph, history, listener, ExecutionDirection.DOWN, 1);

            Set<NodeId> targets = executor.determineRollbackTargets(NodeId.of("b"), false);

            assertThat(targets).containsExactlyInAnyOrder(NodeId.of("b"), NodeId.of("c"));
        }

        @Test
        @DisplayName("targetVersion=null, allMigrations=false のとき空セットを返す")
        void shouldReturnEmptySetWhenTargetVersionIsNullAndAllMigrationsIsFalse() {
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createNode("a", Set.of());
            graph.addNode(nodeA);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            history.record(
                    ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 100L));

            MockExecutionListener listener = new MockExecutionListener();
            DagExecutor executor =
                    new DagExecutor(graph, history, listener, ExecutionDirection.DOWN, 1);

            Set<NodeId> targets = executor.determineRollbackTargets(null, false);

            assertThat(targets).isEmpty();
        }
    }

    @Nested
    @DisplayName("sample/cli DAG ロールバック対象決定 (推移的 dependents)")
    class SampleCliRollbackTargets {

        private MigrationGraph graph;
        private InMemoryHistoryRepository historyRepo;
        private MockExecutionListener listener;
        private DagExecutor executor;

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

            executor = new DagExecutor(graph, historyRepo, listener, ExecutionDirection.DOWN, 1);
        }

        private void addSampleNode(String id, String... deps) {
            Set<NodeId> depSet = new HashSet<>();
            for (String d : deps) {
                depSet.add(NodeId.of(d));
            }
            graph.addNode(createNode(id, depSet));
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
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createNode("a", Set.of());
            graph.addNode(nodeA);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            // 実行済みとして記録
            history.record(
                    ExecutionRecord.upSuccess(
                            NodeId.of("a"), testEnv.id(), "a", "DROP TABLE;", 100L));

            MockExecutionListener listener = new MockExecutionListener();
            DagExecutor executor =
                    new DagExecutor(graph, history, listener, ExecutionDirection.DOWN, 1);

            // When
            ExecutionResult result = executor.execute(Set.of(NodeId.of("a")));

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.summary().executedCount()).isEqualTo(1);
            assertThat(listener.succeededNodes).containsExactly(NodeId.of("a"));
        }

        @Test
        @DisplayName("fail-soft — DOWN 失敗時に独立した実行済みノードは引き続き DOWN 実行される")
        void shouldContinueIndependentNodesAfterDownFailure() {
            // Given: A, B 独立 (UP では兄弟)。両方とも実行済み。B の DOWN が失敗。
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createNode("a", Set.of());
            MigrationNode nodeB = createNodeWithFailingDown("b", "boom", Set.of());
            graph.addNode(nodeA);
            graph.addNode(nodeB);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            history.record(
                    ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 100L));
            history.record(
                    ExecutionRecord.upSuccess(NodeId.of("b"), testEnv.id(), "b", null, 100L));

            MockExecutionListener listener = new MockExecutionListener();
            DagExecutor executor =
                    new DagExecutor(graph, history, listener, ExecutionDirection.DOWN, 1);

            // When
            ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

            // Then: failure result。B 失敗。A は DOWN 完走。
            assertThat(result.success()).isFalse();
            assertThat(listener.failedNodes).containsExactly(NodeId.of("b"));
            assertThat(listener.succeededNodes).containsExactly(NodeId.of("a"));
        }

        @Test
        @DisplayName("fail-soft — DOWN 失敗時に upstream (UP の親) は dep failed reason でスキップされる")
        void shouldSkipUpstreamOnDownFailure() {
            // Given: A -> B -> C (UP では C → B → A の順)、全て実行済み。
            // DOWN 順は C, B, A。B の DOWN が失敗 → A は skip (B の DOWN を待っていたため)。
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createNode("a", Set.of());
            MigrationNode nodeB = createNodeWithFailingDown("b", "boom", Set.of(NodeId.of("a")));
            MigrationNode nodeC = createNode("c", Set.of(NodeId.of("b")));
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            graph.addNode(nodeC);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            history.record(
                    ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 100L));
            history.record(
                    ExecutionRecord.upSuccess(NodeId.of("b"), testEnv.id(), "b", null, 100L));
            history.record(
                    ExecutionRecord.upSuccess(NodeId.of("c"), testEnv.id(), "c", null, 100L));

            MockExecutionListener listener = new MockExecutionListener();
            DagExecutor executor =
                    new DagExecutor(graph, history, listener, ExecutionDirection.DOWN, 1);

            // When
            ExecutionResult result =
                    executor.execute(Set.of(NodeId.of("a"), NodeId.of("b"), NodeId.of("c")));

            // Then
            assertThat(result.success()).isFalse();
            assertThat(listener.succeededNodes).containsExactly(NodeId.of("c"));
            assertThat(listener.failedNodes).containsExactly(NodeId.of("b"));
            assertThat(listener.skippedNodes).containsExactly(NodeId.of("a"));
            assertThat(listener.skipReasons.get(NodeId.of("a"))).isEqualTo("dependency failed: b");
        }

        @Test
        @DisplayName("リスナーに通知される")
        void shouldNotifyListener() {
            // Given
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createNode("a", Set.of());
            graph.addNode(nodeA);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            history.record(
                    ExecutionRecord.upSuccess(
                            NodeId.of("a"), testEnv.id(), "a", "DROP TABLE;", 100L));

            MockExecutionListener listener = new MockExecutionListener();
            DagExecutor executor =
                    new DagExecutor(graph, history, listener, ExecutionDirection.DOWN, 1);

            // When
            executor.execute(Set.of(NodeId.of("a")));

            // Then
            assertThat(listener.startedNodes).containsExactly(NodeId.of("a"));
            assertThat(listener.succeededNodes).containsExactly(NodeId.of("a"));
            assertThat(listener.completedCalled).isTrue();
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

    private MigrationNode createNodeWithFailingDown(
            String id, String error, Set<NodeId> dependencies) {
        Task upTask = SimpleTask.of("UP: " + id);
        Task downTask =
                new Task() {
                    @Override
                    public Result<TaskResult, String> execute() {
                        return Result.err(error);
                    }

                    @Override
                    public String description() {
                        return "FAIL DOWN: " + id;
                    }
                };
        return SimpleMigrationNode.builder()
                .id(NodeId.of(id))
                .name(id)
                .environment(testEnv)
                .dependencies(dependencies)
                .upTask(upTask)
                .downTask(downTask)
                .build();
    }
}
