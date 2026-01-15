package io.github.kakusuke.migraphe.core.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import org.junit.jupiter.api.Test;

class EnvironmentIdTest {

    @Test
    void shouldCreateEnvironmentIdWithValidValue() {
        // given
        String value = "dev";

        // when
        EnvironmentId envId = EnvironmentId.of(value);

        // then
        assertThat(envId.value()).isEqualTo(value);
    }

    @Test
    void shouldThrowExceptionWhenValueIsNull() {
        // when & then
        assertThatThrownBy(() -> EnvironmentId.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("value must not be null");
    }

    @Test
    void shouldThrowExceptionWhenValueIsBlank() {
        // when & then
        assertThatThrownBy(() -> EnvironmentId.of(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EnvironmentId value must not be blank");

        assertThatThrownBy(() -> EnvironmentId.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EnvironmentId value must not be blank");
    }

    @Test
    void shouldBeEqualWhenValuesAreSame() {
        // given
        EnvironmentId envId1 = EnvironmentId.of("production");
        EnvironmentId envId2 = EnvironmentId.of("production");

        // when & then
        assertThat(envId1).isEqualTo(envId2);
        assertThat(envId1.hashCode()).isEqualTo(envId2.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenValuesAreDifferent() {
        // given
        EnvironmentId envId1 = EnvironmentId.of("dev");
        EnvironmentId envId2 = EnvironmentId.of("staging");

        // when & then
        assertThat(envId1).isNotEqualTo(envId2);
    }
}
