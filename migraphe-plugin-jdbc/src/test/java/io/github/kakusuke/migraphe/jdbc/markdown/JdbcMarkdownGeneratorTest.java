package io.github.kakusuke.migraphe.jdbc.markdown;

import static org.assertj.core.api.Assertions.assertThat;

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

        return new JdbcSchemaInfo(List.of(schemaDetail));
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
    void excludesFilteredTables(@TempDir Path outputDir) {
        var generator =
                new JdbcMarkdownGenerator(
                        "mydb", buildSchemaInfo(), List.of(excludeTable("orders")));

        generator.generate(outputDir);

        assertThat(outputDir.resolve("mydb/PUBLIC/tables/users.md")).exists();
        assertThat(outputDir.resolve("mydb/PUBLIC/tables/orders.md")).doesNotExist();
    }
}
