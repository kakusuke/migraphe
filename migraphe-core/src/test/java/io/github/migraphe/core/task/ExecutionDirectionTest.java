package io.github.migraphe.core.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExecutionDirectionTest {

    @Test
    void shouldHaveUpDirection() {
        // when
        ExecutionDirection direction = ExecutionDirection.UP;

        // then
        assertThat(direction).isNotNull();
        assertThat(direction.name()).isEqualTo("UP");
    }

    @Test
    void shouldHaveDownDirection() {
        // when
        ExecutionDirection direction = ExecutionDirection.DOWN;

        // then
        assertThat(direction).isNotNull();
        assertThat(direction.name()).isEqualTo("DOWN");
    }

    @Test
    void shouldHaveExactlyTwoDirections() {
        // when
        ExecutionDirection[] directions = ExecutionDirection.values();

        // then
        assertThat(directions).hasSize(2);
        assertThat(directions)
                .containsExactlyInAnyOrder(ExecutionDirection.UP, ExecutionDirection.DOWN);
    }
}
