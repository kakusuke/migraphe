package io.github.kakusuke.migraphe.postgresql.schema;

import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaDetail;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import java.util.List;
import java.util.Map;

/**
 * Top-level container for PostgreSQL schema information.
 *
 * <p>This record extends the generic JDBC schema snapshot ({@link JdbcSchemaInfo}) with
 * PostgreSQL-specific catalog objects gathered from {@code pg_catalog} — extensions, enum types,
 * sequences, functions and procedures, triggers, materialized views, declarative partitions, and
 * row-level security policies — plus per-relation ownership maps. It is produced by {@link
 * PostgreSQLSchemaInfoProvider} and consumed by PostgreSQL generators (for example the {@code
 * postgresql-markdown} output) to render schema documentation.
 *
 * @param schemas the base JDBC schema details (tables, views, columns, and so on) inherited from
 *     {@link JdbcSchemaInfo}
 * @param extensions the installed PostgreSQL extensions
 * @param enums the user-defined enum types
 * @param sequences the sequences defined in the database
 * @param functions the functions and stored procedures
 * @param triggers the triggers attached to tables
 * @param materializedViews the materialized views
 * @param partitions the declaratively partitioned tables
 * @param policies the row-level security policies
 * @param tableOwners a map from {@code "schema.table"} to the owning role name, for tables and
 *     partitioned tables
 * @param viewOwners a map from {@code "schema.view"} to the owning role name, for views
 */
public record PostgreSQLSchemaInfo(
        List<JdbcSchemaDetail> schemas,
        List<PostgreSQLExtensionInfo> extensions,
        List<PostgreSQLEnumInfo> enums,
        List<PostgreSQLSequenceInfo> sequences,
        List<PostgreSQLFunctionInfo> functions,
        List<PostgreSQLTriggerInfo> triggers,
        List<PostgreSQLMaterializedViewInfo> materializedViews,
        List<PostgreSQLPartitionInfo> partitions,
        List<PostgreSQLPolicyInfo> policies,
        Map<String, String> tableOwners,
        Map<String, String> viewOwners)
        implements JdbcSchemaInfo {

    /**
     * Creates schema information with empty table- and view-owner maps.
     *
     * @param schemas the base JDBC schema details inherited from {@link JdbcSchemaInfo}
     * @param extensions the installed PostgreSQL extensions
     * @param enums the user-defined enum types
     * @param sequences the sequences defined in the database
     * @param functions the functions and stored procedures
     * @param triggers the triggers attached to tables
     * @param materializedViews the materialized views
     * @param partitions the declaratively partitioned tables
     * @param policies the row-level security policies
     */
    public PostgreSQLSchemaInfo(
            List<JdbcSchemaDetail> schemas,
            List<PostgreSQLExtensionInfo> extensions,
            List<PostgreSQLEnumInfo> enums,
            List<PostgreSQLSequenceInfo> sequences,
            List<PostgreSQLFunctionInfo> functions,
            List<PostgreSQLTriggerInfo> triggers,
            List<PostgreSQLMaterializedViewInfo> materializedViews,
            List<PostgreSQLPartitionInfo> partitions,
            List<PostgreSQLPolicyInfo> policies) {
        this(
                schemas,
                extensions,
                enums,
                sequences,
                functions,
                triggers,
                materializedViews,
                partitions,
                policies,
                Map.of(),
                Map.of());
    }
}
