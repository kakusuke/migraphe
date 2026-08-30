package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.core.execution.support.AlwaysAppliedHistoryRepository;
import io.github.kakusuke.migraphe.core.execution.support.DependencyEchoingNode;
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

    @Test
    @DisplayName("apply はプランの fingerprint を履歴に書き、書いた件数を返す")
    void shouldApplyPlannedFingerprints() {
        // Given
        graph.addNode(new FingerprintedNode(createNode("stale", "Stale"), "abc"));
        historyRepo.record(
                ExecutionRecord.upSuccess(NodeId.of("stale"), testEnv.id(), "Stale", null, 1L));

        AmendService service = new AmendService(graph, historyRepo);

        // When
        int written = service.apply(service.plan());

        // Then
        assertThat(written).isEqualTo(1);
        ExecutionRecord after = historyRepo.findLatestRecord(NodeId.of("stale"), testEnv.id());
        assertThat(after).isNotNull();
        assertThat(after.fingerprint()).isEqualTo("abc");
    }

    @Test
    @DisplayName("成功した UP を記録していない行は対象にならない")
    void shouldNotPlanEntriesForRowsThatAreNotASuccessfulUp() {
        // Given
        graph.addNode(new FingerprintedNode(createNode("rolled-back", "Rolled back"), "abc"));
        historyRepo.record(
                ExecutionRecord.downSuccess(
                        NodeId.of("rolled-back"), testEnv.id(), "Rolled back", 1L));

        HistoryRepository lying = new AlwaysAppliedHistoryRepository(historyRepo);

        // When
        AmendService.AmendPlan plan = new AmendService(graph, lying).plan();

        // Then
        assertThat(plan.toRecord()).isEmpty();
    }

    @Test
    @DisplayName("fingerprint を書けないリポジトリでは、書くものがあるときだけ失敗する")
    void shouldRequireTheCapabilityOnlyWhenThereIsSomethingToWrite() {
        // Given: capability を持たないリポジトリと、ドリフトの無いグラフ
        HistoryRepository incapable = new AlwaysAppliedHistoryRepository(historyRepo);
        AmendService nothingToDo = new AmendService(graph, incapable);

        // When & Then: 書くものが無いので成功して0件
        assertThat(nothingToDo.apply(nothingToDo.plan())).isZero();

        // Given: ドリフトしたノードを1つ
        graph.addNode(new FingerprintedNode(createNode("stale", "Stale"), "abc"));
        historyRepo.record(
                ExecutionRecord.upSuccess(NodeId.of("stale"), testEnv.id(), "Stale", null, 1L));
        AmendService somethingToDo = new AmendService(graph, incapable);
        AmendService.AmendPlan plan = somethingToDo.plan();

        // When & Then: 書けないことを明示して停止する
        assertThat(plan.toRecord()).hasSize(1);
        assertThatThrownBy(() -> somethingToDo.apply(plan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot revise")
                .hasMessageContaining(AlwaysAppliedHistoryRepository.class.getName());
    }

    @Test
    @DisplayName("記録する fingerprint は、グラフから計算した推移的依存を渡して得たもの")
    void shouldPassTheTransitiveClosureToTheFingerprint() {
        // Given
        graph.addNode(createNode("db1/001_a", "A"));
        graph.addNode(createNode("db1/900_z", "Z", Set.of(NodeId.of("db1/001_a"))));
        graph.addNode(
                new DependencyEchoingNode(
                        createNode("db1/002_b", "B", Set.of(NodeId.of("db1/900_z")))));
        historyRepo.record(
                ExecutionRecord.upSuccess(NodeId.of("db1/002_b"), testEnv.id(), "B", null, 1L));

        // When
        AmendService.AmendPlan plan = new AmendService(graph, historyRepo).plan();

        // Then
        assertThat(plan.toRecord())
                .singleElement()
                .extracting(AmendService.AmendEntry::fingerprint)
                .isEqualTo("db1/001_a,db1/900_z");
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
