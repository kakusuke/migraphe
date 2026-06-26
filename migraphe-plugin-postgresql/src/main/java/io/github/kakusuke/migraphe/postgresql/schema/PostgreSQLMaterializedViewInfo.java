package io.github.kakusuke.migraphe.postgresql.schema;

import org.jspecify.annotations.Nullable;

/**
 * Schema information for a single PostgreSQL materialized view.
 *
 * <p>A materialized view stores the physical result of a query, which must be explicitly refreshed
 * to reflect changes in the underlying data. The values are read from the {@code pg_matviews} view.
 * This record is one of the PostgreSQL-specific elements collected by {@link
 * PostgreSQLSchemaInfoProvider} and exposed through {@link PostgreSQLSchemaInfo}.
 *
 * @param name the materialized view name
 * @param schema the schema that contains the materialized view
 * @param definition the {@code SELECT} query that defines the materialized view
 * @param tablespace the tablespace the materialized view is stored in, or {@code null} if it uses
 *     the database default
 * @param owner the role name that owns the materialized view, or {@code null} if not captured
 */
public record PostgreSQLMaterializedViewInfo(
        String name,
        String schema,
        String definition,
        @Nullable String tablespace,
        @Nullable String owner) {

    /**
     * Creates materialized-view information without a known owner.
     *
     * @param name the materialized view name
     * @param schema the schema that contains the materialized view
     * @param definition the {@code SELECT} query that defines the materialized view
     * @param tablespace the tablespace, or {@code null} if it uses the database default
     */
    public PostgreSQLMaterializedViewInfo(
            String name, String schema, String definition, @Nullable String tablespace) {
        this(name, schema, definition, tablespace, null);
    }
}
