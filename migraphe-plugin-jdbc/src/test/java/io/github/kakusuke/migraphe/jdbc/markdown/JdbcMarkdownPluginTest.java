package io.github.kakusuke.migraphe.jdbc.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.generator.DefinitionResolver;
import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;
import io.github.kakusuke.migraphe.jdbc.JdbcEnvironment;
import io.github.kakusuke.migraphe.jdbc.schema.DefaultJdbcSchemaInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfoProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
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
                    public String name() {
                        return "testdb";
                    }

                    @Override
                    public String outputDir() {
                        return tempDir.toString();
                    }

                    @Override
                    public boolean erDiagram() {
                        return true;
                    }

                    @Override
                    public boolean erDiagramKeysOnly() {
                        return false;
                    }

                    @Override
                    public Optional<List<ExcludePattern>> excludes() {
                        return Optional.empty();
                    }
                };
        DefinitionResolver resolver =
                new DefinitionResolver() {
                    @Override
                    public <T extends GeneratorDefinition> T resolve(Class<T> klass) {
                        return klass.cast(definition);
                    }
                };
        var context = new OutputContext(resolver, tempDir);
        var outputPlugin = (GeneratorOutputPlugin) plugin;

        outputPlugin.output(schemaInfo, context);

        var indexMd = tempDir.resolve("index.md");
        assertThat(indexMd).exists();
        assertThat(Files.readString(indexMd)).startsWith("# Database: testdb");
    }

    @Test
    void outputOmitsErDiagramWhenDefinitionDisablesIt() throws IOException {
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of());
        JdbcMarkdownDefinition definition =
                new JdbcMarkdownDefinition() {
                    @Override
                    public String type() {
                        return "jdbc-markdown";
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
                    public boolean erDiagram() {
                        return false;
                    }

                    @Override
                    public boolean erDiagramKeysOnly() {
                        return false;
                    }

                    @Override
                    public Optional<List<ExcludePattern>> excludes() {
                        return Optional.empty();
                    }
                };
        DefinitionResolver resolver =
                new DefinitionResolver() {
                    @Override
                    public <T extends GeneratorDefinition> T resolve(Class<T> klass) {
                        return klass.cast(definition);
                    }
                };
        var context = new OutputContext(resolver, tempDir);
        var outputPlugin = (GeneratorOutputPlugin) plugin;

        outputPlugin.output(schemaInfo, context);

        var indexMd = tempDir.resolve("index.md");
        assertThat(Files.readString(indexMd)).doesNotContain("## ER Diagram");
    }

    @Test
    void outputExportedKeyLinksToReferencingTable() throws Exception {
        JdbcEnvironment env =
                JdbcEnvironment.create(
                        "exported_key_link_test",
                        "jdbc:h2:mem:exported_key_link_test;DB_CLOSE_DELAY=-1",
                        "sa",
                        "",
                        "org.h2.Driver",
                        "H2");
        try (Connection conn = env.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS orders");
            stmt.execute("DROP TABLE IF EXISTS users");
            stmt.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(100))");
            stmt.execute(
                    "CREATE TABLE orders ("
                            + "id INTEGER PRIMARY KEY, "
                            + "user_id INTEGER, "
                            + "CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES"
                            + " users(id))");
        }
        JdbcSchemaInfo schemaInfo = new JdbcSchemaInfoProvider().getSchemaInfo(env);
        JdbcMarkdownDefinition definition =
                new JdbcMarkdownDefinition() {
                    @Override
                    public String type() {
                        return "jdbc-markdown";
                    }

                    @Override
                    public String name() {
                        return "exported-key-test";
                    }

                    @Override
                    public String outputDir() {
                        return tempDir.toString();
                    }

                    @Override
                    public boolean erDiagram() {
                        return true;
                    }

                    @Override
                    public boolean erDiagramKeysOnly() {
                        return false;
                    }

                    @Override
                    public Optional<List<ExcludePattern>> excludes() {
                        return Optional.empty();
                    }
                };
        DefinitionResolver resolver =
                new DefinitionResolver() {
                    @Override
                    public <T extends GeneratorDefinition> T resolve(Class<T> klass) {
                        return klass.cast(definition);
                    }
                };
        var context = new OutputContext(resolver, tempDir);
        var outputPlugin = (GeneratorOutputPlugin) plugin;

        outputPlugin.output(schemaInfo, context);

        String schemaName = schemaInfo.schemas().get(0).name();
        var usersMd =
                tempDir.resolve("exported-key-test")
                        .resolve(schemaName)
                        .resolve("tables")
                        .resolve("USERS.md");
        assertThat(Files.readString(usersMd))
                .contains("[ORDERS](../../" + schemaName + "/tables/ORDERS.md)");
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
