package io.github.migraphe.core.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.migraphe.api.common.ValidationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationResultTest {

    @Test
    void shouldCreateValidResult() {
        // when
        ValidationResult result = ValidationResult.valid();

        // then
        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void shouldCreateInvalidResultWithSingleError() {
        // given
        String error = "Node has circular dependency";

        // when
        ValidationResult result = ValidationResult.invalid(error);

        // then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).containsExactly(error);
    }

    @Test
    void shouldCreateInvalidResultWithMultipleErrors() {
        // given
        List<String> errors = List.of("Error 1", "Error 2", "Error 3");

        // when
        ValidationResult result = ValidationResult.invalid(errors);

        // then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).containsExactlyElementsOf(errors);
    }

    @Test
    void shouldThrowExceptionWhenErrorsIsNull() {
        // when & then
        assertThatThrownBy(() -> ValidationResult.invalid((List<String>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("errors must not be null");
    }

    @Test
    void shouldThrowExceptionWhenInvalidResultHasNoErrors() {
        // when & then
        assertThatThrownBy(() -> ValidationResult.invalid(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid result must have at least one error");
    }

    @Test
    void shouldReturnImmutableErrorsList() {
        // given
        ValidationResult result = ValidationResult.invalid("Error");

        // when & then
        assertThatThrownBy(() -> result.errors().add("Another error"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
