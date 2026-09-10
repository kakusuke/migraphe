package io.github.kakusuke.migraphe.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.TargetDefinition;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.jdbc.JdbcHistoryRepository;
import io.github.kakusuke.migraphe.jdbc.JdbcMigrationNode;
import io.github.kakusuke.migraphe.jdbc.SqlTaskDefinition;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MySQLPluginTest {

    @Test
    void migrationNodeProviderCarriesNoWayBackFromTheDefinition() {
        SqlTaskDefinition definition =
                new SmallRyeConfigBuilder()
                        .withMapping(SqlTaskDefinition.class)
                        .withDefaultValue("name", "drop_legacy")
                        .withDefaultValue("target", "db1")
                        .withDefaultValue("up", "ALTER TABLE users DROP COLUMN legacy")
                        .withDefaultValue("no_way_back", "DROP COLUMN discards the data")
                        .build()
                        .getConfigMapping(SqlTaskDefinition.class);

        MigrationNode node =
                new MySQLPlugin()
                        .migrationNodeProvider()
                        .createNode(
                                NodeId.of("drop_legacy"),
                                definition,
                                Set.of(),
                                MySQLTarget.create(
                                        "db1", "jdbc:mysql://localhost:3306/t", "u", "p"));

        assertThat(node.noWayBack()).isEqualTo("DROP COLUMN discards the data");
        assertThat(node.downTask()).isNull();
    }

    @Test
    @SuppressWarnings("rawtypes")
    void shouldBeDiscoverableViaServiceLoader() {
        // when
        ServiceLoader<MigraphePlugin> loader = ServiceLoader.load(MigraphePlugin.class);
        var plugins = loader.stream().map(ServiceLoader.Provider::get).toList();

        // then
        assertThat(plugins).isNotEmpty();
        assertThat(plugins).anyMatch(p -> "mysql".equals(p.type()));
    }

    @Test
    void shouldReturnCorrectType() {
        // given
        MySQLPlugin plugin = new MySQLPlugin();

        // when
        String type = plugin.type();

        // then
        assertThat(type).isEqualTo("mysql");
    }

    @Test
    void shouldReturnTaskDefinitionClass() {
        // given
        MySQLPlugin plugin = new MySQLPlugin();

        // when
        Class<? extends TaskDefinition<String>> taskDefClass = plugin.taskDefinitionClass();

        // then
        assertThat(taskDefClass).isEqualTo(SqlTaskDefinition.class);
    }

    @Test
    void shouldReturnTargetDefinitionClass() {
        // given
        MySQLPlugin plugin = new MySQLPlugin();

        // when
        Class<? extends TargetDefinition> targetDefClass = plugin.targetDefinitionClass();

        // then
        assertThat(targetDefClass).isEqualTo(MySQLTargetDefinition.class);
    }

    @Test
    void shouldProvideTargetProvider() {
        // given
        MySQLPlugin plugin = new MySQLPlugin();

        // when
        var provider = plugin.targetProvider();

        // then
        assertThat(provider).isNotNull();
        assertThat(provider).isInstanceOf(MySQLTargetProvider.class);
    }

    @Test
    void shouldProvideMigrationNodeProvider() {
        // given
        MySQLPlugin plugin = new MySQLPlugin();

        // when
        var provider = plugin.migrationNodeProvider();

        // then
        assertThat(provider).isNotNull();
        assertThat(provider).isInstanceOf(MySQLMigrationNodeProvider.class);
    }

    @Test
    void shouldProvideHistoryRepositoryProvider() {
        // given
        MySQLPlugin plugin = new MySQLPlugin();

        // when
        var provider = plugin.historyRepositoryProvider();

        // then
        assertThat(provider).isNotNull();
        assertThat(provider).isInstanceOf(MySQLHistoryRepositoryProvider.class);
    }

    @Test
    void targetProviderShouldCreateTarget() {
        // given
        var provider = new MySQLTargetProvider();
        var definition =
                createTargetDefinition(
                        "mysql", "jdbc:mysql://localhost:3306/test", "testuser", "testpass");

        // when
        Target target = provider.createTarget("test-db", definition);

        // then
        assertThat(target).isNotNull();
        assertThat(target).isInstanceOf(MySQLTarget.class);
        assertThat(target.name()).isEqualTo("test-db");
    }

    @Test
    void targetProviderShouldThrowWhenGivenWrongDefinitionType() {
        // given
        var provider = new MySQLTargetProvider();
        var wrongDefinition =
                new TargetDefinition() {
                    @Override
                    public String type() {
                        return "other";
                    }
                };

        // when & then
        assertThatThrownBy(() -> provider.createTarget("test", wrongDefinition))
                .isInstanceOf(MySQLException.class)
                .hasMessageContaining("Expected MySQLTargetDefinition");
    }

    @Test
    void migrationNodeProviderShouldCreateNode() {
        // given
        var provider = new MySQLMigrationNodeProvider();
        var target = MySQLTarget.create("test", "jdbc:mysql://localhost:3306/test", "user", "pass");
        var nodeId = NodeId.of("V001");

        SqlTaskDefinition task =
                createTaskDefinition(
                        "Create users table",
                        "Initial schema",
                        "test",
                        "CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY);",
                        "DROP TABLE users;");

        // when
        var node = provider.createNode(nodeId, task, Set.of(), target);

        // then
        assertThat(node).isNotNull();
        assertThat(node).isInstanceOf(JdbcMigrationNode.class);
        assertThat(node.id()).isEqualTo(nodeId);
        assertThat(node.name()).isEqualTo("Create users table");
        assertThat(node.description()).isEqualTo("Initial schema");
    }

    @Test
    void migrationNodeProviderShouldThrowForNonMySQLTarget() {
        // given
        var provider = new MySQLMigrationNodeProvider();
        var nonMySqlEnv =
                new Target() {
                    @Override
                    public TargetId id() {
                        return TargetId.of("test");
                    }

                    @Override
                    public String name() {
                        return "test";
                    }
                };
        var nodeId = NodeId.of("V001");
        SqlTaskDefinition task = createTaskDefinition("test", null, "test", "SELECT 1;", null);

        // when & then
        assertThatThrownBy(() -> provider.createNode(nodeId, task, Set.of(), nonMySqlEnv))
                .isInstanceOf(MySQLException.class)
                .hasMessageContaining("Target must be MySQLTarget");
    }

    @Test
    void historyRepositoryProviderShouldCreateRepository() {
        // given
        var provider = new MySQLHistoryRepositoryProvider();
        var target = MySQLTarget.create("test", "jdbc:mysql://localhost:3306/test", "user", "pass");

        // when
        HistoryRepository repo = provider.createRepository(target);

        // then
        assertThat(repo).isNotNull();
        assertThat(repo).isInstanceOf(JdbcHistoryRepository.class);
    }

    @Test
    void historyRepositoryProviderShouldThrowForNonMySQLTarget() {
        // given
        var provider = new MySQLHistoryRepositoryProvider();
        var nonMySqlEnv =
                new Target() {
                    @Override
                    public TargetId id() {
                        return TargetId.of("test");
                    }

                    @Override
                    public String name() {
                        return "test";
                    }
                };

        // when & then
        assertThatThrownBy(() -> provider.createRepository(nonMySqlEnv))
                .isInstanceOf(MySQLException.class)
                .hasMessageContaining("Target must be MySQLTarget");
    }

    @Test
    void autocommitCanDifferBetweenUpAndDown() {
        assertThat(taskDescriptions("autocommit.up", "true", "autocommit.down", "false"))
                .containsExactly("MySQL UP migration (autocommit)", "MySQL DOWN migration");
        assertThat(taskDescriptions("autocommit.down", "true"))
                .containsExactly("MySQL UP migration", "MySQL DOWN migration (autocommit)");
        assertThat(taskDescriptions("autocommit", "true"))
                .containsExactly(
                        "MySQL UP migration (autocommit)", "MySQL DOWN migration (autocommit)");
        assertThat(taskDescriptions("autocommit", "true", "autocommit.up", "false"))
                .containsExactly("MySQL UP migration", "MySQL DOWN migration (autocommit)");
        assertThat(taskDescriptions())
                .containsExactly("MySQL UP migration", "MySQL DOWN migration");
    }

    private List<String> taskDescriptions(String... autocommitEntries) {
        SmallRyeConfigBuilder builder =
                new SmallRyeConfigBuilder()
                        .withMapping(SqlTaskDefinition.class)
                        .withDefaultValue("name", "create_users")
                        .withDefaultValue("target", "test")
                        .withDefaultValue("up", "CREATE TABLE users (id INT)")
                        .withDefaultValue("down", "DROP TABLE users");
        for (int i = 0; i < autocommitEntries.length; i += 2) {
            builder.withDefaultValue(autocommitEntries[i], autocommitEntries[i + 1]);
        }

        MigrationNode node =
                new MySQLPlugin()
                        .migrationNodeProvider()
                        .createNode(
                                NodeId.of("create_users"),
                                builder.build().getConfigMapping(SqlTaskDefinition.class),
                                Set.of(),
                                MySQLTarget.create(
                                        "test",
                                        "jdbc:mysql://localhost:3306/test",
                                        "user",
                                        "pass"));

        Task downTask = node.downTask();
        return List.of(
                node.upTask().description(),
                Objects.requireNonNull(downTask, "the fixture supplies down SQL").description());
    }

    /** テスト用の SqlTaskDefinition を作成する。 */
    private SqlTaskDefinition createTaskDefinition(
            String name, String description, String target, String up, String down) {
        SmallRyeConfigBuilder builder =
                new SmallRyeConfigBuilder()
                        .withMapping(SqlTaskDefinition.class)
                        .withDefaultValue("name", name)
                        .withDefaultValue("target", target)
                        .withDefaultValue("up", up);

        if (description != null) {
            builder.withDefaultValue("description", description);
        }
        if (down != null) {
            builder.withDefaultValue("down", down);
        }

        SmallRyeConfig config = builder.build();
        return config.getConfigMapping(SqlTaskDefinition.class);
    }

    /** テスト用の MySQLTargetDefinition を作成する。 */
    private MySQLTargetDefinition createTargetDefinition(
            String type, String jdbcUrl, String username, String password) {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withMapping(MySQLTargetDefinition.class)
                        .withDefaultValue("type", type)
                        .withDefaultValue("jdbc_url", jdbcUrl)
                        .withDefaultValue("username", username)
                        .withDefaultValue("password", password)
                        .build();
        return config.getConfigMapping(MySQLTargetDefinition.class);
    }
}
