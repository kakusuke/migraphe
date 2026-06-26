package io.github.kakusuke.migraphe.mysql.schema;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.api.generator.SourceContext;
import java.util.Objects;

/**
 * Generator source plugin that extracts MySQL-specific schema information from a connected
 * environment.
 *
 * <p>This is the MySQL implementation of {@link GeneratorSourcePlugin}; it is selected when a
 * generator's {@code source.type} is {@code "mysql-schema"} and produces a {@link MySQLSchemaInfo}
 * by delegating to {@link MySQLSchemaInfoProvider}. The resulting data can be rendered by any
 * output plugin that accepts {@link MySQLSchemaInfo} (or, since it implements {@link
 * io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo}, by generic JDBC outputs).
 *
 * <p>The plugin is discovered at runtime via {@link java.util.ServiceLoader}; it is listed in the
 * module's {@code
 * META-INF/services/io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin} resource.
 *
 * @see MySQLSchemaInfoProvider
 * @see MySQLSchemaInfo
 */
public final class MySQLSchemaSourcePlugin implements GeneratorSourcePlugin<MySQLSchemaInfo> {

    /** Creates a new {@code MySQLSchemaSourcePlugin}. */
    public MySQLSchemaSourcePlugin() {}

    /**
     * Returns the source-type identifier {@code "mysql-schema"} used to select this plugin from a
     * generator's configuration.
     *
     * @return the constant string {@code "mysql-schema"}
     */
    @Override
    public String type() {
        return "mysql-schema";
    }

    /**
     * Returns the concrete data type this source produces.
     *
     * @return {@code MySQLSchemaInfo.class}
     */
    @Override
    public Class<MySQLSchemaInfo> dataClass() {
        return MySQLSchemaInfo.class;
    }

    /**
     * Extracts MySQL schema information from the environment carried by the given context.
     *
     * @param context the extraction context; its {@link SourceContext#environment() environment}
     *     must be present and must be a MySQL environment
     * @return the extracted MySQL schema snapshot
     * @throws NullPointerException if the context carries no environment
     * @throws io.github.kakusuke.migraphe.mysql.MySQLException if the environment is not a {@code
     *     MySQLEnvironment} or schema introspection fails
     */
    @Override
    public MySQLSchemaInfo extract(SourceContext context) {
        Environment environment =
                Objects.requireNonNull(
                        context.environment(), "Environment is required for mysql-schema source");
        return new MySQLSchemaInfoProvider().getSchemaInfo(environment);
    }
}
