package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
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

    private final Environment testEnv = SimpleEnvironment.create(EnvironmentId.of("env"), "env");

    @Test
    @DisplayName("凍結されていても未適用のノードは「落とせない適用済み移行」に数えない")
    void shouldCountOnlyAppliedMigrationsAmongTheFrozen() {
        // a は落とせるが、落とせない b が立っているので凍結される。ただし a は適用されていない。
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("a", Set.of(), "DOWN: a", null));
        graph.addNode(node("b", Set.of(NodeId.of("a")), null, NO_WAY_BACK));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(ExecutionRecord.upSuccess(NodeId.of("b"), testEnv.id(), "b", null, 1L));

        DownService.DownPlan plan = new DownService(graph, history).plan(null, true);

        assertThat(plan.blocker()).isNull();
        assertThat(plan.leftFrozen()).isTrue();
        assertThat(plan.frozenAppliedCount()).isOne();
        assertThat(plan.irreversible())
                .extracting(MigrationNode::id)
                .containsExactly(NodeId.of("b"));
        assertThat(DownPlanFormatter.formatFrozen(plan))
                .containsExactly(
                        "Error: 1 applied migrations cannot be rolled back:",
                        "  b — no way back: " + NO_WAY_BACK);
    }

    @Test
    @DisplayName("落とせないノードが適用済みの依存を押さえていれば、その数も報告する")
    void shouldCountTheAppliedNodesTheIrreversibleOneHoldsDown() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("a", Set.of(), "DOWN: a", null));
        graph.addNode(node("b", Set.of(NodeId.of("a")), null, NO_WAY_BACK));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 1L));
        history.record(ExecutionRecord.upSuccess(NodeId.of("b"), testEnv.id(), "b", null, 1L));

        DownService.DownPlan plan = new DownService(graph, history).plan(null, true);

        assertThat(plan.frozenAppliedCount()).isEqualTo(2);
        assertThat(plan.targetNodes()).isEmpty();
        assertThat(DownPlanFormatter.formatFrozen(plan))
                .containsExactly(
                        "Error: 2 applied migrations cannot be rolled back (1 of them held down by"
                                + " the rest):",
                        "  b — no way back: " + NO_WAY_BACK);
    }

    @Test
    @DisplayName("適用済みなのに定義が無いノードがあれば、何もロールバックしない")
    void shouldRefuseWhileSomethingAppliedIsNoLongerDefined() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("a", Set.of(), "DOWN: a", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 1L));
        history.record(
                ExecutionRecord.upSuccess(NodeId.of("gone"), testEnv.id(), "gone", null, 1L));

        DownService.DownPlan plan = new DownService(graph, history).plan(null, true);

        assertThat(plan.blocker()).isInstanceOf(DownBlocker.Orphans.class);
        assertThat(plan.targetNodes()).isEmpty();
        assertThat(reasonLines(plan)).contains("  gone");
    }

    @Test
    @DisplayName("指定したノード自身が落とせないときは、宣言された理由を引用して断る")
    void shouldQuoteTheDeclaredReasonWhenTheTargetItselfCannotBeRolledBack() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("a", Set.of(), null, NO_WAY_BACK));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 1L));

        DownService.DownPlan plan = new DownService(graph, history).plan(NodeId.of("a"), false);

        assertThat(plan.blocker()).isInstanceOf(DownBlocker.IrreversibleTarget.class);
        assertThat(reasonLines(plan))
                .containsExactly("Error: a cannot be rolled back — no way back: " + NO_WAY_BACK);
    }

    @Test
    @DisplayName("宣言の無い落とせないノードは、書き忘れとして断られる")
    void shouldSayTheRollbackWasNeverWrittenWhenNoReasonWasDeclared() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("a", Set.of(), null, null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 1L));

        DownService.DownPlan plan = new DownService(graph, history).plan(NodeId.of("a"), false);

        assertThat(reasonLines(plan))
                .containsExactly(
                        "Error: a cannot be rolled back — it has no down migration, and none was"
                                + " declared");
    }

    @Test
    @DisplayName("指定したノードを押さえているのが別のノードなら、その名前を挙げて断る")
    void shouldNameWhatHoldsTheTargetDown() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("a", Set.of(), "DOWN: a", null));
        graph.addNode(node("b", Set.of(NodeId.of("a")), null, NO_WAY_BACK));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 1L));
        history.record(ExecutionRecord.upSuccess(NodeId.of("b"), testEnv.id(), "b", null, 1L));

        DownService.DownPlan plan = new DownService(graph, history).plan(NodeId.of("a"), false);

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
        history.record(ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 1L));

        DownService.DownPlan plan = new DownService(graph, history).plan(null, true);

        assertThat(plan.blocker()).isNull();
        assertThat(plan.leftFrozen()).isFalse();
        assertThat(plan.targetNodes()).containsExactly(NodeId.of("a"));
    }

    /** Renders the plan's blocker, which the calling test has established is present. */
    private static List<String> reasonLines(DownService.DownPlan plan) {
        return DownPlanFormatter.format(
                Objects.requireNonNull(plan.blocker(), "the test expects a blocker"));
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
                        .environment(testEnv)
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
