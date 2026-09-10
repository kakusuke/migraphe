package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.core.execution.support.FingerprintedNode;
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

@DisplayName("RebuildService")
class RebuildServiceTest {

    private MigrationGraph graph;
    private InMemoryHistoryRepository historyRepo;
    private Target testTarget;

    @BeforeEach
    void setUp() {
        graph = MigrationGraph.create();
        historyRepo = new InMemoryHistoryRepository();
        testTarget = SimpleTarget.create(TargetId.of("test"), "Test");
    }

    @Test
    @DisplayName("差分集合は、適用済みで内容が定義とずれたノードだけ")
    void theDifferenceSetIsTheAppliedNodesWhoseContentNoLongerMatches() {
        // 適用時と同じ内容
        graph.addNode(fingerprinted("db1/001_agrees", "token-a"));
        appliedWith("db1/001_agrees", "token-a");

        // 適用後に編集された
        graph.addNode(fingerprinted("db1/002_edited", "token-now"));
        appliedWith("db1/002_edited", "token-when-applied");

        // まだ適用されていない — up の仕事
        graph.addNode(fingerprinted("db1/004_pending", "token-d"));

        Set<NodeId> difference =
                new RebuildService(graph, historyRepo).plan(List.of(testTarget)).toRebuild();

        assertThat(difference).containsExactly(NodeId.of("db1/002_edited"));
    }

    @Test
    @DisplayName("落とす集合は、差分集合とその上に建っている適用済みノード")
    void whatComesDownIsTheDifferenceSetPlusWhateverStandsOnIt() {
        // a ← b ← c、および b に依存する未適用の d
        graph.addNode(fingerprinted("db1/001_a", "token-a"));
        graph.addNode(fingerprinted("db1/002_b", "token-b", Set.of(NodeId.of("db1/001_a"))));
        graph.addNode(fingerprinted("db1/003_c", "token-c", Set.of(NodeId.of("db1/002_b"))));
        graph.addNode(fingerprinted("db1/004_d", "token-d", Set.of(NodeId.of("db1/002_b"))));

        // b が編集された。a は一致。c は適用済み、d は未適用。
        appliedWith("db1/001_a", "token-a");
        appliedWith("db1/002_b", "token-b-when-applied");
        appliedWith("db1/003_c", "token-c");

        RebuildService.RebuildPlan plan =
                new RebuildService(graph, historyRepo).plan(List.of(testTarget));

        assertThat(plan.toRebuild()).containsExactly(NodeId.of("db1/002_b"));
        // c は b の上に建っていて適用済みなので落ちる。d も b の上だが未適用なので落とすものが無い。
        assertThat(plan.toRollBack())
                .containsExactlyInAnyOrder(NodeId.of("db1/002_b"), NodeId.of("db1/003_c"));
    }

