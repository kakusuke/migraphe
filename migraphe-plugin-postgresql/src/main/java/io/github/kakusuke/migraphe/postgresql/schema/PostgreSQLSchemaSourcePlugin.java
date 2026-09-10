package io.github.kakusuke.migraphe.postgresql.schema;

import io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.api.generator.SourceContext;
import io.github.kakusuke.migraphe.api.target.Target;
import java.util.Objects;

/**
 * Generator source plugin that supplies PostgreSQL schema information.
 *
 * <p>This plugin extracts a {@link PostgreSQLSchemaInfo} snapshot from the target carried by a
 * {@link SourceContext}, delegating the actual catalog introspection to {@link
 * PostgreSQLSchemaInfoProvider}. The resulting data can be rendered by any compatible {@link
 * io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin}, such as the {@code
 * postgresql-markdown} output.
 *
 * <p>It is registered for the {@code postgresql-schema} source type and discovered at runtime via
 * the {@link java.util.ServiceLoader} mechanism through the {@code
 * META-INF/services/io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin} resource.
 *
 * @see GeneratorSourcePlugin
 */
public final class PostgreSQLSchemaSourcePlugin
        implements GeneratorSourcePlugin<PostgreSQLSchemaInfo> {

    /** Creates a new {@code PostgreSQLSchemaSourcePlugin}. */
    public PostgreSQLSchemaSourcePlugin() {}

    /**
     * Returns the source-type identifier for this plugin.
     *
     * @return the constant {@code "postgresql-schema"}
     */
    @Override
    public String type() {
        return "postgresql-schema";
    }

    /**
     * Returns the concrete data type produced by this source plugin.
     *
     * @return {@code PostgreSQLSchemaInfo.class}
     */
    @Override
    public Class<PostgreSQLSchemaInfo> dataClass() {
        return PostgreSQLSchemaInfo.class;
    }

    /**
     * Extracts PostgreSQL schema information from the given context.
     *
     * @param context the extraction context; its {@linkplain SourceContext#target() target} is
     *     required and must be a PostgreSQL target
     * @return the extracted PostgreSQL schema information
     * @throws NullPointerException if the context carries no target
     */
    @Override
    public PostgreSQLSchemaInfo extract(SourceContext context) {
        Target target =
                Objects.requireNonNull(
                        context.target(), "Target is required for postgresql-schema source");
        return new PostgreSQLSchemaInfoProvider().getSchemaInfo(target);
    }
}
