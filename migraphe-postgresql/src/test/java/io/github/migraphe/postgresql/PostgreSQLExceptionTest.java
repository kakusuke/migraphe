package io.github.migraphe.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostgreSQLExceptionTest {

    @Test
    void shouldCreateExceptionWithMessage() {
        // given
        String message = "Database connection failed";

        // when
        PostgreSQLException exception = new PostgreSQLException(message);

        // then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void shouldCreateExceptionWithMessageAndCause() {
        // given
        String message = "Failed to execute SQL";
        Throwable cause = new RuntimeException("Connection timeout");

        // when
        PostgreSQLException exception = new PostgreSQLException(message, cause);

        // then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
}
