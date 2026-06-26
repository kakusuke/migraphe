package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/**
 * Information about a single database table.
 *
 * <p>Populated from {@link java.sql.DatabaseMetaData} for tables of type {@code "TABLE"}. The
 * generic JDBC provider fills in name, remarks, columns, primary key, imported and exported foreign
 * keys, and indexes; check constraints and privileges are left empty by the generic path and may be
 * supplied by dialect-specific providers.
 *
 * @param name the table name (the {@code TABLE_NAME} value from {@link
 *     java.sql.DatabaseMetaData#getTables})
 * @param remarks the comment/description on the table ({@code REMARKS}); empty string when none
 * @param columns the columns of the table, in ordinal order
 * @param primaryKey the primary-key definition of the table
 * @param foreignKeys the foreign keys declared on this table (imported keys — this table references
 *     others)
 * @param exportedKeys the foreign keys referencing this table (exported keys — other tables
 *     reference this one)
 * @param checkConstraints the check constraints on the table; empty unless populated by a
 *     dialect-specific provider
 * @param indexes the indexes defined on the table (table-statistic pseudo-index entries excluded)
 * @param privileges the access privileges granted on the table; empty unless populated by a
 *     dialect-specific provider
 */
public record JdbcTableInfo(
        String name,
        String remarks,
        List<JdbcColumnInfo> columns,
        JdbcPrimaryKeyInfo primaryKey,
        List<JdbcForeignKeyInfo> foreignKeys,
        List<JdbcForeignKeyInfo> exportedKeys,
        List<JdbcCheckConstraintInfo> checkConstraints,
        List<JdbcIndexInfo> indexes,
        List<JdbcPrivilegeInfo> privileges) {}
