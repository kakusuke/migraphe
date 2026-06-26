package io.github.kakusuke.migraphe.jdbc.schema;

/**
 * Information about a single column participating in an index.
 *
 * <p>Populated from {@link java.sql.DatabaseMetaData#getIndexInfo}.
 *
 * @param name the indexed column name ({@code COLUMN_NAME})
 * @param ascOrDesc the sort order of the column within the index ({@code ASC_OR_DESC}): {@code "A"}
 *     for ascending or {@code "D"} for descending; defaults to {@code "A"} when the driver reports
 *     no order
 */
public record JdbcIndexColumn(String name, String ascOrDesc) {}
