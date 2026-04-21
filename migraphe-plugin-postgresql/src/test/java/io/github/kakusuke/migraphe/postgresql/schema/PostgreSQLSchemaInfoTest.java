package io.github.kakusuke.migraphe.postgresql.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaDetail;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostgreSQLSchemaInfoTest {

    @Test
    void shouldImplementJdbcSchemaInfo() {
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of());

        assertThat(schemaInfo).isInstanceOf(JdbcSchemaInfo.class);
    }

    @Test
    void shouldHoldEmptyExtensions() {
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of());

        assertThat(schemaInfo.extensions()).isEmpty();
    }

    @Test
    void shouldHoldExtensions() {
        var extension = new PostgreSQLExtensionInfo("pg_stat_statements", "1.10");
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(),
                        List.of(extension),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        assertThat(schemaInfo.extensions()).hasSize(1);
        var ext = schemaInfo.extensions().get(0);
        assertThat(ext.name()).isEqualTo("pg_stat_statements");
        assertThat(ext.version()).isEqualTo("1.10");
    }

    @Test
    void shouldReturnSchemas() {
        var schema =
                new JdbcSchemaDetail(
                        "public", List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(schema),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        assertThat(schemaInfo.schemas()).containsExactly(schema);
    }

    @Test
    void shouldHoldEnumTypes() {
        var enumInfo = new PostgreSQLEnumInfo("mood", List.of("happy", "sad", "ok"));
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(),
                        List.of(),
                        List.of(enumInfo),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        assertThat(schemaInfo.enums()).hasSize(1);
        assertThat(schemaInfo.enums().get(0).name()).isEqualTo("mood");
        assertThat(schemaInfo.enums().get(0).labels()).containsExactly("happy", "sad", "ok");
    }

    @Test
    void shouldHoldSequences() {
        var seq =
                new PostgreSQLSequenceInfo(
                        "public",
                        "user_id_seq",
                        "bigint",
                        1,
                        1,
                        1,
                        Long.MAX_VALUE,
                        false,
                        "users",
                        "id");
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(seq),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        assertThat(schemaInfo.sequences()).hasSize(1);
        assertThat(schemaInfo.sequences().get(0).name()).isEqualTo("user_id_seq");
        assertThat(schemaInfo.sequences().get(0).ownerTable()).isEqualTo("users");
    }

    @Test
    void shouldHoldFunctions() {
        var func =
                new PostgreSQLFunctionInfo(
                        "public", "add", "a integer, b integer", "integer", "sql", false);
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(func),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        assertThat(schemaInfo.functions()).hasSize(1);
        assertThat(schemaInfo.functions().get(0).name()).isEqualTo("add");
        assertThat(schemaInfo.functions().get(0).isProcedure()).isFalse();
    }

    @Test
    void shouldHoldTriggers() {
        var trigger =
                new PostgreSQLTriggerInfo(
                        "audit_trigger",
                        "public",
                        "users",
                        "AFTER",
                        List.of("INSERT", "UPDATE"),
                        "audit_func",
                        false);
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(trigger),
                        List.of(),
                        List.of(),
                        List.of());

        assertThat(schemaInfo.triggers()).hasSize(1);
        assertThat(schemaInfo.triggers().get(0).events()).containsExactly("INSERT", "UPDATE");
    }

    @Test
    void shouldHoldMaterializedViews() {
        var matView =
                new PostgreSQLMaterializedViewInfo(
                        "user_stats", "public", "SELECT count(*) FROM users", null);
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(matView),
                        List.of(),
                        List.of());

        assertThat(schemaInfo.materializedViews()).hasSize(1);
        assertThat(schemaInfo.materializedViews().get(0).tablespace()).isNull();
    }

    @Test
    void shouldHoldPartitions() {
        var partition = new PostgreSQLPartitionInfo("measurements", "public", "RANGE", "ts");
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(partition),
                        List.of());

        assertThat(schemaInfo.partitions()).hasSize(1);
        assertThat(schemaInfo.partitions().get(0).strategy()).isEqualTo("RANGE");
    }

    @Test
    void shouldHoldPolicies() {
        var policy =
                new PostgreSQLPolicyInfo(
                        "owner_policy",
                        "public",
                        "docs",
                        "ALL",
                        List.of("public"),
                        "owner = current_user",
                        null);
        var schemaInfo =
                new PostgreSQLSchemaInfo(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(policy));

        assertThat(schemaInfo.policies()).hasSize(1);
        assertThat(schemaInfo.policies().get(0).usingExpression())
                .isEqualTo("owner = current_user");
        assertThat(schemaInfo.policies().get(0).withCheckExpression()).isNull();
    }
}
