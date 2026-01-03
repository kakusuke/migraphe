package io.github.migraphe.cli.config.model;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import org.junit.jupiter.api.Test;

class ProjectConfigTest {

    private final ObjectMapper mapper = new TomlMapper();

    @Test
    void shouldParseValidProjectConfig() throws Exception {
        String toml = """
                [project]
                name = "my-migrations"

                [history]
                target = "history_db"
                """;

        ProjectConfig config = mapper.readValue(toml, ProjectConfig.class);

        assertThat(config.project().name()).isEqualTo("my-migrations");
        assertThat(config.history().target()).isEqualTo("history_db");
    }

    @Test
    void shouldRejectNullProjectName() {
        assertThatThrownBy(() -> new ProjectConfig.ProjectSection(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("project name must not be null");
    }

    @Test
    void shouldRejectBlankProjectName() {
        assertThatThrownBy(() -> new ProjectConfig.ProjectSection(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("project name must not be blank");

        assertThatThrownBy(() -> new ProjectConfig.ProjectSection("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("project name must not be blank");
    }

    @Test
    void shouldRejectNullHistoryTarget() {
        assertThatThrownBy(() -> new ProjectConfig.HistorySection(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("history target must not be null");
    }

    @Test
    void shouldRejectBlankHistoryTarget() {
        assertThatThrownBy(() -> new ProjectConfig.HistorySection(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("history target must not be blank");

        assertThatThrownBy(() -> new ProjectConfig.HistorySection("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("history target must not be blank");
    }
}
