package io.github.kakusuke.migraphe.jdbc.schema;

/**
 * The role or direction of a routine parameter or result column.
 *
 * <p>Used by {@link JdbcRoutineColumnInfo} to classify each entry in a routine's parameter list,
 * mirroring the {@code COLUMN_TYPE} values reported by the JDBC metadata API.
 *
 * @see JdbcRoutineColumnInfo
 */
public enum ColumnType {
    /** An input-only parameter. */
    IN,
    /** An output-only parameter. */
    OUT,
    /** A parameter used for both input and output. */
    INOUT,
    /** The routine's return value. */
    RETURN,
    /** A column of a result set returned by the routine. */
    RESULT
}
