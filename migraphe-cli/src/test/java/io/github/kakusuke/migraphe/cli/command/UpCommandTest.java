package io.github.kakusuke.migraphe.cli.command;

import static org.assertj.core.api.Assertions.*;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.cli.ExecutionContext;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import io.github.kakusuke.migraphe.postgresql.PostgreSQLEnvironment;
import java.io.ByteArrayInputStream;
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
class UpCommandTest {

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
    void shouldExecuteUpMigrations() throws IOException {
        // Given: テスト用のプロジェクト構造
        createTestProject(tempDir);

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        // skipConfirmation=true で自動実行
        UpCommand command =
                new UpCommand(
                        context, null, true, false, new ByteArrayInputStream(new byte[0]), false);

        // When: UP コマンドを実行
        int exitCode = command.execute();

        // Then: 成功
        assertThat(exitCode).isEqualTo(0);

        // 出力にマイグレーション実行結果が含まれる
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Executing migrations");
    }

    @Test
    void shouldSkipAlreadyExecutedMigrations() throws IOException {
        // Given: プロジェクト構造 + 既に実行済みのマイグレーション
        createTestProject(tempDir);

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        // 最初の実行
        UpCommand command1 =
                new UpCommand(
                        context, null, true, false, new ByteArrayInputStream(new byte[0]), false);
        command1.execute();

        // When: 2回目の実行
        outputStream.reset();
        UpCommand command2 =
                new UpCommand(
                        context, null, true, false, new ByteArrayInputStream(new byte[0]), false);
        int exitCode = command2.execute();

        // Then: スキップされる
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("No migrations to execute");
    }

    @Test
    void shouldExecuteInDependencyOrder() throws IOException {
        // Given: 依存関係のあるマイグレーション
        createTestProject(tempDir);

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        UpCommand command =
                new UpCommand(
                        context, null, true, false, new ByteArrayInputStream(new byte[0]), false);

        // When: 実行
        int exitCode = command.execute();

        // Then: 成功（依存関係順に実行される）
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void shouldDisplayMigrationGraphBeforeExecution() throws IOException {
        // Given: プロジェクト構造
        createTestProject(tempDir);

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        UpCommand command =
                new UpCommand(
                        context, null, true, false, new ByteArrayInputStream(new byte[0]), false);

        // When: 実行
        command.execute();

        // Then: マイグレーショングラフが表示される
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Migrations to execute:");
        assertThat(output).contains("●"); // グラフのノード記号
    }

    @Test
    void shouldDisplayDryRunWithoutExecuting() throws IOException {
        // Given: プロジェクト構造
        createTestProject(tempDir);

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        UpCommand command =
                new UpCommand(
                        context,
                        null,
                        true,
                        true, // dryRun
                        new ByteArrayInputStream(new byte[0]),
                        false);

        // When: dry-run実行
        int exitCode = command.execute();

        // Then: 成功 + dry-runメッセージ
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("[DRY RUN]");
        assertThat(output).contains("No changes made (dry run)");
        assertThat(output).doesNotContain("Executing migrations");
    }

    @Test
    void shouldExecuteOnlyTargetAndDependencies() throws IOException {
        // Given: 3つのマイグレーション V001 <- V002 <- V003
        createTestProjectWithThreeMigrations(tempDir);

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        // ターゲットを V002 に指定（V001 と V002 のみ実行される）
        UpCommand command =
                new UpCommand(
                        context,
                        NodeId.of("test-db/002_add_index"),
                        true,
                        false,
                        new ByteArrayInputStream(new byte[0]),
                        false);

        // When: 実行
        int exitCode = command.execute();

        // Then: 成功
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        // V001 と V002 が実行される
        assertThat(output).contains("Create users table");
        assertThat(output).contains("Add index on users");
        // V003 は実行されない（ターゲットの依存グラフに含まれない）
        assertThat(output).doesNotContain("Create posts table");
    }

    @Test
    void shouldCancelWhenUserDeclines() throws IOException {
        // Given: プロジェクト構造 + "n"を入力
        createTestProject(tempDir);

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        ByteArrayInputStream input =
                new ByteArrayInputStream("n\n".getBytes(StandardCharsets.UTF_8));
        UpCommand command =
                new UpCommand(
                        context, null, false, // 確認プロンプトを表示
                        false, input, false);

        // When: 実行
        int exitCode = command.execute();

        // Then: キャンセルされる
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Migration cancelled");
    }

    @Test
    void shouldProceedWhenUserConfirms() throws IOException {
        // Given: プロジェクト構造 + "y"を入力
        createTestProject(tempDir);

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        ByteArrayInputStream input =
                new ByteArrayInputStream("y\n".getBytes(StandardCharsets.UTF_8));
        UpCommand command =
                new UpCommand(
                        context, null, false, // 確認プロンプトを表示
                        false, input, false);

        // When: 実行
        int exitCode = command.execute();

        // Then: 成功
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Proceed? [y/N]:");
        assertThat(output).contains("Migration completed successfully");
    }

    @Test
    void shouldReturnErrorForNonExistentTarget() throws IOException {
        // Given: プロジェクト構造
        createTestProject(tempDir);

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        UpCommand command =
                new UpCommand(
                        context,
                        NodeId.of("non-existent-target"),
                        true,
                        false,
                        new ByteArrayInputStream(new byte[0]),
                        false);

        // When: 実行
        // 標準エラー出力もキャプチャ
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errStream));

        int exitCode = command.execute();

        System.setErr(originalErr);

        // Then: エラー
        assertThat(exitCode).isEqualTo(1);
        String errOutput = errStream.toString(StandardCharsets.UTF_8);
        assertThat(errOutput).contains("Target not found");
    }

    @Test
    void shouldDisplayColoredOutput() throws IOException {
        // Given: プロジェクト構造
        createTestProject(tempDir);

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        UpCommand command =
                new UpCommand(
                        context,
                        null,
                        true,
                        false,
                        new ByteArrayInputStream(new byte[0]),
                        true); // colorEnabled

        // When: 実行
        int exitCode = command.execute();

        // Then: 成功 + ANSIカラーコードが含まれる
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        // 緑色のOKステータス
        assertThat(output).contains("\u001B[32m");
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

    /** 3つのマイグレーションを持つプロジェクト構造を作成する。 */
    private void createTestProjectWithThreeMigrations(Path baseDir) throws IOException {
        createTestProject(baseDir);

        // tasks/test-db/003_create_posts.yaml (V002に依存しない独立したマイグレーション)
        Path tasksDir = baseDir.resolve("tasks").resolve("test-db");
        String task3Yaml =
                """
                name: Create posts table
                target: test-db
                dependencies:
                  - test-db/002_add_index
                up: CREATE TABLE posts (id SERIAL PRIMARY KEY, title VARCHAR(200));
                down: DROP TABLE posts;
                """;
        Files.writeString(tasksDir.resolve("003_create_posts.yaml"), task3Yaml);
    }
}
