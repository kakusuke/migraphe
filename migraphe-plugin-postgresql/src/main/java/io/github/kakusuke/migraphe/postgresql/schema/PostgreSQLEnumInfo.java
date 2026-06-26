package io.github.kakusuke.migraphe.postgresql.schema;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Schema information for a single PostgreSQL enumerated ({@code ENUM}) type.
 *
 * <p>A PostgreSQL enum is a user-defined type whose values are drawn from a fixed, ordered list of
 * labels, stored in the {@code pg_type}/{@code pg_enum} catalogs. This record is one of the
 * PostgreSQL-specific elements collected by {@link PostgreSQLSchemaInfoProvider} and exposed
 * through {@link PostgreSQLSchemaInfo}.
 *
 * @param name the enum type name as registered in {@code pg_type.typname}
 * @param labels the enum labels in their defined sort order ({@code pg_enum.enumsortorder})
 * @param owner the role name that owns the type, or {@code null} if not captured
 */
public record PostgreSQLEnumInfo(String name, List<String> labels, @Nullable String owner) {

    /**
     * Creates enum information without a known owner.
     *
     * @param name the enum type name
     * @param labels the enum labels in their defined sort order
     */
    public PostgreSQLEnumInfo(String name, List<String> labels) {
        this(name, labels, null);
    }
}
