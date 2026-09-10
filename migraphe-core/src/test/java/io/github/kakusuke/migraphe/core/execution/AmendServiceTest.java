package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionOrigin;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.ExecutionStatus;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.core.execution.support.DependencyEchoingNode;
import io.github.kakusuke.migraphe.core.execution.support.FingerprintedNode;
import io.github.kakusuke.migraphe.core.execution.support.PayloadProvidingTask;
import io.github.kakusuke.migraphe.core.execution.support.PayloadSilentTask;
import io.github.kakusuke.migraphe.core.execution.support.ThrowingFingerprintNode;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTarget;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AmendService")
class AmendServiceTest {

    private MigrationGraph graph;
    private InMemoryHistoryRepository historyRepo;
    private Target testTarget;

    @BeforeEach
    void setUp() {
        graph = MigrationGraph.create();
        historyRepo = new InMemoryHistoryRepository();
        testTarget = SimpleTarget.create(TargetId.of("test"), "Test Target");
    }

    @Test
    @DisplayName("fingerprint を記録していないノードは、現在の fingerprint を持つ項目になる")
    void shouldPlanAnEntryForANodeWithNoRecordedFingerprint() {
        // Given
        graph.addNode(new FingerprintedNode(createNode("stale", "Stale"), "abc"));
        graph.addNode(new FingerprintedNode(createNode("pending", "Pending"), "def"));
        ExecutionRecord applied =
                ExecutionRecord.upSuccess(NodeId.of("stale"), testTarget.id(), "Stale", null, 1L);
        historyRepo.record(applied);

        // When
        AmendService.AmendPlan plan = new AmendService(graph, historyRepo).plan(NodeId.of("stale"));

        // Then
        assertThat(plan.toRecord()).hasSize(1);
        AmendService.AmendEntry entry = plan.toRecord().get(0);
        assertThat(entry.node().id()).isEqualTo(NodeId.of("stale"));
        assertThat(entry.fingerprint()).isEqualTo("abc");
    }

    @Test
    @DisplayName("トークンを畳めないノードを名指したら、成功ではなく拒否として報告する")
    void refusesANamedNodeThatFoldsNoToken() {
        graph.addNode(new FingerprintedNode(createNode("no-token", "No token"), null));

        AmendService.AmendPlan plan =
                new AmendService(graph, historyRepo).plan(NodeId.of("no-token"));

        assertThat(plan.blocker()).isEqualTo(new AmendBlocker.NoFingerprint(NodeId.of("no-token")));
        assertThat(plan.toRecord()).isEmpty();
    }

