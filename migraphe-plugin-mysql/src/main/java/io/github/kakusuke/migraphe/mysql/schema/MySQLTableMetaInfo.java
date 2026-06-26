package io.github.kakusuke.migraphe.mysql.schema;

import org.jspecify.annotations.Nullable;

/**
 * MySQL-specific metadata for a single base table, as reported by {@code
 * information_schema.TABLES}.
 *
 * <p>This data holder captures table-level attributes that the portable JDBC schema model does not
 * expose. Instances are collected into {@link MySQLSchemaInfo#tableMeta()} (one per {@code BASE
 * TABLE} in the inspected schema) for generators to render alongside the portable table structure.
 *
 * @param schema the schema the table belongs to (the {@code TABLE_SCHEMA} column)
 * @param tableName the table name (the {@code TABLE_NAME} column)
 * @param engine the storage engine backing the table (the {@code ENGINE} column, for example {@code
 *     "InnoDB"}); empty string if the source value was {@code null}
 * @param collation the table's default collation (the {@code TABLE_COLLATION} column); empty string
 *     if the source value was {@code null}
 * @param rowFormat the row storage format (the {@code ROW_FORMAT} column, for example {@code
 *     "Dynamic"} or {@code "Compact"}); empty string if the source value was {@code null}
 * @param tableComment the user-supplied table comment (the {@code TABLE_COMMENT} column), or {@code
 *     null} when no comment is set
 */
public record MySQLTableMetaInfo(
        String schema,
        String tableName,
        String engine,
        String collation,
        String rowFormat,
        @Nullable String tableComment) {}
