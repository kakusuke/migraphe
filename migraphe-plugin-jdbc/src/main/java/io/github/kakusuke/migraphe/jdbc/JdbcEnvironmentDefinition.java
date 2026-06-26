package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import java.util.Optional;

/**
 * Configuration mapping for a generic JDBC target ({@code type: jdbc}).
 *
 * <p>This is the SmallRye {@link ConfigMapping} bound to a target YAML file (for example {@code
 * targets/mydb.yaml}). {@link JdbcEnvironmentProvider} reads these values to build a {@link
 * JdbcEnvironment}. Because the JDBC plugin is database-agnostic, the driver class and a human
 * readable database label are supplied explicitly in the YAML; dialect-specific plugins
 * (PostgreSQL, MySQL) fix those values and omit them from their definitions.
 *
 * <p>Example {@code targets/mydb.yaml}:
 *
 * <pre>{@code
 * type: jdbc
 * driver_class: org.mariadb.jdbc.Driver
 * db_label: MariaDB
 * jdbc_url: jdbc:mariadb://localhost:3306/myapp
 * username: user
 * password: secret
 * }</pre>
 */
@ConfigMapping(prefix = "")
public interface JdbcEnvironmentDefinition extends EnvironmentDefinition {

    /**
     * Returns the target type discriminator, {@code "jdbc"} for this definition.
     *
     * @return the target type as declared in YAML
     */
    @Override
    String type();

    /**
     * Returns the JDBC connection URL (YAML key {@code jdbc_url}).
     *
     * @return the JDBC URL passed to {@link java.sql.DriverManager}
     */
    @WithName("jdbc_url")
    String jdbcUrl();

    /**
     * Returns the database user name used to authenticate the connection.
     *
     * @return the database user name
     */
    String username();

    /**
     * Returns the database password, if configured.
     *
     * @return an {@link Optional} containing the password, or empty when no password is set
     */
    Optional<String> password();

    /**
     * Returns the fully qualified JDBC driver class name (YAML key {@code driver_class}).
     *
     * <p>The class is loaded via {@link Class#forName(String)} before connecting, which is required
     * when the plugin is loaded through an isolated class loader where drivers are not
     * auto-registered.
     *
     * @return the JDBC driver class name
     */
    @WithName("driver_class")
    String driverClass();

    /**
     * Returns the human readable database label used in log/description messages (YAML key {@code
     * db_label}).
     *
     * @return an {@link Optional} containing the label, or empty to fall back to a default
     */
    @WithName("db_label")
    Optional<String> dbLabel();
}
