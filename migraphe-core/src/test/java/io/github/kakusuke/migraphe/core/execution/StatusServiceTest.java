package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.core.execution.support.DependencyEchoingNode;
import io.github.kakusuke.migraphe.core.execution.support.FingerprintedNode;
import io.github.kakusuke.migraphe.core.execution.support.ThrowingFingerprintNode;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StatusService")
class StatusServiceTest {

    private MigrationGraph graph;
    private InMemoryHistoryRepository historyRepo;
    private StatusService statusService;
    private Environment testEnv;

    @BeforeEach
    void setUp() {
        graph = MigrationGraph.create();
        historyRepo = new InMemoryHistoryRepository();
        testEnv = SimpleEnvironment.create(EnvironmentId.of("test"), "Test Environment");
    }

    @Nested
    @DisplayName("ステータス取得")
    class GetStatus {

        @Test
        @DisplayName("空のグラフでステータスを取得できる")
        void shouldGetStatusForEmptyGraph() {
            // Given
            statusService = new StatusService(graph, historyRepo);

            // When
            StatusService.StatusInfo status = statusService.getStatus();

            // Then
            assertThat(status.nodes()).isEmpty();
            assertThat(status.executedCount()).isEqualTo(0);
            assertThat(status.pendingCount()).isEqualTo(0);
            assertThat(status.totalCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("未実行ノードのステータスを取得できる")
        void shouldGetStatusForPendingNodes() {
            // Given
            MigrationNode nodeA = createNode("a", "Node A");
            MigrationNode nodeB = createNode("b", "Node B");
            graph.addNode(nodeA);
            graph.addNode(nodeB);
            statusService = new StatusService(graph, historyRepo);

            // When
            StatusService.StatusInfo status = statusService.getStatus();

            // Then
            assertThat(status.nodes()).hasSize(2);
            assertThat(status.executedCount()).isEqualTo(0);
            assertThat(status.pendingCount()).isEqualTo(2);
            assertThat(status.nodes()).allMatch(ns -> !ns.executed());
        }

        @Test
        @DisplayName("実行済みノードのステータスを取得できる")
        void shouldGetStatusForExecutedNodes() {
            // Given
            MigrationNode nodeA = createNode("a", "Node A");
            MigrationNode nodeB = createNode("b", "Node B");
            graph.addNode(nodeA);
            graph.addNode(nodeB);

            // nodeA を実行済みとして記録
            ExecutionRecord record =
                    ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "Node A", null, 100L);
            historyRepo.record(record);

            statusService = new StatusService(graph, historyRepo);

            // When
            StatusService.StatusInfo status = statusService.getStatus();

            // Then
            assertThat(status.executedCount()).isEqualTo(1);
            assertThat(status.pendingCount()).isEqualTo(1);

            // nodeA は実行済み
            StatusService.NodeStatus nodeAStatus =
                    status.nodes().stream()
                            .filter(ns -> ns.node().id().equals(NodeId.of("a")))
                            .findFirst()
                            .orElseThrow();
            assertThat(nodeAStatus.executed()).isTrue();
            assertThat(nodeAStatus.latestRecord()).isNotNull();

            // nodeB は未実行
            StatusService.NodeStatus nodeBStatus =
                    status.nodes().stream()
                            .filter(ns -> ns.node().id().equals(NodeId.of("b")))
                            .findFirst()
                            .orElseThrow();
            assertThat(nodeBStatus.executed()).isFalse();
            assertThat(nodeBStatus.latestRecord()).isNull();
        }

        @Test
        @DisplayName("全ノード実行済みのステータスを取得できる")
        void shouldGetStatusWhenAllExecuted() {
            // Given
            MigrationNode nodeA = createNode("a", "Node A");
            graph.addNode(nodeA);

            ExecutionRecord record =
                    ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "Node A", null, 100L);
            historyRepo.record(record);

            statusService = new StatusService(graph, historyRepo);

            // When
            StatusService.StatusInfo status = statusService.getStatus();

            // Then
            assertThat(status.executedCount()).isEqualTo(1);
            assertThat(status.pendingCount()).isEqualTo(0);
            assertThat(status.totalCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("適用済みだが定義に無いノードを孤立として報告する")
    void shouldReportNodesThatAreAppliedButNoLongerDefined() {
        // Given: グラフには a だけ。履歴には a と b の適用記録がある
        graph.addNode(createNode("a", "Node A"));
        historyRepo.record(
                ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "Node A", null, 100L));
        historyRepo.record(
                ExecutionRecord.upSuccess(NodeId.of("b"), testEnv.id(), "Node B", null, 100L));

        statusService = new StatusService(graph, historyRepo);

        // When
        StatusService.StatusInfo status = statusService.getStatus();

        // Then
        assertThat(status.orphans())
                .extracting(StatusService.OrphanStatus::nodeId)
                .containsExactly(NodeId.of("b"));
    }

    @Test
    @DisplayName("直近の操作が失敗しても、内容の状態は適用した記録から判定する")
    void upContentStateComesFromTheRecordThatApplied() {
        // Given
        graph.addNode(new FingerprintedNode(createNode("a", "Node A"), "abc"));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("a"), testEnv.id(), "Node A", null, 100L, "abc"));
        historyRepo.record(
                ExecutionRecord.failure(
                        NodeId.of("a"),
                        testEnv.id(),
                        ExecutionDirection.DOWN,
                        "Node A",
                        "constraint violation"));

