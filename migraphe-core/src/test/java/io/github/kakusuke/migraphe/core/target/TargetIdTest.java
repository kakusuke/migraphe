package io.github.kakusuke.migraphe.core.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.target.TargetId;
import org.junit.jupiter.api.Test;

class TargetIdTest {

    @Test
    void shouldCreateTargetIdWithValidValue() {
        // given
        String value = "dev";

        // when
        TargetId targetId = TargetId.of(value);

        // then
        assertThat(targetId.value()).isEqualTo(value);
    }

    @Test
    void shouldThrowExceptionWhenValueIsNull() {
        // when & then
        assertThatThrownBy(() -> TargetId.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("value must not be null");
    }

    @Test
    void shouldThrowExceptionWhenValueIsBlank() {
        // when & then
        assertThatThrownBy(() -> TargetId.of(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TargetId value must not be blank");

        assertThatThrownBy(() -> TargetId.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TargetId value must not be blank");
    }

    @Test
    void shouldBeEqualWhenValuesAreSame() {
        // given
        TargetId envId1 = TargetId.of("production");
        TargetId envId2 = TargetId.of("production");

        // when & then
        assertThat(envId1).isEqualTo(envId2);
        assertThat(envId1.hashCode()).isEqualTo(envId2.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenValuesAreDifferent() {
        // given
        TargetId envId1 = TargetId.of("dev");
        TargetId envId2 = TargetId.of("staging");

        // when & then
        assertThat(envId1).isNotEqualTo(envId2);
    }
}
