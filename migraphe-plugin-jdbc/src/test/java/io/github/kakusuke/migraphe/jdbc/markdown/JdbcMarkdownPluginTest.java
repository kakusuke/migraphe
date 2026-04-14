package io.github.kakusuke.migraphe.jdbc.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;
import io.github.kakusuke.migraphe.jdbc.schema.DefaultJdbcSchemaInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcMarkdownPluginTest {

    @TempDir Path tempDir;

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
    void implementsGeneratorOutputPluginAndHandlesJdbcSchemaInfo() {
        assertThat(plugin).isInstanceOf(GeneratorOutputPlugin.class);
        var outputPlugin = (GeneratorOutputPlugin) plugin;
        assertThat(outputPlugin.canHandle(JdbcSchemaInfo.class)).isTrue();
        assertThat(outputPlugin.canHandle(String.class)).isFalse();
    }

    @Test
    void outputGeneratesIndexMdWithDatabaseName() throws IOException {
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of());
        JdbcMarkdownDefinition definition =
                new JdbcMarkdownDefinition() {
                    @Override
                    public String type() {
                        return "jdbc-markdown";
                    }

                    @Override
                    public String target() {
                        return "test-target";
                    }

                    @Override
                    public String name() {
                        return "testdb";
                    }

                    @Override
                    public String outputDir() {
                        return tempDir.toString();
                    }

                    @Override
                    public Optional<List<ExcludePattern>> excludes() {
                        return Optional.empty();
                    }
                };
        var context = new OutputContext(definition, tempDir);
        var outputPlugin = (GeneratorOutputPlugin) plugin;

        outputPlugin.output(schemaInfo, context);

        var indexMd = tempDir.resolve("index.md");
        assertThat(indexMd).exists();
        assertThat(Files.readString(indexMd)).startsWith("# Database: testdb");
    }

    @Test
    void discoveredByServiceLoaderAsOutputPlugin() {
        var plugins = ServiceLoader.load(GeneratorOutputPlugin.class);
        var found =
                plugins.stream()
                        .map(ServiceLoader.Provider::get)
                        .filter(p -> "jdbc-markdown".equals(p.type()))
                        .findFirst();
        assertThat(found).isPresent();
        assertThat(found.get()).isInstanceOf(JdbcMarkdownPlugin.class);
    }
}
