package io.github.migraphe.cli.config.model;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import org.junit.jupiter.api.Test;

class TargetConfigTest {

    private final ObjectMapper mapper = new TomlMapper();

    @Test
    void shouldParseValidTargetConfig() throws Exception {
        String toml = """
                type = "postgresql"
                jdbc_url = "jdbc:postgresql://localhost:5432/mydb"
                username = "user"
                password = "password"
                """;

        TargetConfig config = mapper.readValue(toml, TargetConfig.class);

        assertThat(config.type()).isEqualTo("postgresql");
        assertThat(config.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/mydb");
        assertThat(config.username()).isEqualTo("user");
        assertThat(config.password()).isEqualTo("password");
    }

    @Test
    void shouldParseConfigWithVariableReferences() throws Exception {
        String toml = """
                type = "postgresql"
                jdbc_url = "jdbc:postgresql://${DB_HOST}:${DB_PORT}/mydb"
                username = "${DB_USER}"
                password = "${DB_PASSWORD}"
                """;

        TargetConfig config = mapper.readValue(toml, TargetConfig.class);

        // 変数展開は別コンポーネントで行うため、ここでは${VAR}のまま
        assertThat(config.jdbcUrl()).contains("${DB_HOST}");
        assertThat(config.jdbcUrl()).contains("${DB_PORT}");
        assertThat(config.username()).isEqualTo("${DB_USER}");
        assertThat(config.password()).isEqualTo("${DB_PASSWORD}");
    }

    @Test
    void shouldAllowEmptyPassword() throws Exception {
        String toml = """
                type = "postgresql"
                jdbc_url = "jdbc:postgresql://localhost:5432/mydb"
                username = "user"
                password = ""
                """;

        TargetConfig config = mapper.readValue(toml, TargetConfig.class);

        assertThat(config.password()).isEmpty();
    }

    @Test
    void shouldRejectNullType() {
        assertThatThrownBy(() -> new TargetConfig(null, "jdbc:...", "user", "pass"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type must not be null");
    }

    @Test
    void shouldRejectBlankType() {
        assertThatThrownBy(() -> new TargetConfig("", "jdbc:...", "user", "pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type must not be blank");
    }

    @Test
    void shouldRejectNullJdbcUrl() {
        assertThatThrownBy(() -> new TargetConfig("postgresql", null, "user", "pass"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("jdbc_url must not be null");
    }

    @Test
    void shouldRejectBlankJdbcUrl() {
        assertThatThrownBy(() -> new TargetConfig("postgresql", "", "user", "pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbc_url must not be blank");
    }

    @Test
    void shouldRejectNullUsername() {
        assertThatThrownBy(() -> new TargetConfig("postgresql", "jdbc:...", null, "pass"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("username must not be null");
    }

    @Test
    void shouldRejectBlankUsername() {
        assertThatThrownBy(() -> new TargetConfig("postgresql", "jdbc:...", "", "pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username must not be blank");
    }

    @Test
    void shouldRejectNullPassword() {
        assertThatThrownBy(() -> new TargetConfig("postgresql", "jdbc:...", "user", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("password must not be null");
    }
}
