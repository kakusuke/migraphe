package io.github.kakusuke.migraphe.jdbc.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.generator.api.GeneratorSourcePlugin;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class JdbcSchemaSourcePluginTest {

    private final JdbcSchemaSourcePlugin plugin = new JdbcSchemaSourcePlugin();

    @Test
    void typeReturnsJdbcSchema() {
        assertThat(plugin.type()).isEqualTo("jdbc-schema");
    }

    @Test
    void dataClassReturnsJdbcSchemaInfo() {
        assertThat(plugin.dataClass()).isEqualTo(JdbcSchemaInfo.class);
    }

    @Test
    void serviceLoaderDiscoversPlugin() {
        var plugins = ServiceLoader.load(GeneratorSourcePlugin.class);
        var found =
                plugins.stream()
                        .map(ServiceLoader.Provider::get)
                        .filter(p -> "jdbc-schema".equals(p.type()))
                        .findFirst();
        assertThat(found).isPresent();
        assertThat(found.get()).isInstanceOf(JdbcSchemaSourcePlugin.class);
    }
}
