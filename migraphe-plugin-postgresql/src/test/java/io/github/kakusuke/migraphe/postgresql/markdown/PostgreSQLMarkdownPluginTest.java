package io.github.kakusuke.migraphe.postgresql.markdown;

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
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLExtensionInfo;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLSchemaInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostgreSQLMarkdownPluginTest {

    private final PostgreSQLMarkdownPlugin plugin = new PostgreSQLMarkdownPlugin();

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
                return "postgresql-markdown";
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

    private PostgreSQLSchemaInfo schemaInfoWithUsersTable() {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var usersPk = new JdbcPrimaryKeyInfo("pk_users", List.of("id"));
        var usersTable =
                new JdbcTableInfo(
                        "users",
                        "",
                        List.of(idColumn),
                        usersPk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(usersTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        return new PostgreSQLSchemaInfo(
                List.of(schemaDetail),
                List.of(new PostgreSQLExtensionInfo("plpgsql", "1.0")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private PostgreSQLSchemaInfo schemaInfoWithUsersAndOrdersTable() {
        var usersIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var usersPk = new JdbcPrimaryKeyInfo("pk_users", List.of("id"));
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
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var ordersUserIdColumn =
                new JdbcColumnInfo(
                        "user_id",
                        "INTEGER",
                        Types.INTEGER,
                        10,
                        0,
                        false,
                        null,
                        false,
                        false,
                        null,
                        2);
        var ordersPk = new JdbcPrimaryKeyInfo("pk_orders", List.of("id"));
        var fkOrdersUsers =
                new JdbcForeignKeyInfo(
                        "fk_orders_user_id",
                        List.of("user_id"),
                        "public",
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
                        "public",
                        List.of(usersTable, ordersTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        return new PostgreSQLSchemaInfo(
                List.of(schemaDetail),
                List.of(new PostgreSQLExtensionInfo("plpgsql", "1.0")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    void serviceLoaderDiscoversPlugin() {
        var plugins = ServiceLoader.load(GeneratorOutputPlugin.class);
        var found =
                plugins.stream()
                        .map(ServiceLoader.Provider::get)
                        .filter(p -> "postgresql-markdown".equals(p.type()))
                        .findFirst();
        assertThat(found).isPresent();
        assertThat(found.get()).isInstanceOf(PostgreSQLMarkdownPlugin.class);
    }

    @Test
    void typeIsPostgresqlMarkdown() {
        assertThat(plugin.type()).isEqualTo("postgresql-markdown");
    }

    @Test
    void canHandlePostgreSQLSchemaInfo() {
        assertThat(plugin.canHandle(PostgreSQLSchemaInfo.class)).isTrue();
    }

    @Test
    void cannotHandleJdbcSchemaInfo() {
        assertThat(plugin.canHandle(JdbcSchemaInfo.class)).isFalse();
    }

    @Test
    void definitionClassIsJdbcMarkdownDefinition() {
        assertThat(plugin.definitionClass()).isEqualTo(JdbcMarkdownDefinition.class);
    }

    @Test
    void outputGeneratesMarkdownFiles(@TempDir Path tempDir) throws Exception {
        var schemaInfo = schemaInfoWithUsersTable();
        JdbcMarkdownDefinition definition = definition(true);
        DefinitionResolver resolver = resolverFor(definition);
        var outputContext = new OutputContext(resolver, tempDir);

        plugin.output(schemaInfo, outputContext);

        Path indexFile = tempDir.resolve("index.md");
        assertThat(indexFile).exists();
        assertThat(Files.readString(indexFile)).contains("plpgsql");

        Path tableFile = tempDir.resolve("public/tables/users.md");
        assertThat(tableFile).exists();
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
    void outputOmitsErDiagramOnTablePageWhenPerTableDisabled(@TempDir Path tempDir)
            throws Exception {
        var schemaInfo = schemaInfoWithUsersTable();
        JdbcMarkdownDefinition definition = definition(true, false);
        DefinitionResolver resolver = resolverFor(definition);
        var outputContext = new OutputContext(resolver, tempDir);

        plugin.output(schemaInfo, outputContext);

        Path tableFile = tempDir.resolve("public").resolve("tables").resolve("users.md");
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

        Path tableFile = tempDir.resolve("public").resolve("tables").resolve("users.md");
        String content = Files.readString(tableFile);
        assertThat(content).contains("## ER Diagram");
        assertThat(content).doesNotContain("```mermaid");
    }
}
