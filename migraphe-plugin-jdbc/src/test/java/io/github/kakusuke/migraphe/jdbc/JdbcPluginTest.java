package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfoProvider;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JdbcPluginTest {

    private final JdbcPlugin plugin = new JdbcPlugin();

    @Test
    void typeIsJdbc() {
        assertThat(plugin.type()).isEqualTo("jdbc");
    }

    @Test
    void taskDefinitionClass() {
        assertThat(plugin.taskDefinitionClass()).isEqualTo(SqlTaskDefinition.class);
    }

    @Test
    void environmentDefinitionClass() {
        assertThat(plugin.environmentDefinitionClass()).isEqualTo(JdbcEnvironmentDefinition.class);
    }

    @Test
    void environmentProvider() {
        assertThat(plugin.environmentProvider()).isInstanceOf(JdbcEnvironmentProvider.class);
    }

    @Test
    void migrationNodeProvider() {
        assertThat(plugin.migrationNodeProvider()).isInstanceOf(JdbcMigrationNodeProvider.class);
    }

    @Test
    void historyRepositoryProvider() {
        assertThat(plugin.historyRepositoryProvider())
                .isInstanceOf(JdbcHistoryRepositoryProvider.class);
    }

    @Test
    void discoveredByServiceLoader() {
        var plugins = ServiceLoader.load(MigraphePlugin.class);
        var jdbcPlugin =
                plugins.stream()
                        .map(ServiceLoader.Provider::get)
                        .filter(p -> "jdbc".equals(p.type()))
                        .findFirst();
        assertThat(jdbcPlugin).isPresent();
        assertThat(jdbcPlugin.get()).isInstanceOf(JdbcPlugin.class);
    }

    @Test
    void environmentProviderRejectsWrongType() {
        var provider = plugin.environmentProvider();
        // Create a non-JdbcEnvironmentDefinition
        assertThatThrownBy(() -> provider.createEnvironment("test", () -> "wrong"))
                .isInstanceOf(JdbcException.class);
    }

    @Test
    void historyRepositoryProviderRejectsWrongEnvironment() {
        var provider = plugin.historyRepositoryProvider();
        var fakeEnv =
                new io.github.kakusuke.migraphe.api.environment.Environment() {
                    @Override
                    public io.github.kakusuke.migraphe.api.environment.EnvironmentId id() {
                        return io.github.kakusuke.migraphe.api.environment.EnvironmentId.of("fake");
                    }

                    @Override
                    public String name() {
                        return "fake";
                    }
                };
        assertThatThrownBy(() -> provider.createRepository(fakeEnv))
                .isInstanceOf(JdbcException.class);
    }

    @Test
    void schemaInfoProviderReturnsJdbcSchemaInfoProvider() {
        assertThat(plugin.schemaInfoProvider())
                .isPresent()
                .get()
                .isInstanceOf(JdbcSchemaInfoProvider.class);
    }

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

        JdbcEnvironment env =
                JdbcEnvironment.create(
                        "db1", "jdbc:h2:mem:plugin_test", "sa", "", "org.h2.Driver", "H2");

        MigrationNode node =
                plugin.migrationNodeProvider()
                        .createNode(NodeId.of("drop_legacy"), definition, Set.of(), env);

        assertThat(node.noWayBack()).isEqualTo("DROP COLUMN discards the data");
        assertThat(node.downTask()).isNull();
    }

    @Test
    void migrationNodeProviderRejectsWrongEnvironment() {
        var provider = plugin.migrationNodeProvider();
        var fakeEnv =
                new io.github.kakusuke.migraphe.api.environment.Environment() {
                    @Override
                    public io.github.kakusuke.migraphe.api.environment.EnvironmentId id() {
                        return io.github.kakusuke.migraphe.api.environment.EnvironmentId.of("fake");
                    }

                    @Override
                    public String name() {
                        return "fake";
                    }
                };
        assertThatThrownBy(
                        () ->
                                provider.createNode(
                                        io.github.kakusuke.migraphe.api.graph.NodeId.of("n1"),
                                        new io.github.kakusuke.migraphe.api.spi.TaskDefinition<
                                                String>() {
                                            @Override
                                            public String name() {
                                                return "test";
                                            }

                                            @Override
                                            public java.util.Optional<String> description() {
                                                return java.util.Optional.empty();
                                            }

                                            @Override
                                            public String target() {
                                                return "t";
                                            }

                                            @Override
                                            public java.util.Optional<java.util.List<String>>
                                                    dependencies() {
                                                return java.util.Optional.empty();
                                            }

                                            @Override
                                            public String up() {
                                                return "SELECT 1";
                                            }

                                            @Override
                                            public java.util.Optional<String> down() {
                                                return java.util.Optional.empty();
                                            }
                                        },
                                        java.util.Set.of(),
                                        fakeEnv))
                .isInstanceOf(JdbcException.class);
    }
}
