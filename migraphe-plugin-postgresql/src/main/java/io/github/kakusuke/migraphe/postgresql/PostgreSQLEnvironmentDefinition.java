package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import java.util.Optional;

/**
 * PostgreSQL-specific {@link EnvironmentDefinition} subtype.
 *
 * <p>This is a SmallRye {@link ConfigMapping} interface mapped directly from a target YAML file
 * ({@code targets/*.yaml}); the target name is derived from the file name. {@link
 * PostgreSQLEnvironmentProvider} consumes an instance of this definition to build a {@link
 * PostgreSQLEnvironment}.
 *
 * <p>Example YAML ({@code targets/db1.yaml}):
 *
 * <pre>{@code
 * type: postgresql
 * jdbc_url: jdbc:postgresql://localhost:5432/mydb
 * username: dbuser
 * password: secret
 * }</pre>
 */
@ConfigMapping(prefix = "")
public interface PostgreSQLEnvironmentDefinition extends EnvironmentDefinition {

    /**
     * Returns the target type discriminator, which is {@code "postgresql"} for this definition.
     *
     * @return the configured target {@code type} value
     */
    @Override
    String type();

    /**
     * Returns the JDBC connection URL (mapped from the YAML {@code jdbc_url} key).
     *
     * @return the PostgreSQL JDBC URL, e.g. {@code jdbc:postgresql://host:5432/db}
     */
    @WithName("jdbc_url")
    String jdbcUrl();

    /**
     * Returns the database username used to connect.
     *
     * @return the connection username
     */
    String username();

    /**
     * Returns the database password, if configured.
     *
     * @return an {@link Optional} containing the password, or empty when no password is set
     */
    Optional<String> password();
}
