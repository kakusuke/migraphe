package io.github.migraphe.cli.config.model;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvironmentConfigTest {

    private final ObjectMapper mapper = new TomlMapper();

    @Test
    void shouldParseAllKeyValuePairsAsVariables() throws Exception {
        String toml = """
                DB1_HOST = "localhost"
                DB1_PORT = "5432"
                DB1_USER = "user"
                DB1_PASSWORD = "password"
                """;

        EnvironmentConfig config = mapper.readValue(toml, EnvironmentConfig.class);

        Map<String, String> variables = config.variables();
        assertThat(variables).hasSize(4);
        assertThat(variables).containsEntry("DB1_HOST", "localhost");
        assertThat(variables).containsEntry("DB1_PORT", "5432");
        assertThat(variables).containsEntry("DB1_USER", "user");
        assertThat(variables).containsEntry("DB1_PASSWORD", "password");
    }

    @Test
    void shouldParseEmptyFileAsEmptyVariables() throws Exception {
        String toml = "";

        EnvironmentConfig config = mapper.readValue(toml, EnvironmentConfig.class);

        assertThat(config.variables()).isEmpty();
    }

    @Test
    void shouldReturnImmutableVariablesMap() throws Exception {
        String toml = """
                KEY = "value"
                """;

        EnvironmentConfig config = mapper.readValue(toml, EnvironmentConfig.class);

        Map<String, String> variables = config.variables();
        assertThatThrownBy(() -> variables.put("NEW_KEY", "new_value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldCreateEmptyConfig() {
        EnvironmentConfig config = EnvironmentConfig.empty();

        assertThat(config.variables()).isEmpty();
    }
}
