package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.jdbc.statement.StatementSplitter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Generic JDBC implementation of {@link Environment} responsible for connection management.
 *
 * <p>An instance holds everything needed to open a {@link Connection} to a single database: the
 * JDBC URL, credentials, and the driver class name. It is the central object the rest of the JDBC
 * plugin depends on — {@link JdbcMigrationNode}, {@link JdbcUpTask}, {@link JdbcDownTask}, and
 * {@link JdbcHistoryRepository} all obtain connections through {@link #createConnection()}.
 *
 * <p>This class is the reference implementation for database-specific plugins. PostgreSQL and MySQL
 * plugins subclass it, fixing the driver class name and database label and overriding {@link
 * #statementSplitter()} to supply a dialect-aware SQL splitter. The constructor is {@code
 * protected} so subclasses can supply those fixed values, while end users create plain JDBC
 * environments through {@link #create}.
 */
public class JdbcEnvironment implements Environment {

    private final EnvironmentId id;
    private final String name;
    private final String jdbcUrl;
    private final String username;
    private final @Nullable String password;
    private final String driverClassName;
    private final String dbLabel;

    /**
     * Creates a JDBC environment with all connection attributes supplied explicitly.
     *
     * <p>Intended for subclasses (dialect-specific plugins) that pin the driver class name and
     * label. General callers should use {@link #create}.
     *
     * @param id the unique environment identifier
     * @param name the human readable environment name
     * @param jdbcUrl the JDBC connection URL
     * @param username the database user name
     * @param password the database password, or {@code null} for no password
     * @param driverClassName the fully qualified JDBC driver class name to load before connecting
     * @param dbLabel a human readable database label used in description/log messages
     */
    protected JdbcEnvironment(
            EnvironmentId id,
            String name,
            String jdbcUrl,
            String username,
            @Nullable String password,
            String driverClassName,
            String dbLabel) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.password = password;
        this.driverClassName =
                Objects.requireNonNull(driverClassName, "driverClassName must not be null");
        this.dbLabel = Objects.requireNonNull(dbLabel, "dbLabel must not be null");
    }

    /**
     * Creates a JDBC environment from connection attributes.
     *
     * <p>The environment identifier is derived from {@code name} via {@link
     * EnvironmentId#of(String)}.
     *
     * @param name the environment name (also used to derive the environment identifier)
     * @param jdbcUrl the JDBC connection URL
     * @param username the database user name
     * @param password the database password, or {@code null} for no password
     * @param driverClassName the fully qualified JDBC driver class name to load before connecting
     * @param dbLabel a human readable database label used in description/log messages
     * @return a new JDBC environment
     */
    public static JdbcEnvironment create(
            String name,
            String jdbcUrl,
            String username,
            @Nullable String password,
            String driverClassName,
            String dbLabel) {
        EnvironmentId id = EnvironmentId.of(name);
        return new JdbcEnvironment(id, name, jdbcUrl, username, password, driverClassName, dbLabel);
    }

    @Override
    public EnvironmentId id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Returns the JDBC connection URL.
     *
     * @return the JDBC URL
     */
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Returns the database user name.
     *
     * @return the database user name
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the database password.
     *
     * @return the password, or {@code null} when no password is configured
     */
    public @Nullable String getPassword() {
        return password;
    }

    /**
     * Returns the fully qualified JDBC driver class name.
     *
     * @return the driver class name loaded before connecting
     */
    public String getDriverClassName() {
        return driverClassName;
    }

    /**
     * Returns the human readable database label used in description/log messages.
     *
     * @return the database label
     */
    public String getDbLabel() {
        return dbLabel;
    }

    /**
     * Opens a new database connection.
     *
     * <p>The driver class is loaded (via {@link Class#forName(String)}) before the connection is
     * requested, then the connection is obtained from {@link DriverManager} using the configured
     * URL and credentials. Callers own the returned connection and are responsible for closing it.
     *
     * @return a freshly opened {@link Connection}
     * @throws SQLException if the driver cannot be found or the connection cannot be established
     */
    public Connection createConnection() throws SQLException {
        ensureDriverLoaded();
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /**
     * Returns the {@link StatementSplitter} used to break SQL text into individual statements.
     *
     * <p>The default implementation returns {@link StatementSplitter#standard()}, which recognizes
     * ordinary string/identifier quoting and comment regions and splits on {@code ;}. Subclasses
     * that handle dialect-specific syntax (for example PostgreSQL dollar-quoting or MySQL {@code
     * BEGIN}/{@code END} blocks and {@code DELIMITER}) override this method to supply a
     * grammar-aware splitter.
     *
     * @return the statement splitter for this environment's SQL dialect
     */
    public StatementSplitter statementSplitter() {
        return StatementSplitter.standard();
    }

    /**
     * Ensures the JDBC driver class is loaded and registered with {@link DriverManager}.
     *
     * <p>When the plugin is loaded through an isolated class loader (for example a {@code
     * URLClassLoader}), drivers are not auto-registered, so the class must be loaded explicitly
     * before requesting a connection.
     *
     * @throws SQLException if the driver class cannot be found
     */
    private void ensureDriverLoaded() throws SQLException {
        try {
            Class.forName(driverClassName);
        } catch (ClassNotFoundException e) {
            throw new SQLException(dbLabel + " JDBC driver not found: " + driverClassName, e);
        }
    }
}
