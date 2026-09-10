package io.github.kakusuke.migraphe.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.target.TargetId;
import org.junit.jupiter.api.Test;

class PostgreSQLTargetTest {

    @Test
    void shouldCreateTargetWithJdbcCredentials() {
        // given
        String name = "production";
        String jdbcUrl = "jdbc:postgresql://localhost:5432/mydb";
        String username = "admin";
        String password = "secret";

        // when
        PostgreSQLTarget target = PostgreSQLTarget.create(name, jdbcUrl, username, password);

        // then
        assertThat(target.id()).isEqualTo(TargetId.of(name));
        assertThat(target.name()).isEqualTo(name);
        assertThat(target.getJdbcUrl()).isEqualTo(jdbcUrl);
        assertThat(target.getUsername()).isEqualTo(username);
        assertThat(target.getPassword()).isEqualTo(password);
    }

    @Test
    void shouldThrowExceptionWhenJdbcUrlIsNull() {
        // when & then
        assertThatThrownBy(() -> PostgreSQLTarget.create("test", null, "user", "pass"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("jdbcUrl must not be null");
    }

    @Test
    void shouldThrowExceptionWhenUsernameIsNull() {
        // when & then
        assertThatThrownBy(
                        () ->
                                PostgreSQLTarget.create(
                                        "test",
                                        "jdbc:postgresql://localhost:5432/db",
                                        null,
                                        "pass"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("username must not be null");
    }

    @Test
    void shouldAllowNullPassword() {
        // when
        PostgreSQLTarget target =
                PostgreSQLTarget.create(
                        "test", "jdbc:postgresql://localhost:5432/db", "user", null);

        // then
        assertThat(target.getPassword()).isNull();
    }
}
