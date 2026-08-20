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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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

    private static JdbcSchemaInfo buildFkChainSchemaInfo() {
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

        var productsIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var productsPk = new JdbcPrimaryKeyInfo("pk_products", List.of("id"));
        var productsTable =
                new JdbcTableInfo(
                        "products",
                        "",
                        List.of(productsIdColumn),
                        productsPk,
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
        var fkOrdersUserId =
                new JdbcForeignKeyInfo(
                        "fk_orders_user_id",
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
                        List.of(ordersIdColumn, ordersUserIdColumn),
                        ordersPk,
                        List.of(fkOrdersUserId),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());

        var orderItemsIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var orderItemsOrderIdColumn =
                new JdbcColumnInfo(
                        "order_id",
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
        var orderItemsProductIdColumn =
                new JdbcColumnInfo(
                        "product_id",
                        "INTEGER",
                        Types.INTEGER,
                        10,
                        0,
                        false,
                        null,
                        false,
                        false,
                        null,
                        3);
        var orderItemsPk = new JdbcPrimaryKeyInfo("pk_order_items", List.of("id"));
        var fkOrderItemsOrderId =
                new JdbcForeignKeyInfo(
                        "fk_order_items_order_id",
                        List.of("order_id"),
                        "PUBLIC",
                        "orders",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var fkOrderItemsProductId =
                new JdbcForeignKeyInfo(
                        "fk_order_items_product_id",
                        List.of("product_id"),
                        "PUBLIC",
                        "products",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var orderItemsTable =
                new JdbcTableInfo(
                        "order_items",
                        "",
                        List.of(
                                orderItemsIdColumn,
                                orderItemsOrderIdColumn,
                                orderItemsProductIdColumn),
                        orderItemsPk,
                        List.of(fkOrderItemsOrderId, fkOrderItemsProductId),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());

        var schemaDetail =
                new JdbcSchemaDetail(
                        "PUBLIC",
                        List.of(usersTable, ordersTable, orderItemsTable, productsTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());

        return new DefaultJdbcSchemaInfo(List.of(schemaDetail));
    }

    private static JdbcSchemaInfo buildSelfReferencingSchemaInfo() {
        var idColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var managerIdColumn =
                new JdbcColumnInfo(
                        "manager_id",
                        "INTEGER",
                        Types.INTEGER,
                        10,
                        0,
                        true,
                        null,
                        false,
                        false,
                        null,
                        2);
        var employeesPk = new JdbcPrimaryKeyInfo("pk_employees", List.of("id"));
        var fkManagerId =
                new JdbcForeignKeyInfo(
                        "fk_employees_manager_id",
                        List.of("manager_id"),
                        "PUBLIC",
                        "employees",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var employeesTable =
                new JdbcTableInfo(
                        "employees",
                        "",
                        List.of(idColumn, managerIdColumn),
                        employeesPk,
                        List.of(fkManagerId),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());
        var schemaDetail =
                new JdbcSchemaDetail(
                        "PUBLIC",
                        List.of(employeesTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        return new DefaultJdbcSchemaInfo(List.of(schemaDetail));
    }

    private static JdbcSchemaInfo buildMutualReferenceSchemaInfo() {
        var aIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var aBIdColumn =
                new JdbcColumnInfo(
                        "b_id", "INTEGER", Types.INTEGER, 10, 0, true, null, false, false, null, 2);
        var aPk = new JdbcPrimaryKeyInfo("pk_a", List.of("id"));
        var fkAB =
                new JdbcForeignKeyInfo(
                        "fk_a_b_id",
                        List.of("b_id"),
                        "PUBLIC",
                        "b",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var aTable =
                new JdbcTableInfo(
                        "a",
                        "",
                        List.of(aIdColumn, aBIdColumn),
                        aPk,
                        List.of(fkAB),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());

        var bIdColumn =
                new JdbcColumnInfo(
                        "id", "INTEGER", Types.INTEGER, 10, 0, false, null, false, false, null, 1);
        var bAIdColumn =
                new JdbcColumnInfo(
                        "a_id", "INTEGER", Types.INTEGER, 10, 0, true, null, false, false, null, 2);
        var bPk = new JdbcPrimaryKeyInfo("pk_b", List.of("id"));
        var fkBA =
                new JdbcForeignKeyInfo(
                        "fk_b_a_id",
                        List.of("a_id"),
                        "PUBLIC",
                        "a",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var bTable =
                new JdbcTableInfo(
                        "b",
                        "",
                        List.of(bIdColumn, bAIdColumn),
                        bPk,
                        List.of(fkBA),
                        List.of(),
                        List.<JdbcCheckConstraintInfo>of(),
                        List.of(),
                        List.of());

        var schemaDetail =
                new JdbcSchemaDetail(
                        "PUBLIC",
                        List.of(aTable, bTable),
                        List.of(),
                        List.<JdbcRoutineInfo>of(),
                        List.<JdbcTriggerInfo>of(),
                        List.<JdbcSequenceInfo>of(),
                        List.<JdbcUdtInfo>of());
        return new DefaultJdbcSchemaInfo(List.of(schemaDetail));
    }

    private static JdbcSchemaInfo buildCrossSchemaFkSchemaInfo() {
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
                        "auth",
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

        return new DefaultJdbcSchemaInfo(List.of(salesSchemaDetail, authSchemaDetail));
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

        assertThat(outputDir.resolve("PUBLIC/tables/users.md")).exists();
    }

    @Test
    void tableFileContainsColumnTable(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("PUBLIC/tables/users.md"));
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

        String content = Files.readString(outputDir.resolve("PUBLIC/tables/users.md"));
        assertThat(content).contains("## Primary Key").contains("id");
    }

    @Test
    void tableFileContainsForeignKeys(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("PUBLIC/tables/orders.md"));
        assertThat(content).contains("users").contains("[users](../../PUBLIC/tables/users.md)");
    }

    @Test
    void tableFileContainsIndexes(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("PUBLIC/tables/orders.md"));
        assertThat(content).contains("idx_orders_user_id");
    }

    @Test
    void generatesViewFile(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        Path viewFile = outputDir.resolve("PUBLIC/views/active_users.md");
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

        assertThat(outputDir.resolve("PUBLIC/tables/users.md")).doesNotExist();
        assertThat(outputDir.resolve("PUBLIC/tables/orders.md")).doesNotExist();
        assertThat(outputDir.resolve("PUBLIC/views/active_users.md")).doesNotExist();
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

        String content = Files.readString(outputDir.resolve("PUBLIC/tables/users.md"));
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

        String content = Files.readString(outputDir.resolve("PUBLIC/views/active_users.md"));
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
                .contains("| [users](PUBLIC/tables/users.md) |")
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

        assertThat(outputDir.resolve("PUBLIC/tables/users.md")).exists();
        assertThat(outputDir.resolve("PUBLIC/tables/orders.md")).doesNotExist();
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

        assertThat(outputDir.resolve("PUBLIC/tables/users.md")).exists();
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
                .containsPattern("\\| \\[users\\]\\(PUBLIC/tables/users\\.md\\) \\| InnoDB \\|");
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
                        "\\| \\[active_users\\]\\(PUBLIC/views/active_users\\.md\\) \\| root@%"
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

        String content = Files.readString(outputDir.resolve("PUBLIC/tables/users.md"));
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

        String content = Files.readString(outputDir.resolve("PUBLIC/views/active_users.md"));
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
    void indexMdErDiagramFenceContainsLayoutFrontmatterWhenConfigured(@TempDir Path outputDir)
            throws Exception {
        var generator =
                new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of(), true, false, "elk");

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content)
                .contains(
                        "## ER Diagram\n\n"
                                + "```mermaid\n"
                                + "---\n"
                                + "config:\n"
                                + "  layout: elk\n"
                                + "---\n"
                                + "erDiagram\n");
    }

    @Test
    void indexMdErDiagramFenceOmitsFrontmatterWhenLayoutContainsInvalidCharacters(
            @TempDir Path outputDir) throws Exception {
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildSchemaInfo(), List.of(), true, false, "elk\n---\nfoo");

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content).contains("## ER Diagram\n\n```mermaid\nerDiagram\n");
        assertThat(content).doesNotContain("---\nconfig:");
        assertThat(content).doesNotContain("foo");
    }

    @Test
    void indexMdErDiagramFenceOmitsFrontmatterWhenLayoutIsNull(@TempDir Path outputDir)
            throws Exception {
        var generator =
                new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of(), true, false, null);

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content).contains("## ER Diagram\n\n```mermaid\nerDiagram\n");
        assertThat(content).doesNotContain("---\nconfig:");
    }

    @Test
    void indexMdErDiagramFenceOmitsFrontmatterWhenLayoutHasTrailingNewline(@TempDir Path outputDir)
            throws Exception {
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildSchemaInfo(), List.of(), true, false, "elk\n");

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content).contains("## ER Diagram\n\n```mermaid\nerDiagram\n");
        assertThat(content).doesNotContain("---\nconfig:");
    }

    @Test
    void indexMdErDiagramFenceOmitsFrontmatterWhenLayoutHasTrailingCarriageReturnNewline(
            @TempDir Path outputDir) throws Exception {
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildSchemaInfo(), List.of(), true, false, "elk\r\n");

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content).contains("## ER Diagram\n\n```mermaid\nerDiagram\n");
        assertThat(content).doesNotContain("---\nconfig:");
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

        String content = Files.readString(outputDir.resolve("SALES/tables/orders.md"));
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

        String content = Files.readString(outputDir.resolve("AUTH/tables/users.md"));
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

        String content = Files.readString(outputDir.resolve("PUBLIC/tables/orders.md"));
        assertThat(content).contains("[users](../../PUBLIC/tables/users.md)");
        assertThat(content).doesNotContain("../../public/tables/users.md");
    }

    @Test
    void tablePageErDiagramIncludesTransitiveAncestors(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildFkChainSchemaInfo(), List.of());
        generator.generate(outputDir);

        String orderItems = Files.readString(outputDir.resolve("PUBLIC/tables/order_items.md"));
        assertThat(orderItems).contains(expectedErId("PUBLIC", "orders"));
        assertThat(orderItems).contains(expectedErId("PUBLIC", "users"));

        String orders = Files.readString(outputDir.resolve("PUBLIC/tables/orders.md"));
        assertThat(orders).contains(expectedErId("PUBLIC", "users"));
        assertThat(orders).doesNotContain(expectedErId("PUBLIC", "products"));
    }

    @Test
    void tablePageErDiagramIncludesTransitiveDescendantsButNotTheirAncestors(
            @TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildFkChainSchemaInfo(), List.of());
        generator.generate(outputDir);

        String users = Files.readString(outputDir.resolve("PUBLIC/tables/users.md"));
        assertThat(users).contains(expectedErId("PUBLIC", "orders"));
        assertThat(users).contains(expectedErId("PUBLIC", "order_items"));
        assertThat(users).doesNotContain(expectedErId("PUBLIC", "products"));
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
                Files.readString(outputDir.resolve("account/tables/user_accounts.md"));
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

        String ordersFileContent = Files.readString(outputDir.resolve("sales/tables/orders.md"));
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

        String ordersFileContent = Files.readString(outputDir.resolve("sales/tables/orders.md"));
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

        String tableContent = Files.readString(outputDir.resolve("public/tables/t.md"));
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

        String tableContent = Files.readString(outputDir.resolve("public/tables/t.md"));
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

        String content = Files.readString(outputDir.resolve("public/tables/orders.md"));
        assertThat(content).contains("../../public/tables/ext_things.md");
        assertThat(content).doesNotContain("../../PUBLIC/tables/ext_things.md");
    }

    @Test
    void tablePageContainsErDiagramSection(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("PUBLIC/tables/users.md"));
        assertThat(content).contains("## ER Diagram\n\n```mermaid\n");
        assertThat(content).contains("  " + expectedErId("PUBLIC", "users") + "[\"users\"] {\n");
        assertThat(content.indexOf("## ER Diagram")).isLessThan(content.indexOf("## Columns"));
    }

    @Test
    void tablePageOmitsErDiagramWhenPerTableDisabled(@TempDir Path outputDir) throws Exception {
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildSchemaInfo(), List.of(), true, false, "elk", false);
        generator.generate(outputDir);

        String users = Files.readString(outputDir.resolve("PUBLIC/tables/users.md"));
        assertThat(users).doesNotContain("## ER Diagram");

        String index = Files.readString(outputDir.resolve("index.md"));
        assertThat(index).contains("## ER Diagram");
    }

    @Test
    void tablePageOmitsErDiagramWhenErDiagramDisabled(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of(), false);
        generator.generate(outputDir);

        String users = Files.readString(outputDir.resolve("PUBLIC/tables/users.md"));
        assertThat(users).doesNotContain("## ER Diagram");

        String index = Files.readString(outputDir.resolve("index.md"));
        assertThat(index).doesNotContain("## ER Diagram");
    }

    @Test
    void tablePageOmitsErDiagramWhenNeighborhoodExceedsMaxEntities(@TempDir Path outputDir)
            throws Exception {
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildFkChainSchemaInfo(), List.of(), true, false, "elk", true, 1);
        generator.generate(outputDir);

        String orders = Files.readString(outputDir.resolve("PUBLIC/tables/orders.md"));
        assertThat(orders).contains("## ER Diagram");
        assertThat(orders).doesNotContain("```mermaid");
        assertThat(orders).contains("../../index.md");
    }

    @Test
    void tablePageNeighborhoodIncludesOnlyDescendantNotUnrelatedAncestors(@TempDir Path outputDir)
            throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildFkChainSchemaInfo(), List.of());
        generator.generate(outputDir);

        String products = Files.readString(outputDir.resolve("PUBLIC/tables/products.md"));
        assertThat(products).contains(expectedErId("PUBLIC", "products"));
        assertThat(products).contains(expectedErId("PUBLIC", "order_items"));
        assertThat(products).doesNotContain(expectedErId("PUBLIC", "users"));
        assertThat(products).doesNotContain(expectedErId("PUBLIC", "orders"));
    }

    @Test
    void selfReferencingForeignKeyProducesSingleEntityAndRelationshipWithoutDuplication(
            @TempDir Path outputDir) throws Exception {
        var generator =
                new JdbcMarkdownGenerator("mydb", buildSelfReferencingSchemaInfo(), List.of());
        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("PUBLIC/tables/employees.md"));
        String entityId = expectedErId("PUBLIC", "employees");
        assertThat(content).containsOnlyOnce(entityId + "[\"employees\"]");
        assertThat(content).containsOnlyOnce(entityId + " ||--o{ " + entityId);
    }

    @Test
    void mutuallyReferencingForeignKeysIncludeBothEntitiesWithoutInfiniteLoop(
            @TempDir Path outputDir) throws Exception {
        var generator =
                new JdbcMarkdownGenerator("mydb", buildMutualReferenceSchemaInfo(), List.of());
        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("PUBLIC/tables/a.md"));
        assertThat(content).contains(expectedErId("PUBLIC", "a"));
        assertThat(content).contains(expectedErId("PUBLIC", "b"));
    }

    @Test
    void crossSchemaForeignKeyNeighborhoodIncludesBothDirectionsWithNormalizedSchemaCase(
            @TempDir Path outputDir) throws Exception {
        var generator =
                new JdbcMarkdownGenerator("mydb", buildCrossSchemaFkSchemaInfo(), List.of());
        generator.generate(outputDir);

        String orders = Files.readString(outputDir.resolve("SALES/tables/orders.md"));
        assertThat(orders).contains(expectedErId("AUTH", "users"));

        String users = Files.readString(outputDir.resolve("AUTH/tables/users.md"));
        assertThat(users).contains(expectedErId("SALES", "orders"));
    }

    @Test
    void excludedIntermediateTableBreaksAncestorPathAndOmitsItsOwnFile(@TempDir Path outputDir)
            throws Exception {
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildFkChainSchemaInfo(), List.of(excludeTable("orders")));
        generator.generate(outputDir);

        assertThat(outputDir.resolve("PUBLIC/tables/orders.md")).doesNotExist();

        String orderItems = Files.readString(outputDir.resolve("PUBLIC/tables/order_items.md"));
        assertThat(orderItems).doesNotContain(expectedErId("PUBLIC", "users"));
    }

    @Test
    void tablePageErDiagramRendersWhenNeighborhoodSizeEqualsMaxEntities(@TempDir Path outputDir)
            throws Exception {
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildFkChainSchemaInfo(), List.of(), true, false, null, true, 2);
        generator.generate(outputDir);

        String products = Files.readString(outputDir.resolve("PUBLIC/tables/products.md"));
        assertThat(products).contains("```mermaid");
        assertThat(products).doesNotContain("ER diagram omitted");
    }

    @Test
    void tablePageErDiagramIsUnlimitedWhenMaxEntitiesIsZero(@TempDir Path outputDir)
            throws Exception {
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildFkChainSchemaInfo(), List.of(), true, false, null, true, 0);
        generator.generate(outputDir);

        String users = Files.readString(outputDir.resolve("PUBLIC/tables/users.md"));
        assertThat(users).contains("```mermaid");
        assertThat(users).doesNotContain("ER diagram omitted");
    }

    @Test
    void tablePageErDiagramIsUnlimitedWhenMaxEntitiesIsNegative(@TempDir Path outputDir)
            throws Exception {
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildFkChainSchemaInfo(), List.of(), true, false, null, true, -1);
        generator.generate(outputDir);

        String users = Files.readString(outputDir.resolve("PUBLIC/tables/users.md"));
        assertThat(users).contains("```mermaid");
        assertThat(users).doesNotContain("ER diagram omitted");
    }

    @Test
    void tablePageOmittedErDiagramMessageContainsMarkdownLinkToIndex(@TempDir Path outputDir)
            throws Exception {
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildFkChainSchemaInfo(), List.of(), true, false, "elk", true, 1);
        generator.generate(outputDir);

        String orders = Files.readString(outputDir.resolve("PUBLIC/tables/orders.md"));
        assertThat(orders).contains("](../../index.md)");
    }

    @Test
    void tablePageErDiagramOmissionIsBasedOnEntityCountEvenWithKeysOnly(@TempDir Path outputDir)
            throws Exception {
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildFkChainSchemaInfo(), List.of(), true, true, null, true, 1);
        generator.generate(outputDir);

        String products = Files.readString(outputDir.resolve("PUBLIC/tables/products.md"));
        assertThat(products).contains("## ER Diagram");
        assertThat(products).doesNotContain("```mermaid");
        assertThat(products)
                .contains("ER diagram omitted: this table's neighborhood includes 2 entities");
    }

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\]\\(([^)]+)\\)");

    /**
     * Walks every generated Markdown file and asserts that each relative link resolves to a file
     * that actually exists. Guards the whole cross-linking scheme at once: index -&gt; detail
     * pages, foreign-key links between table pages, and the omitted-ER-diagram link back to the
     * index. A change to the directory depth that forgets to adjust a {@code ../} prefix fails
     * here.
     */
    @Test
    void everyRelativeLinkResolvesToAnExistingFile(@TempDir Path outputDir) throws Exception {
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildFkChainSchemaInfo(), List.of(), true, false, "elk", true, 1);
        generator.generate(outputDir);

        List<String> broken = new ArrayList<>();
        try (Stream<Path> files = Files.walk(outputDir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".md")).toList()) {
                Matcher m = MARKDOWN_LINK.matcher(Files.readString(file));
                while (m.find()) {
                    String link = m.group(1);
                    Path resolved = file.getParent().resolve(link).normalize();
                    if (!Files.exists(resolved)) {
                        broken.add(outputDir.relativize(file) + " -> " + link);
                    }
                }
            }
        }

        assertThat(broken).isEmpty();
    }

    @Test
    void tableAndViewFilesAreWrittenDirectlyUnderTheSchemaDirectory(@TempDir Path outputDir) {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        assertThat(outputDir.resolve("PUBLIC/tables/users.md")).exists();
        assertThat(outputDir.resolve("PUBLIC/views/active_users.md")).exists();
        assertThat(outputDir.resolve("mydb")).doesNotExist();
    }

    @Test
    void indexLinksOmitTheGeneratorNameLevel(@TempDir Path outputDir) throws Exception {
        var generator = new JdbcMarkdownGenerator("mydb", buildSchemaInfo(), List.of());

        generator.generate(outputDir);

        String content = Files.readString(outputDir.resolve("index.md"));
        assertThat(content).contains("| [users](PUBLIC/tables/users.md) |");
        assertThat(content).doesNotContain("mydb/PUBLIC");
    }
}
