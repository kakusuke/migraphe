package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.core.execution.support.ThrowingFingerprintNode;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTarget;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DownService")
class DownServiceTest {

    private static final String NO_WAY_BACK = "the rows cannot be reconstructed";

    private final Target testTarget = SimpleTarget.create(TargetId.of("env"), "env");

    @Test
    @DisplayName("孤立の接続先は行の target_id から引く — 宣言済みノードが名指していなくても")
    void resolvesAnOrphansTargetFromTheConfiguredTargetsNotFromASiblingNode() {
        Target archive = SimpleTarget.create(TargetId.of("archive"), "archive");

        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("db1/001_a", Set.of(), "DOWN: a", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/001_a"),
                        testTarget.id(),
                        "a",
                        "DOWN: a",
                        1L,
                        "fp",
                        null,
                        List.of()));
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("archive/gone"),
                        archive.id(),
                        "Gone",
                        "DROP TABLE gone",
                        1L,
                        "fp",
                        null,
                        List.of()));

        DownService.DownPlan plan =
                new DownService(graph, history)
                        .plan(NodeId.of("archive/gone"), false, List.of(testTarget, archive));

        assertThat(plan.blocker()).isNull();
        assertThat(plan.selectedNodes()).containsExactly(NodeId.of("archive/gone"));
        assertThat(plan.graph().getNode(NodeId.of("archive/gone")))
                .isPresent()
                .get()
                .extracting(node -> node.target().id())
                .isEqualTo(archive.id());
    }

    @Test
    @DisplayName("fingerprint の無い適用済み行が一本でもあれば、名指しの要求でも実行全体を止める")
    void refusesTheWholeRunWhileAnyAppliedRowCarriesNoFingerprint() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("db1/001_a", Set.of(), "DOWN: a", null));
        graph.addNode(node("db1/002_b", Set.of(), "DOWN: b", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/001_a"),
                        testTarget.id(),
                        "a",
                        "DOWN: a",
                        1L,
                        "fp",
                        null,
                        List.of()));
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_b"), testTarget.id(), "b", "DOWN: b", 1L));

        DownService.DownPlan plan =
                new DownService(graph, history)
                        .plan(NodeId.of("db1/001_a"), false, List.of(testTarget));

        assertThat(plan.blocker())
                .isInstanceOfSatisfying(
                        DownBlocker.IncompleteHistory.class,
                        incomplete ->
                                assertThat(incomplete.nodes())
                                        .containsExactly(NodeId.of("db1/002_b")));
        assertThat(plan.selectedNodes()).isEmpty();
    }

    @Test
    @DisplayName("行が読めない原因がプラグインなら、down も amend を案内しない")
    void refusesAPluginThatCannotReportATokenWithoutPrescribingAnAmend() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(new ThrowingFingerprintNode(node("db1/001_a", Set.of(), "DOWN: a", null)));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/001_a"), testTarget.id(), "a", "DOWN: a", 1L));

        DownService.DownPlan plan =
                new DownService(graph, history).plan(null, true, List.of(testTarget));

        assertThat(plan.blocker())
                .isInstanceOfSatisfying(
                        DownBlocker.UnreadableHistory.class,
                        unreadable ->
                                assertThat(unreadable.nodes())
                                        .containsExactly(NodeId.of("db1/001_a")));
        assertThat(plan.selectedNodes()).isEmpty();
    }

    @Test
    @DisplayName("波及は記録された辺に従う — タスクファイルがその依存を宣言しなくなっていても")
    void cascadesOverTheRecordedEdgesRatherThanTheDeclaredOnes() {
        // The task files no longer relate them: b's dependencies: was edited away after both ran.
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("db1/001_a", Set.of(), "DOWN: a", null));
        graph.addNode(node("db1/002_b", Set.of(), "DOWN: b", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/001_a"),
                        testTarget.id(),
                        "a",
                        "DOWN: a",
                        1L,
                        "fp-a",
                        null,
                        List.of()));
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_b"),
                        testTarget.id(),
                        "b",
                        "DOWN: b",
                        1L,
                        "fp-b",
                        null,
                        List.of(NodeId.of("db1/001_a"))));

        DownService.DownPlan plan =
                new DownService(graph, history)
                        .plan(NodeId.of("db1/001_a"), false, List.of(testTarget));

        // b was built on a when it ran, whatever the files say now. Taking a out without taking b
        // out first removes what b still stands on.
        assertThat(plan.selectedNodes())
                .containsExactlyInAnyOrder(NodeId.of("db1/001_a"), NodeId.of("db1/002_b"));
    }

    @Test
    @DisplayName("宣言済みノードでも接続先は行の target_id から引く — タスクの target: が張り替えられていても")
    void connectsADeclaredNodeThroughTheTargetItsAppliedRowNames() {
        Target archive = SimpleTarget.create(TargetId.of("archive"), "archive");

        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("db1/001_a", Set.of(), "DOWN: a", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/001_a"),
                        archive.id(),
                        "a",
                        "DROP TABLE a_old",
                        1L,
                        "fp",
                        null,
                        List.of()));

        DownService.DownPlan plan =
                new DownService(graph, history)
                        .plan(NodeId.of("db1/001_a"), false, List.of(testTarget, archive));

        assertThat(plan.blocker()).isNull();
        assertThat(plan.selectedNodes()).containsExactly(NodeId.of("db1/001_a"));
        assertThat(plan.graph().getNode(NodeId.of("db1/001_a")))
                .isPresent()
                .get()
                .extracting(planned -> planned.target().id())
                .isEqualTo(archive.id());
        assertThat(plan.graph().getNode(NodeId.of("db1/001_a")))
                .isPresent()
                .get()
                .extracting(MigrationNode::downTask)
                .isNull();
    }

    @Test
    @DisplayName("孤立を名指した down は通り、実行用グラフにその孤立のノードが現れる")
    void shouldAcceptAnOrphanAsTheTargetWhenNothingStandsOnIt() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("db1/001_a", Set.of(), "DOWN: a", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/001_a"),
                        testTarget.id(),
                        "a",
                        "DOWN: a",
                        1L,
                        "fp",
                        null,
                        List.of()));
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_removed"),
                        testTarget.id(),
                        "Add index",
                        "DROP INDEX idx",
                        1L,
                        "fp",
                        null,
                        List.of(NodeId.of("db1/001_a"))));

        DownService.DownPlan plan =
                new DownService(graph, history)
                        .plan(NodeId.of("db1/002_removed"), false, List.of(testTarget));

        assertThat(plan.blocker()).isNull();
        assertThat(plan.selectedNodes()).containsExactly(NodeId.of("db1/002_removed"));
        assertThat(plan.graph().getNode(NodeId.of("db1/002_removed")))
                .isPresent()
                .get()
                .extracting(MigrationNode::downTask)
                .isNull();
    }

    @Test
    @DisplayName("孤立自身のロールバックが一度失敗していても、再試行を拒否しない")
    void shouldStillPermitTheRetryAfterTheOrphansOwnRollbackFailed() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("db1/001_a", Set.of(), "DOWN: a", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/001_a"),
                        testTarget.id(),
                        "a",
                        "DOWN: a",
                        1L,
                        "fp",
                        null,
                        List.of()));
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_removed"),
                        testTarget.id(),
                        "Add index",
                        "DROP INDEX idx",
                        1L,
                        "fp",
                        null,
                        List.of()));
        // A rollback that failed leaves the node applied, and its record carries no dependencies.
        history.record(
                ExecutionRecord.failure(
                        NodeId.of("db1/002_removed"),
                        testTarget.id(),
                        ExecutionDirection.DOWN,
                        "Add index",
                        "the index was locked"));

        DownService.DownPlan plan =
                new DownService(graph, history)
                        .plan(NodeId.of("db1/002_removed"), false, List.of(testTarget));

        assertThat(plan.blocker()).isNull();
        assertThat(plan.selectedNodes()).containsExactly(NodeId.of("db1/002_removed"));
    }

    @Test
    @DisplayName("落としてから target: を張り替えたノードは、二重配置ではないので拒否を招かない")
    void shouldNotRefuseWhenAnIdWasRolledBackBeforeBeingReAppliedElsewhere() {
        Target other = SimpleTarget.create(TargetId.of("other"), "other");

        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(
                SimpleMigrationNode.builder()
                        .id(NodeId.of("db1/010_x"))
                        .name("db1/010_x")
                        .target(other)
                        .dependencies(Set.of())
                        .upTask(SimpleTask.of("UP"))
                        .downTask(SimpleTask.of("DOWN"))
                        .build());
        graph.addNode(node("db1/001_a", Set.of(), "DOWN: a", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_removed"),
                        testTarget.id(),
                        "Add index",
                        "DROP INDEX idx",
                        1L,
                        "fp",
                        null,
                        List.of()));
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/010_x"),
                        testTarget.id(),
                        "x",
                        "DOWN: x",
                        1L,
                        "fp",
                        null,
                        List.of()));
        history.record(
                ExecutionRecord.downSuccess(NodeId.of("db1/010_x"), testTarget.id(), "x", 1L));
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/010_x"),
                        other.id(),
                        "x",
                        "DOWN: x",
                        1L,
                        "fp",
                        null,
                        List.of()));

        DownService.DownPlan plan =
                new DownService(graph, history)
                        .plan(NodeId.of("db1/002_removed"), false, List.of(testTarget, other));

        assertThat(plan.blocker()).isNull();
        assertThat(plan.selectedNodes()).containsExactly(NodeId.of("db1/002_removed"));
    }

    @Test
    @DisplayName("孤立への主張は、そのノードの最新の適用済み行だけが決める")
    void shouldReadOnlyTheLatestAppliedRowWhenAskingWhoStandsOnTheOrphan() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("db1/003_c", Set.of(), "DOWN: c", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_removed"),
                        testTarget.id(),
                        "Add index",
                        "DROP INDEX idx",
                        1L,
                        "fp",
                        null,
                        List.of()));
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/003_c"),
                        testTarget.id(),
                        "c",
                        "DOWN: c",
                        1L,
                        "fp",
                        null,
                        List.of(NodeId.of("db1/002_removed"))));
        history.record(
                ExecutionRecord.amendedUp(
                        NodeId.of("db1/003_c"),
                        testTarget.id(),
                        "c",
                        "DOWN: c",
                        "a token",
                        null,
                        List.of(),
                        null));

        DownService.DownPlan plan =
                new DownService(graph, history)
                        .plan(NodeId.of("db1/002_removed"), false, List.of(testTarget));

        assertThat(plan.blocker()).isNull();
        assertThat(plan.selectedNodes()).containsExactly(NodeId.of("db1/002_removed"));
    }

    @Test
    @DisplayName("凍結されていても未適用のノードは「落とせない適用済み移行」に数えない")
    void shouldCountOnlyAppliedMigrationsAmongTheFrozen() {
        // a は落とせるが、落とせない b が立っているので凍結される。ただし a は適用されていない。
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("a", Set.of(), "DOWN: a", null));
        MigrationNode b = node("b", Set.of(NodeId.of("a")), null, NO_WAY_BACK);
        graph.addNode(b);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        applied(history, b, null);

        DownService.DownPlan plan =
                new DownService(graph, history).plan(null, true, List.of(testTarget));

        assertThat(plan.selectedNodes()).isEmpty();
        assertThat(reasonLines(plan))
                .containsExactly(
                        "Error: --all means all, and 1 applied migration(s) cannot be rolled back."
                                + " Nothing was rolled back:",
                        "  b — no way back: " + NO_WAY_BACK);
    }

    @Test
    @DisplayName("落とせないノードが適用済みの依存を押さえていれば、その数も報告する")
    void shouldCountTheAppliedNodesTheIrreversibleOneHoldsDown() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode a = node("a", Set.of(), "DOWN: a", null);
        MigrationNode b = node("b", Set.of(NodeId.of("a")), null, NO_WAY_BACK);
        graph.addNode(a);
        graph.addNode(b);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        applied(history, a, "DOWN: a");
        applied(history, b, null);

        DownService.DownPlan plan =
                new DownService(graph, history).plan(null, true, List.of(testTarget));

        assertThat(plan.selectedNodes()).isEmpty();
        assertThat(reasonLines(plan))
                .containsExactly(
                        "Error: --all means all, and 2 applied migration(s) cannot be rolled back"
                                + " (1 of them held down by the rest). Nothing was rolled back:",
                        "  b — no way back: " + NO_WAY_BACK);
    }

    @Test
    @DisplayName("指定したノード自身が落とせないときは、宣言された理由を引用して断る")
    void shouldQuoteTheDeclaredReasonWhenTheTargetItselfCannotBeRolledBack() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode a = node("a", Set.of(), null, NO_WAY_BACK);
        graph.addNode(a);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        applied(history, a, null);

        DownService.DownPlan plan =
                new DownService(graph, history).plan(NodeId.of("a"), false, List.of(testTarget));

        assertThat(plan.blocker()).isInstanceOf(DownBlocker.IrreversibleTarget.class);
        assertThat(reasonLines(plan))
                .containsExactly("Error: a cannot be rolled back — no way back: " + NO_WAY_BACK);
    }

    @Test
    @DisplayName("payload も理由も無い行は、その行を根拠に断る — 宣言ではなく")
    void shouldRefuseFromTheRowWhenItCarriesNeitherARollbackNorAReason() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("a", Set.of(), null, null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(NodeId.of("a"), testTarget.id(), "a", null, 1L, "fp"));

        DownService.DownPlan plan =
                new DownService(graph, history).plan(NodeId.of("a"), false, List.of(testTarget));

        // A task declaring neither is refused by validate and up, so an applied migration in this
        // state is an old row or a hand-edited one — and the rollback reads the row, not the file
        // that may since have been rewritten.
        assertThat(reasonLines(plan))
                .containsExactly(
                        "Error: a cannot be rolled back — the history recorded no rollback for it"
                                + " and no reason for having none");
    }

    @Test
    @DisplayName("指定したノードを押さえているのが別のノードなら、その名前を挙げて断る")
    void shouldNameWhatHoldsTheTargetDown() {
        MigrationGraph graph = MigrationGraph.create();
        MigrationNode a = node("a", Set.of(), "DOWN: a", null);
        MigrationNode b = node("b", Set.of(NodeId.of("a")), null, NO_WAY_BACK);
        graph.addNode(a);
        graph.addNode(b);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        applied(history, a, "DOWN: a");
        applied(history, b, null);

        DownService.DownPlan plan =
                new DownService(graph, history).plan(NodeId.of("a"), false, List.of(testTarget));

        assertThat(plan.blocker())
                .isInstanceOfSatisfying(
                        DownBlocker.HeldTarget.class,
                        held -> assertThat(held.holders()).containsExactly(NodeId.of("b")));
        assertThat(reasonLines(plan))
                .containsExactly(
                        "Error: a cannot be rolled back while these are applied, because they have"
                                + " no down migration and stand on it: b");
    }

    @Test
    @DisplayName("何も止めなければ、適用済みで落とせるノードが対象になる")
    void shouldPlanTheAppliedNodesThatCanBeRolledBack() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("a", Set.of(), "DOWN: a", null));
        graph.addNode(node("b", Set.of(NodeId.of("a")), "DOWN: b", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("a"), testTarget.id(), "a", "DOWN: a", 1L, "fp"));

        DownService.DownPlan plan =
                new DownService(graph, history).plan(null, true, List.of(testTarget));

        assertThat(plan.blocker()).isNull();
        assertThat(plan.selectedNodes()).containsExactly(NodeId.of("a"));
    }

    @Test
    @DisplayName("拒否が引く一方向の理由は、実行されるノードのもの — 定義が down: を持っていても行が優先")
    void quotesTheReasonRecordedByTheNodeThatWillExecute() {
        Target archive = SimpleTarget.create(TargetId.of("archive"), "archive");

        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("db1/001_a", Set.of(), "DOWN: a", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/001_a"),
                        archive.id(),
                        "a",
                        null,
                        1L,
                        "token-when-applied",
                        null,
                        List.of(),
                        NO_WAY_BACK));

        DownService service = new DownService(graph, history);

        DownService.DownPlan named =
                service.plan(NodeId.of("db1/001_a"), false, List.of(testTarget, archive));
        assertThat(reasonLines(named))
                .containsExactly(
                        "Error: db1/001_a cannot be rolled back — no way back: " + NO_WAY_BACK);

        DownService.DownPlan all = service.plan(null, true, List.of(testTarget, archive));
        assertThat(reasonLines(all))
                .containsExactly(
                        "Error: --all means all, and 1 applied migration(s) cannot be rolled back."
                                + " Nothing was rolled back:",
                        "  db1/001_a — no way back: " + NO_WAY_BACK);
    }

    @Test
    @DisplayName("fingerprint を持つのに payload も理由も無い行は、手編集を指して断る")
    void namesARowThatCarriesNeitherARollbackNorAReasonForHavingNone() {
        Target archive = SimpleTarget.create(TargetId.of("archive"), "archive");

        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("db1/001_a", Set.of(), "DOWN: a", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/001_a"), archive.id(), "a", null, 1L, "token-when-applied"));

        DownService.DownPlan plan =
                new DownService(graph, history).plan(null, true, List.of(testTarget, archive));

        assertThat(reasonLines(plan))
                .containsExactly(
                        "Error: --all means all, and 1 applied migration(s) cannot be rolled back."
                                + " Nothing was rolled back:",
                        "  db1/001_a — the history recorded no rollback for it and no reason for"
                                + " having none");
    }

    @Test
    @DisplayName("--all は凍結が一つでもあれば、何もロールバックせずに拒否する")
    void refusesTheWholeOfAllWhenAnythingIsFrozen() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("a", Set.of(), "DOWN: a", null));
        MigrationNode frozen = node("b", Set.of(), null, NO_WAY_BACK);
        graph.addNode(frozen);

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("a"), testTarget.id(), "a", "DOWN: a", 1L, "fp"));
        applied(history, frozen, null);

        DownService.DownPlan plan =
                new DownService(graph, history).plan(null, true, List.of(testTarget));

        assertThat(plan.selectedNodes()).isEmpty();
        assertThat(reasonLines(plan))
                .containsExactly(
                        "Error: --all means all, and 1 applied migration(s) cannot be rolled back."
                                + " Nothing was rolled back:",
                        "  b — no way back: " + NO_WAY_BACK);
    }

    @Test
    @DisplayName("行の payload を再構築できない target のノードは、計画の段階で凍結される")
    void freezesANodeWhoseTargetCannotRebuildItsRecordedRollback() {
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
                        .id(NodeId.of("db1/x"))
                        .name("db1/x")
                        .target(plain)
                        .dependencies(Set.of())
                        .upTask(SimpleTask.of("UP: x"))
                        .downTask(SimpleTask.of("DOWN: x"))
                        .build());
        graph.addNode(node("db1/y", Set.of(), "DOWN: y", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(NodeId.of("db1/x"), plain.id(), "x", "DROP x", 1L, "fp"));
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/y"), testTarget.id(), "y", "DROP y", 1L, "fp"));

        DownService.DownPlan plan =
                new DownService(graph, history).plan(null, true, List.of(plain, testTarget));

        assertThat(plan.selectedNodes()).isEmpty();
        assertThat(reasonLines(plan))
                .containsExactly(
                        "Error: --all means all, and 1 applied migration(s) cannot be rolled back."
                                + " Nothing was rolled back:",
                        "  db1/x — its target plain cannot rebuild a recorded rollback");
    }

    @Test
    @DisplayName("定義が down: を持っていても、行に payload が無ければ凍結し、行を名指して断る")
    void freezesADeclaredNodeWhoseRowKeptNoPayloadAndNamesTheRow() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("db1/a", Set.of(), "DOWN: a", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/a"), testTarget.id(), "a", null, 1L, "token-when-applied"));

        DownService.DownPlan plan =
                new DownService(graph, history).plan(null, true, List.of(testTarget));

        assertThat(plan.selectedNodes()).isEmpty();
        assertThat(reasonLines(plan))
                .containsExactly(
                        "Error: --all means all, and 1 applied migration(s) cannot be rolled back."
                                + " Nothing was rolled back:",
                        "  db1/a — the history recorded no rollback for it and no reason for having"
                                + " none");
    }

    @Test
    @DisplayName("fingerprint の無い行は額面どおり読めないので、payload があっても --all は止まる")
    void refusesAllWhileARowCarriesNoFingerprintEvenWithAPayload() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("db1/a", Set.of(), "DOWN: a", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/a"), testTarget.id(), "a", "DROP a", 1L, null));

        DownService.DownPlan plan =
                new DownService(graph, history).plan(null, true, List.of(testTarget));

        assertThat(plan.selectedNodes()).isEmpty();
        assertThat(reasonLines(plan))
                .containsExactly(
                        "Error: 1 applied migration(s) were recorded by a version that did not"
                                + " record what it applied, so nothing in those rows can be read:",
                        "  [?] db1/a",
                        "Run 'migraphe upgrade-history', then 'migraphe amend <id>' for any of"
                                + " these no task file declares any more.");
    }

    @Test
    @DisplayName("孤立は特別扱いされず、--all で他と一緒に落ちる")
    void rollsBackAnOrphanAlongsideEverythingElse() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("db1/001_a", Set.of(), "DOWN: a", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/001_a"),
                        testTarget.id(),
                        "a",
                        "DOWN: a",
                        1L,
                        "fp",
                        null,
                        List.of()));
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_gone"),
                        testTarget.id(),
                        "Add index",
                        "DROP INDEX idx",
                        1L,
                        "fp",
                        null,
                        List.of(NodeId.of("db1/001_a"))));

        DownService.DownPlan plan =
                new DownService(graph, history).plan(null, true, List.of(testTarget));

        assertThat(plan.blocker()).isNull();
        assertThat(plan.selectedNodes())
                .containsExactlyInAnyOrder(NodeId.of("db1/001_a"), NodeId.of("db1/002_gone"));
        assertThat(plan.graph().getAllDependents(NodeId.of("db1/001_a")))
                .containsExactly(NodeId.of("db1/002_gone"));
    }

    @Test
    @DisplayName("target が設定に無い適用行は、飛ばさず報告して何もロールバックしない")
    void refusesWhileAnAppliedRowNamesATargetTheProjectNoLongerConfigures() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("db1/001_a", Set.of(), "DOWN: a", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/001_a"), testTarget.id(), "a", "DOWN: a", 1L, "fp"));
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("archive/gone"),
                        TargetId.of("archive"),
                        "Gone",
                        "DROP TABLE gone",
                        1L,
                        "fp"));

        DownService.DownPlan plan =
                new DownService(graph, history).plan(null, true, List.of(testTarget));

        assertThat(plan.selectedNodes()).isEmpty();
        assertThat(reasonLines(plan))
                .containsExactly(
                        "Error: 1 applied migration(s) name a target this project no longer"
                                + " configures, so there is no connection to roll them back"
                                + " through. Nothing was rolled back:",
                        "  archive/gone — target archive");
    }

    /**
     * Records the row a real apply of this node writes.
     *
     * <p>The rollback runs over the history, so a fixture that leaves out what an apply records —
     * the edges the node stood on, the author's reason for being one-way — is describing a row no
     * version writes, and the test then measures a state that cannot occur.
     */
    private void applied(
            InMemoryHistoryRepository history, MigrationNode node, @Nullable String downPayload) {
        history.record(
                ExecutionRecord.upSuccess(
                        node.id(),
                        node.target().id(),
                        node.name(),
                        downPayload,
                        1L,
                        "fp-" + node.id().value(),
                        null,
                        List.copyOf(node.dependencies()),
                        node.noWayBack()));
    }

    /** Renders the plan's blocker, which the calling test has established is present. */
    private static List<String> reasonLines(DownService.DownPlan plan) {
        return DownPlanFormatter.format(
                Objects.requireNonNull(plan.blocker(), "the test expects a blocker"),
                RepairVocabulary.CLI);
    }

    private MigrationNode node(
            String id,
            Set<NodeId> dependencies,
            @Nullable String downSql,
            @Nullable String noWayBack) {
        SimpleMigrationNode.Builder builder =
                SimpleMigrationNode.builder()
                        .id(NodeId.of(id))
                        .name(id)
                        .target(testTarget)
                        .dependencies(dependencies)
                        .upTask(SimpleTask.of("UP: " + id));
        if (downSql != null) {
            builder.downTask(SimpleTask.of(downSql));
        }
        if (noWayBack != null) {
            builder.noWayBack(noWayBack);
        }
        return builder.build();
    }
}
