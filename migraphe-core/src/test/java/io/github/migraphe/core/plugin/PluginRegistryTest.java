package io.github.migraphe.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.migraphe.api.spi.EnvironmentProvider;
import io.github.migraphe.api.spi.HistoryRepositoryProvider;
import io.github.migraphe.api.spi.MigraphePlugin;
import io.github.migraphe.api.spi.MigrationNodeProvider;
import io.github.migraphe.api.spi.TaskDefinition;
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
        assertThat(registry.getPlugin("test-db")).isPresent().contains(mockPlugin);
    }

    @Test
    void shouldReturnEmptyForUnknownType() {
        // when
        var result = registry.getPlugin("unknown");

        // then
        assertThat(result).isEmpty();
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
        assertThat(registry.getPlugin("postgresql")).isPresent().contains(plugin2);
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
                    public EnvironmentProvider environmentProvider() {
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

    @Test
    void shouldThrowExceptionForNonExistentJar() {
        // when & then
        assertThatThrownBy(() -> registry.loadFromJar(java.nio.file.Path.of("/nonexistent.jar")))
                .isInstanceOf(PluginLoadException.class)
                .hasMessageContaining("JAR file not found");
    }

    @Test
    void shouldThrowExceptionForNonJarFile() {
        // given - 実際に存在するファイルを使用
        var nonJarFile = java.nio.file.Path.of("build.gradle.kts");

        // when & then
        if (java.nio.file.Files.exists(nonJarFile)) {
            assertThatThrownBy(() -> registry.loadFromJar(nonJarFile))
                    .isInstanceOf(PluginLoadException.class)
                    .hasMessageContaining("Not a JAR file");
        }
    }

    @Test
    void shouldThrowExceptionForNonDirectoryPath() {
        // given
        var filePath = java.nio.file.Path.of("build.gradle.kts");

        // when & then - ファイルが存在する場合
        if (java.nio.file.Files.exists(filePath)) {
            assertThatThrownBy(() -> registry.loadFromDirectory(filePath))
                    .isInstanceOf(PluginLoadException.class)
                    .hasMessageContaining("Not a directory");
        }
    }

    @Test
    void shouldDoNothingWhenDirectoryDoesNotExist() {
        // given
        var nonExistentDir = java.nio.file.Path.of("/nonexistent/plugins");

        // when
        registry.loadFromDirectory(nonExistentDir);

        // then - 例外がスローされないことを確認
        assertThat(registry.size()).isZero();
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
            public EnvironmentProvider environmentProvider() {
                return (name, config) -> null;
            }

            @Override
            public MigrationNodeProvider<Object> migrationNodeProvider() {
                return (nodeId, task, dependencies, environment) -> null;
            }

            @Override
            public HistoryRepositoryProvider historyRepositoryProvider() {
                return (environment) -> null;
            }
        };
    }
}
