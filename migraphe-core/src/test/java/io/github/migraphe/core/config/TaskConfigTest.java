package io.github.migraphe.core.config;

import static org.assertj.core.api.Assertions.*;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.Map;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

class TaskConfigTest {

    @Test
    void shouldLoadValidTaskConfig() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withSources(
                                new TestConfigSource(
                                        Map.of(
                                                "name",
                                                "Create users table",
                                                "description",
                                                "Initial schema setup",
                                                "target",
                                                "db1",
                                                "up.sql",
                                                "CREATE TABLE users (id SERIAL PRIMARY KEY);",
                                                "down.sql",
                                                "DROP TABLE IF EXISTS users CASCADE;")))
                        .withMapping(TaskConfig.class)
                        .build();

        TaskConfig taskConfig = config.getConfigMapping(TaskConfig.class);

        assertThat(taskConfig.name()).isEqualTo("Create users table");
        assertThat(taskConfig.description()).isPresent();
        assertThat(taskConfig.description().get()).isEqualTo("Initial schema setup");
        assertThat(taskConfig.target()).isEqualTo("db1");
        assertThat(taskConfig.dependencies()).isEmpty();
        assertThat(taskConfig.up().sql()).isEqualTo("CREATE TABLE users (id SERIAL PRIMARY KEY);");
        assertThat(taskConfig.down()).isPresent();
        assertThat(taskConfig.down().get().sql()).isEqualTo("DROP TABLE IF EXISTS users CASCADE;");
    }

    @Test
    void shouldHandleTaskWithDependencies() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withSources(
                                new TestConfigSource(
                                        Map.of(
                                                "name",
                                                "Create posts table",
                                                "target",
                                                "db1",
                                                "dependencies",
                                                "db1/create_users_table",
                                                "up.sql",
                                                "CREATE TABLE posts (...);")))
                        .withMapping(TaskConfig.class)
                        .build();

        TaskConfig taskConfig = config.getConfigMapping(TaskConfig.class);

        assertThat(taskConfig.dependencies()).isPresent();
        assertThat(taskConfig.dependencies().get()).hasSize(1);
        assertThat(taskConfig.dependencies().get()).containsExactly("db1/create_users_table");
    }

    @Test
    void shouldDefaultDescriptionToEmptyString() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withSources(
                                new TestConfigSource(
                                        Map.of(
                                                "name", "Task without description",
                                                "target", "db1",
                                                "up.sql", "SELECT 1;")))
                        .withMapping(TaskConfig.class)
                        .build();

        TaskConfig taskConfig = config.getConfigMapping(TaskConfig.class);

        assertThat(taskConfig.description()).isEmpty();
    }

    @Test
    void shouldDefaultDependenciesToEmptyList() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withSources(
                                new TestConfigSource(
                                        Map.of(
                                                "name", "Task without dependencies",
                                                "target", "db1",
                                                "up.sql", "SELECT 1;")))
                        .withMapping(TaskConfig.class)
                        .build();

        TaskConfig taskConfig = config.getConfigMapping(TaskConfig.class);

        assertThat(taskConfig.dependencies()).isEmpty();
    }

    @Test
    void shouldHandleTaskWithoutDownBlock() {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withSources(
                                new TestConfigSource(
                                        Map.of(
                                                "name", "Task without down",
                                                "target", "db1",
                                                "up.sql", "SELECT 1;")))
                        .withMapping(TaskConfig.class)
                        .build();

        TaskConfig taskConfig = config.getConfigMapping(TaskConfig.class);

        assertThat(taskConfig.down()).isEmpty();
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
