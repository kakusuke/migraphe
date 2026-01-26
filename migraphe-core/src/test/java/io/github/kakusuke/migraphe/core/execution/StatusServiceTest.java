package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.task.Task;
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
