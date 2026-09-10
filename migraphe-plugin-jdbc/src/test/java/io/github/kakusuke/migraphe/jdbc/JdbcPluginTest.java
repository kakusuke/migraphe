package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfoProvider;
import java.util.ServiceLoader;
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
    void targetDefinitionClass() {
        assertThat(plugin.targetDefinitionClass()).isEqualTo(JdbcTargetDefinition.class);
    }

    @Test
    void targetProvider() {
        assertThat(plugin.targetProvider()).isInstanceOf(JdbcTargetProvider.class);
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
    void targetProviderRejectsWrongType() {
        var provider = plugin.targetProvider();
        // Create a non-JdbcTargetDefinition
        assertThatThrownBy(() -> provider.createTarget("test", () -> "wrong"))
                .isInstanceOf(JdbcException.class);
    }

    @Test
    void historyRepositoryProviderRejectsWrongTarget() {
        var provider = plugin.historyRepositoryProvider();
        var fakeEnv =
                new io.github.kakusuke.migraphe.api.target.Target() {
                    @Override
                    public io.github.kakusuke.migraphe.api.target.TargetId id() {
                        return io.github.kakusuke.migraphe.api.target.TargetId.of("fake");
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
    void migrationNodeProviderRejectsWrongTarget() {
        var provider = plugin.migrationNodeProvider();
        var fakeEnv =
                new io.github.kakusuke.migraphe.api.target.Target() {
                    @Override
                    public io.github.kakusuke.migraphe.api.target.TargetId id() {
                        return io.github.kakusuke.migraphe.api.target.TargetId.of("fake");
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
