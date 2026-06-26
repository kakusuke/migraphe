package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/**
 * Top-level container for the schema information extracted from a JDBC database.
 *
 * <p>This is the typed data object produced by the {@code jdbc-schema} generator source (see {@link
 * JdbcSchemaSourcePlugin}) and consumed by output plugins such as the JDBC Markdown generator. It
 * holds one {@link JdbcSchemaDetail} per database schema discovered through {@link
 * java.sql.DatabaseMetaData}.
 *
 * <p>The interface lets database-specific plugins (PostgreSQL, MySQL) return richer implementations
 * while the generic JDBC path uses {@link DefaultJdbcSchemaInfo}.
 *
 * @see DefaultJdbcSchemaInfo
 * @see JdbcSchemaSourcePlugin
 */
public interface JdbcSchemaInfo {

    /**
     * Returns the per-schema detail records that make up this schema snapshot.
     *
     * @return the list of schema details, one entry per discovered database schema; never {@code
     *     null}
     */
    List<JdbcSchemaDetail> schemas();
}
