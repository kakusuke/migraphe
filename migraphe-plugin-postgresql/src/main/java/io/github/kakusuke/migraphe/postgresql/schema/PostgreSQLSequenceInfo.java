package io.github.kakusuke.migraphe.postgresql.schema;

import org.jspecify.annotations.Nullable;

/**
 * Schema information for a single PostgreSQL sequence.
 *
 * <p>A sequence is a special single-row object that generates a monotonic series of numeric values,
 * commonly used to back auto-incrementing columns. The values here are read from the {@code
 * pg_sequences} view together with ownership details from {@code pg_class}/{@code pg_depend}. This
 * record is one of the PostgreSQL-specific elements collected by {@link
 * PostgreSQLSchemaInfoProvider} and exposed through {@link PostgreSQLSchemaInfo}.
 *
 * @param schema the schema that contains the sequence
 * @param name the sequence name
 * @param dataType the data type of the values the sequence produces (for example {@code "bigint"})
 * @param startValue the value the sequence starts from
 * @param increment the amount added on each step (negative for a descending sequence)
 * @param minValue the minimum value the sequence may take
 * @param maxValue the maximum value the sequence may take
 * @param cycle {@code true} if the sequence wraps around when it reaches its bound, {@code false}
 *     if it stops
 * @param ownerTable the table that owns the sequence when it backs a column, or {@code null} if the
 *     sequence is standalone
 * @param ownerColumn the column the sequence is attached to, or {@code null} if the sequence is
 *     standalone
 * @param owner the role name that owns the sequence, or {@code null} if not captured
 */
public record PostgreSQLSequenceInfo(
        String schema,
        String name,
        String dataType,
        long startValue,
        long increment,
        long minValue,
        long maxValue,
        boolean cycle,
        @Nullable String ownerTable,
        @Nullable String ownerColumn,
        @Nullable String owner) {

    /**
     * Creates sequence information without a known owner role.
     *
     * @param schema the schema that contains the sequence
     * @param name the sequence name
     * @param dataType the data type of the values the sequence produces
     * @param startValue the value the sequence starts from
     * @param increment the amount added on each step
     * @param minValue the minimum value the sequence may take
     * @param maxValue the maximum value the sequence may take
     * @param cycle {@code true} if the sequence wraps around at its bound
     * @param ownerTable the table that owns the sequence, or {@code null} if standalone
     * @param ownerColumn the column the sequence is attached to, or {@code null} if standalone
     */
    public PostgreSQLSequenceInfo(
            String schema,
            String name,
            String dataType,
            long startValue,
            long increment,
            long minValue,
            long maxValue,
            boolean cycle,
            @Nullable String ownerTable,
            @Nullable String ownerColumn) {
        this(
                schema,
                name,
                dataType,
                startValue,
                increment,
                minValue,
                maxValue,
                cycle,
                ownerTable,
                ownerColumn,
                null);
    }
}
