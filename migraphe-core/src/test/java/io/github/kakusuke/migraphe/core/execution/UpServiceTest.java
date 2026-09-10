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
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpService")
class UpServiceTest {

    private final Target testTarget = SimpleTarget.create(TargetId.of("env"), "env");

    @Test
    @DisplayName("未解決の依存があるとブロッカーを返し、対象ノードは空になる")
    void shouldRefuseWhileADeclaredDependencyNamesNothing() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("b", Set.of(NodeId.of("gone")), "DOWN: b", null));

        UpService.UpPlan plan = new UpService(graph, new InMemoryHistoryRepository()).plan(null);

        assertThat(plan.blocker())
                .isInstanceOfSatisfying(
                        UpBlocker.UnresolvedDependencies.class,
                        unresolved ->
                                assertThat(unresolved.byNode())
                                        .containsEntry(NodeId.of("b"), Set.of(NodeId.of("gone"))));
        assertThat(plan.selectedNodes()).isEmpty();
    }

    @Test
    @DisplayName("down: も no_way_back: も無いタスクがあるとブロッカーを返す")
    void shouldRefuseATaskThatNeitherRollsBackNorSaysWhyNot() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("a", Set.of(), null, null));

        UpService.UpPlan plan = new UpService(graph, new InMemoryHistoryRepository()).plan(null);

        assertThat(plan.blocker())
                .isInstanceOfSatisfying(
                        UpBlocker.UndeclaredIrreversible.class,
                        undeclared ->
                                assertThat(undeclared.nodes()).containsExactly(NodeId.of("a")));
        assertThat(plan.selectedNodes()).isEmpty();
    }

    @Test
    @DisplayName("ロールバックを宣言していても、up タスクがそれを記録しないなら止める")
    void shouldRefuseATaskWhoseRollbackWouldNotBeRecorded() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(
                SimpleMigrationNode.builder()
                        .id(NodeId.of("a"))
                        .name("a")
                        .target(testTarget)
                        .dependencies(Set.of())
                        // Declares a rollback, but the up task reports none, so the row this would
                        // write says the migration kept no rollback.
                        .upTask(SimpleTask.of("UP: a"))
                        .downTask(SimpleTask.of("DOWN: a"))
                        .build());

        UpService.UpPlan plan = new UpService(graph, new InMemoryHistoryRepository()).plan(null);

        assertThat(plan.blocker())
                .isInstanceOfSatisfying(
                        UpBlocker.UnrecordableRollback.class,
                        unrecordable ->
                                assertThat(unrecordable.nodes()).containsExactly(NodeId.of("a")));
        assertThat(plan.selectedNodes()).isEmpty();
    }

    @Test
    @DisplayName("何も止めなければ、未適用のノードだけが対象になる")
    void shouldPlanOnlyWhatHasNotBeenAppliedYet() {
        MigrationGraph graph = MigrationGraph.create();
        // The recorded token is what the definition folds, which is what "applied and unchanged"
        // means now — an arbitrary one would read as a migration edited since it ran.
        graph.addNode(new FingerprintedNode(node("a", Set.of(), "DOWN: a", null), "fp-a"));
        graph.addNode(node("b", Set.of(NodeId.of("a")), "DOWN: b", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(NodeId.of("a"), testTarget.id(), "a", null, 1L, "fp-a"));

        UpService.UpPlan plan = new UpService(graph, history).plan(null);

        assertThat(plan.blocker()).isNull();
        assertThat(plan.selectedNodes()).containsExactly(NodeId.of("b"));
    }

    @Test
    @DisplayName("fingerprint の無い適用済み行が一本でもあれば、実行全体を止める")
    void shouldRefuseWhileAnAppliedRowCarriesNoFingerprint() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("a", Set.of(), "DOWN: a", null));
        graph.addNode(node("b", Set.of(), "DOWN: b", null));
        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(ExecutionRecord.upSuccess(NodeId.of("a"), testTarget.id(), "a", null, 1L));

        UpService.UpPlan plan = new UpService(graph, history).plan(null);

        assertThat(plan.blocker())
                .isInstanceOfSatisfying(
                        UpBlocker.IncompleteHistory.class,
                        incomplete ->
                                assertThat(incomplete.nodes()).containsExactly(NodeId.of("a")));
        assertThat(plan.selectedNodes()).isEmpty();
    }

    @Test
    @DisplayName("行が読めない原因がプラグインなら、amend ではなくプラグインの不具合として断る")
    void namesAPluginThatCannotReportATokenInsteadOfPrescribingAnAmend() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(new ThrowingFingerprintNode(node("a", Set.of(), "DOWN: a", null)));
        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(ExecutionRecord.upSuccess(NodeId.of("a"), testTarget.id(), "a", null, 1L));

        UpService.UpPlan plan = new UpService(graph, history).plan(null);

        assertThat(plan.blocker())
                .isInstanceOfSatisfying(
                        UpBlocker.UnreadableContent.class,
                        unreadable ->
                                assertThat(unreadable.nodes()).containsExactly(NodeId.of("a")));
        assertThat(plan.selectedNodes()).isEmpty();
    }

    @Test
    @DisplayName("定義がトークンを出さないノードも、amend では直せない側に入る")
    void treatsADefinitionThatReportsNoTokenAsAPluginFaultToo() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(new FingerprintedNode(node("a", Set.of(), "DOWN: a", null), null));
        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(ExecutionRecord.upSuccess(NodeId.of("a"), testTarget.id(), "a", null, 1L));

        UpService.UpPlan plan = new UpService(graph, history).plan(null);

        // Null is reserved for the adapter over a history row, so a declared node returning it is
        // out of contract — and neither amend form fills the row from a definition that has
        // nothing to give.
        assertThat(plan.blocker())
                .isInstanceOfSatisfying(
                        UpBlocker.UnreadableContent.class,
                        unreadable ->
                                assertThat(unreadable.nodes()).containsExactly(NodeId.of("a")));
    }

    @Test
    @DisplayName("これから適用するノードのトークンが取れないなら、何も適用せずに止める")
    void refusesBeforeApplyingANodeWhosePluginCannotReportItsContent() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(new ThrowingFingerprintNode(node("a", Set.of(), "DOWN: a", null)));

        UpService.UpPlan plan = new UpService(graph, new InMemoryHistoryRepository()).plan(null);

        // Asking after the DDL has run cannot refuse anything: the record would be lost and the
        // next run would apply it again. So the question is asked while refusing still costs
        // nothing.
        assertThat(plan.blocker())
                .isInstanceOfSatisfying(
                        UpBlocker.UnreadableContent.class,
                        unreadable ->
                                assertThat(unreadable.nodes()).containsExactly(NodeId.of("a")));
        assertThat(plan.selectedNodes()).isEmpty();
    }

    @Test
    @DisplayName("適用後に編集された移行があれば、その上に積まずに実行全体を止める")
    void refusesToApplyOnTopOfAMigrationThatWasEditedAfterItRan() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(new FingerprintedNode(node("a", Set.of(), "DOWN: a", null), "token-now"));
        graph.addNode(new FingerprintedNode(node("b", Set.of(), "DOWN: b", null), "token-b"));
        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("a"), testTarget.id(), "a", null, 1L, "token-when-applied"));

        UpService.UpPlan plan = new UpService(graph, history).plan(null);

        // The database is known not to match the definitions. Applying b on top of it builds on
        // ground the task files no longer describe — worse than building on ground that merely
        // cannot be read, which already stops the run.
        assertThat(plan.blocker())
                .isInstanceOfSatisfying(
                        UpBlocker.EditedSinceApplied.class,
                        edited -> assertThat(edited.nodes()).containsExactly(NodeId.of("a")));
        assertThat(plan.selectedNodes()).isEmpty();
    }

    @Test
    @DisplayName("no_way_back: だけを宣言したタスクは止められない")
    void shouldAcceptATaskThatDeclaredWhyItCannotBeRolledBack() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("a", Set.of(), null, "the rows cannot be reconstructed"));

        UpService.UpPlan plan = new UpService(graph, new InMemoryHistoryRepository()).plan(null);

        assertThat(plan.blocker()).isNull();
        assertThat(plan.selectedNodes()).containsExactly(NodeId.of("a"));
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
                        // A node whose up task does not report the rollback it declares is one up
                        // now refuses, so the helper builds the valid shape.
                        .upTask(
                                downSql == null
                                        ? SimpleTask.of("UP: " + id)
                                        : SimpleTask.withDownTask("UP: " + id, downSql));
        if (downSql != null) {
            builder.downTask(SimpleTask.of(downSql));
        }
        if (noWayBack != null) {
            builder.noWayBack(noWayBack);
        }
        return builder.build();
    }
}
