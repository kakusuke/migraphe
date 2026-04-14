package io.github.kakusuke.migraphe.postgresql.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class PostgreSQLSchemaSourcePluginTest {

    private final PostgreSQLSchemaSourcePlugin plugin = new PostgreSQLSchemaSourcePlugin();

    @Test
    void typeReturnsPostgresqlSchema() {
        assertThat(plugin.type()).isEqualTo("postgresql-schema");
    }

    @Test
    void dataClassReturnsPostgreSQLSchemaInfo() {
        assertThat(plugin.dataClass()).isEqualTo(PostgreSQLSchemaInfo.class);
    }

    @Test
    void serviceLoaderDiscoversPlugin() {
        var plugins = ServiceLoader.load(GeneratorSourcePlugin.class);
        var found =
                plugins.stream()
                        .map(ServiceLoader.Provider::get)
                        .filter(p -> "postgresql-schema".equals(p.type()))
                        .findFirst();
        assertThat(found).isPresent();
        assertThat(found.get()).isInstanceOf(PostgreSQLSchemaSourcePlugin.class);
    }
}
