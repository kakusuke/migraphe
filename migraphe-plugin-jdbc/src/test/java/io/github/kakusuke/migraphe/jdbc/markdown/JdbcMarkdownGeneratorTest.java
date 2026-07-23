package io.github.kakusuke.migraphe.jdbc.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.jdbc.schema.DefaultJdbcSchemaInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcCheckConstraintInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcColumnInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcForeignKeyInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcIndexColumn;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcIndexInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcPrimaryKeyInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcRoutineInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaDetail;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSequenceInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcTableInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcTriggerInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcUdtInfo;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcViewInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcMarkdownGeneratorTest {

    private static JdbcSchemaInfo buildSchemaInfo() {
        var idColumnUsers =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var nameColumn =
                new JdbcColumnInfo(
                        "name",
                        "VARCHAR",
                        Types.VARCHAR,
                        100,
                        0,
                        false,
                        null,
                        false,
                        false,
                        null,
                        2);
        var emailColumn =
                new JdbcColumnInfo(
                        "email",
                        "VARCHAR",
                        Types.VARCHAR,
                        200,
                        0,
                        true,
                        "'unknown'",
                        false,
                        false,
                        null,
                        3);
        var usersPk = new JdbcPrimaryKeyInfo("pk_users", List.of("id"));
        var usersTable =
                new JdbcTableInfo(
                        "users",
                        "",
                        List.of(idColumnUsers, nameColumn, emailColumn),
                        usersPk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());

        var idColumnOrders =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var userIdColumn =
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
        var amountColumn =
                new JdbcColumnInfo(
                        "amount",
                        "DECIMAL",
                        Types.DECIMAL,
                        10,
                        2,
                        true,
                        null,
                        false,
                        false,
                        null,
                        3);
        var ordersPk = new JdbcPrimaryKeyInfo("pk_orders", List.of("id"));
        var fkUserId =
                new JdbcForeignKeyInfo(
                        "fk_orders_user_id",
                        List.of("user_id"),
                        "PUBLIC",
                        "users",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var idxOrdersUserId =
                new JdbcIndexInfo(
                        "idx_orders_user_id", false, List.of(new JdbcIndexColumn("user_id", "A")));
        var ordersTable =
                new JdbcTableInfo(
                        "orders",
                        "",
                        List.of(idColumnOrders, userIdColumn, amountColumn),
                        ordersPk,
                        List.of(fkUserId),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(idxOrdersUserId),
                        List.of());

        var viewIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var viewNameColumn =
                new JdbcColumnInfo(
                        "name",
                        "VARCHAR",
                        Types.VARCHAR,
                        100,
                        0,
                        false,
                        null,
                        false,
                        false,
                        null,
                        2);
        var activeUsersView =
                new JdbcViewInfo(
                        "active_users",
                        "",
                        List.of(viewIdColumn, viewNameColumn),
                        "SELECT id, name FROM users WHERE name IS NOT NULL");

        var schemaDetail =
                new JdbcSchemaDetail(
                        "PUBLIC",
                        List.of(usersTable, ordersTable),
                        List.of(activeUsersView),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());

        return new DefaultJdbcSchemaInfo(List.of(schemaDetail));
    }

    private static String expectedErId(String schema, String table) {
        return sanitize(schema) + "_" + sanitize(table) + "_" + shortHash(schema, table);
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private static String shortHash(String schema, String table) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            String hashInput = schema.length() + ":" + schema + table;
            byte[] d = md.digest(hashInput.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.substring(0, 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static JdbcMarkdownDefinition.ExcludePattern excludeSchema(String schemaName) {
        return new JdbcMarkdownDefinition.ExcludePattern() {
            @Override
            public Optional<String> schema() {
                return Optional.of(schemaName);
            }

            @Override
            public Optional<String> table() {
                return Optional.empty();
            }
        };
    }

    private static JdbcMarkdownDefinition.ExcludePattern excludeTable(String tableName) {
        return new JdbcMarkdownDefinition.ExcludePattern() {
            @Override
            public Optional<String> schema() {
                return Optional.empty();
            }

            @Override
            public Optional<String> table() {
                return Optional.of(tableName);
            }
        };
    }

    @Test
    void generatesTableFileInCorrectDirectory(@TempDir Path outputDir) {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        assertThat(outputDir.resolve("mydb/PUBLIC/tables/users.md")).exists();
    }

    @Test
    void tableFileContainsColumnTable(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("mydb/PUBLIC/tables/users.md"));
        assertThat(content)
                .contains("| id |")
                .contains("| name |")
                .contains("INTEGER")
                .contains("VARCHAR")
                .contains("NO");
    }

    @Test
    void tableFileContainsPrimaryKey(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("mydb/PUBLIC/tables/users.md"));
        assertThat(content).contains("## Primary Key").contains("id");
    }

    @Test
    void tableFileContainsForeignKeys(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("mydb/PUBLIC/tables/orders.md"));
        assertThat(content).contains("users").contains("[users](../../PUBLIC/tables/users.md)");
    }

    @Test
    void tableFileContainsIndexes(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("mydb/PUBLIC/tables/orders.md"));
        assertThat(content).contains("idx_orders_user_id");
    }

    @Test
    void generatesViewFile(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        Path viewFile = outputDir.resolve("mydb/PUBLIC/views/active_users.md");
        assertThat(viewFile).exists();
        String content = Files.readString(viewFile);
        assertThat(content)
                .contains("id")
                .contains("name")
                .contains("SELECT id, name FROM users WHERE name IS NOT NULL");
    }

    @Test
    void generatesIndexMd(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        Path indexFile = outputDir.resolve("index.md");
        assertThat(indexFile).exists();
        String content = Files.readString(indexFile);
        assertThat(content).contains("users").contains("orders").contains("active_users");
    }

    @Test
    void excludesFilteredSchemas(@TempDir Path outputDir) {
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildSchemaInfo(), List.of(excludeSchema("PUBLIC")));

        generator.generate(outputDir);

        assertThat(outputDir.resolve("mydb/PUBLIC/tables/users.md")).doesNotExist();
        assertThat(outputDir.resolve("mydb/PUBLIC/tables/orders.md")).doesNotExist();
        assertThat(outputDir.resolve("mydb/PUBLIC/views/active_users.md")).doesNotExist();
    }

    @Test
    void tableFileContainsTableRemarks(@TempDir Path outputDir) throws Exception {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var pk = new JdbcPrimaryKeyInfo("pk_users", List.of("id"));
        var tableWithRemarks =
                new JdbcTableInfo(
                        "users",
                        "ユーザーマスタテーブル",
                        List.of(idColumn),
                        pk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var schemaDetail =
                new JdbcSchemaDetail(
                        "PUBLIC",
                        List.of(tableWithRemarks),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("mydb/PUBLIC/tables/users.md"));
        assertThat(content).contains("ユーザーマスタテーブル");
    }

    @Test
    void viewFileContainsViewRemarks(@TempDir Path outputDir) throws Exception {
        var viewIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var viewWithRemarks =
                new JdbcViewInfo(
                        "active_users",
                        "アクティブユーザービュー",
                        List.of(viewIdColumn),
                        "SELECT id FROM users WHERE name IS NOT NULL");
        var schemaDetail =
                new JdbcSchemaDetail(
                        "PUBLIC",
                        List.of(),
                        List.of(viewWithRemarks),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("mydb/PUBLIC/views/active_users.md"));
        assertThat(content)
                .contains("# active_users")
                .contains("アクティブユーザービュー")
                .contains("## Columns");
    }

    @Test
    void indexMdTablesUsePipeTable(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content)
                .contains("| Name | Remarks |")
                .contains("| --- | --- |")
                .contains("| [users](mydb/PUBLIC/tables/users.md) |")
                .doesNotContain("- [users]")
                .doesNotContain("- [orders]");
    }

    @Test
    void indexMdTableLinkIncludesRemarks(@TempDir Path outputDir) throws Exception {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var pk = new JdbcPrimaryKeyInfo("pk_users", List.of("id"));
        var tableWithRemarks =
                new JdbcTableInfo(
                        "users",
                        "Users master table",
                        List.of(idColumn),
                        pk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var schemaDetail =
                new JdbcSchemaDetail(
                        "PUBLIC",
                        List.of(tableWithRemarks),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content).containsPattern("(?m).*\\[users\\].*\\|.*Users master table.*");
    }

    @Test
    void indexMdViewLinkIncludesRemarks(@TempDir Path outputDir) throws Exception {
        var viewIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var viewWithRemarks =
                new JdbcViewInfo(
                        "active_users",
                        "アクティブユーザービュー",
                        List.of(viewIdColumn),
                        "SELECT id FROM users WHERE name IS NOT NULL");
        var schemaDetail =
                new JdbcSchemaDetail(
                        "PUBLIC",
                        List.of(),
                        List.of(viewWithRemarks),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content).containsPattern("(?m).*\\[active_users\\].*\\|.*アクティブユーザービュー.*");
    }

    @Test
    void excludesFilteredTables(@TempDir Path outputDir) {
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildSchemaInfo(), List.of(excludeTable("orders")));

        generator.generate(outputDir);

        assertThat(outputDir.resolve("mydb/PUBLIC/tables/users.md")).exists();
        assertThat(outputDir.resolve("mydb/PUBLIC/tables/orders.md")).doesNotExist();
    }

    @Test
    void tableExcludeWithSchemaRestrictionDoesNotApplyToOtherSchema(@TempDir Path outputDir) {
        var excludeOtherSchemaUsers =
                new JdbcMarkdownDefinition.ExcludePattern() {
                    @Override
                    public Optional<String> schema() {
                        return Optional.of("OTHER");
                    }

                    @Override
                    public Optional<String> table() {
                        return Optional.of("users");
                    }
                };

        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildSchemaInfo(), List.of(excludeOtherSchemaUsers));

        generator.generate(outputDir);

        assertThat(outputDir.resolve("mydb/PUBLIC/tables/users.md")).exists();
    }

    @Test
    void extraTableIndexHeaderAppearsBetweenNameAndRemarks(@TempDir Path outputDir)
            throws Exception {
        var generator =
                new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of()) {
                    @Override
                    protected List<String> extraTableIndexHeaders() {
                        return List.of("Engine");
                    }

                    @Override
                    protected List<String> extraTableIndexCells(
                            String schemaName,
                            io.github.kakusuke.migraphe.jdbc.schema.JdbcTableInfo table) {
                        return List.of("InnoDB");
                    }
                };

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content)
                .contains("| Name | Engine | Remarks |")
                .contains("| --- | --- | --- |")
                .containsPattern(
                        "\\| \\[users\\]\\(mydb/PUBLIC/tables/users\\.md\\) \\| InnoDB \\|");
    }

    @Test
    void extraTableIndexCellsSizeMismatchThrows(@TempDir Path outputDir) {
        var generator =
                new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of()) {
                    @Override
                    protected List<String> extraTableIndexHeaders() {
                        return List.of("A", "B");
                    }

                    @Override
                    protected List<String> extraTableIndexCells(
                            String schemaName,
                            io.github.kakusuke.migraphe.jdbc.schema.JdbcTableInfo table) {
                        return List.of("only-one");
                    }
                };

        assertThatThrownBy(() -> generator.generate(outputDir))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void extraViewIndexHeaderAppearsBetweenNameAndRemarks(@TempDir Path outputDir)
            throws Exception {
        var generator =
                new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of()) {
                    @Override
                    protected List<String> extraViewIndexHeaders() {
                        return List.of("Definer");
                    }

                    @Override
                    protected List<String> extraViewIndexCells(
                            String schemaName,
                            io.github.kakusuke.migraphe.jdbc.schema.JdbcViewInfo view) {
                        return List.of("root@%");
                    }
                };

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content)
                .contains("| Name | Definer | Remarks |")
                .contains("| --- | --- | --- |")
                .containsPattern(
                        "\\| \\[active_users\\]\\(mydb/PUBLIC/views/active_users\\.md\\) \\| root@%"
                                + " \\|");
    }

    @Test
    void appendTableFileHeaderIsCalledAfterTitleAndRemarks(@TempDir Path outputDir)
            throws Exception {
        var generator =
                new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of()) {
                    @Override
                    protected void appendTableFileHeader(
                            StringBuilder sb,
                            String schemaName,
                            io.github.kakusuke.migraphe.jdbc.schema.JdbcTableInfo table) {
                        sb.append("Owner: dba\n\n");
                    }
                };

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("mydb/PUBLIC/tables/users.md"));
        assertThat(content).startsWith("# users\n\nOwner: dba\n\n");
    }

    @Test
    void appendViewFileHeaderIsCalledAfterTitleAndRemarks(@TempDir Path outputDir)
            throws Exception {
        var generator =
                new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of()) {
                    @Override
                    protected void appendViewFileHeader(
                            StringBuilder sb,
                            String schemaName,
                            io.github.kakusuke.migraphe.jdbc.schema.JdbcViewInfo view) {
                        sb.append("Definer: root@%\n\n");
                    }
                };

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("mydb/PUBLIC/views/active_users.md"));
        assertThat(content).startsWith("# active_users\n\nDefiner: root@%\n\n");
    }

    @Test
    void indexMdContainsErDiagramSectionByDefault(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content)
                .contains("## ER Diagram\n\n```mermaid\nerDiagram\n")
                .contains("  " + expectedErId("PUBLIC", "users") + "[\"users\"] {\n")
                .contains("  " + expectedErId("PUBLIC", "orders") + "[\"orders\"] {\n");
    }

    @Test
    void erDiagramContainsEntityBlocksWithColumnsAndPk(@TempDir Path outputDir) throws Exception {
        var usersPk = new JdbcPrimaryKeyInfo("pk_users", List.of("id"));
        var idColumn =
                new JdbcColumnInfo(
                        "id", "bigint", Types.BIGINT, 19, 0, false, null, false, false, null, 1);
        var emailColumn =
                new JdbcColumnInfo(
                        "email",
                        "varchar",
                        Types.VARCHAR,
                        200,
                        0,
                        true,
                        null,
                        false,
                        false,
                        null,
                        2);
        var usersTable =
                new JdbcTableInfo(
                        "users",
                        "",
                        List.of(idColumn, emailColumn),
                        usersPk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());

        var ordersPk = new JdbcPrimaryKeyInfo("pk_orders", List.of("id"));
        var orderIdColumn =
                new JdbcColumnInfo(
                        "id", "bigint", Types.BIGINT, 19, 0, false, null, false, false, null, 1);
        var userIdColumn =
                new JdbcColumnInfo(
                        "user_id",
                        "bigint",
                        Types.BIGINT,
                        19,
                        0,
                        false,
                        null,
                        false,
                        false,
                        null,
                        2);
        var ordersTable =
                new JdbcTableInfo(
                        "orders",
                        "",
                        List.of(orderIdColumn, userIdColumn),
                        ordersPk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());

        var schemaDetail =
                new JdbcSchemaDetail(
                        "PUBLIC",
                        List.of(usersTable, ordersTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));

        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content)
                .contains(
                        "  "
                                + expectedErId("PUBLIC", "users")
                                + "[\"users\"] {\n    bigint id PK\n    varchar email\n  }")
                .contains(
                        "  "
                                + expectedErId("PUBLIC", "orders")
                                + "[\"orders\"] {\n    bigint id PK\n    bigint user_id\n  }");
    }

    @Test
    void erDiagramMarksForeignKeysAndRelationships(@TempDir Path outputDir) throws Exception {
        var usersPk = new JdbcPrimaryKeyInfo("pk_users", List.of("id"));
        var idColumn =
                new JdbcColumnInfo(
                        "id", "bigint", Types.BIGINT, 19, 0, false, null, false, false, null, 1);
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

        var ordersPk = new JdbcPrimaryKeyInfo("pk_orders", List.of("id"));
        var orderIdColumn =
                new JdbcColumnInfo(
                        "id", "bigint", Types.BIGINT, 19, 0, false, null, false, false, null, 1);
        var userIdColumn =
                new JdbcColumnInfo(
                        "user_id",
                        "bigint",
                        Types.BIGINT,
                        19,
                        0,
                        false,
                        null,
                        false,
                        false,
                        null,
                        2);
        var fkOrdersUsers =
                new JdbcForeignKeyInfo(
                        "fk_orders_users",
                        List.of("user_id"),
                        "PUBLIC",
                        "users",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var ordersTable =
                new JdbcTableInfo(
                        "orders",
                        "",
                        List.of(orderIdColumn, userIdColumn),
                        ordersPk,
                        List.of(fkOrdersUsers),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());

        var schemaDetail =
                new JdbcSchemaDetail(
                        "PUBLIC",
                        List.of(usersTable, ordersTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));

        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content)
                .contains("    bigint user_id FK")
                .contains(
                        "  "
                                + expectedErId("PUBLIC", "users")
                                + " ||--o{ "
                                + expectedErId("PUBLIC", "orders")
                                + " : \"fk_orders_users\"");
    }

    @Test
    void erDiagramRelationshipLabelIsSanitized(@TempDir Path outputDir) throws Exception {
        var usersPk = new JdbcPrimaryKeyInfo("pk_users", List.of("id"));
        var idColumn =
                new JdbcColumnInfo(
                        "id", "bigint", Types.BIGINT, 19, 0, false, null, false, false, null, 1);
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

        var ordersPk = new JdbcPrimaryKeyInfo("pk_orders", List.of("id"));
        var orderIdColumn =
                new JdbcColumnInfo(
                        "id", "bigint", Types.BIGINT, 19, 0, false, null, false, false, null, 1);
        var userIdColumn =
                new JdbcColumnInfo(
                        "user_id",
                        "bigint",
                        Types.BIGINT,
                        19,
                        0,
                        false,
                        null,
                        false,
                        false,
                        null,
                        2);
        var fkOrdersUsers =
                new JdbcForeignKeyInfo(
                        "fk_\"weird\"",
                        List.of("user_id"),
                        "PUBLIC",
                        "users",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var ordersTable =
                new JdbcTableInfo(
                        "orders",
                        "",
                        List.of(orderIdColumn, userIdColumn),
                        ordersPk,
                        List.of(fkOrdersUsers),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());

        var schemaDetail =
                new JdbcSchemaDetail(
                        "PUBLIC",
                        List.of(usersTable, ordersTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));

        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content)
                .contains(
                        expectedErId("PUBLIC", "users")
                                + " ||--o{ "
                                + expectedErId("PUBLIC", "orders")
                                + " : \"");
        assertThat(content).doesNotContain("fk_\"weird\"");
    }

    @Test
    void extraViewIndexCellsSizeMismatchThrows(@TempDir Path outputDir) {
        var generator =
                new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of()) {
                    @Override
                    protected List<String> extraViewIndexHeaders() {
                        return List.of("A", "B");
                    }

                    @Override
                    protected List<String> extraViewIndexCells(
                            String schemaName,
                            io.github.kakusuke.migraphe.jdbc.schema.JdbcViewInfo view) {
                        return List.of("only-one");
                    }
                };

        assertThatThrownBy(() -> generator.generate(outputDir))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void erDiagramSkipsRelationshipWhenReferencedTableExcluded(@TempDir Path outputDir)
            throws Exception {
        JdbcMarkdownDefinition.ExcludePattern excludeUsers =
                new JdbcMarkdownDefinition.ExcludePattern() {
                    @Override
                    public Optional<String> schema() {
                        return Optional.of("public");
                    }

                    @Override
                    public Optional<String> table() {
                        return Optional.of("users");
                    }
                };
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of(excludeUsers));

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content).doesNotContain(expectedErId("PUBLIC", "users") + "[");
        assertThat(content).doesNotContain("||--o{");
    }

    @Test
    void erDiagramExcludesViews(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content).doesNotContain("_active_users[");
    }

    @Test
    void erDiagramSanitizesTypeNamesWithWhitespaceParensAndQuotes(@TempDir Path outputDir)
            throws Exception {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var priceColumn =
                new JdbcColumnInfo(
                        "price",
                        "character varying(255)",
                        Types.VARCHAR,
                        255,
                        0,
                        true,
                        null,
                        false,
                        false,
                        null,
                        2);
        var langColumn =
                new JdbcColumnInfo(
                        "lang",
                        "\"app\".\"language_code\"",
                        Types.OTHER,
                        0,
                        0,
                        true,
                        null,
                        false,
                        false,
                        null,
                        3);
        var pk = new JdbcPrimaryKeyInfo("pk_products", List.of("id"));
        var productsTable =
                new JdbcTableInfo(
                        "products",
                        "",
                        List.of(idColumn, priceColumn, langColumn),
                        pk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(productsTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", new DefaultJdbcSchemaInfo(List.of(schemaDetail)), List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        // Sanitized tokens are emitted (non [A-Za-z0-9_] replaced with '_').
        assertThat(content).contains("    character_varying_255_ price");
        assertThat(content).contains("    language_code lang");
        // Raw Mermaid-breaking forms must not leak through.
        assertThat(content).doesNotContain("character varying(255)");
        assertThat(content).doesNotContain("\"app\".\"language_code\"");
    }

    @Test
    void erDiagramKeysOnlyShowsOnlyPkAndFkColumns(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of(), true, true);

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        // Key columns remain (users.id PK, orders.user_id FK).
        assertThat(content).contains("    INTEGER id PK");
        assertThat(content).contains("    INTEGER user_id FK");
        // Non-key columns are omitted from the ER diagram (they appear nowhere in index.md).
        assertThat(content).doesNotContain(" amount");
        assertThat(content).doesNotContain(" email");
    }

    @Test
    void erDiagramShowsAllColumnsByDefault(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        // Default (keys-only = false) keeps non-key columns such as orders.amount.
        assertThat(content).contains(" amount");
    }

    @Test
    void foreignKeyLinkPointsToReferencedTableSchemaDirectory(@TempDir Path outputDir)
            throws Exception {
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
        var authSchemaDetail =
                new JdbcSchemaDetail(
                        "AUTH",
                        List.of(usersTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());

        var ordersIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var userIdColumn =
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
        var fkUserId =
                new JdbcForeignKeyInfo(
                        "fk_orders_user_id",
                        List.of("user_id"),
                        "AUTH",
                        "users",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var ordersTable =
                new JdbcTableInfo(
                        "orders",
                        "",
                        List.of(ordersIdColumn, userIdColumn),
                        ordersPk,
                        List.of(fkUserId),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var salesSchemaDetail =
                new JdbcSchemaDetail(
                        "SALES",
                        List.of(ordersTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());

        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(authSchemaDetail, salesSchemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("mydb/SALES/tables/orders.md"));
        assertThat(content).contains("[users](../../AUTH/tables/users.md)");
    }

    @Test
    void exportedKeyLinkPointsToReferencingTableSchemaDirectory(@TempDir Path outputDir)
            throws Exception {
        var usersIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var usersPk = new JdbcPrimaryKeyInfo("pk_users", List.of("id"));
        var exportedKeyOrders =
                new JdbcForeignKeyInfo(
                        "fk_orders_user_id",
                        List.of("id"),
                        "SALES",
                        "orders",
                        List.of("user_id"),
                        "NO ACTION",
                        "CASCADE");
        var usersTable =
                new JdbcTableInfo(
                        "users",
                        "",
                        List.of(usersIdColumn),
                        usersPk,
                        List.of(),
                        List.of(exportedKeyOrders),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var authSchemaDetail =
                new JdbcSchemaDetail(
                        "AUTH",
                        List.of(usersTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());

        var ordersIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var userIdColumn =
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
        var ordersTable =
                new JdbcTableInfo(
                        "orders",
                        "",
                        List.of(ordersIdColumn, userIdColumn),
                        ordersPk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var salesSchemaDetail =
                new JdbcSchemaDetail(
                        "SALES",
                        List.of(ordersTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());

        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(authSchemaDetail, salesSchemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("mydb/AUTH/tables/users.md"));
        assertThat(content).contains("[orders](../../SALES/tables/orders.md)");
    }

    @Test
    void erDiagramOmittedWhenNoTables(@TempDir Path outputDir) throws Exception {
        var viewIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var activeUsersView =
                new JdbcViewInfo(
                        "active_users",
                        "",
                        List.of(viewIdColumn),
                        "SELECT id FROM users WHERE name IS NOT NULL");
        var schemaDetail =
                new JdbcSchemaDetail(
                        "PUBLIC",
                        List.of(),
                        List.of(activeUsersView),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content).doesNotContain("## ER Diagram").doesNotContain("erDiagram");
    }

    @Test
    void erDiagramQualifiesSameNamedTablesAcrossSchemas(@TempDir Path outputDir) throws Exception {
        var authItemsIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var authItemsPk = new JdbcPrimaryKeyInfo("pk_items_auth", List.of("id"));
        var authItemsTable =
                new JdbcTableInfo(
                        "items",
                        "",
                        List.of(authItemsIdColumn),
                        authItemsPk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var authSchemaDetail =
                new JdbcSchemaDetail(
                        "AUTH",
                        List.of(authItemsTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());

        var salesItemsIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var refIdColumn =
                new JdbcColumnInfo(
                        "ref_id",
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
        var salesItemsPk = new JdbcPrimaryKeyInfo("pk_items_sales", List.of("id"));
        var fkSalesItemsAuthItems =
                new JdbcForeignKeyInfo(
                        "fk_sales_items_auth_items",
                        List.of("ref_id"),
                        "AUTH",
                        "items",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var salesItemsTable =
                new JdbcTableInfo(
                        "items",
                        "",
                        List.of(salesItemsIdColumn, refIdColumn),
                        salesItemsPk,
                        List.of(fkSalesItemsAuthItems),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var salesSchemaDetail =
                new JdbcSchemaDetail(
                        "SALES",
                        List.of(salesItemsTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());

        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(authSchemaDetail, salesSchemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content).contains("  " + expectedErId("AUTH", "items") + "[\"items\"] {");
        assertThat(content).contains("  " + expectedErId("SALES", "items") + "[\"items\"] {");
        assertThat(content)
                .contains(
                        "  "
                                + expectedErId("AUTH", "items")
                                + " ||--o{ "
                                + expectedErId("SALES", "items")
                                + " : \"fk_sales_items_auth_items\"");
        assertThat(content).doesNotContain("  items ||--o{ items");
    }

    @Test
    void foreignKeyLinkNormalizesReferencedSchemaCase(@TempDir Path outputDir) throws Exception {
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
        var userIdColumn =
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
        var fkUserId =
                new JdbcForeignKeyInfo(
                        "fk_orders_user",
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
                        List.of(ordersIdColumn, userIdColumn),
                        ordersPk,
                        List.of(fkUserId),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var schemaDetail =
                new JdbcSchemaDetail(
                        "PUBLIC",
                        List.of(usersTable, ordersTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("mydb/PUBLIC/tables/orders.md"));
        assertThat(content).contains("[users](../../PUBLIC/tables/users.md)");
        assertThat(content).doesNotContain("../../public/tables/users.md");
    }

    @Test
    void erDiagramDisambiguatesCollidingEntityIds(@TempDir Path outputDir) throws Exception {
        var cIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var cPk = new JdbcPrimaryKeyInfo("pk_c", List.of("id"));
        var cTable =
                new JdbcTableInfo(
                        "c",
                        "",
                        List.of(cIdColumn),
                        cPk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var aBSchemaDetail =
                new JdbcSchemaDetail(
                        "a_b",
                        List.of(cTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());

        var bCIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var bCPk = new JdbcPrimaryKeyInfo("pk_b_c", List.of("id"));
        var bCTable =
                new JdbcTableInfo(
                        "b_c",
                        "",
                        List.of(bCIdColumn),
                        bCPk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var aSchemaDetail =
                new JdbcSchemaDetail(
                        "a",
                        List.of(bCTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());

        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(aBSchemaDetail, aSchemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(expectedErId("a_b", "c")).isNotEqualTo(expectedErId("a", "b_c"));
        assertThat(content).contains("  " + expectedErId("a_b", "c") + "[\"c\"] {");
        assertThat(content).contains("  " + expectedErId("a", "b_c") + "[\"b_c\"] {");
    }

    @Test
    void erDiagramMarksColumnAsBothPrimaryAndForeignKey(@TempDir Path outputDir) throws Exception {
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

        var userIdColumn =
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
                        1);
        var membershipsPk = new JdbcPrimaryKeyInfo("pk_memberships", List.of("user_id"));
        var fkMembershipsUser =
                new JdbcForeignKeyInfo(
                        "fk_memberships_user",
                        List.of("user_id"),
                        "PUBLIC",
                        "users",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var membershipsTable =
                new JdbcTableInfo(
                        "memberships",
                        "",
                        List.of(userIdColumn),
                        membershipsPk,
                        List.of(fkMembershipsUser),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());

        var schemaDetail =
                new JdbcSchemaDetail(
                        "PUBLIC",
                        List.of(usersTable, membershipsTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));

        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content).contains("    INTEGER user_id PK, FK");
    }

    @Test
    void enumColumnTypeShownAsBaseName(@TempDir Path outputDir) throws Exception {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "bigint", Types.BIGINT, 19, 0, false, null, false, false, null, 1);
        var statusColumn =
                new JdbcColumnInfo(
                        "status",
                        "\"account\".\"user_account_status\"",
                        Types.VARCHAR,
                        Integer.MAX_VALUE,
                        0,
                        false,
                        null,
                        false,
                        false,
                        null,
                        2);
        var pk = new JdbcPrimaryKeyInfo("pk_user_accounts", List.of("id"));
        var userAccountsTable =
                new JdbcTableInfo(
                        "user_accounts",
                        "",
                        List.of(idColumn, statusColumn),
                        pk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var schemaDetail =
                new JdbcSchemaDetail(
                        "account",
                        List.of(userAccountsTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String tableContent =
                Files.readString(outputDir.resolve("mydb/account/tables/user_accounts.md"));
        assertThat(tableContent).contains("| status | user_account_status | NO |  | NO |  |\n");
        assertThat(tableContent).doesNotContain("\"account\".\"user_account_status\"");
        assertThat(tableContent).doesNotContain("(2147483647)");

        String indexContent = Files.readString(outputDir.resolve("index.md"));
        assertThat(indexContent).contains("    user_account_status status");
        assertThat(indexContent).doesNotContain("_account___user_account_status_");
    }

    @Test
    void erDiagramEntityLabelSanitizesBrackets(@TempDir Path outputDir) throws Exception {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "bigint", Types.BIGINT, 19, 0, false, null, false, false, null, 1);
        var pk = new JdbcPrimaryKeyInfo("pk_weird", List.of("id"));
        var weirdTable =
                new JdbcTableInfo(
                        "weird]name",
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
                        "public",
                        List.of(weirdTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content).contains("  " + expectedErId("public", "weird]name") + "[\"");
        assertThat(content).doesNotContain("weird]name\"");
    }

    @Test
    void crossSchemaForeignKeyWithEmptyReferencedSchemaResolvesToOwningSchema(
            @TempDir Path outputDir) throws Exception {
        var accountUsersIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var accountUsersPk = new JdbcPrimaryKeyInfo("pk_users", List.of("id"));
        var accountUsersTable =
                new JdbcTableInfo(
                        "users",
                        "",
                        List.of(accountUsersIdColumn),
                        accountUsersPk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var accountSchemaDetail =
                new JdbcSchemaDetail(
                        "account",
                        List.of(accountUsersTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());

        var salesOrdersIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var salesOrdersUserIdColumn =
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
        var salesOrdersPk = new JdbcPrimaryKeyInfo("pk_orders", List.of("id"));
        var fkUserIdEmptyReferencedSchema =
                new JdbcForeignKeyInfo(
                        "fk_orders_user_id",
                        List.of("user_id"),
                        "",
                        "users",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var salesOrdersTable =
                new JdbcTableInfo(
                        "orders",
                        "",
                        List.of(salesOrdersIdColumn, salesOrdersUserIdColumn),
                        salesOrdersPk,
                        List.of(fkUserIdEmptyReferencedSchema),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var salesSchemaDetail =
                new JdbcSchemaDetail(
                        "sales",
                        List.of(salesOrdersTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());

        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(accountSchemaDetail, salesSchemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String indexContent = Files.readString(outputDir.resolve("index.md"));
        String usersErId = expectedErId("account", "users");
        String ordersErId = expectedErId("sales", "orders");
        boolean relationshipLinePresent =
                indexContent
                        .lines()
                        .anyMatch(
                                line ->
                                        line.contains(" ||--o{ ")
                                                && line.contains(usersErId)
                                                && line.contains(ordersErId));
        assertThat(relationshipLinePresent).isTrue();

        String ordersFileContent =
                Files.readString(outputDir.resolve("mydb/sales/tables/orders.md"));
        assertThat(ordersFileContent).contains("[users](../../account/tables/users.md)");
    }

    @Test
    void referencedSchemaResolutionDoesNotMisResolveOnCaseAmbiguity(@TempDir Path outputDir)
            throws Exception {
        var itemsIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var itemsPk = new JdbcPrimaryKeyInfo("pk_items", List.of("id"));
        var itemsTable =
                new JdbcTableInfo(
                        "items",
                        "",
                        List.of(itemsIdColumn),
                        itemsPk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var lowercaseAccountSchemaDetail =
                new JdbcSchemaDetail(
                        "account",
                        List.of(itemsTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());

        var widgetsIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var widgetsPk = new JdbcPrimaryKeyInfo("pk_widgets", List.of("id"));
        var widgetsTable =
                new JdbcTableInfo(
                        "widgets",
                        "",
                        List.of(widgetsIdColumn),
                        widgetsPk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var uppercaseAccountSchemaDetail =
                new JdbcSchemaDetail(
                        "Account",
                        List.of(widgetsTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());

        var salesOrdersIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var widgetIdColumn =
                new JdbcColumnInfo(
                        "widget_id",
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
        var salesOrdersPk = new JdbcPrimaryKeyInfo("pk_orders", List.of("id"));
        var fkWidgetIdAllCapsReferencedSchema =
                new JdbcForeignKeyInfo(
                        "fk_orders_widget_id",
                        List.of("widget_id"),
                        "ACCOUNT",
                        "widgets",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var salesOrdersTable =
                new JdbcTableInfo(
                        "orders",
                        "",
                        List.of(salesOrdersIdColumn, widgetIdColumn),
                        salesOrdersPk,
                        List.of(fkWidgetIdAllCapsReferencedSchema),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var salesSchemaDetail =
                new JdbcSchemaDetail(
                        "sales",
                        List.of(salesOrdersTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());

        var schemaInfo =
                new DefaultJdbcSchemaInfo(
                        List.of(
                                lowercaseAccountSchemaDetail,
                                uppercaseAccountSchemaDetail,
                                salesSchemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String ordersFileContent =
                Files.readString(outputDir.resolve("mydb/sales/tables/orders.md"));
        assertThat(ordersFileContent).contains("[widgets](../../Account/tables/widgets.md)");
    }

    @Test
    void columnTypeWithTrailingDotFallsBackToRawName(@TempDir Path outputDir) throws Exception {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "bigint", Types.BIGINT, 19, 0, false, null, false, false, null, 1);
        var cColumn =
                new JdbcColumnInfo(
                        "c", "weird.", Types.OTHER, 0, 0, false, null, false, false, null, 2);
        var pk = new JdbcPrimaryKeyInfo("pk_t", List.of("id"));
        var tTable =
                new JdbcTableInfo(
                        "t",
                        "",
                        List.of(idColumn, cColumn),
                        pk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(tTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String tableContent = Files.readString(outputDir.resolve("mydb/public/tables/t.md"));
        assertThat(tableContent).doesNotContain("| c |  |").contains("weird");

        String indexContent = Files.readString(outputDir.resolve("index.md"));
        assertThat(indexContent).contains("weird");
    }

    @Test
    void columnTypeWithTrailingDotDropsTrailingDot(@TempDir Path outputDir) throws Exception {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "bigint", Types.BIGINT, 19, 0, false, null, false, false, null, 1);
        var cColumn =
                new JdbcColumnInfo(
                        "c", "weird.", Types.OTHER, 0, 0, false, null, false, false, null, 2);
        var pk = new JdbcPrimaryKeyInfo("pk_t", List.of("id"));
        var tTable =
                new JdbcTableInfo(
                        "t",
                        "",
                        List.of(idColumn, cColumn),
                        pk,
                        List.of(),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(tTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String tableContent = Files.readString(outputDir.resolve("mydb/public/tables/t.md"));
        assertThat(tableContent).contains("| c | weird | NO |  | NO |  |\n");
        assertThat(tableContent).doesNotContain("weird.");

        String indexContent = Files.readString(outputDir.resolve("index.md"));
        assertThat(indexContent).contains("    weird c\n");
        assertThat(indexContent).doesNotContain("weird.");
    }

    @Test
    void referencedSchemaFallsBackToMatchedSchemaCasingWhenTableNotFound(@TempDir Path outputDir)
            throws Exception {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var extIdColumn =
                new JdbcColumnInfo(
                        "ext_id",
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
        var fkOrdersExt =
                new JdbcForeignKeyInfo(
                        "fk_orders_ext",
                        List.of("ext_id"),
                        "PUBLIC",
                        "ext_things",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var ordersTable =
                new JdbcTableInfo(
                        "orders",
                        "",
                        List.of(idColumn, extIdColumn),
                        ordersPk,
                        List.of(fkOrdersExt),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var publicSchemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(ordersTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var capitalizedPublicSchemaDetail =
                new JdbcSchemaDetail(
                        "Public",
                        List.of(),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        var schemaInfo =
                new DefaultJdbcSchemaInfo(
                        List.of(publicSchemaDetail, capitalizedPublicSchemaDetail));
        var generator = new JdbcMarkdownGenerator("mydb", schemaInfo, List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("mydb/public/tables/orders.md"));
        assertThat(content).contains("../../public/tables/ext_things.md");
        assertThat(content).doesNotContain("../../PUBLIC/tables/ext_things.md");
    }
}
