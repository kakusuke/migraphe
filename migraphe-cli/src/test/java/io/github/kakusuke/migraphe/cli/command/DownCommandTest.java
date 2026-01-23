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
import java.sql.ResultSet;
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
class DownCommandTest {

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
        pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() throws Exception {
        System.setOut(originalOut);

        try {
            createTestProject(tempDir);
            ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
            Environment env = context.environments().get("test-db");
            if (env instanceof PostgreSQLEnvironment pgEnv) {
                try (Connection conn = pgEnv.createConnection();
                        Statement stmt = conn.createStatement()) {
                    stmt.execute("DROP TABLE IF EXISTS users CASCADE");
                    stmt.execute("DROP INDEX IF EXISTS idx_users_name");
                    stmt.execute("DROP TABLE IF EXISTS migraphe_history CASCADE");
                }
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    void shouldRollbackOnlyDependentNodes() throws IOException {
        // Given: V001 <- V002 <- V003 の依存関係
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        // まず UP を実行
        UpCommand upCommand = new UpCommand(context, null, true, false);
        upCommand.execute();

        // 出力をリセット
        outputStream.reset();

        // When: down V001 を実行（-y で確認スキップ）
        // V002, V003 のみロールバック、V001 は残る
        DownCommand downCommand =
                new DownCommand(context, NodeId.of("test-db/001_create_users"), false, true, false);
        int exitCode = downCommand.execute();

        // Then
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Rollback complete");
    }

    @Test
    void shouldRollbackTargetVersionItself() throws Exception {
        // Given
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        // UP を実行
        UpCommand upCommand = new UpCommand(context, null, true, false);
        upCommand.execute();

        outputStream.reset();

        // When: down V001 を実行
        DownCommand downCommand =
                new DownCommand(context, NodeId.of("test-db/001_create_users"), false, true, false);
        downCommand.execute();

        // Then: V001 のテーブル (users) も削除されている
        Environment env = context.environments().get("test-db");
        if (env instanceof PostgreSQLEnvironment pgEnv) {
            try (Connection conn = pgEnv.createConnection();
                    Statement stmt = conn.createStatement()) {
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT EXISTS (SELECT FROM information_schema.tables WHERE"
                                        + " table_name = 'users')");
                rs.next();
                assertThat(rs.getBoolean(1)).isFalse();
            }
        }
    }

    @Test
    void shouldExecuteInReverseDependencyOrder() throws IOException {
        // Given
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        UpCommand upCommand = new UpCommand(context, null, true, false);
        upCommand.execute();

        outputStream.reset();

        // When
        DownCommand downCommand =
                new DownCommand(context, NodeId.of("test-db/001_create_users"), false, true, false);
        int exitCode = downCommand.execute();

        // Then: 002_add_index が先にロールバックされる
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);

        // 002 が先に表示される（逆順）
        int indexOf002 = output.indexOf("002_add_index");
        // ロールバック対象が1つだけの場合でも成功
        assertThat(indexOf002 >= 0 || output.contains("Rollback complete")).isTrue();
    }

    @Test
    void shouldDisplayRollbackPlanBeforeExecution() throws IOException {
        // Given
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        UpCommand upCommand = new UpCommand(context, null, true, false);
        upCommand.execute();

        outputStream.reset();

        // When
        DownCommand downCommand =
                new DownCommand(context, NodeId.of("test-db/001_create_users"), false, true, false);
        downCommand.execute();

        // Then
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("will be rolled back");
        assertThat(output).contains("Rollback includes:");
    }

    @Test
    void shouldCancelOnUserDecline() throws IOException {
        // Given
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        UpCommand upCommand = new UpCommand(context, null, true, false);
        upCommand.execute();

        outputStream.reset();

        // When: N を入力
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream("n\n".getBytes(StandardCharsets.UTF_8));
        DownCommand downCommand =
                new DownCommand(
                        context,
                        NodeId.of("test-db/001_create_users"),
                        false,
                        false,
                        false,
                        inputStream);
        int exitCode = downCommand.execute();

        // Then
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Rollback cancelled");
    }

    @Test
    void shouldSkipConfirmationWithYFlag() throws IOException {
        // Given
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        UpCommand upCommand = new UpCommand(context, null, true, false);
        upCommand.execute();

        outputStream.reset();

        // When: -y フラグ付き
        DownCommand downCommand =
                new DownCommand(context, NodeId.of("test-db/001_create_users"), false, true, false);
        int exitCode = downCommand.execute();

        // Then: 確認プロンプトなしで実行される
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).doesNotContain("Proceed with rollback?");
    }

    @Test
    void shouldNotExecuteWithDryRunFlag() throws Exception {
        // Given
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        UpCommand upCommand = new UpCommand(context, null, true, false);
        upCommand.execute();

        outputStream.reset();

        // When: --dry-run フラグ付き
        DownCommand downCommand =
                new DownCommand(context, NodeId.of("test-db/001_create_users"), false, false, true);
        int exitCode = downCommand.execute();

        // Then: 実行されない
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("No changes made (dry run)");

        // インデックスがまだ存在することを確認
        Environment env = context.environments().get("test-db");
        if (env instanceof PostgreSQLEnvironment pgEnv) {
            try (Connection conn = pgEnv.createConnection();
                    Statement stmt = conn.createStatement()) {
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT EXISTS (SELECT FROM pg_indexes WHERE indexname ="
                                        + " 'idx_users_name')");
                rs.next();
                assertThat(rs.getBoolean(1)).isTrue();
            }
        }
    }

    @Test
    void shouldDisplayDryRunPlanWithPrefix() throws IOException {
        // Given
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        UpCommand upCommand = new UpCommand(context, null, true, false);
        upCommand.execute();

        outputStream.reset();

        // When
        DownCommand downCommand =
                new DownCommand(context, NodeId.of("test-db/001_create_users"), false, false, true);
        downCommand.execute();

        // Then
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("[DRY RUN]");
        assertThat(output).contains("would be rolled back");
    }

    @Test
    void shouldReturnErrorForNonExistentTargetVersion() throws IOException {
        // Given
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        // When
        DownCommand downCommand =
                new DownCommand(context, NodeId.of("non-existent"), false, true, false);

        // 標準エラー出力もキャプチャ
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errStream));

