package io.github.kakusuke.migraphe.core.config;

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

    @Test
    void shouldLoadGeneratorsSection() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withSources(
                                new TestConfigSource(
                                        Map.of(
                                                "project.name",
                                                "my-migrations",
                                                "history.target",
                                                "history_db",
                                                "generators[0].name",
                                                "mydb",
                                                "generators[0].type",
                                                "jdbc-markdown",
                                                "generators[0].target",
                                                "db1",
                                                "generators[0].output-dir",
                                                "docs/schema")))
                        .withMapping(ProjectConfig.class)
                        .build();

        ProjectConfig projectConfig = config.getConfigMapping(ProjectConfig.class);

        assertThat(projectConfig.generators()).isPresent();
        var generators = projectConfig.generators().get();
        assertThat(generators).hasSize(1);
        var gen = generators.get(0);
        assertThat(gen.name()).isEqualTo("mydb");
        assertThat(gen.type()).isEqualTo("jdbc-markdown");
        assertThat(gen.target()).isEqualTo("db1");
        assertThat(gen.outputDir()).isEqualTo("docs/schema");
    }

    @Test
    void generatorsDefaultsToEmpty() {
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

        assertThat(projectConfig.generators()).isEmpty();
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
