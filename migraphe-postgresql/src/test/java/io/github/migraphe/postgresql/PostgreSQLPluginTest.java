package io.github.migraphe.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.environment.EnvironmentConfig;
import io.github.migraphe.api.graph.MigrationNode;
import io.github.migraphe.api.graph.NodeId;
import io.github.migraphe.api.history.HistoryRepository;
import io.github.migraphe.api.spi.MigraphePlugin;
import io.github.migraphe.api.spi.SqlDefinition;
import io.github.migraphe.api.spi.TaskDefinition;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PostgreSQLPluginTest {

    @Test
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
        var config =
                EnvironmentConfig.of(
                        Map.of(
                                "jdbc_url", "jdbc:postgresql://localhost:5432/test",
                                "username", "testuser",
                                "password", "testpass"));

        // when
        Environment env = provider.createEnvironment("test-db", config);

        // then
        assertThat(env).isNotNull();
        assertThat(env).isInstanceOf(PostgreSQLEnvironment.class);
        assertThat(env.name()).isEqualTo("test-db");
    }

    @Test
    void environmentProviderShouldThrowWhenMissingJdbcUrl() {
        // given
        var provider = new PostgreSQLEnvironmentProvider();
        var config = EnvironmentConfig.of(Map.of("username", "user", "password", "pass"));

        // when & then
        assertThatThrownBy(() -> provider.createEnvironment("test", config))
                .isInstanceOf(PostgreSQLException.class)
                .hasMessageContaining("Missing required config: jdbc_url");
    }

    @Test
    void environmentProviderShouldThrowWhenMissingUsername() {
        // given
        var provider = new PostgreSQLEnvironmentProvider();
        var config =
                EnvironmentConfig.of(
                        Map.of("jdbc_url", "jdbc:postgresql://localhost/db", "password", "pass"));

        // when & then
        assertThatThrownBy(() -> provider.createEnvironment("test", config))
                .isInstanceOf(PostgreSQLException.class)
                .hasMessageContaining("Missing required config: username");
    }

    @Test
    void environmentProviderShouldThrowWhenMissingPassword() {
        // given
        var provider = new PostgreSQLEnvironmentProvider();
        var config =
                EnvironmentConfig.of(
                        Map.of("jdbc_url", "jdbc:postgresql://localhost/db", "username", "user"));

        // when & then
        assertThatThrownBy(() -> provider.createEnvironment("test", config))
                .isInstanceOf(PostgreSQLException.class)
                .hasMessageContaining("Missing required config: password");
    }

    @Test
    void migrationNodeProviderShouldCreateNode() {
        // given
        var provider = new PostgreSQLMigrationNodeProvider();
        var env =
                PostgreSQLEnvironment.create(
                        "test", "jdbc:postgresql://localhost:5432/test", "user", "pass");
        var nodeId = NodeId.of("V001");

        TaskDefinition task =
                TaskDefinition.of(
                        "Create users table",
                        "Initial schema",
                        SqlDefinition.ofSql("CREATE TABLE users (id SERIAL);"),
                        SqlDefinition.ofSql("DROP TABLE users;"));

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
                    public io.github.migraphe.api.environment.EnvironmentId id() {
                        return io.github.migraphe.api.environment.EnvironmentId.of("test");
                    }

                    @Override
                    public String name() {
                        return "test";
                    }

                    @Override
                    public EnvironmentConfig config() {
                        return EnvironmentConfig.empty();
                    }
                };
        var nodeId = NodeId.of("V001");
        TaskDefinition task =
                TaskDefinition.of("test", null, SqlDefinition.ofSql("SELECT 1;"), null);

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
                    public io.github.migraphe.api.environment.EnvironmentId id() {
                        return io.github.migraphe.api.environment.EnvironmentId.of("test");
                    }

                    @Override
                    public String name() {
                        return "test";
                    }

                    @Override
                    public EnvironmentConfig config() {
                        return EnvironmentConfig.empty();
                    }
                };

        // when & then
        assertThatThrownBy(() -> provider.createRepository(nonPgEnv))
                .isInstanceOf(PostgreSQLException.class)
                .hasMessageContaining("Environment must be PostgreSQLEnvironment");
    }
}
