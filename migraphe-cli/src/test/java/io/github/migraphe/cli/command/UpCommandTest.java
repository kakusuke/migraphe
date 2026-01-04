package io.github.migraphe.cli.command;

import static org.assertj.core.api.Assertions.*;

import io.github.migraphe.cli.ExecutionContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Disabled("Requires real PostgreSQL database. TODO: Add integration tests with Testcontainers")
class UpCommandTest {

    @TempDir Path tempDir;

    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        // 標準出力をキャプチャ
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        // 標準出力を復元
        System.setOut(originalOut);
    }

    @Test
    void shouldExecuteUpMigrations() throws IOException {
        // Given: テスト用のプロジェクト構造
        createTestProject(tempDir);

        ExecutionContext context = ExecutionContext.load(tempDir);
        UpCommand command = new UpCommand(context);

        // When: UP コマンドを実行
        int exitCode = command.execute();

        // Then: 成功
        assertThat(exitCode).isEqualTo(0);

        // 出力にマイグレーション実行結果が含まれる
        String output = outputStream.toString();
        assertThat(output).contains("Executing migrations");
    }

    @Test
    void shouldSkipAlreadyExecutedMigrations() throws IOException {
        // Given: プロジェクト構造 + 既に実行済みのマイグレーション
        createTestProject(tempDir);

        ExecutionContext context = ExecutionContext.load(tempDir);

        // 最初の実行
        UpCommand command1 = new UpCommand(context);
        command1.execute();

        // When: 2回目の実行
        outputStream.reset();
        UpCommand command2 = new UpCommand(context);
        int exitCode = command2.execute();

        // Then: スキップされる
        assertThat(exitCode).isEqualTo(0);
        String output = outputStream.toString();
        assertThat(output)
                .satisfiesAnyOf(
                        s -> assertThat(s).contains("already executed"),
                        s -> assertThat(s).contains("No migrations to execute"));
    }

    @Test
    void shouldExecuteInDependencyOrder() throws IOException {
        // Given: 依存関係のあるマイグレーション
        createTestProject(tempDir);

        ExecutionContext context = ExecutionContext.load(tempDir);
        UpCommand command = new UpCommand(context);

        // When: 実行
        int exitCode = command.execute();

        // Then: 成功（依存関係順に実行される）
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void shouldDisplayExecutionPlan() throws IOException {
        // Given: プロジェクト構造
        createTestProject(tempDir);

        ExecutionContext context = ExecutionContext.load(tempDir);
        UpCommand command = new UpCommand(context);

        // When: 実行
        command.execute();

        // Then: 実行計画が表示される
        String output = outputStream.toString();
        assertThat(output)
                .satisfiesAnyOf(
                        s -> assertThat(s).contains("Level"), s -> assertThat(s).contains("Task"));
    }

    /** テスト用のプロジェクト構造を作成する（ExecutionContextTestと同じ）。 */
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

        String targetYaml =
                """
                type: postgresql
                jdbc_url: jdbc:postgresql://localhost:5432/testdb
                username: testuser
                password: testpass
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);

        // tasks/test-db/001_create_users.yaml
        Path tasksDir = baseDir.resolve("tasks").resolve("test-db");
        Files.createDirectories(tasksDir);

        String task1Yaml =
                """
                name: Create users table
                target: test-db
                up:
                  sql: CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100));
                down:
                  sql: DROP TABLE users;
                """;
        Files.writeString(tasksDir.resolve("001_create_users.yaml"), task1Yaml);

        // tasks/test-db/002_add_index.yaml
        String task2Yaml =
                """
                name: Add index on users
                target: test-db
                dependencies:
                  - test-db/001_create_users
                up:
                  sql: CREATE INDEX idx_users_name ON users(name);
                down:
                  sql: DROP INDEX idx_users_name;
                """;
        Files.writeString(tasksDir.resolve("002_add_index.yaml"), task2Yaml);
    }
}
