package io.github.kakusuke.migraphe.cli.command;

import static org.assertj.core.api.Assertions.*;

import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import io.github.kakusuke.migraphe.mysql.MySQLEnvironment;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * MySQL に対する CLI e2e テスト。 YAML 設定 → {@link ExecutionContext#load} → {@link UpCommand#execute()} →
 * 実DB(Testcontainers MySQL) の副作用確認までを通す。
 */
@Testcontainers
@SuppressWarnings("NullAway.Init")
class UpCommandMySQLTest {

    @Container
    static MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0").withDatabaseName("migraphe_test");

    @TempDir Path tempDir;

    private PluginRegistry pluginRegistry;
    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        // MySQL プラグインをクラスパスからロード
        pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        // 標準出力をキャプチャ
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        // 標準出力を復元
        System.setOut(originalOut);

        // データベースをクリーンアップ
        try (Connection conn = newConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP PROCEDURE IF EXISTS seed_proc");
            stmt.execute("DROP PROCEDURE IF EXISTS delim_proc");
            stmt.execute("DROP TABLE IF EXISTS multi_a");
            stmt.execute("DROP TABLE IF EXISTS multi_b");
            stmt.execute("DROP TABLE IF EXISTS proc_target");
            stmt.execute("DROP TABLE IF EXISTS delim_target");
            stmt.execute("DROP TABLE IF EXISTS migraphe_history");
        } catch (Exception e) {
            // クリーンアップエラーは無視
        }
    }

    @Test
    void shouldExecuteMultipleCreateTableInTransactionMode() throws Exception {
        // Given: 1タスクに複数の CREATE TABLE; CREATE TABLE;（autocommit 指定なし=トランザクションモード）。
        // 旧実装では全文を 1 ステートメントとして送り失敗していた本命ケース。
        createSqlSplittingProject(
                tempDir,
                """
                name: Create two tables
                target: test-db
                up: |
                  CREATE TABLE multi_a (id INT PRIMARY KEY);
                  CREATE TABLE multi_b (id INT PRIMARY KEY);
                down: |
                  DROP TABLE IF EXISTS multi_b;
                  DROP TABLE IF EXISTS multi_a;
                """);

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        UpCommand command =
                new UpCommand(
                        context, null, true, false, new ByteArrayInputStream(new byte[0]), false);

        // When: 実行
        int exitCode = command.execute();

        // Then: 成功し、両テーブルが存在する
        assertThat(exitCode).isEqualTo(0);
        assertThat(tableExists("multi_a")).isTrue();
        assertThat(tableExists("multi_b")).isTrue();
    }

    @Test
    void shouldExecuteProcedureWithoutDelimiterInTransactionMode() throws Exception {
        // Given: DELIMITER 無し直書きの CREATE PROCEDURE。先行で対象テーブルを作成。
        createSqlSplittingProject(
                tempDir,
                """
                name: Create procedure inline
                target: test-db
                up: |
                  CREATE TABLE proc_target (id INT);
                  CREATE PROCEDURE seed_proc()
                  BEGIN
                    INSERT INTO proc_target VALUES (1);
                    INSERT INTO proc_target VALUES (2);
                  END
                down: |
                  DROP PROCEDURE IF EXISTS seed_proc;
                  DROP TABLE IF EXISTS proc_target;
                """);

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        UpCommand command =
                new UpCommand(
                        context, null, true, false, new ByteArrayInputStream(new byte[0]), false);

        // When: 実行
        int exitCode = command.execute();

        // Then: 成功し、プロシージャが存在する
        assertThat(exitCode).isEqualTo(0);
        assertThat(procedureExists("seed_proc")).isTrue();
    }

    @Test
    void shouldExecuteDelimiterScriptInTransactionMode() throws Exception {
        // Given: DELIMITER $$ ... END$$ DELIMITER ; 形式のスクリプト。
        createSqlSplittingProject(
                tempDir,
                """
                name: Create procedure with DELIMITER
                target: test-db
                up: |
                  CREATE TABLE delim_target (id INT);
                  DELIMITER $$
                  CREATE PROCEDURE delim_proc()
                  BEGIN
                    INSERT INTO delim_target VALUES (1);
                    INSERT INTO delim_target VALUES (2);
                  END$$
                  DELIMITER ;
                down: |
                  DROP PROCEDURE IF EXISTS delim_proc;
                  DROP TABLE IF EXISTS delim_target;
                """);

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        UpCommand command =
                new UpCommand(
                        context, null, true, false, new ByteArrayInputStream(new byte[0]), false);

        // When: 実行
        int exitCode = command.execute();

        // Then: 成功し、プロシージャが存在する
        assertThat(exitCode).isEqualTo(0);
        assertThat(procedureExists("delim_proc")).isTrue();
    }

    /** SQL分割検証用に、単一タスクだけを含むプロジェクト構造を作成する。 */
    private void createSqlSplittingProject(Path baseDir, String taskYaml) throws IOException {
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                """;
        Files.writeString(baseDir.resolve("migraphe.yaml"), projectYaml);

        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);
        String targetYaml =
                String.format(
                        """
                        type: mysql
                        jdbc_url: %s
                        username: %s
                        password: %s
                        """,
                        mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);

        Path tasksDir = baseDir.resolve("tasks").resolve("test-db");
        Files.createDirectories(tasksDir);
        Files.writeString(tasksDir.resolve("001_split.yaml"), taskYaml);
    }

    /** Testcontainers の接続情報で新規 JDBC 接続を開く。 */
    private Connection newConnection() throws Exception {
        MySQLEnvironment env =
                MySQLEnvironment.create(
                        "cleanup", mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        return env.createConnection();
    }

    private boolean tableExists(String table) throws Exception {
        try (Connection conn = newConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT table_name FROM information_schema.tables "
                                        + "WHERE table_name = '"
                                        + table
                                        + "' AND table_schema = 'migraphe_test'")) {
            return rs.next();
        }
    }

    private boolean procedureExists(String name) throws Exception {
        try (Connection conn = newConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT routine_name FROM information_schema.routines "
                                        + "WHERE routine_name = '"
                                        + name
                                        + "' AND routine_schema = 'migraphe_test'")) {
            return rs.next();
        }
    }
}
