package io.github.kakusuke.migraphe.mysql;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.jdbc.JdbcEnvironment;
import io.github.kakusuke.migraphe.jdbc.statement.StatementSplitter;
import io.github.kakusuke.migraphe.mysql.statement.MySqlGrammar;
import org.jspecify.annotations.Nullable;

/** MySQL 環境の実装。JdbcEnvironment を継承し、ドライバとラベルを固定する。 */
public final class MySQLEnvironment extends JdbcEnvironment {

    private MySQLEnvironment(
            EnvironmentId id,
            String name,
            String jdbcUrl,
            String username,
            @Nullable String password) {
        super(id, name, jdbcUrl, username, password, "com.mysql.cj.jdbc.Driver", "MySQL");
    }

    public static MySQLEnvironment create(
            String name, String jdbcUrl, String username, @Nullable String password) {
        EnvironmentId id = EnvironmentId.of(name);
        return new MySQLEnvironment(id, name, jdbcUrl, username, password);
    }

    /** MySQL 方言（バッククォート識別子・{@code #} コメント・複合文ブロック・{@code DELIMITER}）に対応した分割器を返す。 */
    @Override
    public StatementSplitter statementSplitter() {
        return MySqlGrammar.splitter();
    }
}
