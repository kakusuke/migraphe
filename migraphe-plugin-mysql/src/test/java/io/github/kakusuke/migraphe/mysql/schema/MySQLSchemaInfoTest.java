package io.github.kakusuke.migraphe.mysql.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class MySQLSchemaInfoTest {

    @Test
    void shouldHoldStorageEngineInfo() {
        var engineInfo = new MySQLStorageEngineInfo("InnoDB", "DEFAULT", "YES", "YES", "YES");

        assertThat(engineInfo.name()).isEqualTo("InnoDB");
        assertThat(engineInfo.support()).isEqualTo("DEFAULT");
        assertThat(engineInfo.transactions()).isEqualTo("YES");
        assertThat(engineInfo.xa()).isEqualTo("YES");
        assertThat(engineInfo.savepoints()).isEqualTo("YES");
    }

    @Test
    void shouldHoldTableMetaInfo() {
        var tableMetaInfo =
                new MySQLTableMetaInfo(
                        "mydb", "users", "InnoDB", "utf8mb4_general_ci", "Dynamic", "User table");

        assertThat(tableMetaInfo.schema()).isEqualTo("mydb");
        assertThat(tableMetaInfo.tableName()).isEqualTo("users");
        assertThat(tableMetaInfo.engine()).isEqualTo("InnoDB");
        assertThat(tableMetaInfo.collation()).isEqualTo("utf8mb4_general_ci");
        assertThat(tableMetaInfo.rowFormat()).isEqualTo("Dynamic");
        assertThat(tableMetaInfo.tableComment()).isEqualTo("User table");
    }

    @Test
    void shouldHoldTriggerInfo() {
        var triggerInfo =
                new MySQLTriggerInfo(
                        "mydb",
                        "users",
                        "trg_users_insert",
                        "BEFORE",
                        "INSERT",
                        "SET NEW.created_at = NOW()");

        assertThat(triggerInfo.schema()).isEqualTo("mydb");
        assertThat(triggerInfo.tableName()).isEqualTo("users");
        assertThat(triggerInfo.name()).isEqualTo("trg_users_insert");
        assertThat(triggerInfo.timing()).isEqualTo("BEFORE");
        assertThat(triggerInfo.event()).isEqualTo("INSERT");
        assertThat(triggerInfo.statement()).isEqualTo("SET NEW.created_at = NOW()");
    }

    @Test
    void shouldHoldRoutineInfo() {
        var routineInfo =
                new MySQLRoutineInfo(
                        "mydb",
                        "get_user",
                        "FUNCTION",
                        "VARCHAR",
                        List.of(new MySQLParameterInfo(1, "IN", "id", "int")),
                        "DEFINER");

        assertThat(routineInfo.schema()).isEqualTo("mydb");
        assertThat(routineInfo.name()).isEqualTo("get_user");
        assertThat(routineInfo.type()).isEqualTo("FUNCTION");
        assertThat(routineInfo.dataType()).isEqualTo("VARCHAR");
        assertThat(routineInfo.parameters())
                .containsExactly(new MySQLParameterInfo(1, "IN", "id", "int"));
        assertThat(routineInfo.securityType()).isEqualTo("DEFINER");
    }

    @Test
    void shouldHoldEventInfo() {
        var eventInfo =
                new MySQLEventInfo(
                        "mydb",
                        "cleanup_event",
                        "RECURRING",
                        "1",
                        "DAY",
                        "ENABLED",
                        "DELETE FROM logs WHERE created_at < NOW() - INTERVAL 30 DAY");

        assertThat(eventInfo.schema()).isEqualTo("mydb");
        assertThat(eventInfo.name()).isEqualTo("cleanup_event");
        assertThat(eventInfo.type()).isEqualTo("RECURRING");
        assertThat(eventInfo.intervalValue()).isEqualTo("1");
        assertThat(eventInfo.intervalField()).isEqualTo("DAY");
        assertThat(eventInfo.status()).isEqualTo("ENABLED");
        assertThat(eventInfo.definition())
                .isEqualTo("DELETE FROM logs WHERE created_at < NOW() - INTERVAL 30 DAY");
    }

    @Test
    void shouldHoldPartitionInfo() {
        var partitionInfo = new MySQLPartitionInfo("mydb", "orders", "RANGE", "order_date", 4);

        assertThat(partitionInfo.schema()).isEqualTo("mydb");
        assertThat(partitionInfo.tableName()).isEqualTo("orders");
        assertThat(partitionInfo.partitionMethod()).isEqualTo("RANGE");
        assertThat(partitionInfo.partitionExpression()).isEqualTo("order_date");
        assertThat(partitionInfo.partitionCount()).isEqualTo(4);
    }

    @Test
    void shouldImplementJdbcSchemaInfoAndExposeAllFields() {
        var schemaInfo =
                new MySQLSchemaInfo(
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of());

        assertThat(schemaInfo).isInstanceOf(JdbcSchemaInfo.class);
        assertThat(schemaInfo.schemas()).isEmpty();
        assertThat(schemaInfo.storageEngines()).isEmpty();
        assertThat(schemaInfo.tableMeta()).isEmpty();
        assertThat(schemaInfo.triggers()).isEmpty();
        assertThat(schemaInfo.routines()).isEmpty();
        assertThat(schemaInfo.events()).isEmpty();
        assertThat(schemaInfo.partitions()).isEmpty();
    }
}
