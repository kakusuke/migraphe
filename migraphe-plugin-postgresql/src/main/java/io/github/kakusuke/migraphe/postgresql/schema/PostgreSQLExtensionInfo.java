package io.github.kakusuke.migraphe.postgresql.schema;

import org.jspecify.annotations.Nullable;

/**
 * Schema information for a single installed PostgreSQL extension.
 *
 * <p>An extension is an installable package of SQL objects (types, functions, operators, and so on)
 * registered in the {@code pg_extension} catalog, such as {@code uuid-ossp} or {@code postgis}.
 * This record is one of the PostgreSQL-specific elements collected by {@link
 * PostgreSQLSchemaInfoProvider} and exposed through {@link PostgreSQLSchemaInfo} so generators can
 * document the extensions a database depends on.
 *
 * @param name the extension name as registered in {@code pg_extension.extname} (for example {@code
 *     "uuid-ossp"})
 * @param version the installed version string of the extension ({@code pg_extension.extversion})
 * @param owner the role name that owns the extension, or {@code null} if not captured
 */
public record PostgreSQLExtensionInfo(String name, String version, @Nullable String owner) {

    /**
     * Creates extension information without a known owner.
     *
     * @param name the extension name
     * @param version the installed version string of the extension
     */
    public PostgreSQLExtensionInfo(String name, String version) {
        this(name, version, null);
    }
}