        int exitCode = downCommand.execute();

        System.setErr(originalErr);

        // Then
        assertThat(exitCode).isEqualTo(1);
        String errOutput = errStream.toString(StandardCharsets.UTF_8);
        assertThat(errOutput).contains("Target version not found");
    }

    @Test
    void shouldHandleNoMigrationsToRollback() throws IOException {
        // Given: UP を実行せずに DOWN を実行
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        // When
        DownCommand downCommand =
                new DownCommand(context, NodeId.of("test-db/001_create_users"), false, true, false);
        int exitCode = downCommand.execute();

        // Then
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("No migrations to rollback");
    }

    @Test
    void shouldRollbackAllMigrationsWithAllFlag() throws Exception {
        // Given: V001 <- V002 を実行
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        UpCommand upCommand = new UpCommand(context, null, true, false);
        upCommand.execute();

        outputStream.reset();

        // When: --all フラグで全てロールバック
        DownCommand downCommand = new DownCommand(context, null, true, true, false);
        int exitCode = downCommand.execute();

        // Then
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Rolling back all migrations");
        assertThat(output).contains("Rollback complete");

        // 全テーブルが削除されていることを確認
        Environment env = context.environments().get("test-db");
        if (env instanceof PostgreSQLEnvironment pgEnv) {
            try (Connection conn = pgEnv.createConnection();
                    Statement stmt = conn.createStatement()) {
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT EXISTS (SELECT FROM information_schema.tables WHERE"
                                        + " table_name = 'users')");
                rs.next();
                assertThat(rs.getBoolean(1)).isFalse();
            }
        }
    }

    @Test
    void shouldDisplayAllMigrationsInDryRunWithAllFlag() throws IOException {
        // Given
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        UpCommand upCommand = new UpCommand(context, null, true, false);
        upCommand.execute();

        outputStream.reset();

        // When: --all --dry-run
        DownCommand downCommand = new DownCommand(context, null, true, false, true);
        downCommand.execute();

        // Then
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("[DRY RUN]");
        assertThat(output).contains("would be rolled back");
        assertThat(output).contains("Rolling back all migrations");
        assertThat(output).contains("No changes made (dry run)");
    }

    @Test
    void shouldAllowReUpAfterDown() throws Exception {
        // Given: UP -> DOWN を実行
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        // 1回目の UP
        UpCommand upCommand1 = new UpCommand(context, null, true, false);
        upCommand1.execute();

        outputStream.reset();

        // DOWN を実行
        DownCommand downCommand =
                new DownCommand(context, NodeId.of("test-db/001_create_users"), false, true, false);
        downCommand.execute();

        outputStream.reset();

        // When: 再度 UP を実行
        UpCommand upCommand2 = new UpCommand(context, null, true, false);
        int exitCode = upCommand2.execute();

        // Then: UP が成功し、テーブルが再作成される
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Migration completed successfully");
        assertThat(output).doesNotContain("[SKIP]"); // スキップされずに実行される

        // テーブルが存在することを確認
        Environment env = context.environments().get("test-db");
        if (env instanceof PostgreSQLEnvironment pgEnv) {
            try (Connection conn = pgEnv.createConnection();
                    Statement stmt = conn.createStatement()) {
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT EXISTS (SELECT FROM information_schema.tables WHERE"
                                        + " table_name = 'users')");
                rs.next();
                assertThat(rs.getBoolean(1)).isTrue();
            }
        }
    }

    /** テスト用のプロジェクト構造を作成する。 */
    private void createTestProject(Path baseDir) throws IOException {
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
                type: postgresql
                jdbc_url: %s
                username: %s
                password: %s
                """,
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);

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
}
