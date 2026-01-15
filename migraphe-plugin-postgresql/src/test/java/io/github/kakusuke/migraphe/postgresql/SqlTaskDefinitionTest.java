package io.github.kakusuke.migraphe.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;
import org.junit.jupiter.api.Test;

class SqlTaskDefinitionTest {

    @Test
    void shouldParseAutocommitTrue() {
        // Given: autocommit: true を含む YAML
        String yaml =
                """
                name: create_database
                target: admin
                autocommit: true
                up: "CREATE DATABASE myapp;"
                """;

        // When
        SqlTaskDefinition taskDef = parseYaml(yaml);

        // Then
        assertThat(taskDef.autocommit()).isPresent();
        assertThat(taskDef.autocommit().get()).isTrue();
    }

    @Test
    void shouldParseAutocommitFalse() {
        // Given: autocommit: false を含む YAML
        String yaml =
                """
                name: create_users
                target: db1
                autocommit: false
                up: "CREATE TABLE users (id SERIAL);"
                """;

        // When
        SqlTaskDefinition taskDef = parseYaml(yaml);

        // Then
        assertThat(taskDef.autocommit()).isPresent();
        assertThat(taskDef.autocommit().get()).isFalse();
    }

    @Test
    void shouldDefaultAutocommitToEmpty() {
        // Given: autocommit を含まない YAML
        String yaml =
                """
                name: create_users
                target: db1
                up: "CREATE TABLE users (id SERIAL);"
                """;

        // When
        SqlTaskDefinition taskDef = parseYaml(yaml);

        // Then: autocommit() は Optional.empty() を返す
        assertThat(taskDef.autocommit()).isEmpty();
    }

    private SqlTaskDefinition parseYaml(String yaml) {
        YamlConfigSource source = new YamlConfigSource("test", yaml);

        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withSources(source)
                        .withMapping(SqlTaskDefinition.class)
                        .build();

        return config.getConfigMapping(SqlTaskDefinition.class);
    }
}
