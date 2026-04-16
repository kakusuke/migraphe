package io.github.kakusuke.migraphe.mysql.markdown;

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
import io.github.kakusuke.migraphe.mysql.schema.MySQLEventInfo;
import io.github.kakusuke.migraphe.mysql.schema.MySQLPartitionInfo;
import io.github.kakusuke.migraphe.mysql.schema.MySQLRoutineInfo;
import io.github.kakusuke.migraphe.mysql.schema.MySQLSchemaInfo;
import io.github.kakusuke.migraphe.mysql.schema.MySQLStorageEngineInfo;
import io.github.kakusuke.migraphe.mysql.schema.MySQLTableMetaInfo;
import io.github.kakusuke.migraphe.mysql.schema.MySQLTriggerInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MySQLMarkdownGeneratorTest {

    private JdbcSchemaDetail emptySchema(String name) {
        return new JdbcSchemaDetail(
                name,
                List.of(),
                List.of(),
                List.<JdbcRoutineInfo>of(),
                List.<JdbcTriggerInfo>of(),
                List.<JdbcSequenceInfo>of(),
                List.<JdbcUdtInfo>of());
    }

    private JdbcSchemaDetail schemaWithTable(String schemaName) {
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
        return new JdbcSchemaDetail(
                schemaName,
                List.of(table),
                List.of(),
                List.<JdbcRoutineInfo>of(),
                List.<JdbcTriggerInfo>of(),
                List.<JdbcSequenceInfo>of(),
                List.<JdbcUdtInfo>of());
    }

    @Test
    void generatesStorageEnginesInIndex(@TempDir Path tempDir) throws Exception {
        var schemaInfo =
                new MySQLSchemaInfo(
                        List.of(emptySchema("mydb")),
                        List.of(
                                new MySQLStorageEngineInfo(
                                        "InnoDB", "DEFAULT", "YES", "YES", "YES"),
                                new MySQLStorageEngineInfo("MyISAM", "YES", "NO", "NO", "NO")),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        var generator =
                new MySQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent)
                .contains("## Storage Engines")
                .contains("InnoDB")
                .contains("DEFAULT")
                .contains("MyISAM");
    }

    @Test
    void generatesTablePropertiesSection(@TempDir Path tempDir) throws Exception {
        var schemaInfo =
                new MySQLSchemaInfo(
                        List.of(schemaWithTable("mydb")),
                        List.of(),
                        List.of(
                                new MySQLTableMetaInfo(
                                        "mydb",
                                        "users",
                                        "InnoDB",
                                        "utf8mb4_general_ci",
                                        "Dynamic",
                                        "User table")),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        var generator =
                new MySQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String tableContent = Files.readString(tempDir.resolve("testdb/mydb/tables/users.md"));
        assertThat(tableContent)
                .contains("## Table Properties")
                .contains("InnoDB")
                .contains("utf8mb4_general_ci")
                .contains("Dynamic");
    }

    @Test
    void generatesTriggersInTableSection(@TempDir Path tempDir) throws Exception {
        var schemaInfo =
                new MySQLSchemaInfo(
                        List.of(schemaWithTable("mydb")),
                        List.of(),
                        List.of(),
                        List.of(
                                new MySQLTriggerInfo(
                                        "mydb",
                                        "users",
                                        "trg_insert",
                                        "BEFORE",
                                        "INSERT",
                                        "SET NEW.val = 1")),
                        List.of(),
                        List.of(),
                        List.of());

        var generator =
                new MySQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String tableContent = Files.readString(tempDir.resolve("testdb/mydb/tables/users.md"));
        assertThat(tableContent)
                .contains("## Triggers")
                .contains("trg_insert")
                .contains("BEFORE")
                .contains("INSERT");
    }

    @Test
    void generatesRoutinesInSchemaIndex(@TempDir Path tempDir) throws Exception {
        var schemaInfo =
                new MySQLSchemaInfo(
                        List.of(emptySchema("mydb")),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new MySQLRoutineInfo(
                                        "mydb",
                                        "get_user",
                                        "FUNCTION",
                                        "VARCHAR",
                                        "id INT",
                                        "DEFINER")),
                        List.of(),
                        List.of());

        var generator =
                new MySQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent).contains("### Routines").contains("get_user");

        Path routineFile = tempDir.resolve("testdb/mydb/routines/get_user.md");
        assertThat(routineFile).exists();
        String routineContent = Files.readString(routineFile);
        assertThat(routineContent)
                .contains("# get_user")
                .contains("FUNCTION")
                .contains("VARCHAR")
                .contains("DEFINER");
    }

    @Test
    void generatesEventsInSchemaIndex(@TempDir Path tempDir) throws Exception {
        var schemaInfo =
                new MySQLSchemaInfo(
                        List.of(emptySchema("mydb")),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new MySQLEventInfo(
                                        "mydb",
                                        "cleanup",
                                        "RECURRING",
                                        "1",
                                        "DAY",
                                        "ENABLED",
                                        "DELETE FROM logs")),
                        List.of());

        var generator =
                new MySQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent)
                .contains("### Events")
                .contains("cleanup")
                .contains("RECURRING")
                .contains("ENABLED");
    }

    @Test
    void generatesPartitionsInSchemaIndex(@TempDir Path tempDir) throws Exception {
        var schemaInfo =
                new MySQLSchemaInfo(
                        List.of(emptySchema("mydb")),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new MySQLPartitionInfo(
                                        "mydb", "orders", "RANGE", "order_date", 4)));

        var generator =
                new MySQLMarkdownGenerator(
                        "testdb", schemaInfo, List.<JdbcMarkdownDefinition.ExcludePattern>of());

        generator.generate(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertThat(indexContent).contains("### Partitions").contains("orders").contains("RANGE");
    }
}
