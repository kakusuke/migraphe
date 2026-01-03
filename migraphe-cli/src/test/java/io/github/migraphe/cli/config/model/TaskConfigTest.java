package io.github.migraphe.cli.config.model;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import org.junit.jupiter.api.Test;

class TaskConfigTest {

    private final ObjectMapper mapper = new TomlMapper();

    @Test
    void shouldParseValidTaskConfig() throws Exception {
        String toml = """
                name = "Create users table"
                description = "Initial schema setup"
                target = "db1"
                dependencies = []

                [up]
                sql = \"\"\"
                CREATE TABLE users (
                  id SERIAL PRIMARY KEY,
                  name VARCHAR(100)
                );
                \"\"\"

                [down]
                sql = \"\"\"
                DROP TABLE IF EXISTS users CASCADE;
                \"\"\"
                """;

        TaskConfig config = mapper.readValue(toml, TaskConfig.class);

        assertThat(config.name()).isEqualTo("Create users table");
        assertThat(config.description()).isEqualTo("Initial schema setup");
        assertThat(config.target()).isEqualTo("db1");
        assertThat(config.dependencies()).isEmpty();
        assertThat(config.up().sql()).contains("CREATE TABLE users");
        assertThat(config.downOptional()).isPresent();
        assertThat(config.downOptional().get().sql()).contains("DROP TABLE IF EXISTS users");
    }

    @Test
    void shouldParseTaskWithDependencies() throws Exception {
        String toml = """
                name = "Create posts table"
                target = "db1"
                dependencies = ["db1/create_users_table"]

                [up]
                sql = "CREATE TABLE posts (...);"
                """;

        TaskConfig config = mapper.readValue(toml, TaskConfig.class);

        assertThat(config.dependencies()).hasSize(1);
        assertThat(config.dependencies()).containsExactly("db1/create_users_table");
    }

    @Test
    void shouldDefaultDescriptionToEmptyString() throws Exception {
        String toml = """
                name = "Task without description"
                target = "db1"

                [up]
                sql = "SELECT 1;"
                """;

        TaskConfig config = mapper.readValue(toml, TaskConfig.class);

        assertThat(config.description()).isEmpty();
    }

    @Test
    void shouldDefaultDependenciesToEmptyList() throws Exception {
        String toml = """
                name = "Task without dependencies"
                target = "db1"

                [up]
                sql = "SELECT 1;"
                """;

        TaskConfig config = mapper.readValue(toml, TaskConfig.class);

        assertThat(config.dependencies()).isEmpty();
    }

    @Test
    void shouldHandleTaskWithoutDownBlock() throws Exception {
        String toml = """
                name = "Task without down"
                target = "db1"

                [up]
                sql = "SELECT 1;"
                """;

        TaskConfig config = mapper.readValue(toml, TaskConfig.class);

        assertThat(config.downOptional()).isEmpty();
    }

    @Test
    void shouldRejectNullName() {
        var up = new TaskConfig.SqlBlock("SELECT 1;");
        assertThatThrownBy(() -> new TaskConfig(null, "", "db1", null, up, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name must not be null");
    }

    @Test
    void shouldRejectBlankName() {
        var up = new TaskConfig.SqlBlock("SELECT 1;");
        assertThatThrownBy(() -> new TaskConfig("", "", "db1", null, up, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    void shouldRejectNullTarget() {
        var up = new TaskConfig.SqlBlock("SELECT 1;");
        assertThatThrownBy(() -> new TaskConfig("Task", "", null, null, up, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("target must not be null");
    }

    @Test
    void shouldRejectBlankTarget() {
        var up = new TaskConfig.SqlBlock("SELECT 1;");
        assertThatThrownBy(() -> new TaskConfig("Task", "", "", null, up, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target must not be blank");
    }

    @Test
    void shouldRejectNullUpBlock() {
        assertThatThrownBy(() -> new TaskConfig("Task", "", "db1", null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("up must not be null");
    }

    @Test
    void shouldRejectNullSqlInSqlBlock() {
        assertThatThrownBy(() -> new TaskConfig.SqlBlock(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sql must not be null");
    }

    @Test
    void shouldRejectBlankSqlInSqlBlock() {
        assertThatThrownBy(() -> new TaskConfig.SqlBlock(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sql must not be blank");

        assertThatThrownBy(() -> new TaskConfig.SqlBlock("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sql must not be blank");
    }
}
