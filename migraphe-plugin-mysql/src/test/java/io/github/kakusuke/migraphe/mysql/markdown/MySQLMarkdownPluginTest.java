package io.github.kakusuke.migraphe.mysql.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownDefinition;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import io.github.kakusuke.migraphe.mysql.schema.MySQLSchemaInfo;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class MySQLMarkdownPluginTest {

    private final MySQLMarkdownPlugin plugin = new MySQLMarkdownPlugin();

    @Test
    void typeReturnsMysqlMarkdown() {
        assertThat(plugin.type()).isEqualTo("mysql-markdown");
    }

    @Test
    void canHandleMySQLSchemaInfo() {
        assertThat(plugin.canHandle(MySQLSchemaInfo.class)).isTrue();
    }

    @Test
    void cannotHandleJdbcSchemaInfo() {
        assertThat(plugin.canHandle(JdbcSchemaInfo.class)).isFalse();
    }

    @Test
    void definitionClassReturnsJdbcMarkdownDefinition() {
        assertThat(plugin.definitionClass()).isEqualTo(JdbcMarkdownDefinition.class);
    }

    @Test
    void serviceLoaderDiscoversPlugin() {
        var plugins = ServiceLoader.load(GeneratorOutputPlugin.class);
        assertThat(plugins).anyMatch(p -> p instanceof MySQLMarkdownPlugin);
    }
}
