package io.github.kakusuke.migraphe.jdbc.schema;

/**
 * Information about a single routine parameter or result column.
 *
 * <p>Immutable data holder for one entry in a {@link JdbcRoutineInfo}'s parameter list. Depending
 * on {@code columnType}, it may represent an input/output argument, the routine's return value, or
 * a column of a returned result set.
 *
 * @param name the parameter or column name
 * @param typeName the SQL data type name of the parameter or column (for example {@code "varchar"})
 * @param columnType the role/direction of this entry within the routine
 * @see ColumnType
 * @see JdbcRoutineInfo
 */
public record JdbcRoutineColumnInfo(String name, String typeName, ColumnType columnType) {}
