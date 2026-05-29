package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReadyNodeTracker")
class ReadyNodeTrackerTest {

    private MigrationGraph graph;
    private Environment testEnv;

    @BeforeEach
    void setUp() {
        graph = MigrationGraph.create();
        testEnv = SimpleEnvironment.create(EnvironmentId.of("test"), "Test Environment");
    }

    @Test
    @DisplayName("依存なしの単一ノードはinitialReadyNodesに含まれる")
    void singleNodeWithNoDependenciesAppearsInInitialReadyNodes() {
        MigrationNode nodeA = createNode("a", "Node A", Set.of());
        graph.addNode(nodeA);
        Set<NodeId> targets = Set.of(NodeId.of("a"));

        ReadyNodeTracker tracker = new ReadyNodeTracker(graph, targets);

        assertThat(tracker.initialReadyNodes()).containsExactly(NodeId.of("a"));
    }

    @Test
    @DisplayName("A→B チェーンでmarkCompleted(A)がBをreadyにする")
    void markCompletedMakesDependentReady() {
        MigrationNode nodeA = createNode("a", "Node A", Set.of());
        MigrationNode nodeB = createNode("b", "Node B", Set.of(NodeId.of("a")));
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        Set<NodeId> targets = Set.of(NodeId.of("a"), NodeId.of("b"));

        ReadyNodeTracker tracker = new ReadyNodeTracker(graph, targets);

        assertThat(tracker.initialReadyNodes()).containsExactly(NodeId.of("a"));
        assertThat(tracker.markCompleted(NodeId.of("a"))).containsExactly(NodeId.of("b"));
    }

    @Test
    @DisplayName("ダイアモンド構造で両親が完了してから子がreadyになる")
    void diamondRequiresBothParentsBeforeChildBecomesReady() {
        MigrationNode nodeA = createNode("a", "Node A", Set.of());
        MigrationNode nodeB = createNode("b", "Node B", Set.of());
        MigrationNode nodeC = createNode("c", "Node C", Set.of(NodeId.of("a"), NodeId.of("b")));
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);
        Set<NodeId> targets = Set.of(NodeId.of("a"), NodeId.of("b"), NodeId.of("c"));

        ReadyNodeTracker tracker = new ReadyNodeTracker(graph, targets);

        assertThat(tracker.initialReadyNodes())
                .containsExactlyInAnyOrder(NodeId.of("a"), NodeId.of("b"));
        assertThat(tracker.markCompleted(NodeId.of("a"))).isEmpty();
        assertThat(tracker.markCompleted(NodeId.of("b"))).containsExactly(NodeId.of("c"));
    }

    @Test
    @DisplayName("DOWN方向ではA→BチェーンのinitialReadyNodesにBだけが含まれる")
    void downDirectionInitialReadyNodesContainsLeafNodeOnly() {
        MigrationNode nodeA = createNode("a", "Node A", Set.of());
        MigrationNode nodeB = createNode("b", "Node B", Set.of(NodeId.of("a")));
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        Set<NodeId> targets = Set.of(NodeId.of("a"), NodeId.of("b"));

        ReadyNodeTracker tracker = new ReadyNodeTracker(graph, targets, ExecutionDirection.DOWN);

        assertThat(tracker.initialReadyNodes()).containsExactly(NodeId.of("b"));
    }

    @Test
    @DisplayName("DOWN方向ではmarkCompleted(B)がAをreadyにする")
    void downDirectionMarkCompletedMakesParentReady() {
        MigrationNode nodeA = createNode("a", "Node A", Set.of());
        MigrationNode nodeB = createNode("b", "Node B", Set.of(NodeId.of("a")));
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        Set<NodeId> targets = Set.of(NodeId.of("a"), NodeId.of("b"));

        ReadyNodeTracker tracker = new ReadyNodeTracker(graph, targets, ExecutionDirection.DOWN);

        assertThat(tracker.markCompleted(NodeId.of("b"))).containsExactly(NodeId.of("a"));
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
