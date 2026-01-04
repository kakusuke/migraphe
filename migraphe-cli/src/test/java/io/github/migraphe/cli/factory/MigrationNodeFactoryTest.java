package io.github.migraphe.cli.factory;

import static org.assertj.core.api.Assertions.*;

import io.github.migraphe.api.graph.NodeId;
import io.github.migraphe.core.config.TaskConfig;
import io.github.migraphe.postgresql.PostgreSQLEnvironment;
import io.github.migraphe.postgresql.PostgreSQLMigrationNode;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MigrationNodeFactoryTest {

    private MigrationNodeFactory factory;
    private PostgreSQLEnvironment environment;
    private SmallRyeConfig config;

    @BeforeEach
    void setUp() {
        factory = new MigrationNodeFactory();

        // テスト用の Environment を作成
        environment =
                PostgreSQLEnvironment.create(
                        "test-db", "jdbc:postgresql://localhost:5432/test", "user", "pass");

        // テスト用の Config を作成
        config =
                new SmallRyeConfigBuilder()
                        .withMapping(TaskConfig.class)
                        .withDefaultValue("name", "test-task")
                        .withDefaultValue("target", "test-db")
                        .withDefaultValue("up.sql", "CREATE TABLE users (id SERIAL PRIMARY KEY);")
                        .build();
    }

    @Test
    void shouldCreateNodeFromTaskConfig() {
        // Given: 基本的なTaskConfig
        TaskConfig taskConfig = config.getConfigMapping(TaskConfig.class);
        NodeId nodeId = NodeId.of("task-001");

        // When: ノードを生成
        PostgreSQLMigrationNode node = factory.createNode(taskConfig, nodeId, environment);

        // Then: 正しく生成されている
        assertThat(node.id()).isEqualTo(nodeId);
        assertThat(node.name()).isEqualTo("test-task");
        assertThat(node.environment()).isEqualTo(environment);
        assertThat(node.dependencies()).isEmpty();
    }

    @Test
    void shouldCreateNodeWithDependencies() {
        // Given: 依存関係を持つTaskConfig
        SmallRyeConfig configWithDeps =
                new SmallRyeConfigBuilder()
                        .withMapping(TaskConfig.class)
                        .withDefaultValue("name", "test-task")
                        .withDefaultValue("target", "test-db")
                        .withDefaultValue("up.sql", "ALTER TABLE users ADD COLUMN name VARCHAR;")
                        .withDefaultValue("dependencies[0]", "task-001")
                        .withDefaultValue("dependencies[1]", "task-002")
                        .build();

        TaskConfig taskConfig = configWithDeps.getConfigMapping(TaskConfig.class);
        NodeId nodeId = NodeId.of("task-003");

        // When: ノードを生成
        PostgreSQLMigrationNode node = factory.createNode(taskConfig, nodeId, environment);

        // Then: 依存関係が設定されている
        assertThat(node.dependencies())
                .containsExactlyInAnyOrder(NodeId.of("task-001"), NodeId.of("task-002"));
    }

    @Test
    void shouldCreateNodeWithoutDownTask() {
        // Given: DOWN SQLが無いTaskConfig
        TaskConfig taskConfig = config.getConfigMapping(TaskConfig.class);
        NodeId nodeId = NodeId.of("task-001");

        // When: ノードを生成
        PostgreSQLMigrationNode node = factory.createNode(taskConfig, nodeId, environment);

        // Then: downTask()がemptyを返す
        assertThat(node.downTask()).isEmpty();
    }

    @Test
    void shouldCreateNodeWithDownTask() {
        // Given: DOWN SQLを持つTaskConfig
        SmallRyeConfig configWithDown =
                new SmallRyeConfigBuilder()
                        .withMapping(TaskConfig.class)
                        .withDefaultValue("name", "test-task")
                        .withDefaultValue("target", "test-db")
                        .withDefaultValue("up.sql", "CREATE TABLE users (id SERIAL PRIMARY KEY);")
                        .withDefaultValue("down.sql", "DROP TABLE users;")
                        .build();

        TaskConfig taskConfig = configWithDown.getConfigMapping(TaskConfig.class);
        NodeId nodeId = NodeId.of("task-001");

        // When: ノードを生成
        PostgreSQLMigrationNode node = factory.createNode(taskConfig, nodeId, environment);

        // Then: downTask()が存在する
        assertThat(node.downTask()).isPresent();
    }

    @Test
    void shouldCreateMultipleNodesFromConfigs() {
        // Given: 複数のTaskConfigとNodeIdのマップ
        SmallRyeConfig config1 =
                new SmallRyeConfigBuilder()
                        .withMapping(TaskConfig.class)
                        .withDefaultValue("name", "task-1")
                        .withDefaultValue("target", "test-db")
                        .withDefaultValue("up.sql", "CREATE TABLE t1 (id INT);")
                        .build();

        SmallRyeConfig config2 =
                new SmallRyeConfigBuilder()
                        .withMapping(TaskConfig.class)
                        .withDefaultValue("name", "task-2")
                        .withDefaultValue("target", "test-db")
                        .withDefaultValue("up.sql", "CREATE TABLE t2 (id INT);")
                        .withDefaultValue("dependencies[0]", "task-001")
                        .build();

        Map<NodeId, TaskConfig> taskConfigs = new LinkedHashMap<>();
        taskConfigs.put(NodeId.of("task-001"), config1.getConfigMapping(TaskConfig.class));
        taskConfigs.put(NodeId.of("task-002"), config2.getConfigMapping(TaskConfig.class));

        Map<String, PostgreSQLEnvironment> environments = Map.of("test-db", environment);

        // When: 一括生成
        List<PostgreSQLMigrationNode> nodes = factory.createNodes(taskConfigs, environments);

        // Then: 2つのノードが生成される
        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(0).id()).isEqualTo(NodeId.of("task-001"));
        assertThat(nodes.get(1).id()).isEqualTo(NodeId.of("task-002"));
    }
}