        statusService = new StatusService(graph, historyRepo);

        // When
        StatusService.StatusInfo status = statusService.getStatus();

        // Then
        assertThat(stateFor(status, "a")).isEqualTo(UpContentState.UNCHANGED);
    }

    @Test
    @DisplayName("UP 内容の状態は 対象外・不明・変更なし・変更あり・読めない を区別する")
    void upContentStateDistinguishesEveryCase() {
        // Given
        graph.addNode(new FingerprintedNode(createNode("pending", "Pending"), "abc"));
        graph.addNode(createNode("opt-out", "Opt out"));
        graph.addNode(new FingerprintedNode(createNode("unknown", "Unknown"), "abc"));
        graph.addNode(new FingerprintedNode(createNode("same", "Same"), "abc"));
        graph.addNode(new FingerprintedNode(createNode("edited", "Edited"), "abc"));
        graph.addNode(new ThrowingFingerprintNode(createNode("throwing", "Throwing")));
        graph.addNode(
                new ThrowingFingerprintNode(createNode("pending-throwing", "Pending throwing")));

        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("opt-out"), testEnv.id(), "Opt out", null, 1L, "abc"));
        historyRepo.record(
                ExecutionRecord.upSuccess(NodeId.of("unknown"), testEnv.id(), "Unknown", null, 1L));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("same"), testEnv.id(), "Same", null, 1L, "abc"));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("edited"), testEnv.id(), "Edited", null, 1L, "xyz"));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("throwing"), testEnv.id(), "Throwing", null, 1L, "abc"));

        statusService = new StatusService(graph, historyRepo);

        // When
        StatusService.StatusInfo status = statusService.getStatus();

        // Then
        assertThat(stateFor(status, "pending")).isEqualTo(UpContentState.NOT_APPLICABLE);
        assertThat(stateFor(status, "opt-out")).isEqualTo(UpContentState.NOT_APPLICABLE);
        assertThat(stateFor(status, "unknown")).isEqualTo(UpContentState.UNKNOWN);
        assertThat(stateFor(status, "same")).isEqualTo(UpContentState.UNCHANGED);
        assertThat(stateFor(status, "edited")).isEqualTo(UpContentState.CHANGED);
        assertThat(stateFor(status, "throwing")).isEqualTo(UpContentState.UNREADABLE);
        assertThat(stateFor(status, "pending-throwing")).isEqualTo(UpContentState.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("内容の比較には、グラフから計算した推移的依存が渡される")
    void upContentStateComparesAgainstTheTokenTheClosureProduces() {
        // Given
        graph.addNode(createNode("db1/000_base", "Base"));
        graph.addNode(createNode("db1/001_a", "Node A", Set.of(NodeId.of("db1/000_base"))));
        graph.addNode(
                new DependencyEchoingNode(
                        createNode("db1/002_b", "Node B", Set.of(NodeId.of("db1/001_a")))));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_b"),
                        testEnv.id(),
                        "Node B",
                        null,
                        1L,
                        "db1/000_base,db1/001_a"));

        statusService = new StatusService(graph, historyRepo);

        // When
        StatusService.StatusInfo status = statusService.getStatus();

        // Then
        assertThat(stateFor(status, "db1/002_b")).isEqualTo(UpContentState.UNCHANGED);
    }

    private UpContentState stateFor(StatusService.StatusInfo status, String nodeId) {
        return status.nodes().stream()
                .filter(ns -> ns.node().id().equals(NodeId.of(nodeId)))
                .findFirst()
                .orElseThrow()
                .upContentState();
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
}
