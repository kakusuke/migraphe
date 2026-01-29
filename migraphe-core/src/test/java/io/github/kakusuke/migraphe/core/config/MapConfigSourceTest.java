package io.github.kakusuke.migraphe.core.config;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MapConfigSourceTest {

    @Test
    void shouldReturnValueForExistingKey() {
        MapConfigSource source =
                new MapConfigSource(Map.of("DB_URL", "jdbc:postgresql://localhost"));

        assertThat(source.getValue("DB_URL")).isEqualTo("jdbc:postgresql://localhost");
    }

    @Test
    void shouldReturnNullForMissingKey() {
        MapConfigSource source =
                new MapConfigSource(Map.of("DB_URL", "jdbc:postgresql://localhost"));

        assertThat(source.getValue("MISSING")).isNull();
    }

    @Test
    void shouldReturnAllPropertyNames() {
        MapConfigSource source =
                new MapConfigSource(
                        Map.of("DB_URL", "jdbc:postgresql://localhost", "DB_USER", "admin"));

        assertThat(source.getPropertyNames()).containsExactlyInAnyOrder("DB_URL", "DB_USER");
    }

    @Test
    void shouldHaveOrdinal600() {
        MapConfigSource source = new MapConfigSource(Map.of());

        assertThat(source.getOrdinal()).isEqualTo(600);
    }

    @Test
    void shouldHandleEmptyMap() {
        MapConfigSource source = new MapConfigSource(Map.of());

        assertThat(source.getPropertyNames()).isEmpty();
        assertThat(source.getValue("anything")).isNull();
        assertThat(source.getProperties()).isEmpty();
    }

    @Test
    void shouldReturnImmutableProperties() {
        MapConfigSource source = new MapConfigSource(Map.of("key", "value"));

        Map<String, String> properties = source.getProperties();
        assertThatThrownBy(() -> properties.put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
