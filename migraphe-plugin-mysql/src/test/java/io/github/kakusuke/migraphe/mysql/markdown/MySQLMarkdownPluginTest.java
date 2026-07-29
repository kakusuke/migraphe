package io.github.kakusuke.migraphe.mysql.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.generator.DefinitionResolver;
import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;
import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownDefinition;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcCheckConstraintInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcColumnInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcForeignKeyInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcPrimaryKeyInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcRoutineInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaDetail;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSequenceInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcTableInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcTriggerInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcUdtInfo;
import io.github.kakusuke.migraphe.mysql.schema.MySQLSchemaInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MySQLMarkdownPluginTest {

    private final MySQLMarkdownPlugin plugin = new MySQLMarkdownPlugin();

    private JdbcMarkdownDefinition definition(boolean erDiagram) {
        return definition(erDiagram, true);
    }

    private JdbcMarkdownDefinition definition(boolean erDiagram, boolean erDiagramPerTable) {
        return definition(erDiagram, erDiagramPerTable, 60);
    }

    private JdbcMarkdownDefinition definition(
            boolean erDiagram, boolean erDiagramPerTable, int erDiagramPerTableMaxEntities) {
        return new JdbcMarkdownDefinition() {
            @Override
            public String type() {
                return "mysql-markdown";
            }

            @Override
            public String name() {
                return "testdb";
            }

            @Override
            public String outputDir() {
                return "docs/schema";
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

    private DefinitionResolver resolverFor(GeneratorDefinition definition) {
        return new DefinitionResolver() {
            @Override
            public <T extends GeneratorDefinition> T resolve(Class<T> klass) {
                return klass.cast(definition);
            }
        };
    }

    private MySQLSchemaInfo schemaInfoWithUsersTable() {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "INT", Types.INTEGER, 10, 0, false, null, true, false, null, 1);
        var pk = new JdbcPrimaryKeyInfo("PRIMARY", List.of("id"));
        var table =
                new JdbcTableInfo(
                        "users",
                        "",
                        List.of(idColumn),
                        pk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var schemaDetail =
                new JdbcSchemaDetail(
                        "mydb",
                        List.of(table),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        return new MySQLSchemaInfo(
                List.of(schemaDetail),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private MySQLSchemaInfo schemaInfoWithUsersAndOrdersTable() {
        var usersIdColumn =
                new JdbcColumnInfo(
                        "id", "INT", Types.INTEGER, 10, 0, false, null, true, false, null, 1);
        var usersPk = new JdbcPrimaryKeyInfo("PRIMARY", List.of("id"));
        var usersTable =
                new JdbcTableInfo(
                        "users",
                        "",
                        List.of(usersIdColumn),
                        usersPk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var ordersIdColumn =
                new JdbcColumnInfo(
                        "id", "INT", Types.INTEGER, 10, 0, false, null, true, false, null, 1);
        var ordersUserIdColumn =
                new JdbcColumnInfo(
                        "user_id", "INT", Types.INTEGER, 10, 0, false, null, false, false, null, 2);
        var ordersPk = new JdbcPrimaryKeyInfo("PRIMARY", List.of("id"));
        var fkOrdersUsers =
                new JdbcForeignKeyInfo(
                        "fk_orders_user_id",
                        List.of("user_id"),
                        "mydb",
                        "users",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var ordersTable =
                new JdbcTableInfo(
                        "orders",
                        "",
                        List.of(ordersIdColumn, ordersUserIdColumn),
                        ordersPk,
                        List.of(fkOrdersUsers),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var schemaDetail =
                new JdbcSchemaDetail(
                        "mydb",
                        List.of(usersTable, ordersTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        return new MySQLSchemaInfo(
                List.of(schemaDetail),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

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

    @Test
    void outputPassesErDiagramLayoutToGenerator(@TempDir Path tempDir) throws Exception {
        var schemaInfo = schemaInfoWithUsersTable();
        JdbcMarkdownDefinition definition = definition(true);
        DefinitionResolver resolver = resolverFor(definition);
        var outputContext = new OutputContext(resolver, tempDir);

        plugin.output(schemaInfo, outputContext);

        Path indexFile = tempDir.resolve("index.md");
        assertThat(Files.readString(indexFile))
                .contains("---\nconfig:\n  layout: elk\n---\nerDiagram\n");
    }

    @Test
    void outputGeneratesIndexMdWithDatabaseName(@TempDir Path tempDir) throws Exception {
        var schemaInfo = schemaInfoWithUsersTable();
        JdbcMarkdownDefinition definition = definition(true);
        DefinitionResolver resolver = resolverFor(definition);
        var outputContext = new OutputContext(resolver, tempDir);

        plugin.output(schemaInfo, outputContext);

        Path indexFile = tempDir.resolve("index.md");
        assertThat(indexFile).exists();
        assertThat(Files.readString(indexFile)).startsWith("# Database: testdb");
    }

    @Test
    void outputOmitsErDiagramWhenDefinitionDisablesIt(@TempDir Path tempDir) throws Exception {
        var schemaInfo = schemaInfoWithUsersTable();
        JdbcMarkdownDefinition definition = definition(false);
        DefinitionResolver resolver = resolverFor(definition);
        var outputContext = new OutputContext(resolver, tempDir);

        plugin.output(schemaInfo, outputContext);

        Path indexFile = tempDir.resolve("index.md");
        assertThat(indexFile).exists();
        assertThat(Files.readString(indexFile)).doesNotContain("## ER Diagram");
    }

    @Test
    void outputOmitsErDiagramOnTablePageWhenPerTableDisabled(@TempDir Path tempDir)
            throws Exception {
        var schemaInfo = schemaInfoWithUsersTable();
        JdbcMarkdownDefinition definition = definition(true, false);
        DefinitionResolver resolver = resolverFor(definition);
        var outputContext = new OutputContext(resolver, tempDir);

        plugin.output(schemaInfo, outputContext);

        Path tableFile =
                tempDir.resolve(definition.name())
                        .resolve("mydb")
                        .resolve("tables")
                        .resolve("users.md");
        assertThat(Files.readString(tableFile)).doesNotContain("## ER Diagram");

        Path indexFile = tempDir.resolve("index.md");
        assertThat(Files.readString(indexFile)).contains("## ER Diagram");
    }

    @Test
    void outputOmitsPerTableErDiagramWhenNeighborhoodExceedsMaxEntities(@TempDir Path tempDir)
            throws Exception {
        var schemaInfo = schemaInfoWithUsersAndOrdersTable();
        JdbcMarkdownDefinition definition = definition(true, true, 1);
        DefinitionResolver resolver = resolverFor(definition);
        var outputContext = new OutputContext(resolver, tempDir);

        plugin.output(schemaInfo, outputContext);

        Path tableFile =
                tempDir.resolve(definition.name())
                        .resolve("mydb")
                        .resolve("tables")
                        .resolve("users.md");
        String content = Files.readString(tableFile);
        assertThat(content).contains("## ER Diagram");
        assertThat(content).doesNotContain("```mermaid");
    }
}
