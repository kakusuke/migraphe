package io.github.kakusuke.migraphe.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.jdbc.JdbcHistoryRepository;
import io.github.kakusuke.migraphe.jdbc.JdbcMigrationNode;
import io.github.kakusuke.migraphe.jdbc.SqlTaskDefinition;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
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
                                MySQLEnvironment.create(
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
    void shouldReturnEnvironmentDefinitionClass() {
        // given
        MySQLPlugin plugin = new MySQLPlugin();

        // when
        Class<? extends EnvironmentDefinition> envDefClass = plugin.environmentDefinitionClass();

        // then
        assertThat(envDefClass).isEqualTo(MySQLEnvironmentDefinition.class);
    }

    @Test
    void shouldProvideEnvironmentProvider() {
        // given
        MySQLPlugin plugin = new MySQLPlugin();

        // when
        var provider = plugin.environmentProvider();

        // then
        assertThat(provider).isNotNull();
        assertThat(provider).isInstanceOf(MySQLEnvironmentProvider.class);
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
    void environmentProviderShouldCreateEnvironment() {
        // given
        var provider = new MySQLEnvironmentProvider();
        var definition =
                createEnvironmentDefinition(
                        "mysql", "jdbc:mysql://localhost:3306/test", "testuser", "testpass");

        // when
        Environment env = provider.createEnvironment("test-db", definition);

        // then
        assertThat(env).isNotNull();
        assertThat(env).isInstanceOf(MySQLEnvironment.class);
        assertThat(env.name()).isEqualTo("test-db");
    }

    @Test
    void environmentProviderShouldThrowWhenGivenWrongDefinitionType() {
        // given
        var provider = new MySQLEnvironmentProvider();
        var wrongDefinition =
                new EnvironmentDefinition() {
                    @Override
                    public String type() {
                        return "other";
                    }
                };

        // when & then
        assertThatThrownBy(() -> provider.createEnvironment("test", wrongDefinition))
                .isInstanceOf(MySQLException.class)
                .hasMessageContaining("Expected MySQLEnvironmentDefinition");
    }

    @Test
    void migrationNodeProviderShouldCreateNode() {
        // given
        var provider = new MySQLMigrationNodeProvider();
        var env =
                MySQLEnvironment.create("test", "jdbc:mysql://localhost:3306/test", "user", "pass");
        var nodeId = NodeId.of("V001");

        SqlTaskDefinition task =
                createTaskDefinition(
                        "Create users table",
                        "Initial schema",
                        "test",
                        "CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY);",
                        "DROP TABLE users;");

        // when
        var node = provider.createNode(nodeId, task, Set.of(), env);

        // then
        assertThat(node).isNotNull();
        assertThat(node).isInstanceOf(JdbcMigrationNode.class);
        assertThat(node.id()).isEqualTo(nodeId);
        assertThat(node.name()).isEqualTo("Create users table");
        assertThat(node.description()).isEqualTo("Initial schema");
    }

    @Test
    void migrationNodeProviderShouldThrowForNonMySQLEnvironment() {
        // given
        var provider = new MySQLMigrationNodeProvider();
        var nonMySqlEnv =
                new Environment() {
                    @Override
                    public EnvironmentId id() {
                        return EnvironmentId.of("test");
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
                .hasMessageContaining("Environment must be MySQLEnvironment");
    }

    @Test
    void historyRepositoryProviderShouldCreateRepository() {
        // given
        var provider = new MySQLHistoryRepositoryProvider();
        var env =
                MySQLEnvironment.create("test", "jdbc:mysql://localhost:3306/test", "user", "pass");

        // when
        HistoryRepository repo = provider.createRepository(env);

        // then
        assertThat(repo).isNotNull();
        assertThat(repo).isInstanceOf(JdbcHistoryRepository.class);
    }

    @Test
    void historyRepositoryProviderShouldThrowForNonMySQLEnvironment() {
        // given
        var provider = new MySQLHistoryRepositoryProvider();
        var nonMySqlEnv =
                new Environment() {
                    @Override
                    public EnvironmentId id() {
                        return EnvironmentId.of("test");
                    }

                    @Override
                    public String name() {
                        return "test";
                    }
                };

        // when & then
        assertThatThrownBy(() -> provider.createRepository(nonMySqlEnv))
                .isInstanceOf(MySQLException.class)
                .hasMessageContaining("Environment must be MySQLEnvironment");
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

    /** テスト用の MySQLEnvironmentDefinition を作成する。 */
    private MySQLEnvironmentDefinition createEnvironmentDefinition(
            String type, String jdbcUrl, String username, String password) {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withMapping(MySQLEnvironmentDefinition.class)
                        .withDefaultValue("type", type)
                        .withDefaultValue("jdbc_url", jdbcUrl)
                        .withDefaultValue("username", username)
                        .withDefaultValue("password", password)
                        .build();
        return config.getConfigMapping(MySQLEnvironmentDefinition.class);
    }
}
