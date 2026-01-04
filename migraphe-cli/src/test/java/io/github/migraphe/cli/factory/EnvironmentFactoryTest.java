package io.github.migraphe.cli.factory;

import static org.assertj.core.api.Assertions.*;

import io.github.migraphe.postgresql.PostgreSQLEnvironment;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.Map;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

class EnvironmentFactoryTest {

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

        EnvironmentFactory factory = new EnvironmentFactory();

        // When: db1 の Environment を生成
        PostgreSQLEnvironment environment = factory.createEnvironment(config, "db1");

        // Then: 正しく生成される
        assertThat(environment.name()).isEqualTo("db1");
        assertThat(environment.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/mydb");
        assertThat(environment.getUsername()).isEqualTo("dbuser");
        assertThat(environment.getPassword()).isEqualTo("secret");
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

        EnvironmentFactory factory = new EnvironmentFactory();

        // When: 複数の Environment を生成
        Map<String, PostgreSQLEnvironment> environments = factory.createEnvironments(config);

        // Then: 両方のターゲットが生成される
        assertThat(environments).hasSize(2);
        assertThat(environments).containsKeys("db1", "db2");

        PostgreSQLEnvironment db1 = environments.get("db1");
        assertThat(db1.name()).isEqualTo("db1");
        assertThat(db1.getJdbcUrl()).isEqualTo("jdbc:postgresql://db1");

        PostgreSQLEnvironment db2 = environments.get("db2");
        assertThat(db2.name()).isEqualTo("db2");
        assertThat(db2.getJdbcUrl()).isEqualTo("jdbc:postgresql://db2");
    }

    @Test
    void shouldThrowExceptionWhenTargetNotFound() {
        // Given: db1 の設定がない
        SmallRyeConfig config =
                new SmallRyeConfigBuilder().withSources(new TestConfigSource(Map.of())).build();

        EnvironmentFactory factory = new EnvironmentFactory();

        // When & Then: 例外が発生
        assertThatThrownBy(() -> factory.createEnvironment(config, "db1"))
                .isInstanceOf(io.github.migraphe.core.config.ConfigurationException.class)
                .hasMessageContaining("Target configuration not found: db1");
    }

    @Test
    void shouldHandleEmptyTargets() {
        // Given: ターゲット設定が空
        SmallRyeConfig config =
                new SmallRyeConfigBuilder().withSources(new TestConfigSource(Map.of())).build();

        EnvironmentFactory factory = new EnvironmentFactory();

        // When: 全ターゲットを生成
        Map<String, PostgreSQLEnvironment> environments = factory.createEnvironments(config);

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
