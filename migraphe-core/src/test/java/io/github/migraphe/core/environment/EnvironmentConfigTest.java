package io.github.migraphe.core.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.migraphe.api.environment.EnvironmentConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvironmentConfigTest {

    @Test
    void shouldCreateEmptyConfig() {
        // when
        EnvironmentConfig config = EnvironmentConfig.empty();

        // then
        assertThat(config.properties()).isEmpty();
    }

    @Test
    void shouldCreateConfigWithProperties() {
        // given
        Map<String, String> props = Map.of("key1", "value1", "key2", "value2");

        // when
        EnvironmentConfig config = EnvironmentConfig.of(props);

        // then
        assertThat(config.properties()).containsAllEntriesOf(props);
    }

    @Test
    void shouldGetPropertyByKey() {
        // given
        Map<String, String> props = Map.of("database.url", "jdbc:postgresql://localhost/db");
        EnvironmentConfig config = EnvironmentConfig.of(props);

        // when & then
        assertThat(config.getProperty("database.url")).hasValue("jdbc:postgresql://localhost/db");
        assertThat(config.getProperty("non.existent.key")).isEmpty();
    }

    @Test
    void shouldReturnImmutableProperties() {
        // given
        Map<String, String> props = Map.of("key1", "value1");
        EnvironmentConfig config = EnvironmentConfig.of(props);

        // when & then
        assertThatThrownBy(() -> config.properties().put("key2", "value2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldThrowExceptionWhenPropertiesIsNull() {
        // when & then
        assertThatThrownBy(() -> EnvironmentConfig.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("properties must not be null");
    }
}