    @Test
    @DisplayName("名指しなら、記録が既に一致していても追記する — ドリフトの有無は条件ではない")
    void amendsANamedNodeEvenWhenTheRecordAlreadyAgrees() {
        graph.addNode(new FingerprintedNode(createNode("unchanged", "Unchanged"), "xyz"));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("unchanged"), testTarget.id(), "Unchanged", null, 1L, "xyz"));

        AmendService.AmendPlan plan =
                new AmendService(graph, historyRepo).plan(NodeId.of("unchanged"));

        assertThat(plan.blocker()).isNull();
        assertThat(plan.toRecord())
                .extracting(entry -> entry.node().id())
                .containsExactly(NodeId.of("unchanged"));
    }

    @Test
    @DisplayName("適用後に編集されたノードも対象になり、現在の内容の fingerprint を持つ")
    void shouldPlanAnEntryForAnEditedNode() {
        // Given
        graph.addNode(new FingerprintedNode(createNode("edited", "Edited"), "new"));
        ExecutionRecord applied =
                ExecutionRecord.upSuccess(
                        NodeId.of("edited"), testTarget.id(), "Edited", null, 1L, "old");
        historyRepo.record(applied);

        // When
        AmendService.AmendPlan plan =
                new AmendService(graph, historyRepo).plan(NodeId.of("edited"));

        // Then
        assertThat(plan.toRecord()).hasSize(1);
        AmendService.AmendEntry entry = plan.toRecord().get(0);
        assertThat(entry.node().id()).isEqualTo(NodeId.of("edited"));
        assertThat(entry.fingerprint()).isEqualTo("new");
    }

    @Test
    @DisplayName("apply はプランの fingerprint を履歴に書き、書いた件数を返す")
    void shouldApplyPlannedFingerprints() {
        // Given
        graph.addNode(new FingerprintedNode(createNode("stale", "Stale"), "abc"));
        historyRepo.record(
                ExecutionRecord.upSuccess(NodeId.of("stale"), testTarget.id(), "Stale", null, 1L));

        AmendService service = new AmendService(graph, historyRepo);

        // When
        int written = service.apply(service.plan(NodeId.of("stale"))).recorded();

        // Then
        assertThat(written).isEqualTo(1);
        ExecutionRecord after = historyRepo.findLatestRecord(NodeId.of("stale"));
        assertThat(after).isNotNull();
        assertThat(after.fingerprint()).isEqualTo("abc");
    }

    @Test
    @DisplayName("amend は行を書き換えず、定義から組み立てた行を追記する")
    void shouldAppendARowBuiltFromTheDefinitionRatherThanRewritingOne() {
        // Given
        graph.addNode(new FingerprintedNode(createNode("stale", "Stale"), "abc"));
        ExecutionRecord applied =
                ExecutionRecord.upSuccess(NodeId.of("stale"), testTarget.id(), "Stale", null, 1L);
        historyRepo.record(applied);

        AmendService service = new AmendService(graph, historyRepo);

        // When
        assertThat(service.apply(service.plan(NodeId.of("stale"))).recorded()).isEqualTo(1);

        // Then
        List<ExecutionRecord> rows = historyRepo.allRecords();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).isEqualTo(applied);

        ExecutionRecord appended = rows.get(1);
        assertThat(appended.id()).isNotEqualTo(applied.id());
        assertThat(appended.origin()).isEqualTo(ExecutionOrigin.AMENDED);
        assertThat(appended.nodeId()).isEqualTo(NodeId.of("stale"));
        assertThat(appended.targetId()).isEqualTo(testTarget.id());
        assertThat(appended.direction()).isEqualTo(ExecutionDirection.UP);
        assertThat(appended.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(appended.fingerprint()).isEqualTo("abc");
    }

    @Test
    @DisplayName("fingerprint が読めないノードは、名指しても対象にならない")
    void shouldPlanNothingForANodeWhoseFingerprintCannotBeRead() {
        // Given
        graph.addNode(new ThrowingFingerprintNode(createNode("throwing", "Throwing")));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("throwing"), testTarget.id(), "Throwing", null, 1L));

        AmendService service = new AmendService(graph, historyRepo);

        // When / Then
        assertThat(service.plan(NodeId.of("throwing")).toRecord()).isEmpty();
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
                ExecutionRecord.upSuccess(NodeId.of("db1/002_b"), testTarget.id(), "B", null, 1L));

        // When
        AmendService.AmendPlan plan =
                new AmendService(graph, historyRepo).plan(NodeId.of("db1/002_b"));

        // Then
        assertThat(plan.toRecord())
                .singleElement()
                .extracting(AmendService.AmendEntry::fingerprint)
                .isEqualTo(graph.fingerprinterFor(NodeId.of("db1/002_b")).over("echoed"));
    }

    @Test
    @DisplayName("記録する依存は、グラフが宣言する直接の辺だけ")
    void shouldPlanTheDeclaredDirectDependenciesNotTheClosure() {
        // Given: 対象は 001_a から二段離れている
        graph.addNode(createNode("db1/001_a", "A"));
        graph.addNode(createNode("db1/900_z", "Z", Set.of(NodeId.of("db1/001_a"))));
        graph.addNode(
                new DependencyEchoingNode(
                        createNode("db1/002_b", "B", Set.of(NodeId.of("db1/900_z")))));
        historyRepo.record(
                ExecutionRecord.upSuccess(NodeId.of("db1/002_b"), testTarget.id(), "B", null, 1L));

        // When
        AmendService.AmendPlan plan =
                new AmendService(graph, historyRepo).plan(NodeId.of("db1/002_b"));

        // Then: 二段離れた 001_a は入らない——列は閉包ではなく辺を持つ
        assertThat(plan.toRecord())
                .singleElement()
                .extracting(AmendService.AmendEntry::dependencies)
                .isEqualTo(List.of(NodeId.of("db1/900_z")));
    }

    @Test
    @DisplayName("apply は、宣言された直接依存を履歴に渡す")
    void applyHandsTheRepositoryTheDeclaredDirectDependencies() {
        graph.addNode(createNode("db1/001_a", "A"));
        graph.addNode(createNode("db1/900_z", "Z", Set.of(NodeId.of("db1/001_a"))));
        graph.addNode(
                new DependencyEchoingNode(
                        createNode("db1/002_b", "B", Set.of(NodeId.of("db1/900_z")))));
        ExecutionRecord applied =
                ExecutionRecord.upSuccess(NodeId.of("db1/002_b"), testTarget.id(), "B", null, 1L);
        historyRepo.record(applied);

        AmendService service = new AmendService(graph, historyRepo);
        assertThat(service.apply(service.plan(NodeId.of("db1/002_b"))).recorded()).isOne();

        ExecutionRecord revised = historyRepo.findLatestRecord(NodeId.of("db1/002_b"));
        assertThat(revised).isNotNull();
        assertThat(revised.dependencies()).containsExactly(NodeId.of("db1/900_z"));
    }

    @Test
    @DisplayName("target: を張り替えた移行も選ばれ、追記される行は定義側の target を名指す")
    void appliesToAMigrationWhoseTargetMovedAndRecordsTheDeclaredTarget() {
        graph.addNode(new FingerprintedNode(createNode("db1/moved", "Moved"), "abc"));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/moved"), TargetId.of("old"), "Moved", null, 1L));

        AmendService service = new AmendService(graph, historyRepo);
        assertThat(service.apply(service.plan(NodeId.of("db1/moved"))).recorded()).isOne();

        ExecutionRecord revised = historyRepo.findLatestRecord(NodeId.of("db1/moved"));
        assertThat(revised).isNotNull();
        assertThat(revised.targetId()).isEqualTo(testTarget.id());
        assertThat(revised.fingerprint()).isEqualTo("abc");
        assertThat(revised.origin()).isEqualTo(ExecutionOrigin.AMENDED);
    }

    @Test
    @DisplayName("apply は、記録されたロールバックとプラグイン metadata も現在の定義に揃える")
    void applyBringsTheRecordedRollbackUpToTheCurrentDefinition() {
        MigrationNode node =
                SimpleMigrationNode.builder()
                        .id(NodeId.of("db1/002_b"))
                        .name("B")
                        .target(testTarget)
                        .upTask(
                                new PayloadProvidingTask(
                                        "DROP TABLE b_v2;", "autocommit.down=true\n"))
                        .downTask(SimpleTask.of("DOWN: B"))
                        .build();
        graph.addNode(new FingerprintedNode(node, "abc"));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_b"), testTarget.id(), "B", "DROP TABLE b_v1;", 1L));

        AmendService service = new AmendService(graph, historyRepo);
        assertThat(service.apply(service.plan(NodeId.of("db1/002_b"))).recorded()).isOne();

        ExecutionRecord revised = historyRepo.findLatestRecord(NodeId.of("db1/002_b"));
        assertThat(revised).isNotNull();
        assertThat(revised.serializedDownTask()).isEqualTo("DROP TABLE b_v2;");
        assertThat(revised.pluginMetadata()).isEqualTo("autocommit.down=true\n");
    }

    @Test
    @DisplayName("ロールバックを報告できない UP タスクのノードは、どちらの形でも断られる")
    void shouldRefuseANodeWhoseUpTaskCannotReportItsRollbackPayload() {
        graph.addNode(
                new FingerprintedNode(
                        SimpleMigrationNode.builder()
                                .id(NodeId.of("db1/002_b"))
                                .name("B")
                                .target(testTarget)
                                .upTask(new PayloadSilentTask("UP: B"))
                                .downTask(SimpleTask.of("DOWN: B"))
                                .build(),
                        "abc"));
        ExecutionRecord applied =
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_b"), testTarget.id(), "B", "DROP TABLE b_v1;", 1L);
        historyRepo.record(applied);

        AmendService service = new AmendService(graph, historyRepo);

        AmendBlocker expected =
                new AmendBlocker.CannotReportRollbackPayload(
                        NodeId.of("db1/002_b"), PayloadSilentTask.class.getName());

        AmendService.AmendPlan named = service.plan(NodeId.of("db1/002_b"));
        assertThat(named.blocker()).isEqualTo(expected);
        assertThat(named.toRecord()).isEmpty();

        assertThat(historyRepo.allRecords()).containsExactly(applied);
    }

    @Test
    @DisplayName("履歴が一度も記録していないノードも、指定すれば適用済みとして追記される")
    void shouldAppendAnAppliedRowForANodeTheHistoryHasNeverRecorded() {
        graph.addNode(new FingerprintedNode(createNode("bootstrap", "Bootstrap"), "abc"));

        AmendService service = new AmendService(graph, historyRepo);
        AmendService.AmendPlan plan = service.plan(NodeId.of("bootstrap"));

        assertThat(plan.blocker()).isNull();
        assertThat(plan.toRecord()).hasSize(1);
        assertThat(service.apply(plan).recorded()).isOne();

        assertThat(historyRepo.wasExecuted(NodeId.of("bootstrap"))).isTrue();
        List<ExecutionRecord> rows = historyRepo.allRecords();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).origin()).isEqualTo(ExecutionOrigin.AMENDED);
        assertThat(rows.get(0).fingerprint()).isEqualTo("abc");
    }

    @Test
    @DisplayName("孤立ノードを指定すると、もう適用されていないと述べる行が追記される")
    void shouldAppendAWithdrawalForAnOrphan() {
        graph.addNode(new FingerprintedNode(createNode("db1/003_c", "C"), "ccc"));
        ExecutionRecord applied =
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_removed"),
                        testTarget.id(),
                        "Add index",
                        "DROP INDEX idx",
                        1L);
        historyRepo.record(applied);

        AmendService service = new AmendService(graph, historyRepo);
        AmendService.AmendPlan plan = service.plan(NodeId.of("db1/002_removed"));

        assertThat(plan.blocker()).isNull();
        assertThat(plan.toWithdraw()).containsExactly(NodeId.of("db1/002_removed"));
        assertThat(service.apply(plan).withdrawn()).isOne();

        assertThat(historyRepo.wasExecuted(NodeId.of("db1/002_removed"))).isFalse();
        List<ExecutionRecord> rows = historyRepo.allRecords();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).direction()).isEqualTo(ExecutionDirection.DOWN);
        assertThat(rows.get(1).status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(rows.get(1).origin()).isEqualTo(ExecutionOrigin.AMENDED);
    }

    @Test
    @DisplayName("ノードを指定すると、そのノードだけが対象になる")
    void shouldPlanOnlyTheNamedNode() {
        // Given
        graph.addNode(new FingerprintedNode(createNode("a", "A"), "aaa"));
        graph.addNode(new FingerprintedNode(createNode("b", "B"), "bbb"));
        historyRepo.record(
                ExecutionRecord.upSuccess(NodeId.of("a"), testTarget.id(), "A", null, 1L));
        historyRepo.record(
                ExecutionRecord.upSuccess(NodeId.of("b"), testTarget.id(), "B", null, 1L));

        AmendService service = new AmendService(graph, historyRepo);

        // When / Then
        assertThat(service.plan(NodeId.of("a")).toRecord())
                .singleElement()
                .extracting(entry -> entry.node().id())
                .isEqualTo(NodeId.of("a"));
        assertThat(service.plan(NodeId.of("b")).toRecord())
                .singleElement()
                .extracting(entry -> entry.node().id())
                .isEqualTo(NodeId.of("b"));
    }

    @Test
    @DisplayName("行動できない id は断り、対象が無いだけの id は断らない")
    void shouldRefuseAnIdItCannotActOnButNotOneWithNothingToDo() {
        // Given: drifted / agreeing in the graph, and an orphan only in the history
        graph.addNode(new FingerprintedNode(createNode("drifted", "Drifted"), "abc"));
        graph.addNode(new FingerprintedNode(createNode("agreeing", "Agreeing"), "xyz"));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("drifted"), testTarget.id(), "Drifted", null, 1L));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("agreeing"), testTarget.id(), "Agreeing", null, 1L, "xyz"));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("orphan"), testTarget.id(), "Orphan", null, 1L, "old"));

        AmendService service = new AmendService(graph, historyRepo);

        // Then: an id that names nothing anywhere
        AmendService.AmendPlan unknown = service.plan(NodeId.of("no-such-migration"));
        assertThat(unknown.blocker())
                .isEqualTo(new AmendBlocker.NoSuchMigration(NodeId.of("no-such-migration")));
        assertThat(unknown.toRecord()).isEmpty();

        // Then: an id the history holds but the definitions no longer declare is withdrawn, not
        // refused — there is no definition to agree with, so the only claim left is "not applied"
        AmendService.AmendPlan orphan = service.plan(NodeId.of("orphan"));
        assertThat(orphan.blocker()).isNull();
        assertThat(orphan.toRecord()).isEmpty();
        assertThat(orphan.toWithdraw()).containsExactly(NodeId.of("orphan"));

        // Then: a node that is in the graph is never refused and always claimed, drifted or
        // not — what the definitions say is the same sentence either way
        AmendService.AmendPlan drifted = service.plan(NodeId.of("drifted"));
        assertThat(drifted.blocker()).isNull();
        assertThat(drifted.toRecord()).hasSize(1);

        AmendService.AmendPlan agreeing = service.plan(NodeId.of("agreeing"));
        assertThat(agreeing.blocker()).isNull();
        assertThat(agreeing.toRecord()).hasSize(1);
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
                .target(testTarget)
                .dependencies(dependencies)
                .upTask(upTask)
                .downTask(downTask)
                .build();
    }
}
