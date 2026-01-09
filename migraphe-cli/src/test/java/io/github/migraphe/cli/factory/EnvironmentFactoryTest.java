package io.github.migraphe.cli.factory;

import static org.assertj.core.api.Assertions.*;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.core.plugin.PluginRegistry;
import io.github.migraphe.postgresql.PostgreSQLEnvironment;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.Map;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnvironmentFactoryTest {

    private PluginRegistry pluginRegistry;
    private EnvironmentFactory factory;

    @BeforeEach
    void setUp() {
        // PostgreSQLプラグインをクラスパスからロード
        pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();
        factory = new EnvironmentFactory(pluginRegistry);
    }

    @Test
    void shouldCreatePostgreSQLEnvironmentFromConfig() {
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

        // When: db1 の Environment を生成
        Environment environment = factory.createEnvironment(config, "db1");

        // Then: 正しく生成される
        assertThat(environment).isInstanceOf(PostgreSQLEnvironment.class);
        assertThat(environment.name()).isEqualTo("db1");

        // PostgreSQLEnvironment 固有のプロパティを検証
        PostgreSQLEnvironment pgEnv = (PostgreSQLEnvironment) environment;
        assertThat(pgEnv.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/mydb");
        assertThat(pgEnv.getUsername()).isEqualTo("dbuser");
        assertThat(pgEnv.getPassword()).isEqualTo("secret");
    }

    @Test
    void shouldCreateMultipleEnvironments() {
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

        // When: 複数の Environment を生成
        Map<String, Environment> environments = factory.createEnvironments(config);

        // Then: 両方のターゲットが生成される
        assertThat(environments).hasSize(2);
        assertThat(environments).containsKeys("db1", "db2");

        Environment db1 = environments.get("db1");
        assertThat(db1.name()).isEqualTo("db1");
        assertThat(db1).isInstanceOf(PostgreSQLEnvironment.class);
        assertThat(((PostgreSQLEnvironment) db1).getJdbcUrl()).isEqualTo("jdbc:postgresql://db1");

        Environment db2 = environments.get("db2");
        assertThat(db2.name()).isEqualTo("db2");
        assertThat(db2).isInstanceOf(PostgreSQLEnvironment.class);
        assertThat(((PostgreSQLEnvironment) db2).getJdbcUrl()).isEqualTo("jdbc:postgresql://db2");
    }

    @Test
    void shouldThrowExceptionWhenTargetNotFound() {
        // Given: db1 の設定がない
        SmallRyeConfig config =
                new SmallRyeConfigBuilder().withSources(new TestConfigSource(Map.of())).build();

        // When & Then: 例外が発生
        assertThatThrownBy(() -> factory.createEnvironment(config, "db1"))
                .isInstanceOf(io.github.migraphe.core.config.ConfigurationException.class)
                .hasMessageContaining("Target configuration not found: db1");
    }

    @Test
    void shouldThrowExceptionWhenPluginNotFound() {
        // Given: 未知の type を持つ設定
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withSources(
                                new TestConfigSource(
                                        Map.of(
                                                "target.db1.type",
                                                "unknown-type",
                                                "target.db1.jdbc_url",
                                                "jdbc:unknown://localhost")))
                        .build();

        // When & Then: プラグインが見つからない例外が発生
        assertThatThrownBy(() -> factory.createEnvironment(config, "db1"))
                .isInstanceOf(io.github.migraphe.core.config.ConfigurationException.class)
                .hasMessageContaining("No plugin found for type: unknown-type");
    }

    @Test
    void shouldHandleEmptyTargets() {
        // Given: ターゲット設定が空
        SmallRyeConfig config =
                new SmallRyeConfigBuilder().withSources(new TestConfigSource(Map.of())).build();

        // When: 全ターゲットを生成
        Map<String, Environment> environments = factory.createEnvironments(config);

        // Then: 空のマップが返される
        assertThat(environments).isEmpty();
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
        public String getValue(String propertyName) {
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
