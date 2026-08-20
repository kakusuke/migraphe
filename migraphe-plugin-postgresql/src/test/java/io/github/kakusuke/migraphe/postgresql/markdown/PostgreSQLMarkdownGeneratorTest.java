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
import io.github.kakusuke.migraphe.jdbc.schema.JdbcViewInfo;
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
import java.util.Map;
import java.util.Optional;
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
        assertThat(indexContent).contains("# testdb").contains("users");

        Path tableFile = tempDir.resolve("public/tables/users.md");
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

        Path functionFile = tempDir.resolve("public/functions/add_nums_a_integer_b_integer.md");
        assertThat(functionFile).exists();
        String functionContent = Files.readString(functionFile);
        assertThat(functionContent).contains("# add_nums").contains("sql").contains("integer");
        assertThat(functionContent).doesNotContain("## Definition");
    }

    @Test
    void functionFileIncludesDefinitionBody(@TempDir Path tempDir) throws Exception {
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
                                        false,
                                        "postgres",
                                        "SELECT a + b")),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String functionContent =
                Files.readString(
                        tempDir.resolve("public/functions/add_nums_a_integer_b_integer.md"));
        assertThat(functionContent).contains("## Definition\n\n```sql\nSELECT a + b\n```\n");
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

        Path matViewFile = tempDir.resolve("public/materialized-views/mv_test.md");
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

        Path tableFile = tempDir.resolve("public/tables/users.md");
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

        Path tableFile = tempDir.resolve("public/tables/docs.md");
        assertThat(tableFile).exists();
        String tableContent = Files.readString(tableFile);
        assertThat(tableContent)
                .contains("## Policies")
                .contains("owner_only")
                .contains("ALL")
                .contains("owner = current_user");
    }

    @Test
    void extensionsTableHasOwnerColumn(@TempDir Path tempDir) throws Exception {
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
                        List.of(new PostgreSQLExtensionInfo("uuid-ossp", "1.1", "alice")),
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
                .contains("| Name | Version | Owner |")
                .containsPattern("\\| uuid-ossp \\| 1\\.1 \\| alice \\|");
    }

    @Test
    void enumTypesTableHasOwnerColumn(@TempDir Path tempDir) throws Exception {
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
                        List.of(new PostgreSQLEnumInfo("mood", List.of("happy", "sad"), "alice")),
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
                .contains("| Name | Labels | Owner |")
                .containsPattern("\\| mood \\| happy, sad \\| alice \\|");
    }

    @Test
    void functionsFileHasOwnerRow(@TempDir Path tempDir) throws Exception {
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
                                        false,
                                        "alice")),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        Path functionFile = tempDir.resolve("public/functions/add_nums_a_integer_b_integer.md");
        String content = Files.readString(functionFile);
        assertThat(content).contains("| Owner | alice |");
    }

    @Test
    void materializedViewsFileHasOwnerRow(@TempDir Path tempDir) throws Exception {
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
                                        "mv_test", "public", "SELECT 1", null, "alice")),
                        List.of(),
                        List.of());

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        Path mvFile = tempDir.resolve("public/materialized-views/mv_test.md");
        String content = Files.readString(mvFile);
        assertThat(content).contains("| Owner | alice |");
    }

    @Test
    void sequencesTableHasOwnedByAndOwnerColumns(@TempDir Path tempDir) throws Exception {
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
                                        "users",
                                        "id",
                                        "alice")),
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
                .contains(
                        "| Name | Type | Start | Increment | Min | Max | Cycle | Owned By | Owner"
                                + " |")
                .containsPattern(
                        "\\| users_id_seq \\| bigint \\| 1 \\| 1 \\| 1 \\| 9999 \\| false \\|"
                                + " users\\.id \\| alice \\|");
    }

    @Test
    void tableOwnerAppearsInIndexColumnAndTableFileHeader(@TempDir Path tempDir) throws Exception {
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
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of("public.users", "alice"),
                        Map.of());

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent).contains("| Name | Owner | Remarks |");
        assertThat(indexContent)
                .containsPattern("\\| \\[users\\]\\(public/tables/users\\.md\\) \\| alice \\|");

        String tableContent = Files.readString(tempDir.resolve("public/tables/users.md"));
        assertThat(tableContent).startsWith("# users\n\nOwner: alice\n\n");
    }

    @Test
    void viewOwnerAppearsInIndexColumnAndViewFileHeader(@TempDir Path tempDir) throws Exception {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var viewInfo = new JdbcViewInfo("active_users", "", List.of(idColumn), "SELECT 1");
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(),
                        List.of(viewInfo),
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
                        List.of(),
                        Map.of(),
                        Map.of("public.active_users", "bob"));

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent).contains("| Name | Owner | Remarks |");
        assertThat(indexContent)
                .containsPattern(
                        "\\| \\[active_users\\]\\(public/views/active_users\\.md\\) \\| bob"
                                + " \\|");

        String viewContent = Files.readString(tempDir.resolve("public/views/active_users.md"));
        assertThat(viewContent).startsWith("# active_users\n\nOwner: bob\n\n");
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

        Path tableFile = tempDir.resolve("public/tables/events.md");
        assertThat(tableFile).exists();
        String tableContent = Files.readString(tableFile);
        assertThat(tableContent)
                .contains("## Partition Info")
                .contains("RANGE")
                .contains("event_date");
    }

    @Test
    void sequencesOwnedByIsLinkWhenOwnerTableExistsInSchema(@TempDir Path tempDir)
            throws Exception {
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
                                        "users",
                                        "id",
                                        "alice")),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of(),
                        Map.of());

        var generator =
                new PostgreSQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent)
                .containsPattern(
                        "\\| users_id_seq \\| bigint \\| 1 \\| 1 \\| 1 \\| 9999 \\| false \\|"
                                + " \\[users\\.id\\]\\(public/tables/users\\.md\\) \\| alice"
                                + " \\|");
    }

    @Test
    void sequencesOwnedByFallsBackToTextWhenOwnerTableExcluded(@TempDir Path tempDir)
            throws Exception {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "BIGINT", Types.BIGINT, 19, 0, false, null, false, false, null, 1);
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
                                        "users",
                                        "id",
                                        "alice")),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of(),
                        Map.of());
        var excludePattern =
                new JdbcMarkdownDefinition.ExcludePattern() {
                    @Override
                    public Optional<String> schema() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<String> table() {
                        return Optional.of("users");
                    }
                };

        var generator =
                new PostgreSQLMarkdownGenerator("testdb", schemaInfo, List.of(excludePattern));

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent)
                .containsPattern(
                        "\\| users_id_seq \\| bigint \\| 1 \\| 1 \\| 1 \\| 9999 \\| false \\|"
                                + " users\\.id \\| alice \\|");
        assertThat(indexContent)
                .doesNotContainPattern("\\[users\\.id\\]\\(public/tables/users\\.md\\)");
    }
}
