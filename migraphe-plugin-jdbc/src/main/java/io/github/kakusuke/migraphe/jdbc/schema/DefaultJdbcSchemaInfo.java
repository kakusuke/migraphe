package io.github.kakusuke.migraphe.jdbc.schema;

import java.util.List;

/**
 * Default record implementation of {@link JdbcSchemaInfo}.
 *
 * <p>This immutable holder is returned by the generic JDBC schema provider for databases that have
 * no dialect-specific extras. Database-specific plugins may instead supply their own {@link
 * JdbcSchemaInfo} implementation carrying additional metadata.
 *
 * @param schemas the per-schema detail records that make up this schema snapshot, one entry per
 *     discovered database schema
 */
public record DefaultJdbcSchemaInfo(List<JdbcSchemaDetail> schemas) implements JdbcSchemaInfo {}
