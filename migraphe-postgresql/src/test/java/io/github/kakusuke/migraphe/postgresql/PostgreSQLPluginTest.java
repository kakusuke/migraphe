package io.github.kakusuke.migraphe.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.EnvironmentDefinition;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PostgreSQLPluginTest {

    @Test
    @SuppressWarnings("rawtypes")
    void shouldBeDiscoverableViaServiceLoader() {
        // when
        ServiceLoader<MigraphePlugin> loader = ServiceLoader.load(MigraphePlugin.class);
        List<MigraphePlugin> plugins = loader.stream().map(ServiceLoader.Provider::get).toList();

        // then
        assertThat(plugins).isNotEmpty();
        assertThat(plugins).anyMatch(p -> "postgresql".equals(p.type()));
    }

    @Test
    void shouldReturnCorrectType() {
        // given
        PostgreSQLPlugin plugin = new PostgreSQLPlugin();

        // when
        String type = plugin.type();

        // then
        assertThat(type).isEqualTo("postgresql");
    }

    @Test
    void shouldReturnTaskDefinitionClass() {
        // given
        PostgreSQLPlugin plugin = new PostgreSQLPlugin();

        // when
        Class<? extends TaskDefinition<String>> taskDefClass = plugin.taskDefinitionClass();

        // then
        assertThat(taskDefClass).isEqualTo(SqlTaskDefinition.class);
    }

    @Test
    void shouldReturnEnvironmentDefinitionClass() {
        // given
        PostgreSQLPlugin plugin = new PostgreSQLPlugin();

        // when
        Class<? extends EnvironmentDefinition> envDefClass = plugin.environmentDefinitionClass();

        // then
        assertThat(envDefClass).isEqualTo(PostgreSQLEnvironmentDefinition.class);
    }

    @Test
    void shouldProvideEnvironmentProvider() {
        // given
        PostgreSQLPlugin plugin = new PostgreSQLPlugin();

        // when
        var provider = plugin.environmentProvider();

        // then
        assertThat(provider).isNotNull();
        assertThat(provider).isInstanceOf(PostgreSQLEnvironmentProvider.class);
    }

    @Test
    void shouldProvideMigrationNodeProvider() {
        // given
        PostgreSQLPlugin plugin = new PostgreSQLPlugin();

        // when
        var provider = plugin.migrationNodeProvider();

        // then
        assertThat(provider).isNotNull();
        assertThat(provider).isInstanceOf(PostgreSQLMigrationNodeProvider.class);
    }

    @Test
    void shouldProvideHistoryRepositoryProvider() {
        // given
        PostgreSQLPlugin plugin = new PostgreSQLPlugin();

        // when
        var provider = plugin.historyRepositoryProvider();

        // then
        assertThat(provider).isNotNull();
        assertThat(provider).isInstanceOf(PostgreSQLHistoryRepositoryProvider.class);
    }

    @Test
    void environmentProviderShouldCreateEnvironment() {
        // given
        var provider = new PostgreSQLEnvironmentProvider();
        var definition =
                createEnvironmentDefinition(
                        "postgresql",
                        "jdbc:postgresql://localhost:5432/test",
                        "testuser",
                        "testpass");

        // when
        Environment env = provider.createEnvironment("test-db", definition);

        // then
        assertThat(env).isNotNull();
        assertThat(env).isInstanceOf(PostgreSQLEnvironment.class);
        assertThat(env.name()).isEqualTo("test-db");
    }

    @Test
    void environmentProviderShouldThrowWhenGivenWrongDefinitionType() {
        // given
        var provider = new PostgreSQLEnvironmentProvider();
        var wrongDefinition =
                new EnvironmentDefinition() {
                    @Override
                    public String type() {
                        return "other";
                    }
                };

        // when & then
        assertThatThrownBy(() -> provider.createEnvironment("test", wrongDefinition))
                .isInstanceOf(PostgreSQLException.class)
                .hasMessageContaining("Expected PostgreSQLEnvironmentDefinition");
    }

    @Test
    void migrationNodeProviderShouldCreateNode() {
        // given
        var provider = new PostgreSQLMigrationNodeProvider();
        var env =
                PostgreSQLEnvironment.create(
                        "test", "jdbc:postgresql://localhost:5432/test", "user", "pass");
        var nodeId = NodeId.of("V001");

        SqlTaskDefinition task =
                createTaskDefinition(
                        "Create users table",
                        "Initial schema",
                        "test",
                        "CREATE TABLE users (id SERIAL);",
                        "DROP TABLE users;");

        // when
        MigrationNode node = provider.createNode(nodeId, task, Set.of(), env);

        // then
        assertThat(node).isNotNull();
        assertThat(node).isInstanceOf(PostgreSQLMigrationNode.class);
        assertThat(node.id()).isEqualTo(nodeId);
        assertThat(node.name()).isEqualTo("Create users table");
        assertThat(node.description()).isEqualTo("Initial schema");
    }

    @Test
    void migrationNodeProviderShouldThrowForNonPostgreSQLEnvironment() {
        // given
        var provider = new PostgreSQLMigrationNodeProvider();
        var nonPgEnv =
                new Environment() {
                    @Override
                    public io.github.kakusuke.migraphe.api.environment.EnvironmentId id() {
                        return io.github.kakusuke.migraphe.api.environment.EnvironmentId.of("test");
                    }

                    @Override
                    public String name() {
                        return "test";
                    }
                };
        var nodeId = NodeId.of("V001");
        SqlTaskDefinition task = createTaskDefinition("test", null, "test", "SELECT 1;", null);

        // when & then
        assertThatThrownBy(() -> provider.createNode(nodeId, task, Set.of(), nonPgEnv))
                .isInstanceOf(PostgreSQLException.class)
                .hasMessageContaining("Environment must be PostgreSQLEnvironment");
    }

    @Test
    void historyRepositoryProviderShouldCreateRepository() {
        // given
        var provider = new PostgreSQLHistoryRepositoryProvider();
        var env =
                PostgreSQLEnvironment.create(
                        "test", "jdbc:postgresql://localhost:5432/test", "user", "pass");

        // when
        HistoryRepository repo = provider.createRepository(env);

        // then
        assertThat(repo).isNotNull();
        assertThat(repo).isInstanceOf(PostgreSQLHistoryRepository.class);
    }

    @Test
    void historyRepositoryProviderShouldThrowForNonPostgreSQLEnvironment() {
        // given
        var provider = new PostgreSQLHistoryRepositoryProvider();
        var nonPgEnv =
                new Environment() {
                    @Override
                    public io.github.kakusuke.migraphe.api.environment.EnvironmentId id() {
                        return io.github.kakusuke.migraphe.api.environment.EnvironmentId.of("test");
                    }

                    @Override
                    public String name() {
                        return "test";
                    }
                };

        // when & then
        assertThatThrownBy(() -> provider.createRepository(nonPgEnv))
                .isInstanceOf(PostgreSQLException.class)
                .hasMessageContaining("Environment must be PostgreSQLEnvironment");
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

    /** テスト用の PostgreSQLEnvironmentDefinition を作成する。 */
    private PostgreSQLEnvironmentDefinition createEnvironmentDefinition(
            String type, String jdbcUrl, String username, String password) {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withMapping(PostgreSQLEnvironmentDefinition.class)
                        .withDefaultValue("type", type)
                        .withDefaultValue("jdbc_url", jdbcUrl)
                        .withDefaultValue("username", username)
                        .withDefaultValue("password", password)
                        .build();
        return config.getConfigMapping(PostgreSQLEnvironmentDefinition.class);
    }
}
