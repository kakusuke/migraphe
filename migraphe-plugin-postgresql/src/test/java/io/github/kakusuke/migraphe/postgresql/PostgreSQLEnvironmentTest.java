package io.github.kakusuke.migraphe.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import org.junit.jupiter.api.Test;

class PostgreSQLEnvironmentTest {

    @Test
    void shouldCreateEnvironmentWithJdbcCredentials() {
        // given
        String name = "production";
        String jdbcUrl = "jdbc:postgresql://localhost:5432/mydb";
        String username = "admin";
        String password = "secret";

        // when
        PostgreSQLEnvironment env = PostgreSQLEnvironment.create(name, jdbcUrl, username, password);

        // then
        assertThat(env.id()).isEqualTo(EnvironmentId.of(name));
        assertThat(env.name()).isEqualTo(name);
        assertThat(env.getJdbcUrl()).isEqualTo(jdbcUrl);
        assertThat(env.getUsername()).isEqualTo(username);
        assertThat(env.getPassword()).isEqualTo(password);
    }

    @Test
    void shouldThrowExceptionWhenJdbcUrlIsNull() {
        // when & then
        assertThatThrownBy(() -> PostgreSQLEnvironment.create("test", null, "user", "pass"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("jdbcUrl must not be null");
    }

    @Test
    void shouldThrowExceptionWhenUsernameIsNull() {
        // when & then
        assertThatThrownBy(
                        () ->
                                PostgreSQLEnvironment.create(
                                        "test",
                                        "jdbc:postgresql://localhost:5432/db",
                                        null,
                                        "pass"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("username must not be null");
    }

    @Test
    void shouldThrowExceptionWhenPasswordIsNull() {
        // when & then
        assertThatThrownBy(
                        () ->
                                PostgreSQLEnvironment.create(
                                        "test",
                                        "jdbc:postgresql://localhost:5432/db",
                                        "user",
                                        null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("password must not be null");
    }
}
