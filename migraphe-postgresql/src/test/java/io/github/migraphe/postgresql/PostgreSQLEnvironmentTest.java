package io.github.migraphe.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.migraphe.api.environment.EnvironmentConfig;
import io.github.migraphe.api.environment.EnvironmentId;
import java.util.Map;
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
    void shouldCreateEnvironmentWithConfig() {
        // given
        EnvironmentId id = EnvironmentId.of("staging");
        String name = "staging";
        Map<String, String> properties =
                Map.of(
                        PostgreSQLEnvironment.JDBC_URL,
                        "jdbc:postgresql://staging:5432/db",
                        PostgreSQLEnvironment.JDBC_USERNAME,
                        "user",
                        PostgreSQLEnvironment.JDBC_PASSWORD,
                        "pass");
        EnvironmentConfig config = EnvironmentConfig.of(properties);

        // when
        PostgreSQLEnvironment env = PostgreSQLEnvironment.create(id, name, config);

        // then
        assertThat(env.id()).isEqualTo(id);
        assertThat(env.name()).isEqualTo(name);
        assertThat(env.getJdbcUrl()).isEqualTo("jdbc:postgresql://staging:5432/db");
        assertThat(env.getUsername()).isEqualTo("user");
        assertThat(env.getPassword()).isEqualTo("pass");
    }

    @Test
    void shouldThrowExceptionWhenJdbcUrlIsMissing() {
        // given
        EnvironmentId id = EnvironmentId.of("test");
        Map<String, String> properties =
                Map.of(
                        PostgreSQLEnvironment.JDBC_USERNAME,
                        "user",
                        PostgreSQLEnvironment.JDBC_PASSWORD,
                        "pass");
        EnvironmentConfig config = EnvironmentConfig.of(properties);

        // when & then
        assertThatThrownBy(() -> PostgreSQLEnvironment.create(id, "test", config))
                .isInstanceOf(PostgreSQLException.class)
                .hasMessageContaining("JDBC URL is required");
    }

    @Test
    void shouldThrowExceptionWhenUsernameIsMissing() {
        // given
        EnvironmentId id = EnvironmentId.of("test");
        Map<String, String> properties =
                Map.of(
                        PostgreSQLEnvironment.JDBC_URL,
                        "jdbc:postgresql://localhost:5432/db",
                        PostgreSQLEnvironment.JDBC_PASSWORD,
                        "pass");
        EnvironmentConfig config = EnvironmentConfig.of(properties);

        // when & then
        assertThatThrownBy(() -> PostgreSQLEnvironment.create(id, "test", config))
                .isInstanceOf(PostgreSQLException.class)
                .hasMessageContaining("JDBC username is required");
    }

    @Test
    void shouldThrowExceptionWhenPasswordIsMissing() {
        // given
        EnvironmentId id = EnvironmentId.of("test");
        Map<String, String> properties =
                Map.of(
                        PostgreSQLEnvironment.JDBC_URL,
                        "jdbc:postgresql://localhost:5432/db",
                        PostgreSQLEnvironment.JDBC_USERNAME,
                        "user");
        EnvironmentConfig config = EnvironmentConfig.of(properties);

        // when & then
        assertThatThrownBy(() -> PostgreSQLEnvironment.create(id, "test", config))
                .isInstanceOf(PostgreSQLException.class)
                .hasMessageContaining("JDBC password is required");
    }
}
