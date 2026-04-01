package io.github.kakusuke.migraphe.core.generator.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import io.github.kakusuke.migraphe.generator.api.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.generator.api.SourceContext;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class MigrationTreeSourcePluginTest {

    private final MigrationTreeSourcePlugin plugin = new MigrationTreeSourcePlugin();

    @Test
    void typeIsMigrationTree() {
        assertThat(plugin.type()).isEqualTo("migration-tree");
    }

    @Test
    void dataClassIsMigrationTreeData() {
        assertThat(plugin.dataClass()).isEqualTo(MigrationTreeData.class);
    }

    @Test
    void extractReturnsMigrationTreeData() {
        MigrationGraph graph = MigrationGraph.create();
        var env = SimpleEnvironment.create(EnvironmentId.of("test"), "test");
        var nodeA =
                SimpleMigrationNode.builder()
                        .id(NodeId.of("a"))
                        .name("a")
                        .environment(env)
                        .upTask(SimpleTask.of("a"))
                        .build();
        var nodeB =
                SimpleMigrationNode.builder()
                        .id(NodeId.of("b"))
                        .name("b")
                        .environment(env)
                        .upTask(SimpleTask.of("b"))
                        .build();
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addDependency(NodeId.of("b"), NodeId.of("a"));

        SourceContext context = new SourceContext(null, graph);
        MigrationTreeData result = plugin.extract(context);

        assertThat(result.nodes()).hasSize(2);
        // sorted by id
        assertThat(result.nodes().get(0).id()).isEqualTo("a");
        assertThat(result.nodes().get(0).dependencies()).isEmpty();
        assertThat(result.nodes().get(1).id()).isEqualTo("b");
        assertThat(result.nodes().get(1).dependencies()).containsExactly("a");
    }

    @Test
    void extractReturnsEmptyNodesForEmptyGraph() {
        MigrationGraph graph = MigrationGraph.create();
        SourceContext context = new SourceContext(null, graph);

        MigrationTreeData result = plugin.extract(context);

        assertThat(result.nodes()).isEmpty();
    }

    @Test
    void extractThrowsWhenGraphIsNull() {
        SourceContext context = new SourceContext(null, null);

        assertThatThrownBy(() -> plugin.extract(context))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("graph");
    }

    @Test
    void nodeEntryIncludesNameTargetAndStatus() {
        MigrationGraph graph = MigrationGraph.create();
        var env = SimpleEnvironment.create(EnvironmentId.of("test"), "test");
        var nodeA =
                SimpleMigrationNode.builder()
                        .id(NodeId.of("a"))
                        .name("a")
                        .environment(env)
                        .upTask(SimpleTask.of("a"))
                        .build();
        var nodeB =
                SimpleMigrationNode.builder()
                        .id(NodeId.of("b"))
                        .name("b")
                        .environment(env)
                        .upTask(SimpleTask.of("b"))
                        .build();
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addDependency(NodeId.of("b"), NodeId.of("a"));

        var historyRepository = new InMemoryHistoryRepository();
        historyRepository.record(
                ExecutionRecord.upSuccess(NodeId.of("a"), EnvironmentId.of("test"), "a", null, 0L));

        SourceContext context = new SourceContext(null, graph, historyRepository);
        MigrationTreeData result = plugin.extract(context);

        assertThat(result.nodes()).hasSize(2);
        var entryA = result.nodes().get(0);
        assertThat(entryA.id()).isEqualTo("a");
        assertThat(entryA.name()).isEqualTo("a");
        assertThat(entryA.target()).isEqualTo("test");
        assertThat(entryA.status()).isEqualTo("executed");
        var entryB = result.nodes().get(1);
        assertThat(entryB.id()).isEqualTo("b");
        assertThat(entryB.name()).isEqualTo("b");
        assertThat(entryB.target()).isEqualTo("test");
        assertThat(entryB.status()).isEqualTo("pending");
    }

    @Test
    void discoveredByServiceLoader() {
        var plugins = ServiceLoader.load(GeneratorSourcePlugin.class);
        var found =
                plugins.stream()
                        .map(ServiceLoader.Provider::get)
                        .filter(p -> "migration-tree".equals(p.type()))
                        .findFirst();
        assertThat(found).isPresent();
        assertThat(found.get()).isInstanceOf(MigrationTreeSourcePlugin.class);
    }
}
