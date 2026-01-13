package io.github.migraphe.cli.factory;

import static org.assertj.core.api.Assertions.*;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.graph.MigrationNode;
import io.github.migraphe.api.graph.NodeId;
import io.github.migraphe.api.spi.TaskDefinition;
import io.github.migraphe.core.plugin.PluginRegistry;
import io.github.migraphe.postgresql.PostgreSQLEnvironment;
import io.github.migraphe.postgresql.PostgreSQLMigrationNode;
import io.github.migraphe.postgresql.SqlTaskDefinition;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.*;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MigrationNodeFactoryTest {

    private PluginRegistry pluginRegistry;
    private Environment environment;

    @BeforeEach
    void setUp() {
        // PostgreSQLプラグインをクラスパスからロード
        pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        // テスト用の Environment を作成
        environment =
                PostgreSQLEnvironment.create(
                        "test-db", "jdbc:postgresql://localhost:5432/test", "user", "pass");
    }

    /** target 情報を含む Config を作成（MigrationNodeFactory 用） */
    private SmallRyeConfig createConfigWithTarget() {
        return new SmallRyeConfigBuilder()
                .withSources(
                        new TestConfigSource(
                                Map.of(
                                        "target.test-db.type", "postgresql",
                                        "target.test-db.jdbc_url",
                                                "jdbc:postgresql://localhost:5432/test",
                                        "target.test-db.username", "user",
                                        "target.test-db.password", "pass")))
                .build();
    }

    /** SqlTaskDefinition を作成（基本形） */
    private SqlTaskDefinition createBasicTaskDefinition() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withMapping(SqlTaskDefinition.class)
                        .withDefaultValue("name", "test-task")
                        .withDefaultValue("target", "test-db")
                        .withDefaultValue("up", "CREATE TABLE users (id SERIAL PRIMARY KEY);")
                        .build();
        return config.getConfigMapping(SqlTaskDefinition.class);
    }

    @Test
    void shouldCreateNodeFromTaskDefinition() {
        // Given: 基本的なTaskDefinition
        SmallRyeConfig targetConfig = createConfigWithTarget();
        MigrationNodeFactory factory = new MigrationNodeFactory(pluginRegistry, targetConfig);

        SqlTaskDefinition taskDef = createBasicTaskDefinition();
        NodeId nodeId = NodeId.of("task-001");

        // When: ノードを生成
        MigrationNode node = factory.createNode(taskDef, nodeId, environment);

        // Then: 正しく生成されている
        assertThat(node).isInstanceOf(PostgreSQLMigrationNode.class);
        assertThat(node.id()).isEqualTo(nodeId);
        assertThat(node.name()).isEqualTo("test-task");
        assertThat(node.environment()).isEqualTo(environment);
        assertThat(node.dependencies()).isEmpty();
    }

    @Test
    void shouldCreateNodeWithDependencies() {
        // Given: 依存関係を持つTaskDefinition
        SmallRyeConfig targetConfig = createConfigWithTarget();
        MigrationNodeFactory factory = new MigrationNodeFactory(pluginRegistry, targetConfig);

        SmallRyeConfig configWithDeps =
                new SmallRyeConfigBuilder()
                        .withMapping(SqlTaskDefinition.class)
                        .withDefaultValue("name", "test-task")
                        .withDefaultValue("target", "test-db")
                        .withDefaultValue("up", "ALTER TABLE users ADD COLUMN name VARCHAR;")
                        .withDefaultValue("dependencies[0]", "task-001")
                        .withDefaultValue("dependencies[1]", "task-002")
                        .build();

        SqlTaskDefinition taskDef = configWithDeps.getConfigMapping(SqlTaskDefinition.class);
        NodeId nodeId = NodeId.of("task-003");

        // When: ノードを生成
        MigrationNode node = factory.createNode(taskDef, nodeId, environment);

        // Then: 依存関係が設定されている
        assertThat(node.dependencies())
                .containsExactlyInAnyOrder(NodeId.of("task-001"), NodeId.of("task-002"));
    }

    @Test
    void shouldCreateNodeWithoutDownTask() {
        // Given: DOWN SQLが無いTaskDefinition
        SmallRyeConfig targetConfig = createConfigWithTarget();
        MigrationNodeFactory factory = new MigrationNodeFactory(pluginRegistry, targetConfig);

        SqlTaskDefinition taskDef = createBasicTaskDefinition();
        NodeId nodeId = NodeId.of("task-001");

        // When: ノードを生成
        MigrationNode node = factory.createNode(taskDef, nodeId, environment);

        // Then: downTask()がnullを返す
        assertThat(node.downTask()).isNull();
    }

    @Test
    void shouldCreateNodeWithDownTask() {
        // Given: DOWN SQLを持つTaskDefinition
        SmallRyeConfig targetConfig = createConfigWithTarget();
        MigrationNodeFactory factory = new MigrationNodeFactory(pluginRegistry, targetConfig);

        SmallRyeConfig configWithDown =
                new SmallRyeConfigBuilder()
                        .withMapping(SqlTaskDefinition.class)
                        .withDefaultValue("name", "test-task")
                        .withDefaultValue("target", "test-db")
                        .withDefaultValue("up", "CREATE TABLE users (id SERIAL PRIMARY KEY);")
                        .withDefaultValue("down", "DROP TABLE users;")
                        .build();

        SqlTaskDefinition taskDef = configWithDown.getConfigMapping(SqlTaskDefinition.class);
        NodeId nodeId = NodeId.of("task-001");

        // When: ノードを生成
        MigrationNode node = factory.createNode(taskDef, nodeId, environment);

        // Then: downTask()が存在する
        assertThat(node.downTask()).isNotNull();
    }

    @Test
    void shouldCreateMultipleNodesFromTaskDefinitions() {
        // Given: 複数のTaskDefinitionとNodeIdのマップ
        SmallRyeConfig targetConfig = createConfigWithTarget();
        MigrationNodeFactory factory = new MigrationNodeFactory(pluginRegistry, targetConfig);

        SmallRyeConfig config1 =
                new SmallRyeConfigBuilder()
                        .withMapping(SqlTaskDefinition.class)
                        .withDefaultValue("name", "task-1")
                        .withDefaultValue("target", "test-db")
                        .withDefaultValue("up", "CREATE TABLE t1 (id INT);")
                        .build();

        SmallRyeConfig config2 =
                new SmallRyeConfigBuilder()
                        .withMapping(SqlTaskDefinition.class)
                        .withDefaultValue("name", "task-2")
                        .withDefaultValue("target", "test-db")
                        .withDefaultValue("up", "CREATE TABLE t2 (id INT);")
                        .withDefaultValue("dependencies[0]", "task-001")
                        .build();

        Map<NodeId, TaskDefinition<?>> taskDefinitions = new LinkedHashMap<>();
        taskDefinitions.put(
                NodeId.of("task-001"), config1.getConfigMapping(SqlTaskDefinition.class));
        taskDefinitions.put(
                NodeId.of("task-002"), config2.getConfigMapping(SqlTaskDefinition.class));

        Map<String, Environment> environments = Map.of("test-db", environment);

        // When: 一括生成
        List<MigrationNode> nodes = factory.createNodes(taskDefinitions, environments);

        // Then: 2つのノードが生成される
        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(0).id()).isEqualTo(NodeId.of("task-001"));
        assertThat(nodes.get(1).id()).isEqualTo(NodeId.of("task-002"));
    }

    /** テスト用のシンプルなConfigSource実装。 */
    private static class TestConfigSource implements ConfigSource {
        private final Map<String, String> properties;

        TestConfigSource(Map<String, String> properties) {
            this.properties = properties;
        }

        @Override
        public Map<String, String> getProperties() {
            return properties;
        }

        @Override
        public String getValue(String propertyName) {
            return properties.get(propertyName);
        }

        @Override
        public String getName() {
            return "TestConfigSource";
        }

        @Override
        public java.util.Set<String> getPropertyNames() {
            return properties.keySet();
        }
    }
}
