package io.github.kakusuke.migraphe.core.factory;

import static org.assertj.core.api.Assertions.*;

import io.github.kakusuke.migraphe.api.spi.TargetDefinition;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.core.config.ConfigLoader;
import io.github.kakusuke.migraphe.core.plugin.PluginNotFoundException;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import io.github.kakusuke.migraphe.postgresql.PostgreSQLTarget;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.Map;
import java.util.Objects;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TargetFactoryTest {

    private PluginRegistry pluginRegistry;
    private TargetFactory factory;
    private ConfigLoader configLoader;

    @BeforeEach
    void setUp() {
        // PostgreSQLプラグインをクラスパスからロード
        pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();
        factory = new TargetFactory(pluginRegistry);
        configLoader = new ConfigLoader();
    }

    @Test
    void shouldCreatePostgreSQLTargetFromDefinition() {
        // Given: TargetConfig がロードされた SmallRyeConfig
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withSources(
                                new TestConfigSource(
                                        Map.of(
                                                "target.db1.type",
                                                "postgresql",
                                                "target.db1.jdbc_url",
                                                "jdbc:postgresql://localhost:5432/mydb",
                                                "target.db1.username",
                                                "dbuser",
                                                "target.db1.password",
                                                "secret")))
                        .build();

        // When: TargetDefinition を読み込んで Target を生成
        Map<String, TargetDefinition> definitions =
                configLoader.loadTargetDefinitions(config, pluginRegistry);
        TargetDefinition definition = Objects.requireNonNull(definitions.get("db1"));
        Target target = factory.createTarget("db1", definition);

        // Then: 正しく生成される
        assertThat(target).isInstanceOf(PostgreSQLTarget.class);
        assertThat(target.name()).isEqualTo("db1");

        // PostgreSQLTarget 固有のプロパティを検証
        PostgreSQLTarget pgEnv = (PostgreSQLTarget) target;
        assertThat(pgEnv.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/mydb");
        assertThat(pgEnv.getUsername()).isEqualTo("dbuser");
        assertThat(pgEnv.getPassword()).isEqualTo("secret");
    }

    @Test
    void shouldCreateMultipleTargets() {
        // Given: 複数のターゲット設定
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withSources(
                                new TestConfigSource(
                                        Map.of(
                                                "target.db1.type",
                                                "postgresql",
                                                "target.db1.jdbc_url",
                                                "jdbc:postgresql://db1",
                                                "target.db1.username",
                                                "user1",
                                                "target.db1.password",
                                                "pass1",
                                                "target.db2.type",
                                                "postgresql",
                                                "target.db2.jdbc_url",
                                                "jdbc:postgresql://db2",
                                                "target.db2.username",
                                                "user2",
                                                "target.db2.password",
                                                "pass2")))
                        .build();

        // When: TargetDefinition を読み込んで Target を生成
        Map<String, TargetDefinition> definitions =
                configLoader.loadTargetDefinitions(config, pluginRegistry);
        Map<String, Target> targets = factory.createTargets(definitions);

        // Then: 両方のターゲットが生成される
        assertThat(targets).hasSize(2);
        assertThat(targets).containsKeys("db1", "db2");

        Target db1 = Objects.requireNonNull(targets.get("db1"));
        assertThat(db1.name()).isEqualTo("db1");
        assertThat(db1).isInstanceOf(PostgreSQLTarget.class);
        assertThat(((PostgreSQLTarget) db1).getJdbcUrl()).isEqualTo("jdbc:postgresql://db1");

        Target db2 = Objects.requireNonNull(targets.get("db2"));
        assertThat(db2.name()).isEqualTo("db2");
        assertThat(db2).isInstanceOf(PostgreSQLTarget.class);
        assertThat(((PostgreSQLTarget) db2).getJdbcUrl()).isEqualTo("jdbc:postgresql://db2");
    }

    @Test
    void shouldThrowExceptionWhenPluginNotFound() {
        // Given: 未知の type を持つ定義
        TargetDefinition unknownDefinition =
                new TargetDefinition() {
                    @Override
                    public String type() {
                        return "unknown-type";
                    }
                };

        // When & Then: プラグインが見つからない例外が発生
        assertThatThrownBy(() -> factory.createTarget("db1", unknownDefinition))
                .isInstanceOf(PluginNotFoundException.class)
                .hasMessageContaining("No plugin found for type 'unknown-type'");
    }

    @Test
    void shouldHandleEmptyDefinitions() {
        // Given: 空の定義マップ
        Map<String, TargetDefinition> definitions = Map.of();

        // When: 全 Target を生成
        Map<String, Target> targets = factory.createTargets(definitions);

        // Then: 空のマップが返される
        assertThat(targets).isEmpty();
    }

    /** テスト用のシンプルなConfigSource実装。 */
    private static class TestConfigSource implements ConfigSource {
        private final Map<String, String> properties;

        TestConfigSource(Map<String, String> properties) {
            this.properties = properties;
        }

        @Override
        public Map<String, String> getProperties() {
            return properties;
        }

        @Override
        public @Nullable String getValue(String propertyName) {
            return properties.get(propertyName);
        }

        @Override
        public String getName() {
            return "TestConfigSource";
        }

        @Override
        public java.util.Set<String> getPropertyNames() {
            return properties.keySet();
        }
    }
}
