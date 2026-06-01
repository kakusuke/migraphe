package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.jdbc.statement.StatementSplitter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** 汎用 JDBC 環境の実装。接続管理を担当する。 */
public class JdbcEnvironment implements Environment {

    private final EnvironmentId id;
    private final String name;
    private final String jdbcUrl;
    private final String username;
    private final @Nullable String password;
    private final String driverClassName;
    private final String dbLabel;

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
     * JDBC 接続情報から環境を作成する。
     *
     * @param name 環境名（環境IDとしても使用される）
     * @param jdbcUrl JDBC 接続 URL
     * @param username データベースユーザー名
     * @param password データベースパスワード（nullの場合はパスワードなし）
     * @param driverClassName JDBCドライバクラス名
     * @param dbLabel データベースラベル（説明用）
     * @return JDBC 環境
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

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public @Nullable String getPassword() {
        return password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public String getDbLabel() {
        return dbLabel;
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
     * SQL テキストをステートメント単位に分割する {@link StatementSplitter} を返す。
     *
     * <p>標準実装は標準的な引用・コメント領域を認識する {@link StatementSplitter#standard()} を返す。 方言固有の文法（例: PostgreSQL
     * のドル引用符）を扱うサブクラスはこれをオーバーライドする。
     *
     * @return ステートメント分割器
     */
    public StatementSplitter statementSplitter() {
        return StatementSplitter.standard();
    }

    /**
     * JDBC ドライバがロードされていることを確認する。
     *
     * <p>URLClassLoader でプラグインを読み込んだ場合、ドライバは DriverManager に自動登録されないため、 明示的にクラスをロードする必要がある。
     */
    private void ensureDriverLoaded() throws SQLException {
        try {
            Class.forName(driverClassName);
        } catch (ClassNotFoundException e) {
            throw new SQLException(dbLabel + " JDBC driver not found: " + driverClassName, e);
        }
    }
}
