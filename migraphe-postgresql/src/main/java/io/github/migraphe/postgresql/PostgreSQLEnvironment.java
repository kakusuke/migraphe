package io.github.migraphe.postgresql;

import io.github.migraphe.core.environment.Environment;
import io.github.migraphe.core.environment.EnvironmentConfig;
import io.github.migraphe.core.environment.EnvironmentId;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

/** PostgreSQL 環境の実装。 JDBC 接続情報を保持し、データベース接続を提供する。 */
public final class PostgreSQLEnvironment implements Environment {

    public static final String JDBC_URL = "jdbc.url";
    public static final String JDBC_USERNAME = "jdbc.username";
    public static final String JDBC_PASSWORD = "jdbc.password";

    private final EnvironmentId id;
    private final String name;
    private final EnvironmentConfig config;

    private PostgreSQLEnvironment(EnvironmentId id, String name, EnvironmentConfig config) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");

        // JDBC 接続情報の検証
        validateJdbcConfig(config);
    }

    /**
     * JDBC 接続情報から PostgreSQL 環境を作成する。
     *
     * @param name 環境名（環境IDとしても使用される）
     * @param jdbcUrl JDBC 接続 URL
     * @param username データベースユーザー名
     * @param password データベースパスワード
     * @return PostgreSQL 環境
     */
    public static PostgreSQLEnvironment create(
            String name, String jdbcUrl, String username, String password) {
        EnvironmentId id = EnvironmentId.of(name);
        Map<String, String> properties =
                Map.of(
                        JDBC_URL, jdbcUrl,
                        JDBC_USERNAME, username,
                        JDBC_PASSWORD, password);
        EnvironmentConfig config = EnvironmentConfig.of(properties);
        return new PostgreSQLEnvironment(id, name, config);
    }

    /**
     * 環境設定から PostgreSQL 環境を作成する。
     *
     * @param id 環境ID
     * @param name 環境名
     * @param config 環境設定（JDBC 接続情報を含む）
     * @return PostgreSQL 環境
     */
    public static PostgreSQLEnvironment create(
            EnvironmentId id, String name, EnvironmentConfig config) {
        return new PostgreSQLEnvironment(id, name, config);
    }

    @Override
    public EnvironmentId id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public EnvironmentConfig config() {
        return config;
    }

    /** JDBC 接続 URL を取得する。 */
    public String getJdbcUrl() {
        return config.getProperty(JDBC_URL)
                .orElseThrow(() -> new PostgreSQLException("JDBC URL is required but not found"));
    }

    /** JDBC ユーザー名を取得する。 */
    public String getUsername() {
        return config.getProperty(JDBC_USERNAME)
                .orElseThrow(
                        () -> new PostgreSQLException("JDBC username is required but not found"));
    }

    /** JDBC パスワードを取得する。 */
    public String getPassword() {
        return config.getProperty(JDBC_PASSWORD)
                .orElseThrow(
                        () -> new PostgreSQLException("JDBC password is required but not found"));
    }

    /**
     * データベース接続を作成する。
     *
     * @return データベース接続
     * @throws SQLException 接続の作成に失敗した場合
     */
    public Connection createConnection() throws SQLException {
        return DriverManager.getConnection(getJdbcUrl(), getUsername(), getPassword());
    }

    private void validateJdbcConfig(EnvironmentConfig config) {
        // 検証は getter で行われるため、ここでは呼び出すだけ
        if (config.getProperty(JDBC_URL).isEmpty()) {
            throw new PostgreSQLException("JDBC URL is required");
        }
        if (config.getProperty(JDBC_USERNAME).isEmpty()) {
            throw new PostgreSQLException("JDBC username is required");
        }
        if (config.getProperty(JDBC_PASSWORD).isEmpty()) {
            throw new PostgreSQLException("JDBC password is required");
        }
    }
}
