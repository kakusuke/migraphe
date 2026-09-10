package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.target.DownTaskRestorer;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import io.github.kakusuke.migraphe.core.execution.support.MockExecutionListener;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTarget;
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

@DisplayName("DagExecutor (DOWN / maxParallelism=1)")
class DagExecutorRollbackTest {

    private final Target testTarget = SimpleTarget.create(TargetId.of("env"), "env");

    @Test
    @DisplayName("環境が復元できるなら、DOWN は現在の定義ではなく履歴が記録した SQL を実行する")
    void shouldRunTheRollbackTheHistoryRecordedRatherThanTheCurrentDefinition() {
        List<String> executed = new ArrayList<>();
        RestoringTarget target = new RestoringTarget(executed);

        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(
                SimpleMigrationNode.builder()
                        .id(NodeId.of("a"))
                        .name("a")
                        .target(target)
                        .dependencies(Set.of())
                        .upTask(SimpleTask.of("UP: a"))
                        .downTask(recordingTask("the edited definition", executed))
                        .build());

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("a"),
                        target.id(),
                        "a",
                        "what was recorded",
                        100L,
                        "fp",
                        "autocommit.down=true\n"));

        ExecutionResult result =
                new DagExecutor(
                                graph,
                                history,
                                new MockExecutionListener(),
                                ExecutionDirection.DOWN,
                                1)
                        .execute(Set.of(NodeId.of("a")));

        assertThat(result.success()).isTrue();
        assertThat(executed).containsExactly("restored:what was recorded/autocommit.down=true\n");
        assertThat(history.wasExecuted(NodeId.of("a"))).isFalse();
    }

    @Test
    @DisplayName("ロールバックが一度失敗しても、記録された SQL は隠れない — 適用した行から読む")
    void shouldStillFindTheRecordedRollbackAfterAFailedAttempt() {
        List<String> executed = new ArrayList<>();
        RestoringTarget target = new RestoringTarget(executed);

        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(
                SimpleMigrationNode.builder()
                        .id(NodeId.of("a"))
                        .name("a")
                        .target(target)
                        .dependencies(Set.of())
                        .upTask(SimpleTask.of("UP: a"))
                        .downTask(recordingTask("the edited definition", executed))
                        .build());

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("a"),
                        target.id(),
                        "a",
                        "what was recorded",
                        100L,
                        "fp",
                        "autocommit.down=true\n"));
        // A failed rollback is now the newest row, and a failure carries no payload.
        history.record(
                ExecutionRecord.failure(
                        NodeId.of("a"), target.id(), ExecutionDirection.DOWN, "a", "boom"));

        ExecutionResult result =
                new DagExecutor(
                                graph,
                                history,
                                new MockExecutionListener(),
                                ExecutionDirection.DOWN,
                                1)
                        .execute(Set.of(NodeId.of("a")));

        assertThat(result.success()).isTrue();
        assertThat(executed).containsExactly("restored:what was recorded/autocommit.down=true\n");
    }

    @Test
    @DisplayName("記録に payload が無ければ定義で代用せず止まり、3つの理由を言い分ける")
    void shouldRefuseRatherThanSubstituteTheDefinitionWhenTheHistoryRecordedNoRollback() {
        List<String> executed = new ArrayList<>();
        RestoringTarget target = new RestoringTarget(executed);

        MigrationGraph graph = MigrationGraph.create();
        for (String id : List.of("declared-one-way", "no-reason", "no-fingerprint")) {
            graph.addNode(
                    SimpleMigrationNode.builder()
                            .id(NodeId.of(id))
                            .name(id)
                            .target(target)
                            .dependencies(Set.of())
                            .upTask(SimpleTask.of("UP: " + id))
                            .downTask(recordingTask("definition:" + id, executed))
                            .build());
        }

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("declared-one-way"),
                        target.id(),
                        "declared-one-way",
                        null,
                        1L,
                        "fp",
                        null,
                        List.of(),
                        "the rows cannot be reconstructed"));
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("no-reason"),
                        target.id(),
                        "no-reason",
                        null,
                        1L,
                        "fp",
                        null,
                        List.of(),
                        null));
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("no-fingerprint"),
                        target.id(),
                        "no-fingerprint",
                        null,
                        1L,
                        null,
                        null,
                        List.of(),
                        null));

        MockExecutionListener listener = new MockExecutionListener();
        ExecutionResult result =
                new DagExecutor(graph, history, listener, ExecutionDirection.DOWN, 1)
                        .execute(
                                Set.of(
                                        NodeId.of("declared-one-way"),
                                        NodeId.of("no-reason"),
                                        NodeId.of("no-fingerprint")));

        assertThat(result.success()).isFalse();
        assertThat(executed).isEmpty();
        assertThat(listener.failureMessages.get(NodeId.of("declared-one-way")))
                .contains("one-way")
                .contains("the rows cannot be reconstructed");
        assertThat(listener.failureMessages.get(NodeId.of("no-reason")))
                .contains("no rollback")
                .contains("no reason");
        assertThat(listener.failureMessages.get(NodeId.of("no-fingerprint")))
                .contains("migraphe amend");
        // A refusal is not an attempt: nothing is written, so upgrade still reaches
        // them.
        assertThat(history.allRecords())
                .allSatisfy(
                        record -> assertThat(record.direction()).isEqualTo(ExecutionDirection.UP));
    }

    @Test
    @DisplayName("payload から復元できないターゲットでも、記録が無ければ定義で代用しない")
    void shouldRefuseEvenWhenTheTargetCannotRestoreFromAPayload() {
        List<String> executed = new ArrayList<>();

        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(
                SimpleMigrationNode.builder()
                        .id(NodeId.of("a"))
                        .name("a")
                        // Not a DownTaskRestorer: it would have run the definition's task.
                        .target(testTarget)
                        .dependencies(Set.of())
                        .upTask(SimpleTask.of("UP: a"))
                        .downTask(recordingTask("the edited definition", executed))
                        .build());

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("a"),
                        testTarget.id(),
                        "a",
                        null,
                        1L,
                        "fp",
                        null,
                        List.of(),
                        null));

        MockExecutionListener listener = new MockExecutionListener();
        ExecutionResult result =
                new DagExecutor(graph, history, listener, ExecutionDirection.DOWN, 1)
                        .execute(Set.of(NodeId.of("a")));

        assertThat(result.success()).isFalse();
        assertThat(executed).isEmpty();
        assertThat(listener.failureMessages.get(NodeId.of("a"))).contains("no rollback");
    }

    /** A task that records the fact it ran, so the test can say which one the executor chose. */
    /**
     * Records the row a real apply of this node writes, edges included.
     *
     * <p>A rollback is ordered by the recorded edges, so a fixture that records none describes
     * migrations that stood on nothing — and then measures an order no run would take.
     */
    private void applied(
            InMemoryHistoryRepository history, MigrationNode node, @Nullable String downPayload) {
        history.record(
                ExecutionRecord.upSuccess(
                        node.id(),
                        node.target().id(),
                        node.name(),
                        downPayload,
                        100L,
                        "fp",
                        null,
                        List.copyOf(node.dependencies())));
    }

    private static Task recordingTask(String label, List<String> executed) {
        return new Task() {
            @Override
            public Result<TaskResult, String> execute() {
                executed.add(label);
                return Result.ok(TaskResult.withoutDownTask(label));
            }

            @Override
            public String description() {
                return label;
            }

            @Override
            public String signature() {

                return label;
            }
        };
    }

    /** A target whose rebuilt rollback is whatever task the test supplied. */
    private record FixedRestoringTarget(TargetId id, String name, Task restored)
            implements Target, DownTaskRestorer {

        @Override
        public Task restoreDownTask(String serializedDownTask, @Nullable String pluginMetadata) {
            return restored;
        }
    }

    /** An target that can rebuild a rollback from what the history kept. */
    private static final class RestoringTarget implements Target, DownTaskRestorer {
        private final List<String> executed;

        RestoringTarget(List<String> executed) {
            this.executed = executed;
        }

        @Override
        public TargetId id() {
            return TargetId.of("env");
        }

        @Override
        public String name() {
            return "env";
        }

        @Override
        public Task restoreDownTask(String serializedDownTask, @Nullable String pluginMetadata) {
            return recordingTask("restored:" + serializedDownTask + "/" + pluginMetadata, executed);
        }
    }

    @Test
    @DisplayName("A→B チェーンを逆順 (B→A) にロールバックし成功する")
    void shouldRollbackAbChainInReverseOrder() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode nodeA = createNode("a", Set.of());
        MigrationNode nodeB = createNode("b", Set.of(NodeId.of("a")));
        graph.addNode(nodeA);
        graph.addNode(nodeB);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("a"), testTarget.id(), "a", "DOWN: a", 100L, "fp"));
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("b"), testTarget.id(), "b", "DOWN: b", 100L, "fp"));

        MockExecutionListener listener = new MockExecutionListener();

        DagExecutor executor =
                new DagExecutor(graph, history, listener, ExecutionDirection.DOWN, 1);
        ExecutionResult result = executor.execute(Set.of(NodeId.of("a"), NodeId.of("b")));

        assertThat(result.success()).isTrue();
        assertThat(listener.succeededNodes).containsExactly(NodeId.of("b"), NodeId.of("a"));
        assertThat(listener.completedCalled).isTrue();
        assertThat(history.wasExecuted(NodeId.of("a"))).isFalse();
        assertThat(history.wasExecuted(NodeId.of("b"))).isFalse();
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
                    ExecutionRecord.upSuccess(
                            NodeId.of("a"), testTarget.id(), "a", "DOWN: a", 100L, "fp"));

            Set<NodeId> targets =
                    new DownService(graph, history)
                            .plan(null, true, List.of(testTarget))
                            .selectedNodes();

            assertThat(targets).containsExactly(NodeId.of("a"));
        }

        @Test
        @DisplayName("落とせないノードと、それが依存するものは対象に入らない")
        void shouldExcludeWhatCannotBeRolledBackAndWhatItStandsOn() {
            MigrationGraph graph = MigrationGraph.create();
            graph.addNode(createNode("a", Set.of()));
            graph.addNode(createNodeWithoutDown("b", Set.of(NodeId.of("a"))));

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            history.record(
                    ExecutionRecord.upSuccess(
                            NodeId.of("a"), testTarget.id(), "a", "DOWN: a", 100L, "fp"));
            // b declares no rollback, so its apply recorded no payload — what up writes.
            history.record(
                    ExecutionRecord.upSuccess(
                            NodeId.of("b"), testTarget.id(), "b", null, 100L, "fp"));

            assertThat(
                            new DownService(graph, history)
                                    .plan(null, true, List.of(testTarget))
                                    .selectedNodes())
                    .isEmpty();
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
            applied(history, nodeA, "DOWN: a");
            applied(history, nodeB, "DOWN: b");
            applied(history, nodeC, "DOWN: c");

            Set<NodeId> targets =
                    new DownService(graph, history)
                            .plan(NodeId.of("b"), false, List.of(testTarget))
                            .selectedNodes();

            assertThat(targets).containsExactlyInAnyOrder(NodeId.of("b"), NodeId.of("c"));
        }

        @Test
        @DisplayName("requestedNode=null, allMigrations=false のとき空セットを返す")
        void shouldReturnEmptySetWhenTargetVersionIsNullAndAllMigrationsIsFalse() {
            MigrationGraph graph = MigrationGraph.create();
            MigrationNode nodeA = createNode("a", Set.of());
            graph.addNode(nodeA);

            InMemoryHistoryRepository history = new InMemoryHistoryRepository();
            history.record(
                    ExecutionRecord.upSuccess(
                            NodeId.of("a"), testTarget.id(), "a", "DOWN: a", 100L, "fp"));

            Set<NodeId> targets =
                    new DownService(graph, history)
                            .plan(null, false, List.of(testTarget))
                            .selectedNodes();

            assertThat(targets).isEmpty();
        }
    }

    @Nested
    @DisplayName("sample/cli DAG ロールバック対象決定 (推移的 dependents)")
    class SampleCliRollbackTargets {

        private MigrationGraph graph;
        private InMemoryHistoryRepository historyRepo;

        @BeforeEach
        void buildSampleCliGraph() {
            graph = MigrationGraph.create();
            historyRepo = new InMemoryHistoryRepository();

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
                applied(historyRepo, node, "DOWN: " + node.id().value());
            }
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
                    new DownService(graph, historyRepo)
                            .plan(
                                    NodeId.of("mysql/02_catalog/004_variants"),
                                    false,
                                    List.of(testTarget))
                            .selectedNodes();

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

                Set<NodeId> actual =
                        new DownService(graph, historyRepo)
                                .plan(target, false, List.of(testTarget))
                                .selectedNodes();

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
                            NodeId.of("a"), testTarget.id(), "a", "DROP TABLE;", 100L, "fp"));

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
                    ExecutionRecord.upSuccess(
                            NodeId.of("a"), testTarget.id(), "a", "DOWN: a", 100L, "fp"));
            history.record(
                    ExecutionRecord.upSuccess(
                            NodeId.of("b"), testTarget.id(), "b", "DOWN: b", 100L, "fp"));

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
            applied(history, nodeA, "DOWN: a");
            applied(history, nodeB, "DOWN: b");
            applied(history, nodeC, "DOWN: c");

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
                            NodeId.of("a"), testTarget.id(), "a", "DROP TABLE;", 100L, "fp"));

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

    @Test
    @DisplayName("行の payload を target が再構築できないなら、定義の down: を代入せず拒否する")
    void refusesWhenTheTargetCannotRebuildTheRecordedRollback() {
        Target plain =
                new Target() {
                    @Override
                    public TargetId id() {
                        return TargetId.of("plain");
                    }

                    @Override
                    public String name() {
                        return "plain";
                    }
                };

        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(
                SimpleMigrationNode.builder()
                        .id(NodeId.of("a"))
                        .name("a")
                        .target(plain)
                        .dependencies(Set.of())
                        .upTask(SimpleTask.of("UP: a"))
                        .downTask(SimpleTask.of("DOWN: a"))
                        .build());

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("a"), plain.id(), "a", "DROP TABLE a_old", 100L, "fp"));

        MockExecutionListener listener = new MockExecutionListener();
        ExecutionResult result =
                new DagExecutor(graph, history, listener, ExecutionDirection.DOWN, 1)
                        .execute(Set.of(NodeId.of("a")));

        assertThat(result.success()).isFalse();
        assertThat(listener.failedNodes).containsExactly(NodeId.of("a"));
        assertThat(listener.succeededNodes).isEmpty();
        assertThat(listener.failureMessages.get(NodeId.of("a")))
                .isEqualTo(
                        "a: the history recorded a rollback for it, but the target plain cannot"
                                + " rebuild one — only the plugin that wrote the payload can read"
                                + " it back.");
    }

    @Test
    @DisplayName("fingerprint の無い行は、payload があっても実行側で拒否される")
    void refusesToRunARollbackFromARowThatCarriesNoFingerprint() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(createNode("a", Set.of()));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("a"), testTarget.id(), "a", "DOWN: a", 100L, null));

        MockExecutionListener listener = new MockExecutionListener();
        ExecutionResult result =
                new DagExecutor(graph, history, listener, ExecutionDirection.DOWN, 1)
                        .execute(Set.of(NodeId.of("a")));

        assertThat(result.success()).isFalse();
        assertThat(listener.succeededNodes).isEmpty();
        assertThat(listener.failureMessages.get(NodeId.of("a")))
                .isEqualTo(
                        "a: the row that applied it carries no fingerprint, so what it recorded"
                                + " cannot be read at face value; run 'migraphe amend a'");
    }

    private MigrationNode createNode(String id, Set<NodeId> dependencies) {
        // The up task reports the rollback it declares: that is what a real apply records, and a
        // row without it is one up now refuses to create.
        Task upTask = SimpleTask.withDownTask("UP: " + id, "DOWN: " + id);
        Task downTask = SimpleTask.of("DOWN: " + id);
        return SimpleMigrationNode.builder()
                .id(NodeId.of(id))
                .name(id)
                .target(testTarget)
                .dependencies(dependencies)
                .upTask(upTask)
                .downTask(downTask)
                .build();
    }

    private MigrationNode createNodeWithoutDown(String id, Set<NodeId> dependencies) {
        return SimpleMigrationNode.builder()
                .id(NodeId.of(id))
                .name(id)
                .target(testTarget)
                .dependencies(dependencies)
                .upTask(SimpleTask.of("UP: " + id))
                .build();
    }

    /**
     * A node whose <em>recorded</em> rollback fails when it runs.
     *
     * <p>The failure has to come from the task the target rebuilds, not from the definition's
     * {@code down:}: a rollback runs what the history kept, and nothing else, so a definition-side
     * failure would never be reached.
     */
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

                    @Override
                    public String signature() {

                        return "FAIL DOWN: " + id;
                    }
                };
        return SimpleMigrationNode.builder()
                .id(NodeId.of(id))
                .name(id)
                .target(new FixedRestoringTarget(testTarget.id(), testTarget.name(), downTask))
                .dependencies(dependencies)
                .upTask(upTask)
                .downTask(downTask)
                .build();
    }
}
