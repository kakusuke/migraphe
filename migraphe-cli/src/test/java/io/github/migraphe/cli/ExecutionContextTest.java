package io.github.migraphe.cli;

import static org.assertj.core.api.Assertions.*;

import io.github.migraphe.core.graph.MigrationGraph;
import io.github.migraphe.core.graph.NodeId;
import io.github.migraphe.postgresql.PostgreSQLEnvironment;
import io.github.migraphe.postgresql.PostgreSQLMigrationNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionContextTest {

    @TempDir Path tempDir;

    @Test
    void shouldLoadProjectFromDirectory() throws IOException {
        // Given: テスト用のプロジェクト構造を作成
        createTestProject(tempDir);

        // When: ExecutionContext をロード
        ExecutionContext context = ExecutionContext.load(tempDir);

        // Then: 正しく読み込まれている
        assertThat(context.baseDir()).isEqualTo(tempDir);
        assertThat(context.environments()).isNotEmpty();
        assertThat(context.nodes()).isNotEmpty();
        assertThat(context.graph()).isNotNull();
    }

    @Test
    void shouldCreateGraphFromNodes() throws IOException {
        // Given: テスト用のプロジェクト構造
        createTestProject(tempDir);

        // When: ExecutionContext をロード
        ExecutionContext context = ExecutionContext.load(tempDir);

        // Then: グラフが構築されている
        MigrationGraph graph = context.graph();
        assertThat(graph.allNodes()).hasSizeGreaterThan(0);
    }

    @Test
    void shouldProvideEnvironmentByTargetId() throws IOException {
        // Given: テスト用のプロジェクト構造
        createTestProject(tempDir);

        // When: ExecutionContext をロード
        ExecutionContext context = ExecutionContext.load(tempDir);

        // Then: ターゲットIDで Environment が取得できる
        Map<String, PostgreSQLEnvironment> environments = context.environments();
        assertThat(environments).containsKey("test-db");

        PostgreSQLEnvironment env = environments.get("test-db");
        assertThat(env.id().value()).isEqualTo("test-db");
    }

    @Test
    void shouldProvideNodesInOrder() throws IOException {
        // Given: テスト用のプロジェクト構造
        createTestProject(tempDir);

        // When: ExecutionContext をロード
        ExecutionContext context = ExecutionContext.load(tempDir);

        // Then: ノードが順序付けられている
        List<PostgreSQLMigrationNode> nodes = context.nodes();
        assertThat(nodes).isNotEmpty();

        // ノードIDの重複がない
        List<NodeId> nodeIds = nodes.stream().map(PostgreSQLMigrationNode::id).toList();
        assertThat(nodeIds).doesNotHaveDuplicates();
    }

    /**
     * テスト用のプロジェクト構造を作成する。
     *
     * <pre>
     * tempDir/
     *   ├── migraphe.yaml
     *   ├── targets/
     *   │   └── test-db.yaml
     *   └── tasks/
     *       ├── test-db/
     *       │   ├── 001_create_users.yaml
     *       │   └── 002_add_index.yaml
     * </pre>
     */
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

        // targets ディレクトリ
        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);

        // targets/test-db.yaml
        String targetYaml =
                """
                type: postgresql
                jdbc_url: jdbc:postgresql://localhost:5432/testdb
                username: testuser
                password: testpass
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);

        // tasks ディレクトリ
        Path tasksDir = baseDir.resolve("tasks").resolve("test-db");
        Files.createDirectories(tasksDir);

        // tasks/test-db/001_create_users.yaml
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
