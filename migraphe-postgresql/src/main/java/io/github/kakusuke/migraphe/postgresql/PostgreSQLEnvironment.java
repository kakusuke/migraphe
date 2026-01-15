package io.github.kakusuke.migraphe.postgresql;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/** PostgreSQL 環境の実装。 JDBC 接続情報を保持し、データベース接続を提供する。 */
public final class PostgreSQLEnvironment implements Environment {

    private final EnvironmentId id;
    private final String name;
    private final String jdbcUrl;
    private final String username;
    private final String password;

    private PostgreSQLEnvironment(
            EnvironmentId id, String name, String jdbcUrl, String username, String password) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.password = Objects.requireNonNull(password, "password must not be null");
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
        return new PostgreSQLEnvironment(id, name, jdbcUrl, username, password);
    }

    @Override
    public EnvironmentId id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    /** JDBC 接続 URL を取得する。 */
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /** JDBC ユーザー名を取得する。 */
    public String getUsername() {
        return username;
    }

    /** JDBC パスワードを取得する。 */
    public String getPassword() {
        return password;
    }

    /**
     * データベース接続を作成する。
     *
     * @return データベース接続
     * @throws SQLException 接続の作成に失敗した場合
     */
    public Connection createConnection() throws SQLException {
        ensureDriverLoaded();
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /**
     * PostgreSQL JDBC ドライバがロードされていることを確認する。
     *
     * <p>URLClassLoader でプラグインを読み込んだ場合、ドライバは DriverManager に自動登録されないため、 明示的にクラスをロードする必要がある。
     */
    private static void ensureDriverLoaded() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC driver not found", e);
        }
    }
}
