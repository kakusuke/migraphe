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
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpService")
class UpServiceTest {

    private final Environment testEnv = SimpleEnvironment.create(EnvironmentId.of("env"), "env");

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
        assertThat(plan.targetNodes()).isEmpty();
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
        assertThat(plan.targetNodes()).isEmpty();
    }

    @Test
    @DisplayName("何も止めなければ、未適用のノードだけが対象になる")
    void shouldPlanOnlyWhatHasNotBeenAppliedYet() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("a", Set.of(), "DOWN: a", null));
        graph.addNode(node("b", Set.of(NodeId.of("a")), "DOWN: b", null));

        InMemoryHistoryRepository history = new InMemoryHistoryRepository();
        history.record(ExecutionRecord.upSuccess(NodeId.of("a"), testEnv.id(), "a", null, 1L));

        UpService.UpPlan plan = new UpService(graph, history).plan(null);

        assertThat(plan.blocker()).isNull();
        assertThat(plan.targetNodes()).containsExactly(NodeId.of("b"));
    }

    @Test
    @DisplayName("no_way_back: だけを宣言したタスクは止められない")
    void shouldAcceptATaskThatDeclaredWhyItCannotBeRolledBack() {
        MigrationGraph graph = MigrationGraph.create();
        graph.addNode(node("a", Set.of(), null, "the rows cannot be reconstructed"));

        UpService.UpPlan plan = new UpService(graph, new InMemoryHistoryRepository()).plan(null);

        assertThat(plan.blocker()).isNull();
        assertThat(plan.targetNodes()).containsExactly(NodeId.of("a"));
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
