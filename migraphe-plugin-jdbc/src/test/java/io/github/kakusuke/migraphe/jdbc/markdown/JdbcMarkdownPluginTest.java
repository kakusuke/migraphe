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

    private JdbcMarkdownDefinition definition(String name, boolean erDiagram) {
        return definition(name, erDiagram, true);
    }

    private JdbcMarkdownDefinition definition(
            String name, boolean erDiagram, boolean erDiagramPerTable) {
        return definition(name, erDiagram, erDiagramPerTable, 60);
    }

    private JdbcMarkdownDefinition definition(
            String name,
            boolean erDiagram,
            boolean erDiagramPerTable,
            int erDiagramPerTableMaxEntities) {
        return new JdbcMarkdownDefinition() {
            @Override
            public String type() {
                return "jdbc-markdown";
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String outputDir() {
                return tempDir.toString();
            }

            @Override
            public boolean erDiagram() {
                return erDiagram;
            }

            @Override
            public boolean erDiagramKeysOnly() {
                return false;
            }

            @Override
            public String erDiagramLayout() {
                return "elk";
            }

            @Override
            public boolean erDiagramPerTable() {
                return erDiagramPerTable;
            }

            @Override
            public int erDiagramPerTableMaxEntities() {
                return erDiagramPerTableMaxEntities;
            }

            @Override
            public Optional<List<ExcludePattern>> excludes() {
                return Optional.empty();
            }
        };
    }

    private Path usersMdPath(String schemaName) {
        return tempDir.resolve(schemaName).resolve("tables").resolve("USERS.md");
    }

    private DefinitionResolver resolverFor(GeneratorDefinition definition) {
        return new DefinitionResolver() {
            @Override
            public <T extends GeneratorDefinition> T resolve(Class<T> klass) {
                return klass.cast(definition);
            }
        };
    }

    private JdbcSchemaInfo schemaInfoWithUsersAndOrders(String dbName) throws Exception {
        JdbcEnvironment env =
                JdbcEnvironment.create(
                        dbName,
                        "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1",
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
        return new JdbcSchemaInfoProvider().getSchemaInfo(env);
    }

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
        JdbcMarkdownDefinition definition = definition("testdb", true);
        DefinitionResolver resolver = resolverFor(definition);
        var context = new OutputContext(resolver, tempDir);
        var outputPlugin = (GeneratorOutputPlugin) plugin;

        outputPlugin.output(schemaInfo, context);

        var indexMd = tempDir.resolve("index.md");
        assertThat(indexMd).exists();
        assertThat(Files.readString(indexMd)).startsWith("# testdb");
    }

    @Test
    void outputOmitsErDiagramWhenDefinitionDisablesIt() throws IOException {
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of());
        JdbcMarkdownDefinition definition = definition("testdb", false);
        DefinitionResolver resolver = resolverFor(definition);
        var context = new OutputContext(resolver, tempDir);
        var outputPlugin = (GeneratorOutputPlugin) plugin;

        outputPlugin.output(schemaInfo, context);

        var indexMd = tempDir.resolve("index.md");
        assertThat(Files.readString(indexMd)).doesNotContain("## ER Diagram");
    }

    @Test
    void outputExportedKeyLinksToReferencingTable() throws Exception {
        JdbcSchemaInfo schemaInfo = schemaInfoWithUsersAndOrders("exported_key_link_test");
        JdbcMarkdownDefinition definition = definition("exported-key-test", true);
        DefinitionResolver resolver = resolverFor(definition);
        var context = new OutputContext(resolver, tempDir);
        var outputPlugin = (GeneratorOutputPlugin) plugin;

        outputPlugin.output(schemaInfo, context);

        String schemaName = schemaInfo.schemas().get(0).name();
        var usersMd = usersMdPath(schemaName);
        assertThat(Files.readString(usersMd))
                .contains("[ORDERS](../../" + schemaName + "/tables/ORDERS.md)");
    }

    @Test
    void outputOmitsErDiagramOnTablePageWhenPerTableDisabled() throws Exception {
        JdbcSchemaInfo schemaInfo = schemaInfoWithUsersAndOrders("er_diagram_per_table_test");
        JdbcMarkdownDefinition definition = definition("er-diagram-per-table-test", true, false);
        DefinitionResolver resolver = resolverFor(definition);
        var context = new OutputContext(resolver, tempDir);
        var outputPlugin = (GeneratorOutputPlugin) plugin;

        outputPlugin.output(schemaInfo, context);

        String schemaName = schemaInfo.schemas().get(0).name();
        var usersMd = usersMdPath(schemaName);
        assertThat(Files.readString(usersMd)).doesNotContain("## ER Diagram");

        var indexMd = tempDir.resolve("index.md");
        assertThat(Files.readString(indexMd)).contains("## ER Diagram");
    }

    @Test
    void outputPassesErDiagramLayoutToGenerator() throws Exception {
        JdbcSchemaInfo schemaInfo = schemaInfoWithUsersAndOrders("er_diagram_layout_test");
        JdbcMarkdownDefinition definition = definition("er-diagram-layout-test", true);
        DefinitionResolver resolver = resolverFor(definition);
        var context = new OutputContext(resolver, tempDir);
        var outputPlugin = (GeneratorOutputPlugin) plugin;

        outputPlugin.output(schemaInfo, context);

        var indexMd = tempDir.resolve("index.md");
        assertThat(Files.readString(indexMd))
                .contains("---\nconfig:\n  layout: elk\n---\nerDiagram\n");
    }

    @Test
    void outputOmitsPerTableErDiagramWhenNeighborhoodExceedsMaxEntities() throws Exception {
        JdbcSchemaInfo schemaInfo = schemaInfoWithUsersAndOrders("er_diagram_max_entities_test");
        JdbcMarkdownDefinition definition =
                definition("er-diagram-max-entities-test", true, true, 1);
        DefinitionResolver resolver = resolverFor(definition);
        var context = new OutputContext(resolver, tempDir);
        var outputPlugin = (GeneratorOutputPlugin) plugin;

        outputPlugin.output(schemaInfo, context);

        String schemaName = schemaInfo.schemas().get(0).name();
        var usersMd = usersMdPath(schemaName);
        String content = Files.readString(usersMd);
        assertThat(content).contains("## ER Diagram");
        assertThat(content).doesNotContain("```mermaid");
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
