package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/**
 * Information about a single index on a table.
 *
 * <p>Populated from {@link java.sql.DatabaseMetaData#getIndexInfo}; the rows of an index are
 * grouped by index name, and table-statistic pseudo-index entries are excluded.
 *
 * @param name the index name ({@code INDEX_NAME})
 * @param unique {@code true} if the index enforces uniqueness (derived from {@code NON_UNIQUE}
 *     being {@code false})
 * @param columns the indexed columns, in index order
 */
public record JdbcIndexInfo(String name, boolean unique, List<JdbcIndexColumn> columns) {}
