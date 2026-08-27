package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.core.execution.support.FingerprintedNode;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AmendService")
class AmendServiceTest {

    private MigrationGraph graph;
    private InMemoryHistoryRepository historyRepo;
    private Environment testEnv;

    @BeforeEach
    void setUp() {
        graph = MigrationGraph.create();
        historyRepo = new InMemoryHistoryRepository();
        testEnv = SimpleEnvironment.create(EnvironmentId.of("test"), "Test Environment");
    }

    @Test
    @DisplayName("記録の無いノードは、その記録 id と現在の fingerprint を持つ項目になる")
    void shouldPlanAnEntryForANodeWithNoRecordedFingerprint() {
        // Given
        graph.addNode(new FingerprintedNode(createNode("stale", "Stale"), "abc"));
        graph.addNode(new FingerprintedNode(createNode("pending", "Pending"), "def"));
        ExecutionRecord applied =
                ExecutionRecord.upSuccess(NodeId.of("stale"), testEnv.id(), "Stale", null, 1L);
        historyRepo.record(applied);

        // When
        AmendService.AmendPlan plan = new AmendService(graph, historyRepo).plan();

        // Then
        assertThat(plan.toRecord()).hasSize(1);
        AmendService.AmendEntry entry = plan.toRecord().get(0);
        assertThat(entry.node().id()).isEqualTo(NodeId.of("stale"));
        assertThat(entry.recordId()).isEqualTo(applied.id());
        assertThat(entry.fingerprint()).isEqualTo("abc");
    }

    @Test
    @DisplayName("ドリフトの無いノードは対象にならない")
    void shouldPlanNothingForNodesWithoutDrift() {
        // Given
        graph.addNode(new FingerprintedNode(createNode("pending", "Pending"), "def"));
        graph.addNode(new FingerprintedNode(createNode("unchanged", "Unchanged"), "xyz"));
        graph.addNode(createNode("opt-out", "Opt out"));

        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("unchanged"), testEnv.id(), "Unchanged", null, 1L, "xyz"));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("opt-out"), testEnv.id(), "Opt out", null, 1L, "abc"));

        // When
        AmendService.AmendPlan plan = new AmendService(graph, historyRepo).plan();

        // Then
        assertThat(plan.toRecord()).isEmpty();
    }

    @Test
    @DisplayName("適用後に編集されたノードも対象になり、現在の内容の fingerprint を持つ")
    void shouldPlanAnEntryForAnEditedNode() {
        // Given
        graph.addNode(new FingerprintedNode(createNode("edited", "Edited"), "new"));
        ExecutionRecord applied =
                ExecutionRecord.upSuccess(
                        NodeId.of("edited"), testEnv.id(), "Edited", null, 1L, "old");
        historyRepo.record(applied);

        // When
        AmendService.AmendPlan plan = new AmendService(graph, historyRepo).plan();

        // Then
        assertThat(plan.toRecord()).hasSize(1);
        AmendService.AmendEntry entry = plan.toRecord().get(0);
        assertThat(entry.node().id()).isEqualTo(NodeId.of("edited"));
        assertThat(entry.recordId()).isEqualTo(applied.id());
        assertThat(entry.fingerprint()).isEqualTo("new");
    }

    private MigrationNode createNode(String id, String name) {
        Task upTask = SimpleTask.of("UP: " + name);
        Task downTask = SimpleTask.of("DOWN: " + name);
        return SimpleMigrationNode.builder()
                .id(NodeId.of(id))
                .name(name)
                .environment(testEnv)
                .dependencies(Set.of())
                .upTask(upTask)
                .downTask(downTask)
                .build();
    }
}
