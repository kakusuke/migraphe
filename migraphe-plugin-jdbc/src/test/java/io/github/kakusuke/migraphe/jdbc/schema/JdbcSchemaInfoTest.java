package io.github.kakusuke.migraphe.jdbc.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcSchemaInfoTest {

    @Test
    void constructCompleteSchemaInfoTreeAndAccessFields() {
        var column =
                new JdbcColumnInfo(
                        "id",
                        "INTEGER",
                        java.sql.Types.INTEGER,
                        10,
                        0,
                        false,
                        null,
                        true,
                        false,
                        "Primary key column",
                        1);
        var primaryKey = new JdbcPrimaryKeyInfo("pk_orders", List.of("id"));
        var foreignKey =
                new JdbcForeignKeyInfo(
                        "fk_orders_customers",
                        List.of("customer_id"),
                        "public",
                        "customers",
                        List.of("id"),
                        "NO ACTION",
                        "CASCADE");
        var exportedKey =
                new JdbcForeignKeyInfo(
                        "fk_items_orders",
                        List.of("order_id"),
                        "public",
                        "order_items",
                        List.of("id"),
                        "NO ACTION",
                        "NO ACTION");
        var checkConstraint = new JdbcCheckConstraintInfo("chk_positive", "amount > 0");
        var indexColumn = new JdbcIndexColumn("id", "A");
        var index = new JdbcIndexInfo("idx_orders_id", true, List.of(indexColumn));
        var privilege = new JdbcPrivilegeInfo("admin", "appuser", "SELECT", true);
        var table =
                new JdbcTableInfo(
                        "orders",
                        "Orders table",
                        List.of(column),
                        primaryKey,
                        List.of(foreignKey),
                        List.of(exportedKey),
                        List.of(checkConstraint),
                        List.of(index),
                        List.of(privilege));

        var viewColumn =
                new JdbcColumnInfo(
                        "total",
                        "NUMERIC",
                        java.sql.Types.NUMERIC,
                        19,
                        2,
                        true,
                        null,
                        false,
                        false,
                        null,
                        1);
        var view =
                new JdbcViewInfo(
                        "order_summary",
                        "Summarized orders",
                        List.of(viewColumn),
                        "SELECT id, SUM(amount) AS total FROM orders GROUP BY id");

        var routineParam = new JdbcRoutineColumnInfo("p_id", "INTEGER", ColumnType.IN);
        var routine =
                new JdbcRoutineInfo(
                        "get_order",
                        RoutineType.FUNCTION,
                        "Fetches an order by ID",
                        "BEGIN RETURN ...; END",
                        List.of(routineParam));

        var trigger =
                new JdbcTriggerInfo(
                        "trg_orders_audit",
                        "orders",
                        "INSERT",
                        "AFTER",
                        "BEGIN INSERT INTO audit_log VALUES (NEW.id); END");

        var sequence =
                new JdbcSequenceInfo("orders_seq", "BIGINT", 1L, 1L, 1L, Long.MAX_VALUE, false);

        var udt =
                new JdbcUdtInfo(
                        "address_type",
                        "io.example.Address",
                        java.sql.Types.STRUCT,
                        "Custom address UDT");

        var schemaDetail =
                new JdbcSchemaDetail(
                        "public",
                        List.of(table),
                        List.of(view),
                        List.of(routine),
                        List.of(trigger),
                        List.of(sequence),
                        List.of(udt));

        var schemaInfo = new DefaultJdbcSchemaInfo(List.of(schemaDetail));

        assertThat(schemaInfo.schemas()).hasSize(1);
        var schema = schemaInfo.schemas().get(0);
        assertThat(schema.name()).isEqualTo("public");

        assertThat(schema.tables()).hasSize(1);
        var t = schema.tables().get(0);
        assertThat(t.name()).isEqualTo("orders");
        assertThat(t.remarks()).isEqualTo("Orders table");
        assertThat(t.columns()).hasSize(1);
        assertThat(t.columns().get(0).name()).isEqualTo("id");
        assertThat(t.primaryKey()).isNotNull();
        assertThat(t.primaryKey().name()).isEqualTo("pk_orders");
        assertThat(t.foreignKeys()).hasSize(1);
        assertThat(t.foreignKeys().get(0).referencedTable()).isEqualTo("customers");
        assertThat(t.exportedKeys()).hasSize(1);
        assertThat(t.exportedKeys().get(0).referencedTable()).isEqualTo("order_items");
        assertThat(t.checkConstraints()).hasSize(1);
        assertThat(t.checkConstraints().get(0).checkClause()).isEqualTo("amount > 0");
        assertThat(t.indexes()).hasSize(1);
        assertThat(t.indexes().get(0).unique()).isTrue();
        assertThat(t.indexes().get(0).columns().get(0).ascOrDesc()).isEqualTo("A");
        assertThat(t.privileges()).hasSize(1);
        assertThat(t.privileges().get(0).grantable()).isTrue();

        assertThat(schema.views()).hasSize(1);
        var v = schema.views().get(0);
        assertThat(v.name()).isEqualTo("order_summary");
        assertThat(v.definition()).contains("SUM(amount)");
        assertThat(v.columns().get(0).name()).isEqualTo("total");

        assertThat(schema.routines()).hasSize(1);
        var r = schema.routines().get(0);
        assertThat(r.name()).isEqualTo("get_order");
        assertThat(r.type()).isEqualTo(RoutineType.FUNCTION);
        assertThat(r.parameters()).hasSize(1);
        assertThat(r.parameters().get(0).columnType()).isEqualTo(ColumnType.IN);

        assertThat(schema.triggers()).hasSize(1);
        var tr = schema.triggers().get(0);
        assertThat(tr.tableName()).isEqualTo("orders");
        assertThat(tr.event()).isEqualTo("INSERT");
        assertThat(tr.timing()).isEqualTo("AFTER");

        assertThat(schema.sequences()).hasSize(1);
        var seq = schema.sequences().get(0);
        assertThat(seq.name()).isEqualTo("orders_seq");
        assertThat(seq.cycle()).isFalse();
        assertThat(seq.startValue()).isEqualTo(1L);

        assertThat(schema.udts()).hasSize(1);
        var u = schema.udts().get(0);
        assertThat(u.name()).isEqualTo("address_type");
        assertThat(u.className()).isEqualTo("io.example.Address");
    }
}
