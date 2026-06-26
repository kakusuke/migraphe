package io.github.kakusuke.migraphe.jdbc.schema;

import org.jspecify.annotations.Nullable;

/**
 * Information about a single column of a table or view.
 *
 * <p>Populated from {@link java.sql.DatabaseMetaData#getColumns}. Field names in parentheses below
 * refer to the corresponding result-set columns of that call.
 *
 * @param name the column name ({@code COLUMN_NAME})
 * @param typeName the database-specific data-type name ({@code TYPE_NAME}, for example {@code
 *     VARCHAR} or {@code INTEGER})
 * @param dataType the JDBC type code ({@code DATA_TYPE}); one of the constants in {@link
 *     java.sql.Types}
 * @param size the column size ({@code COLUMN_SIZE}); for character types the maximum length, for
 *     numeric types the precision
 * @param decimalDigits the number of fractional digits ({@code DECIMAL_DIGITS}); {@code 0} for
 *     types that have no fractional part
 * @param nullable {@code true} if the column accepts {@code NULL} values (derived from {@code
 *     IS_NULLABLE} equal to {@code "YES"})
 * @param defaultValue the column's default value expression ({@code COLUMN_DEF}); {@code null} when
 *     no default is defined
 * @param autoIncrement {@code true} if the column is auto-incremented (derived from {@code
 *     IS_AUTOINCREMENT} equal to {@code "YES"})
 * @param generated {@code true} if the column is a generated (computed) column (derived from {@code
 *     IS_GENERATEDCOLUMN} equal to {@code "YES"})
 * @param remarks the comment/description on the column ({@code REMARKS}); {@code null} when none
 * @param ordinalPosition the 1-based position of the column within its table or view ({@code
 *     ORDINAL_POSITION})
 */
public record JdbcColumnInfo(
        String name,
        String typeName,
        int dataType,
        int size,
        int decimalDigits,
        boolean nullable,
        @Nullable String defaultValue,
        boolean autoIncrement,
        boolean generated,
        @Nullable String remarks,
        int ordinalPosition) {}
