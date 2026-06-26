package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/**
 * Foreign key information for a table.
 *
 * <p>Immutable data holder describing a single foreign key relationship, aggregated across all its
 * key columns. It is populated from {@link java.sql.DatabaseMetaData#getImportedKeys} (this table
 * references another) or {@link java.sql.DatabaseMetaData#getExportedKeys} (another table
 * references this one); {@code columns} and {@code referencedColumns} are positionally aligned. The
 * {@code updateRule}/{@code deleteRule} values are the human-readable referential-action strings
 * such as {@code "CASCADE"}, {@code "SET NULL"}, {@code "SET DEFAULT"}, {@code "RESTRICT"}, {@code
 * "NO ACTION"} or {@code "UNKNOWN"}.
 *
 * @param name the foreign key constraint name, or an empty string if the driver reports none
 * @param columns the local column names participating in the key, in key order
 * @param referencedSchema the schema of the referenced table, or an empty string if unavailable
 * @param referencedTable the name of the referenced table
 * @param referencedColumns the referenced column names, aligned with {@code columns}
 * @param updateRule the referential action applied on update of the referenced row
 * @param deleteRule the referential action applied on delete of the referenced row
 */
public record JdbcForeignKeyInfo(
        String name,
        List<String> columns,
        String referencedSchema,
        String referencedTable,
        List<String> referencedColumns,
        String updateRule,
        String deleteRule) {}
