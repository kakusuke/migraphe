package io.github.kakusuke.migraphe.postgresql.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownDefinition;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcCheckConstraintInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcColumnInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcPrimaryKeyInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcRoutineInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaDetail;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSequenceInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcTableInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcTriggerInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcUdtInfo;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLEnumInfo;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLExtensionInfo;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLFunctionInfo;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLMaterializedViewInfo;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLPartitionInfo;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLPolicyInfo;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLSchemaInfo;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLSequenceInfo;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLTriggerInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostgreSQLMarkdownGeneratorTest {

    @Test
    void generatesIndexMdAndTableFileFromPostgreSQLSchemaInfo(@TempDir Path tempDir)
            throws Exception {
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
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        Path indexFile = tempDir.resolve("index.md");
        assertThat(indexFile).exists();
        String indexContent = Files.readString(indexFile);
        assertThat(indexContent).contains("# Database: testdb").contains("users");

        Path tableFile = tempDir.resolve("testdb/public/tables/users.md");
        assertThat(tableFile).exists();
        String tableContent = Files.readString(tableFile);
        assertThat(tableContent).contains("# users").contains("id");
    }

    @Test
    void generatesEnumsSection(@TempDir Path tempDir) throws Exception {
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(schemaDetail),
                        List.of(),
                        List.of(new PostgreSQLEnumInfo("mood", List.of("happy", "sad", "ok"))),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent)
                .contains("## Enum Types")
                .contains("mood")
                .contains("happy, sad, ok");
    }

    @Test
    void generatesExtensionsSection(@TempDir Path tempDir) throws Exception {
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(schemaDetail),
                        List.of(
                                new PostgreSQLExtensionInfo("plpgsql", "1.0"),
                                new PostgreSQLExtensionInfo("uuid-ossp", "1.1")),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent)
                .contains("## Extensions")
                .contains("plpgsql")
                .contains("1.0")
                .contains("uuid-ossp")
                .contains("1.1");
    }

    @Test
    void generatesSequencesSection(@TempDir Path tempDir) throws Exception {
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(schemaDetail),
                        List.of(),
                        List.of(),
                        List.of(
                                new PostgreSQLSequenceInfo(
                                        "public",
                                        "users_id_seq",
                                        "bigint",
                                        1,
                                        1,
                                        1,
                                        9999,
                                        false,
                                        null,
                                        null)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent)
                .contains("### Sequences")
                .contains("users_id_seq")
                .contains("bigint");
    }

    @Test
    void generatesFunctionsSection(@TempDir Path tempDir) throws Exception {
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(schemaDetail),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new PostgreSQLFunctionInfo(
                                        "public",
                                        "add_nums",
                                        "a integer, b integer",
                                        "integer",
                                        "sql",
                                        false)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent).contains("### Functions").contains("add_nums");

        Path functionFile =
                tempDir.resolve("testdb/public/functions/add_nums_a_integer_b_integer.md");
        assertThat(functionFile).exists();
        String functionContent = Files.readString(functionFile);
        assertThat(functionContent).contains("# add_nums").contains("sql").contains("integer");
    }

    @Test
    void generatesMaterializedViewsSection(@TempDir Path tempDir) throws Exception {
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(schemaDetail),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new PostgreSQLMaterializedViewInfo(
                                        "mv_test", "public", "SELECT 1", null)),
                        List.of(),
                        List.of());

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent).contains("### Materialized Views").contains("mv_test");

        Path matViewFile = tempDir.resolve("testdb/public/materialized-views/mv_test.md");
        assertThat(matViewFile).exists();
        String matViewContent = Files.readString(matViewFile);
        assertThat(matViewContent).contains("# mv_test").contains("SELECT 1");
    }

    @Test
    void generatesTriggersSection(@TempDir Path tempDir) throws Exception {
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(schemaDetail),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new PostgreSQLTriggerInfo(
                                        "audit_trig",
                                        "public",
                                        "users",
                                        "BEFORE",
                                        List.of("INSERT"),
                                        "audit_func",
                                        false)),
                        List.of(),
                        List.of(),
                        List.of());

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent)
                .contains("### Triggers")
                .contains("audit_trig")
                .contains("BEFORE")
                .contains("INSERT")
                .contains("audit_func");
    }

    @Test
    void generatesPartitionsSection(@TempDir Path tempDir) throws Exception {
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(schemaDetail),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new PostgreSQLPartitionInfo(
                                        "events", "public", "RANGE", "event_date")),
                        List.of());

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent)
                .contains("### Partitions")
                .contains("events")
                .contains("RANGE")
                .contains("event_date");
    }

    @Test
    void generatesPoliciesSection(@TempDir Path tempDir) throws Exception {
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(schemaDetail),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new PostgreSQLPolicyInfo(
                                        "owner_only",
                                        "public",
                                        "docs",
                                        "ALL",
                                        List.of(),
                                        "owner = current_user",
                                        null)));

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent)
                .contains("### Policies")
                .contains("owner_only")
                .contains("docs")
                .contains("ALL")
                .contains("owner = current_user");
    }

    @Test
    void appendsTriggersToTableFile(@TempDir Path tempDir) throws Exception {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var usersTable =
                new JdbcTableInfo(
                        "users",
                        "",
                        List.of(idColumn),
                        null,
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
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new PostgreSQLTriggerInfo(
                                        "audit_trig",
                                        "public",
                                        "users",
                                        "BEFORE",
                                        List.of("INSERT"),
                                        "audit_func",
                                        false)),
                        List.of(),
                        List.of(),
                        List.of());

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        Path tableFile = tempDir.resolve("testdb/public/tables/users.md");
        assertThat(tableFile).exists();
        String tableContent = Files.readString(tableFile);
        assertThat(tableContent)
                .contains("## Triggers")
                .contains("audit_trig")
                .contains("BEFORE")
                .contains("INSERT")
                .contains("audit_func");
    }

    @Test
    void appendsPoliciesToTableFile(@TempDir Path tempDir) throws Exception {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var docsTable =
                new JdbcTableInfo(
                        "docs",
                        "",
                        List.of(idColumn),
                        null,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(docsTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(schemaDetail),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new PostgreSQLPolicyInfo(
                                        "owner_only",
                                        "public",
                                        "docs",
                                        "ALL",
                                        List.of(),
                                        "owner = current_user",
                                        null)));

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        Path tableFile = tempDir.resolve("testdb/public/tables/docs.md");
        assertThat(tableFile).exists();
        String tableContent = Files.readString(tableFile);
        assertThat(tableContent)
                .contains("## Policies")
                .contains("owner_only")
                .contains("ALL")
                .contains("owner = current_user");
    }

    @Test
    void appendsPartitionInfoToTableFile(@TempDir Path tempDir) throws Exception {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var eventsTable =
                new JdbcTableInfo(
                        "events",
                        "",
                        List.of(idColumn),
                        null,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(eventsTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(schemaDetail),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new PostgreSQLPartitionInfo(
                                        "events", "public", "RANGE", "event_date")),
                        List.of());

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        Path tableFile = tempDir.resolve("testdb/public/tables/events.md");
        assertThat(tableFile).exists();
        String tableContent = Files.readString(tableFile);
        assertThat(tableContent)
                .contains("## Partition Info")
                .contains("RANGE")
                .contains("event_date");
    }
}
