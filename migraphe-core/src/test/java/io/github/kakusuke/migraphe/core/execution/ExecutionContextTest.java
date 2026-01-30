package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.*;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import io.github.kakusuke.migraphe.postgresql.PostgreSQLEnvironment;
import io.github.kakusuke.migraphe.postgresql.PostgreSQLHistoryRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("NullAway.Init")
class ExecutionContextTest {

    @TempDir Path tempDir;

    private PluginRegistry pluginRegistry;

    @BeforeEach
    void setUp() {
        // PostgreSQLプラグインをクラスパスからロード
        pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();
    }

    @Test
    void shouldLoadProjectFromDirectory() throws IOException {
        // Given: テスト用のプロジェクト構造を作成
        createTestProject(tempDir);

        // When: ExecutionContext をロード
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

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
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        // Then: グラフが構築されている
        MigrationGraph graph = context.graph();
        assertThat(graph.allNodes()).hasSizeGreaterThan(0);
    }

    @Test
    void shouldProvideEnvironmentByTargetId() throws IOException {
        // Given: テスト用のプロジェクト構造
        createTestProject(tempDir);

        // When: ExecutionContext をロード
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        // Then: ターゲットIDで Environment が取得できる
        Map<String, Environment> environments = context.environments();
        assertThat(environments).containsKey("test-db");

        Environment env = Objects.requireNonNull(environments.get("test-db"));
        assertThat(env).isInstanceOf(PostgreSQLEnvironment.class);
        assertThat(env.id().value()).isEqualTo("test-db");
    }

    @Test
    void shouldProvideNodesInOrder() throws IOException {
        // Given: テスト用のプロジェクト構造
        createTestProject(tempDir);

        // When: ExecutionContext をロード
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        // Then: ノードが順序付けられている
        List<MigrationNode> nodes = context.nodes();
        assertThat(nodes).isNotEmpty();

        // ノードIDの重複がない
        List<NodeId> nodeIds = nodes.stream().map(MigrationNode::id).toList();
        assertThat(nodeIds).doesNotHaveDuplicates();
    }

    @Test
    void shouldCreateHistoryRepositoryFromPlugin() throws IOException {
        // Given: PostgreSQL プラグインが設定されたプロジェクト
        createTestProject(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        // When: createHistoryRepository() を呼び出す
        HistoryRepository historyRepo = context.createHistoryRepository();

        // Then: PostgreSQLHistoryRepository が返される（history.target = test-db で type = postgresql）
        assertThat(historyRepo).isInstanceOf(PostgreSQLHistoryRepository.class);
    }

    @Test
    void shouldFallbackToInMemoryHistoryRepository() throws IOException {
        // Given: history.target が存在しない環境を指すプロジェクト
        createTestProjectWithMissingHistoryTarget(tempDir);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        // When: createHistoryRepository() を呼び出す
        HistoryRepository historyRepo = context.createHistoryRepository();

        // Then: フォールバックとして InMemoryHistoryRepository が返される
        assertThat(historyRepo).isInstanceOf(InMemoryHistoryRepository.class);
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

    /**
     * history.target が存在しない環境を指すプロジェクト構造を作成する。
     *
     * <p>history.target = "nonexistent-db" で、environments に存在しないため InMemory にフォールバックする。
     */
    private void createTestProjectWithMissingHistoryTarget(Path baseDir) throws IOException {
        // migraphe.yaml — history.target が存在しない環境を指す
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: nonexistent-db
                """;
        Files.writeString(baseDir.resolve("migraphe.yaml"), projectYaml);

        // targets
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

        // tasks
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
    }
}
