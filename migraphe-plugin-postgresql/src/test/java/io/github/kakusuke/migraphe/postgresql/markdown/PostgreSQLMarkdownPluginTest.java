package io.github.kakusuke.migraphe.postgresql.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.generator.DefinitionResolver;
import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;
import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownDefinition;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcCheckConstraintInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcColumnInfo;
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
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(schemaDetail),
                        List.of(new PostgreSQLExtensionInfo("plpgsql", "1.0")),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());
        JdbcMarkdownDefinition definition =
                new JdbcMarkdownDefinition() {
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
        var outputContext = new OutputContext(resolver, tempDir);

        plugin.output(schemaInfo, outputContext);

        Path indexFile = tempDir.resolve("index.md");
        assertThat(indexFile).exists();
        assertThat(Files.readString(indexFile)).contains("plpgsql");

        Path tableFile = tempDir.resolve("testdb/public/tables/users.md");
        assertThat(tableFile).exists();
    }

    @Test
    void outputOmitsErDiagramWhenDefinitionDisablesIt(@TempDir Path tempDir) throws Exception {
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
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(schemaDetail),
                        List.of(new PostgreSQLExtensionInfo("plpgsql", "1.0")),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());
        JdbcMarkdownDefinition definition =
                new JdbcMarkdownDefinition() {
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
        var outputContext = new OutputContext(resolver, tempDir);

        plugin.output(schemaInfo, outputContext);

        Path indexFile = tempDir.resolve("index.md");
        assertThat(indexFile).exists();
        assertThat(Files.readString(indexFile)).doesNotContain("## ER Diagram");
    }
}
