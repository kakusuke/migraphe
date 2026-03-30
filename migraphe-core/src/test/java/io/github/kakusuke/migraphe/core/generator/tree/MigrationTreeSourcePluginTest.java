package io.github.kakusuke.migraphe.core.generator.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
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
    void dataClassIsMigrationGraphView() {
        assertThat(plugin.dataClass()).isEqualTo(MigrationGraphView.class);
    }

    @Test
    void extractReturnsGraphFromContext() {
        MigrationGraph graph = MigrationGraph.create();
        SourceContext context = new SourceContext(null, graph);

        MigrationGraphView result = plugin.extract(context);

        assertThat(result).isSameAs(graph);
    }

    @Test
    void extractThrowsWhenGraphIsNull() {
        SourceContext context = new SourceContext(null, null);

        assertThatThrownBy(() -> plugin.extract(context))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("graph");
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