    @Test
    @DisplayName("落とせないノードが絡むと、作り直しはそこで止まる")
    void reportsWhatCannotComeDownSoTheRunStopsThere() {
        // a が編集された。b は a の上に建っていて適用済みだが、ロールバックを持たない。
        graph.addNode(fingerprinted("db1/001_a", "token-now"));
        graph.addNode(
                new FingerprintedNode(
                        SimpleMigrationNode.builder()
                                .id(NodeId.of("db1/002_b"))
                                .name("db1/002_b")
                                .target(testTarget)
                                .dependencies(Set.of(NodeId.of("db1/001_a")))
                                .upTask(SimpleTask.of("UP: b"))
                                .build(),
                        "token-b"));
        appliedWith("db1/001_a", "token-when-applied");
        // b declares no rollback, so its apply recorded no payload — what up writes. It did record
        // what it stood on, which is what the rollback orders itself by.
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_b"),
                        testTarget.id(),
                        "db1/002_b",
                        null,
                        1L,
                        "token-b",
                        null,
                        List.of(NodeId.of("db1/001_a"))));

        RebuildService.RebuildPlan plan =
                new RebuildService(graph, historyRepo).plan(List.of(testTarget));

        assertThat(plan.toRebuild()).containsExactly(NodeId.of("db1/001_a"));
        // b は落とせず、a は b の下にあるので道連れで凍結される。
        assertThat(plan.frozen())
                .containsExactlyInAnyOrder(NodeId.of("db1/001_a"), NodeId.of("db1/002_b"));
    }

    @Test
    @DisplayName("比較のできない適用済み行があれば、作り直しは止まる")
    void reportsAnIncompleteHistoryRatherThanRebuildingAroundIt() {
        graph.addNode(fingerprinted("db1/001_a", "token-now"));
        graph.addNode(fingerprinted("db1/002_b", "token-b"));
        appliedWith("db1/001_a", "token-when-applied");
        appliedWith("db1/002_b", null); // 列より前に書かれた行

        RebuildService.RebuildPlan plan =
                new RebuildService(graph, historyRepo).plan(List.of(testTarget));

        // 判定できない行が一本でもあれば拒否は実行全体に及ぶので、計画は何も運ばない。
        assertThat(plan.incomplete()).containsExactly(NodeId.of("db1/002_b"));
        assertThat(plan.toRebuild()).isEmpty();
        assertThat(plan.toRollBack()).isEmpty();
    }

    @Test
    @DisplayName("不完全な履歴で止まるときも、target が解決できない行は運ぶ — 先に報告されるのはそちら")
    void carriesUnresolvableRowsThroughTheIncompleteRefusal() {
        graph.addNode(fingerprinted("db1/001_a", "token-a"));
        appliedWith("db1/001_a", null);
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("archive/gone"),
                        TargetId.of("archive"),
                        "Gone",
                        "DROP TABLE gone",
                        1L,
                        "token-gone"));

        RebuildService.RebuildPlan plan =
                new RebuildService(graph, historyRepo).plan(List.of(testTarget));

        assertThat(plan.incomplete()).containsExactly(NodeId.of("db1/001_a"));
        assertThat(plan.unresolvableRows())
                .extracting(ExecutionRecord::nodeId)
                .containsExactly(NodeId.of("archive/gone"));
        assertThat(plan.toRebuild()).isEmpty();
        assertThat(plan.toRollBack()).isEmpty();
        assertThat(plan.frozen()).isEmpty();
    }

    @Test
    @DisplayName("定義がもう宣言しない行の fingerprint 欠落でも止まる — 比較の対象にすら入らないから")
    void refusesOnAnUnreadableRowTheDefinitionsNoLongerDeclare() {
        graph.addNode(fingerprinted("db1/001_a", "token-now"));
        appliedWith("db1/001_a", "token-when-applied");
        appliedWith("db1/002_gone", null);

        RebuildService.RebuildPlan plan =
                new RebuildService(graph, historyRepo).plan(List.of(testTarget));

        assertThat(plan.incomplete()).containsExactly(NodeId.of("db1/002_gone"));
        assertThat(plan.toRebuild()).isEmpty();
        assertThat(plan.toRollBack()).isEmpty();
        assertThat(plan.frozen()).isEmpty();
    }

    @Test
    @DisplayName("行が読めない原因がプラグインなら、rebuild も amend ではなく不具合として報告する")
    void separatesAPluginThatCannotReportATokenFromARowAnAmendWouldFill() {
        graph.addNode(new ThrowingFingerprintNode(fingerprinted("db1/001_a", "unused")));
        appliedWith("db1/001_a", null);

        RebuildService.RebuildPlan plan =
                new RebuildService(graph, historyRepo).plan(List.of(testTarget));

        assertThat(plan.unreadable()).containsExactly(NodeId.of("db1/001_a"));
        assertThat(plan.incomplete()).isEmpty();
    }

    @Test
    @DisplayName("作り直しのロールバック相でも、接続先は行の target_id から引く")
    void connectsTheRollbackPhaseThroughTheTargetTheRowNames() {
        Target archive = SimpleTarget.create(TargetId.of("archive"), "archive");
        graph.addNode(fingerprinted("db1/001_a", "token-now"));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/001_a"),
                        archive.id(),
                        "db1/001_a",
                        "DROP TABLE a_old",
                        1L,
                        "token-when-applied"));

        RebuildService.RebuildPlan plan =
                new RebuildService(graph, historyRepo).plan(List.of(testTarget, archive));

        assertThat(plan.toRollBack()).containsExactly(NodeId.of("db1/001_a"));
        assertThat(plan.graph().getNode(NodeId.of("db1/001_a")))
                .isPresent()
                .get()
                .extracting(planned -> planned.target().id())
                .isEqualTo(archive.id());
    }

    @Test
    @DisplayName("行に立脚するノードが落とせるかは、その行が payload を持つかで決まる")
    void aNodeStandingForARowCanComeDownOnlyIfThatRowCarriesAPayload() {
        Target archive = SimpleTarget.create(TargetId.of("archive"), "archive");
        graph.addNode(fingerprinted("db1/001_a", "token-now"));
        graph.addNode(fingerprinted("db1/002_b", "token-now"));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/001_a"),
                        archive.id(),
                        "db1/001_a",
                        "DROP TABLE a_old",
                        1L,
                        "token-when-applied"));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_b"),
                        archive.id(),
                        "db1/002_b",
                        null,
                        1L,
                        "token-when-applied"));

        RebuildService.RebuildPlan plan =
                new RebuildService(graph, historyRepo).plan(List.of(testTarget, archive));

        assertThat(plan.toRollBack())
                .containsExactlyInAnyOrder(NodeId.of("db1/001_a"), NodeId.of("db1/002_b"));
        assertThat(plan.frozen()).containsExactly(NodeId.of("db1/002_b"));
    }

    @Test
    @DisplayName("定義がもう宣言しない適用済み移行も落とす — 差分の H 側そのものだから")
    void rollsBackWhatOnlyTheHistoryHolds() {
        graph.addNode(fingerprinted("db1/001_a", "token-a"));
        appliedWith("db1/001_a", "token-a");
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_gone"),
                        testTarget.id(),
                        "db1/002_gone",
                        "DROP INDEX idx",
                        1L,
                        "token-gone",
                        null,
                        List.of(NodeId.of("db1/001_a"))));

        RebuildService.RebuildPlan plan =
                new RebuildService(graph, historyRepo).plan(List.of(testTarget));

        assertThat(plan.toRollBack()).containsExactly(NodeId.of("db1/002_gone"));
        assertThat(plan.toRebuild()).isEmpty();
    }

    @Test
    @DisplayName("target が設定に無い適用行は、rebuild も down と同じく報告する")
    void reportsARowWhoseTargetTheProjectNoLongerConfigures() {
        graph.addNode(fingerprinted("db1/001_a", "token-a"));
        appliedWith("db1/001_a", "token-a");
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("archive/gone"),
                        TargetId.of("archive"),
                        "Gone",
                        "DROP TABLE gone",
                        1L,
                        "token-gone"));

        RebuildService.RebuildPlan plan =
                new RebuildService(graph, historyRepo).plan(List.of(testTarget));

        assertThat(plan.unresolvableRows())
                .extracting(ExecutionRecord::nodeId)
                .containsExactly(NodeId.of("archive/gone"));
    }

    private MigrationNode fingerprinted(String id, String fingerprint) {
        return fingerprinted(id, fingerprint, Set.of());
    }

    private MigrationNode fingerprinted(String id, String fingerprint, Set<NodeId> dependencies) {
        return new FingerprintedNode(
                SimpleMigrationNode.builder()
                        .id(NodeId.of(id))
                        .name(id)
                        .target(testTarget)
                        .dependencies(dependencies)
                        .upTask(SimpleTask.of("UP: " + id))
                        .downTask(SimpleTask.of("DOWN: " + id))
                        .build(),
                fingerprint);
    }

    /**
     * Records the row a real apply of this node writes, edges included.
     *
     * <p>The rollback phase runs over the history, so a row that records no dependencies describes
     * a migration that stood on nothing — and a fixture writing that while the graph says otherwise
     * is measuring a state no run produces.
     */
    private void appliedWith(String id, String recordedFingerprint) {
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of(id),
                        testTarget.id(),
                        id,
                        "DOWN SQL",
                        1L,
                        recordedFingerprint,
                        null,
                        graph.getNode(NodeId.of(id))
                                .map(node -> List.copyOf(node.dependencies()))
                                .orElse(List.of())));
    }
}
