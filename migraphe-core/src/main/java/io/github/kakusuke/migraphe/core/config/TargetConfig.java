package io.github.kakusuke.migraphe.core.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import java.util.Optional;

/**
 * Configuration for a single target, such as a database connection.
 *
 * <p>A SmallRye {@code @ConfigMapping} interface (empty prefix) read from a {@code targets/*.yaml}
 * file. It captures the JDBC connection settings common to the built-in JDBC-based plugins;
 * plugin-specific environment definitions are mapped separately via their own
 * {@code @ConfigMapping} types in {@link ConfigLoader#loadEnvironmentDefinition}.
 */
@ConfigMapping(prefix = "")
public interface TargetConfig {

    /**
     * The target type, identifying the plugin to use (for example {@code "postgresql"} or {@code
     * "mysql"}).
     *
     * @return the target type identifier
     */
    String type();

    /**
     * The JDBC connection URL.
     *
     * <p>Bound to the {@code jdbc_url} key in YAML (snake-case), via {@link WithName}.
     *
     * @return the JDBC URL
     */
    @WithName("jdbc_url")
    String jdbcUrl();

    /**
     * The database user name used to connect.
     *
     * @return the user name
     */
    String username();

    /**
     * The database password.
     *
     * @return the password, or an empty {@link Optional} if unset or blank
     */
    Optional<String> password();
}
