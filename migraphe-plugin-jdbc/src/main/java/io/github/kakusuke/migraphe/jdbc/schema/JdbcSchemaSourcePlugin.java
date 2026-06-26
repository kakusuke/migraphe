package io.github.kakusuke.migraphe.jdbc.schema;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.api.generator.SourceContext;
import java.util.Objects;

/**
 * Generator source plugin that extracts JDBC database schema information.
 *
 * <p>This is the {@code jdbc-schema} source: given a {@link SourceContext} carrying a JDBC {@link
 * Environment}, it produces a {@link JdbcSchemaInfo} snapshot by delegating to {@link
 * JdbcSchemaInfoProvider}. The resulting data can then be rendered by any output plugin that
 * accepts {@link JdbcSchemaInfo} (for example the JDBC Markdown generator).
 *
 * <p>The plugin is discovered at runtime via {@link java.util.ServiceLoader}; it is registered in
 * the {@code META-INF/services/io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin}
 * resource of the JDBC plugin module.
 *
 * @see GeneratorSourcePlugin
 * @see JdbcSchemaInfoProvider
 */
public final class JdbcSchemaSourcePlugin implements GeneratorSourcePlugin<JdbcSchemaInfo> {

    /** Creates a new {@code JdbcSchemaSourcePlugin}. */
    public JdbcSchemaSourcePlugin() {}

    /**
     * {@inheritDoc}
     *
     * @return the constant identifier {@code "jdbc-schema"}
     */
    @Override
    public String type() {
        return "jdbc-schema";
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code JdbcSchemaInfo.class}
     */
    @Override
    public Class<JdbcSchemaInfo> dataClass() {
        return JdbcSchemaInfo.class;
    }

    /**
     * Extracts the JDBC schema snapshot from the given context.
     *
     * <p>Requires the context to carry a non-{@code null} {@link Environment}, which must be a JDBC
     * environment usable by {@link JdbcSchemaInfoProvider}.
     *
     * @param context the extraction context; its {@link SourceContext#environment()} must be
     *     present
     * @return the extracted schema information
     * @throws NullPointerException if the context carries no environment
     */
    @Override
    public JdbcSchemaInfo extract(SourceContext context) {
        Environment environment =
                Objects.requireNonNull(
                        context.environment(), "Environment is required for jdbc-schema source");
        return new JdbcSchemaInfoProvider().getSchemaInfo(environment);
    }
}
