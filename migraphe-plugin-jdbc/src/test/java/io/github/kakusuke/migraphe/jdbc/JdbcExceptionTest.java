package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdbcExceptionTest {

    @Test
    void constructWithMessage() {
        var ex = new JdbcException("test error");
        assertThat(ex).hasMessage("test error");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void constructWithMessageAndCause() {
        var cause = new RuntimeException("original");
        var ex = new JdbcException("wrapped", cause);
        assertThat(ex).hasMessage("wrapped");
        assertThat(ex).hasCause(cause);
    }
}
