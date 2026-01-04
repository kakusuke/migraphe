package io.github.migraphe.core.history;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.migraphe.api.history.ExecutionStatus;
import org.junit.jupiter.api.Test;

class ExecutionStatusTest {

    @Test
    void shouldHaveSuccessStatus() {
        // when
        ExecutionStatus status = ExecutionStatus.SUCCESS;

        // then
        assertThat(status).isNotNull();
        assertThat(status.name()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldHaveFailureStatus() {
        // when
        ExecutionStatus status = ExecutionStatus.FAILURE;

        // then
        assertThat(status).isNotNull();
        assertThat(status.name()).isEqualTo("FAILURE");
    }

    @Test
    void shouldHaveSkippedStatus() {
        // when
        ExecutionStatus status = ExecutionStatus.SKIPPED;

        // then
        assertThat(status).isNotNull();
        assertThat(status.name()).isEqualTo("SKIPPED");
    }

    @Test
    void shouldHaveExactlyThreeStatuses() {
        // when
        ExecutionStatus[] statuses = ExecutionStatus.values();

        // then
        assertThat(statuses).hasSize(3);
        assertThat(statuses)
                .containsExactlyInAnyOrder(
                        ExecutionStatus.SUCCESS, ExecutionStatus.FAILURE, ExecutionStatus.SKIPPED);
    }
}
