package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import java.util.Optional;

/**
 * MySQL-specific {@link EnvironmentDefinition} subtype.
 *
 * <p>This is the configuration view of a single MySQL target. It is a SmallRye
 * {@code @ConfigMapping} interface whose properties bind directly from a target's YAML file; the
 * target name is derived from the file name. {@link MySQLEnvironmentProvider} consumes a bound
 * instance to build a {@link MySQLEnvironment}.
 *
 * <p>Example {@code targets/db1.yaml}:
 *
 * <pre>{@code
 * type: mysql
 * jdbc_url: jdbc:mysql://localhost:3306/mydb
 * username: dbuser
 * password: secret
 * }</pre>
 *
 * @see MySQLPlugin#environmentDefinitionClass()
 * @see MySQLEnvironmentProvider
 */
@ConfigMapping(prefix = "")
public interface MySQLEnvironmentDefinition extends EnvironmentDefinition {

    /**
     * Returns the target type, which is {@code "mysql"} for MySQL targets.
     *
     * @return the environment type identifier
     */
    @Override
    String type();

    /**
     * Returns the MySQL JDBC connection URL.
     *
     * <p>Bound from the {@code jdbc_url} YAML key.
     *
     * @return the JDBC connection URL
     */
    @WithName("jdbc_url")
    String jdbcUrl();

    /**
     * Returns the database user name used to connect.
     *
     * @return the database user name
     */
    String username();

    /**
     * Returns the database password, if configured.
     *
     * @return an {@link Optional} holding the password, or an empty {@link Optional} when no
     *     password is configured
     */
    Optional<String> password();
}
