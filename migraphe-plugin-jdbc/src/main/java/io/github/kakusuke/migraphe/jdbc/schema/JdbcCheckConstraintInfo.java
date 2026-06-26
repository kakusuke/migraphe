package io.github.kakusuke.migraphe.jdbc.schema;

/**
 * Information about a single check constraint on a table.
 *
 * <p>{@link java.sql.DatabaseMetaData} does not expose check constraints, so this record is not
 * populated by the generic JDBC provider; it is filled in by dialect-specific providers that query
 * the database's catalog.
 *
 * @param name the constraint name
 * @param checkClause the boolean SQL expression that the constraint enforces (the {@code CHECK
 *     (...)} clause body)
 */
public record JdbcCheckConstraintInfo(String name, String checkClause) {}
