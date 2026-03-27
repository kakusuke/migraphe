package io.github.kakusuke.migraphe.jdbc.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.generator.api.GeneratorPlugin;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class JdbcMarkdownPluginTest {

    private final JdbcMarkdownPlugin plugin = new JdbcMarkdownPlugin();

    @Test
    void typeIsJdbcMarkdown() {
        assertThat(plugin.type()).isEqualTo("jdbc-markdown");
    }

    @Test
    void definitionClassIsJdbcMarkdownDefinition() {
        assertThat(plugin.definitionClass()).isEqualTo(JdbcMarkdownDefinition.class);
    }

    @Test
    void discoveredByServiceLoader() {
        var plugins = ServiceLoader.load(GeneratorPlugin.class);
        var found =
                plugins.stream()
                        .map(ServiceLoader.Provider::get)
                        .filter(p -> "jdbc-markdown".equals(p.type()))
                        .findFirst();
        assertThat(found).isPresent();
        assertThat(found.get()).isInstanceOf(JdbcMarkdownPlugin.class);
    }
}
