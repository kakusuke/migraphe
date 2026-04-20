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
        assertThat(content).contains("users").contains("[users](../tables/users.md)");
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
}
