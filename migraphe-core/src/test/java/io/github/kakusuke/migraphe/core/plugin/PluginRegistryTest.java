package io.github.kakusuke.migraphe.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.kakusuke.migraphe.api.spi.MigraphePlugin;
import io.github.kakusuke.migraphe.api.spi.MigrationNodeProvider;
import io.github.kakusuke.migraphe.api.spi.TargetDefinition;
import io.github.kakusuke.migraphe.api.spi.TargetProvider;
import io.github.kakusuke.migraphe.api.spi.TaskDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PluginRegistryTest {

    private PluginRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PluginRegistry();
    }

    @Test
    void shouldStartEmpty() {
        // then
        assertThat(registry.size()).isZero();
        assertThat(registry.supportedTypes()).isEmpty();
    }

    @Test
    void shouldRegisterPluginViaReflection() {
        // given
        MigraphePlugin mockPlugin = createMockPlugin("test-db");

        // when
        registry.register(mockPlugin);

        // then
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.supportedTypes()).containsExactly("test-db");
        assertThat(registry.getPlugin("test-db")).isNotNull().isSameAs(mockPlugin);
    }

    @Test
    void shouldReturnEmptyForUnknownType() {
        // when
        var result = registry.getPlugin("unknown");

        // then
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnPluginForRequiredPlugin() {
        // given
        MigraphePlugin mockPlugin = createMockPlugin("test-db");
        registry.register(mockPlugin);

        // when
        var result = registry.getRequiredPlugin("test-db");

        // then
        assertThat(result).isSameAs(mockPlugin);
    }

    @Test
    void shouldThrowExceptionForUnknownRequiredPlugin() {
        // given
        registry.register(createMockPlugin("postgresql"));

        // when & then
        assertThatThrownBy(() -> registry.getRequiredPlugin("mysql"))
                .isInstanceOf(PluginNotFoundException.class)
                .hasMessageContaining("No plugin found for type 'mysql'")
                .hasMessageContaining("Available plugins: [postgresql]")
                .hasMessageContaining("under plugins: in migraphe.yaml")
                .hasMessageContaining("migraphePlugin");
    }

    @Test
    void shouldThrowExceptionWithEmptyPluginsMessage() {
        // when & then
        assertThatThrownBy(() -> registry.getRequiredPlugin("any"))
                .isInstanceOf(PluginNotFoundException.class)
                .hasMessageContaining("No plugins are currently loaded");
    }

    @Test
    void shouldOverridePluginWithSameType() {
        // given
        MigraphePlugin plugin1 = createMockPlugin("postgresql");
        MigraphePlugin plugin2 = createMockPlugin("postgresql");

        // when
        registry.register(plugin1);
        registry.register(plugin2);

        // then
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.getPlugin("postgresql")).isNotNull().isSameAs(plugin2);
    }

    @Test
    void shouldSupportMultiplePluginTypes() {
        // given
        MigraphePlugin postgres = createMockPlugin("postgresql");
        MigraphePlugin mysql = createMockPlugin("mysql");
        MigraphePlugin mongodb = createMockPlugin("mongodb");

        // when
        registry.register(postgres);
        registry.register(mysql);
        registry.register(mongodb);

        // then
        assertThat(registry.size()).isEqualTo(3);
        assertThat(registry.supportedTypes())
                .containsExactlyInAnyOrder("postgresql", "mysql", "mongodb");
    }

    @Test
    void shouldThrowExceptionForNullPlugin() {
        // when & then
        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("plugin must not be null");
    }

    @Test
    void shouldThrowExceptionForNullType() {
        // given
        MigraphePlugin<?> pluginWithNullType =
                new MigraphePlugin<>() {
                    @Override
                    public String type() {
                        return null;
                    }

                    @Override
                    public Class<? extends TaskDefinition<Object>> taskDefinitionClass() {
                        return null;
                    }

                    @Override
                    public Class<? extends TargetDefinition> targetDefinitionClass() {
                        return null;
                    }

                    @Override
                    public TargetProvider targetProvider() {
                        return null;
                    }

                    @Override
                    public MigrationNodeProvider<Object> migrationNodeProvider() {
                        return null;
                    }

                    @Override
                    public HistoryRepositoryProvider historyRepositoryProvider() {
                        return null;
                    }
                };

        // when & then
        assertThatThrownBy(() -> registry.register(pluginWithNullType))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("plugin.type() must not be null");
    }

    @Test
    void shouldThrowExceptionForBlankType() {
        // given
        MigraphePlugin pluginWithBlankType = createMockPlugin("   ");

        // when & then
        assertThatThrownBy(() -> registry.register(pluginWithBlankType))
                .isInstanceOf(PluginLoadException.class)
                .hasMessageContaining("Plugin type must not be blank");
    }

    @Test
    void shouldClearAllPlugins() {
        // given
        registry.register(createMockPlugin("postgresql"));
        registry.register(createMockPlugin("mysql"));

        // when
        registry.clear();

        // then
        assertThat(registry.size()).isZero();
        assertThat(registry.supportedTypes()).isEmpty();
    }

    // ========== テストヘルパー ==========

    private MigraphePlugin<?> createMockPlugin(String type) {
        return new MigraphePlugin<>() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public Class<? extends TaskDefinition<Object>> taskDefinitionClass() {
                return null;
            }

            @Override
            public Class<? extends TargetDefinition> targetDefinitionClass() {
                return null;
            }

            @Override
            public TargetProvider targetProvider() {
                return (name, definition) -> null;
            }

            @Override
            public MigrationNodeProvider<Object> migrationNodeProvider() {
                return (nodeId, task, dependencies, target) -> null;
            }

            @Override
            public HistoryRepositoryProvider historyRepositoryProvider() {
                return (target) -> null;
            }
        };
    }
}
