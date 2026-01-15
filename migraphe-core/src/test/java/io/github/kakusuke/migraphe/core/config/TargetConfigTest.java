package io.github.kakusuke.migraphe.core.config;

import static org.assertj.core.api.Assertions.*;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.Map;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

class TargetConfigTest {

    @Test
    void shouldLoadValidTargetConfig() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withSources(
                                new TestConfigSource(
                                        Map.of(
                                                "type",
                                                "postgresql",
                                                "jdbc_url",
                                                "jdbc:postgresql://localhost:5432/mydb",
                                                "username",
                                                "dbuser",
                                                "password",
                                                "secret")))
                        .withMapping(TargetConfig.class)
                        .build();

        TargetConfig targetConfig = config.getConfigMapping(TargetConfig.class);

        assertThat(targetConfig.type()).isEqualTo("postgresql");
        assertThat(targetConfig.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/mydb");
        assertThat(targetConfig.username()).isEqualTo("dbuser");
        assertThat(targetConfig.password()).isEqualTo("secret");
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
