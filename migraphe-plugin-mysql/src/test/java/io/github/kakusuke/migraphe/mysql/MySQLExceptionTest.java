package io.github.kakusuke.migraphe.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.jdbc.JdbcException;
import org.junit.jupiter.api.Test;

class MySQLExceptionTest {

    @Test
    void shouldConstructWithMessage() {
        // when
        var ex = new MySQLException("test error");

        // then
        assertThat(ex).hasMessage("test error");
        assertThat(ex).isInstanceOf(JdbcException.class);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldConstructWithMessageAndCause() {
        // given
        var cause = new RuntimeException("original");

        // when
        var ex = new MySQLException("wrapped", cause);

        // then
        assertThat(ex).hasMessage("wrapped");
        assertThat(ex).hasCause(cause);
    }
}
