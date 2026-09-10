package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfoProvider;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.List;
import java.util.Objects;
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

        JdbcTarget target =
                JdbcTarget.create(
                        "db1", "jdbc:h2:mem:plugin_test", "sa", "", "org.h2.Driver", "H2");

        MigrationNode node =
                plugin.migrationNodeProvider()
                        .createNode(NodeId.of("drop_legacy"), definition, Set.of(), target);

        assertThat(node.noWayBack()).isEqualTo("DROP COLUMN discards the data");
        assertThat(node.downTask()).isNull();
    }

    @Test
    void autocommitCanDifferBetweenUpAndDown() {
        assertThat(taskDescriptions("autocommit.up", "true", "autocommit.down", "false"))
                .containsExactly("H2 UP migration (autocommit)", "H2 DOWN migration");
        assertThat(taskDescriptions("autocommit.up", "false", "autocommit.down", "true"))
                .containsExactly("H2 UP migration", "H2 DOWN migration (autocommit)");
        assertThat(taskDescriptions("autocommit.down", "true"))
                .containsExactly("H2 UP migration", "H2 DOWN migration (autocommit)");
        assertThat(taskDescriptions("autocommit", "true"))
                .containsExactly("H2 UP migration (autocommit)", "H2 DOWN migration (autocommit)");
        assertThat(taskDescriptions("autocommit", "true", "autocommit.up", "false"))
                .containsExactly("H2 UP migration", "H2 DOWN migration (autocommit)");
        assertThat(taskDescriptions()).containsExactly("H2 UP migration", "H2 DOWN migration");
    }

    private List<String> taskDescriptions(String... autocommitEntries) {
        SmallRyeConfigBuilder builder =
                new SmallRyeConfigBuilder()
                        .withMapping(SqlTaskDefinition.class)
                        .withDefaultValue("name", "create_users")
                        .withDefaultValue("target", "db1")
                        .withDefaultValue("up", "CREATE TABLE users (id INT)")
                        .withDefaultValue("down", "DROP TABLE users");
        for (int i = 0; i < autocommitEntries.length; i += 2) {
            builder.withDefaultValue(autocommitEntries[i], autocommitEntries[i + 1]);
        }

        JdbcTarget target =
                JdbcTarget.create(
                        "db1", "jdbc:h2:mem:plugin_test", "sa", "", "org.h2.Driver", "H2");
        MigrationNode node =
                plugin.migrationNodeProvider()
                        .createNode(
                                NodeId.of("create_users"),
                                builder.build().getConfigMapping(SqlTaskDefinition.class),
                                Set.of(),
                                target);

        Task downTask = node.downTask();
        return List.of(
                node.upTask().description(),
                Objects.requireNonNull(downTask, "the fixture supplies down SQL").description());
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
