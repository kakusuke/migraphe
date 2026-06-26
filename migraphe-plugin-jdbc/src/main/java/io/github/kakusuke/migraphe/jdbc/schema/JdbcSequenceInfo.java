package io.github.kakusuke.migraphe.jdbc.schema;

/**
 * Sequence (auto-increment generator) information.
 *
 * <p>Immutable data holder describing a database sequence object. The generic JDBC provider does
 * not populate sequences (the portable metadata API does not expose them); this record is filled in
 * by dialect-specific providers such as PostgreSQL.
 *
 * @param name the sequence name
 * @param dataType the data type of the values the sequence produces (for example {@code "bigint"})
 * @param startValue the value the sequence starts at
 * @param increment the amount added on each step (negative for a descending sequence)
 * @param minValue the minimum value the sequence may take
 * @param maxValue the maximum value the sequence may take
 * @param cycle {@code true} if the sequence wraps around after reaching its bound, {@code false} if
 *     it stops
 */
public record JdbcSequenceInfo(
        String name,
        String dataType,
        long startValue,
        long increment,
        long minValue,
        long maxValue,
        boolean cycle) {}
