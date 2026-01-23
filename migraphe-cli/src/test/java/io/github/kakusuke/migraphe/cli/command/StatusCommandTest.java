package io.github.kakusuke.migraphe.cli.command;

import static org.assertj.core.api.Assertions.*;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.cli.ExecutionContext;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import io.github.kakusuke.migraphe.postgresql.PostgreSQLEnvironment;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SuppressWarnings("NullAway.Init")
class StatusCommandTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("migraphe_test")
                    .withUsername("test")
                    .withPassword("test")
                    .waitingFor(new HostPortWaitStrategy().forPorts(5432));

    @TempDir Path tempDir;

    private PluginRegistry pluginRegistry;
    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        // PostgreSQLプラグインをクラスパスからロード
        pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        // 標準出力をキャプチャ
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() throws Exception {
        // 標準出力を復元
        System.setOut(originalOut);

        // データベースをクリーンアップ
        try {
            createTestProject(tempDir);
            ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
            Environment env = context.environments().get("test-db");
            if (env instanceof PostgreSQLEnvironment pgEnv) {
                try (Connection conn = pgEnv.createConnection();
                        Statement stmt = conn.createStatement()) {
                    // Drop all user tables
                    stmt.execute("DROP TABLE IF EXISTS users CASCADE");
                    stmt.execute("DROP INDEX IF EXISTS idx_users_name");
                    // Clear history
                    stmt.execute("DROP TABLE IF EXISTS migraphe_history CASCADE");
                }
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    void shouldShowGraphBasedStatus() throws IOException {
        // Given: テスト用のプロジェクト構造
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        StatusCommand command = new StatusCommand(context);

        // When: status コマンドを実行
        int exitCode = command.execute();

        // Then: 成功
        assertThat(exitCode).isEqualTo(0);

        // 出力に git graph 風の表示が含まれる
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Migration Status");
        assertThat(output).contains("●"); // グラフのノード表示
        assertThat(output).contains("[ ]"); // 未実行マーク
        assertThat(output).contains("│"); // 縦線（依存関係の線）
    }

    @Test
    void shouldShowExecutionDetailsForExecutedNodes() throws IOException {
        // Given: マイグレーションを実行済み
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        // 先に UP を実行
        UpCommand upCommand = new UpCommand(context, null, true, false);
        upCommand.execute();

        // 出力をリセット
        outputStream.reset();

        // When: status コマンドを実行
        StatusCommand statusCommand = new StatusCommand(context);
        int exitCode = statusCommand.execute();

        // Then: 成功
        assertThat(exitCode).isEqualTo(0);

        // 出力に実行日時と所要時間が同じ行に含まれる
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("[✓]");
        // 実行情報が同じ行にインラインで表示される（例: (58ms, 2026-01-18 22:01:22)）
        assertThat(output)
                .containsPattern(
                        "\\(\\d+ms, \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\)"
                                + "|\\(\\d+\\.\\d+s, \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\)");
    }

    @Test
    void shouldShowDependenciesWithLine() throws IOException {
        // Given: テスト用のプロジェクト構造（未実行）
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        StatusCommand command = new StatusCommand(context);

        // When: status コマンドを実行
        int exitCode = command.execute();

        // Then: 成功
        assertThat(exitCode).isEqualTo(0);

        // 出力にグラフ線で依存関係が表示される
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("[ ]"); // 未実行マーク
        assertThat(output).contains("│"); // 依存関係の線
        assertThat(output).contains("●"); // ノードマーク
    }

    @Test
    void shouldShowSummaryLine() throws IOException {
        // Given: テスト用のプロジェクト構造
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        StatusCommand command = new StatusCommand(context);

        // When: status コマンドを実行
        command.execute();

        // Then: サマリー行が含まれる
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Summary:");
        assertThat(output).contains("Total: 2");
        assertThat(output).contains("Executed:");
        assertThat(output).contains("Pending:");
    }

    @Test
    void shouldShowCorrectCountsAfterPartialExecution() throws IOException {
        // Given: 一部のマイグレーションのみ実行
        createTestProjectWithThreeNodes(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        // 最初のマイグレーションのみ実行するため、UpCommand を使用
        UpCommand upCommand = new UpCommand(context, null, true, false);
        upCommand.execute();

        // 出力をリセット
        outputStream.reset();

        // When: status コマンドを実行
        StatusCommand statusCommand = new StatusCommand(context);
        int exitCode = statusCommand.execute();

        // Then: 成功
        assertThat(exitCode).isEqualTo(0);

        // 正しいカウントが表示される
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Summary:");
        assertThat(output).contains("Total: 3");
        assertThat(output).contains("Executed: 3");
        assertThat(output).contains("Pending: 0");
    }

    /** テスト用のプロジェクト構造を作成する。 */
    private void createTestProject(Path baseDir) throws IOException {
        // migraphe.yaml
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                """;
        Files.writeString(baseDir.resolve("migraphe.yaml"), projectYaml);

        // targets/test-db.yaml
        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);

        // Testcontainersの実際の接続情報を使用
        String targetYaml =
                String.format(
                        """
                type: postgresql
                jdbc_url: %s
                username: %s
                password: %s
                """,
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);

        // tasks/test-db/001_create_users.yaml
        Path tasksDir = baseDir.resolve("tasks").resolve("test-db");
        Files.createDirectories(tasksDir);

        String task1Yaml =
                """
                name: Create users table
                target: test-db
                up: CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100));
                down: DROP TABLE users;
                """;
        Files.writeString(tasksDir.resolve("001_create_users.yaml"), task1Yaml);

        // tasks/test-db/002_add_index.yaml
        String task2Yaml =
                """
                name: Add index on users
                target: test-db
                dependencies:
                  - test-db/001_create_users
                up: CREATE INDEX idx_users_name ON users(name);
                down: DROP INDEX idx_users_name;
                """;
        Files.writeString(tasksDir.resolve("002_add_index.yaml"), task2Yaml);
    }

    /** テスト用のプロジェクト構造（3ノード）を作成する。 */
    private void createTestProjectWithThreeNodes(Path baseDir) throws IOException {
        // migraphe.yaml
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                """;
        Files.writeString(baseDir.resolve("migraphe.yaml"), projectYaml);

        // targets/test-db.yaml
        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);

        String targetYaml =
                String.format(
                        """
                type: postgresql
                jdbc_url: %s
                username: %s
                password: %s
                """,
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);

        // tasks/test-db/001_create_users.yaml
        Path tasksDir = baseDir.resolve("tasks").resolve("test-db");
        Files.createDirectories(tasksDir);

        String task1Yaml =
                """
                name: Create users table
                target: test-db
                up: CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100));
                down: DROP TABLE users;
                """;
        Files.writeString(tasksDir.resolve("001_create_users.yaml"), task1Yaml);

        // tasks/test-db/002_add_index.yaml
        String task2Yaml =
                """
                name: Add index on users
                target: test-db
                dependencies:
                  - test-db/001_create_users
                up: CREATE INDEX idx_users_name ON users(name);
                down: DROP INDEX idx_users_name;
                """;
        Files.writeString(tasksDir.resolve("002_add_index.yaml"), task2Yaml);

        // tasks/test-db/003_add_email.yaml
        String task3Yaml =
                """
                name: Add email column
                target: test-db
                dependencies:
                  - test-db/002_add_index
                up: ALTER TABLE users ADD COLUMN email VARCHAR(255);
                down: ALTER TABLE users DROP COLUMN email;
                """;
        Files.writeString(tasksDir.resolve("003_add_email.yaml"), task3Yaml);
    }
}
