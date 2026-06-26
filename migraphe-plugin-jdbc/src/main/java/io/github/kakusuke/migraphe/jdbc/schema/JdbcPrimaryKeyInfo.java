package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/**
 * Primary key information for a table.
 *
 * <p>Immutable data holder populated from {@link java.sql.DatabaseMetaData#getPrimaryKeys}. A table
 * with no primary key is represented by an empty {@code name} and an empty {@code columns} list.
 *
 * @param name the primary key constraint name, or an empty string if the driver reports none
 * @param columns the names of the columns that make up the primary key, in key order
 */
public record JdbcPrimaryKeyInfo(String name, List<String> columns) {}
