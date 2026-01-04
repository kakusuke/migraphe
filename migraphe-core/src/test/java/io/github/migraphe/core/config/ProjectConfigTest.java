package io.github.migraphe.core.config;

import static org.assertj.core.api.Assertions.*;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.Map;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

class ProjectConfigTest {

    @Test
    void shouldLoadValidProjectConfig() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withSources(
                                new TestConfigSource(
                                        Map.of(
                                                "project.name",
                                                "my-migrations",
                                                "history.target",
                                                "history_db")))
                        .withMapping(ProjectConfig.class)
                        .build();

        ProjectConfig projectConfig = config.getConfigMapping(ProjectConfig.class);

        assertThat(projectConfig.project().name()).isEqualTo("my-migrations");
        assertThat(projectConfig.history().target()).isEqualTo("history_db");
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
