package io.github.kakusuke.migraphe.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.TargetDefinition;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import io.github.kakusuke.migraphe.api.target.Target;
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

class PostgreSQLPluginTest {

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
                new PostgreSQLPlugin()
                        .migrationNodeProvider()
                        .createNode(
                                NodeId.of("drop_legacy"),
                                definition,
                                Set.of(),
                                PostgreSQLTarget.create(
                                        "db1", "jdbc:postgresql://localhost:5432/t", "u", "p"));

        assertThat(node.noWayBack()).isEqualTo("DROP COLUMN discards the data");
        assertThat(node.downTask()).isNull();
    }

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
    void shouldReturnTargetDefinitionClass() {
        // given
        PostgreSQLPlugin plugin = new PostgreSQLPlugin();

        // when
        Class<? extends TargetDefinition> targetDefClass = plugin.targetDefinitionClass();

        // then
        assertThat(targetDefClass).isEqualTo(PostgreSQLTargetDefinition.class);
    }

    @Test
    void shouldProvideTargetProvider() {
        // given
        PostgreSQLPlugin plugin = new PostgreSQLPlugin();

        // when
        var provider = plugin.targetProvider();

        // then
        assertThat(provider).isNotNull();
        assertThat(provider).isInstanceOf(PostgreSQLTargetProvider.class);
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
    void targetProviderShouldCreateTarget() {
        // given
        var provider = new PostgreSQLTargetProvider();
        var definition =
                createTargetDefinition(
                        "postgresql",
                        "jdbc:postgresql://localhost:5432/test",
                        "testuser",
                        "testpass");

        // when
        Target target = provider.createTarget("test-db", definition);

        // then
        assertThat(target).isNotNull();
        assertThat(target).isInstanceOf(PostgreSQLTarget.class);
        assertThat(target.name()).isEqualTo("test-db");
    }

    @Test
    void targetProviderShouldThrowWhenGivenWrongDefinitionType() {
        // given
        var provider = new PostgreSQLTargetProvider();
        var wrongDefinition =
                new TargetDefinition() {
                    @Override
                    public String type() {
                        return "other";
                    }
                };

        // when & then
        assertThatThrownBy(() -> provider.createTarget("test", wrongDefinition))
                .isInstanceOf(PostgreSQLException.class)
                .hasMessageContaining("Expected PostgreSQLTargetDefinition");
    }

    @Test
    void migrationNodeProviderShouldCreateNode() {
        // given
        var provider = new PostgreSQLMigrationNodeProvider();
        var target =
                PostgreSQLTarget.create(
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
        MigrationNode node = provider.createNode(nodeId, task, Set.of(), target);

        // then
        assertThat(node).isNotNull();
        assertThat(node).isInstanceOf(JdbcMigrationNode.class);
        assertThat(node.id()).isEqualTo(nodeId);
        assertThat(node.name()).isEqualTo("Create users table");
        assertThat(node.description()).isEqualTo("Initial schema");
    }

    @Test
    void migrationNodeProviderShouldThrowForNonPostgreSQLTarget() {
        // given
        var provider = new PostgreSQLMigrationNodeProvider();
        var nonPgEnv =
                new Target() {
                    @Override
                    public io.github.kakusuke.migraphe.api.target.TargetId id() {
                        return io.github.kakusuke.migraphe.api.target.TargetId.of("test");
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
                .hasMessageContaining("Target must be PostgreSQLTarget");
    }

    @Test
    void historyRepositoryProviderShouldCreateRepository() {
        // given
        var provider = new PostgreSQLHistoryRepositoryProvider();
        var target =
                PostgreSQLTarget.create(
                        "test", "jdbc:postgresql://localhost:5432/test", "user", "pass");

        // when
        HistoryRepository repo = provider.createRepository(target);

        // then
        assertThat(repo).isNotNull();
        assertThat(repo).isInstanceOf(JdbcHistoryRepository.class);
    }

    @Test
    void historyRepositoryProviderShouldThrowForNonPostgreSQLTarget() {
        // given
        var provider = new PostgreSQLHistoryRepositoryProvider();
        var nonPgEnv =
                new Target() {
                    @Override
                    public io.github.kakusuke.migraphe.api.target.TargetId id() {
                        return io.github.kakusuke.migraphe.api.target.TargetId.of("test");
                    }

                    @Override
                    public String name() {
                        return "test";
                    }
                };

        // when & then
        assertThatThrownBy(() -> provider.createRepository(nonPgEnv))
                .isInstanceOf(PostgreSQLException.class)
                .hasMessageContaining("Target must be PostgreSQLTarget");
    }

    @Test
    void autocommitCanDifferBetweenUpAndDown() {
        assertThat(taskDescriptions("autocommit.up", "true", "autocommit.down", "false"))
                .containsExactly(
                        "PostgreSQL UP migration (autocommit)", "PostgreSQL DOWN migration");
        assertThat(taskDescriptions("autocommit.down", "true"))
                .containsExactly(
                        "PostgreSQL UP migration", "PostgreSQL DOWN migration (autocommit)");
        assertThat(taskDescriptions("autocommit", "true"))
                .containsExactly(
                        "PostgreSQL UP migration (autocommit)",
                        "PostgreSQL DOWN migration (autocommit)");
        assertThat(taskDescriptions("autocommit", "true", "autocommit.up", "false"))
                .containsExactly(
                        "PostgreSQL UP migration", "PostgreSQL DOWN migration (autocommit)");
        assertThat(taskDescriptions())
                .containsExactly("PostgreSQL UP migration", "PostgreSQL DOWN migration");
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
                new PostgreSQLPlugin()
                        .migrationNodeProvider()
                        .createNode(
                                NodeId.of("create_users"),
                                builder.build().getConfigMapping(SqlTaskDefinition.class),
                                Set.of(),
                                PostgreSQLTarget.create(
                                        "test",
                                        "jdbc:postgresql://localhost:5432/test",
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

    /** テスト用の PostgreSQLTargetDefinition を作成する。 */
    private PostgreSQLTargetDefinition createTargetDefinition(
            String type, String jdbcUrl, String username, String password) {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withMapping(PostgreSQLTargetDefinition.class)
                        .withDefaultValue("type", type)
                        .withDefaultValue("jdbc_url", jdbcUrl)
                        .withDefaultValue("username", username)
                        .withDefaultValue("password", password)
                        .build();
        return config.getConfigMapping(PostgreSQLTargetDefinition.class);
    }
}
