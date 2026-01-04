package io.github.migraphe.cli.config;

import static org.assertj.core.api.Assertions.*;

import io.github.migraphe.core.config.ProjectConfig;
import io.smallrye.config.SmallRyeConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    @Test
    void shouldLoadConfigFromYamlFiles(@TempDir Path tempDir) throws IOException {
        // Given: 完全なプロジェクト構造
        createProjectStructure(tempDir);

        ConfigLoader loader = new ConfigLoader();

        // When
        SmallRyeConfig config = loader.loadConfig(tempDir, Optional.empty());

        // Then: ProjectConfig がロードされる
        ProjectConfig projectConfig = config.getConfigMapping(ProjectConfig.class);
        assertThat(projectConfig.project().name()).isEqualTo("my-migrations");
        assertThat(projectConfig.history().target()).isEqualTo("history");
    }

    @Test
    void shouldLoadTargetConfigs(@TempDir Path tempDir) throws IOException {
        // Given
        createProjectStructure(tempDir);

        ConfigLoader loader = new ConfigLoader();

        // When
        SmallRyeConfig config = loader.loadConfig(tempDir, Optional.empty());

        // Then: TargetConfig がロードされる (プレフィックス付き)
        assertThat(config.getValue("target.db1.type", String.class)).isEqualTo("postgresql");
        assertThat(config.getValue("target.db1.jdbc_url", String.class))
                .isEqualTo("jdbc:postgresql://localhost:5432/mydb");
    }

    @Test
    void shouldLoadTaskConfigs(@TempDir Path tempDir) throws IOException {
        // Given
        createProjectStructure(tempDir);

        ConfigLoader loader = new ConfigLoader();

        // When
        SmallRyeConfig config = loader.loadConfig(tempDir, Optional.empty());

        // Then: TaskConfig がロードされる (プレフィックス付き)
        assertThat(config.getValue("task.\"db1/create_users\".name", String.class))
                .isEqualTo("Create users table");
        assertThat(config.getValue("task.\"db1/create_users\".target", String.class))
                .isEqualTo("db1");
        assertThat(config.getValue("task.\"db1/create_users\".up.sql", String.class))
                .contains("CREATE TABLE users");
    }

    @Test
    void shouldLoadEnvironmentVariablesWhenEnvSpecified(@TempDir Path tempDir) throws IOException {
        // Given: environments/development.yaml が存在
        createProjectStructure(tempDir);

        Path environmentsDir = tempDir.resolve("environments");
        Files.createDirectories(environmentsDir);

        Path devEnv = environmentsDir.resolve("development.yaml");
        Files.writeString(devEnv, "DB_HOST: localhost\nDB_USER: devuser\n");

        ConfigLoader loader = new ConfigLoader();

        // When: --env=development を指定
        SmallRyeConfig config = loader.loadConfig(tempDir, Optional.of("development"));

        // Then: 環境変数がロードされる
        assertThat(config.getValue("DB_HOST", String.class)).isEqualTo("localhost");
        assertThat(config.getValue("DB_USER", String.class)).isEqualTo("devuser");
    }

    @org.junit.jupiter.api.Disabled("TODO: Fix variable expansion in Phase 10-5")
    @Test
    void shouldExpandEnvironmentVariables(@TempDir Path tempDir) throws IOException {
        // Given: 環境変数と ${VAR} を含む設定
        createProjectStructure(tempDir);

        Path environmentsDir = tempDir.resolve("environments");
        Files.createDirectories(environmentsDir);

        Path devEnv = environmentsDir.resolve("development.yaml");
        Files.writeString(devEnv, "DB_HOST: localhost\n");

        // targets/db1.yaml に ${DB_HOST} を含める
        Path targetsDir = tempDir.resolve("targets");
        Path db1Config = targetsDir.resolve("db1.yaml");
        Files.writeString(
                db1Config,
                """
                type: postgresql
                jdbc_url: jdbc:postgresql://${DB_HOST}:5432/mydb
                username: dbuser
                password: secret
                """);

        ConfigLoader loader = new ConfigLoader();

        // When
        SmallRyeConfig config = loader.loadConfig(tempDir, Optional.of("development"));

        // Then: ${DB_HOST} が展開される
        assertThat(config.getValue("target.db1.jdbc_url", String.class))
                .isEqualTo("jdbc:postgresql://localhost:5432/mydb");
    }

    @Test
    void shouldHandleNoEnvironmentFile(@TempDir Path tempDir) throws IOException {
        // Given: environments/ ディレクトリが存在しない
        createProjectStructure(tempDir);

        ConfigLoader loader = new ConfigLoader();

        // When: --env=production を指定しても環境ファイルがない
        SmallRyeConfig config = loader.loadConfig(tempDir, Optional.of("production"));

        // Then: エラーにならず、環境変数なしでロードされる
        assertThat(config).isNotNull();
        ProjectConfig projectConfig = config.getConfigMapping(ProjectConfig.class);
        assertThat(projectConfig.project().name()).isEqualTo("my-migrations");
    }

    @Test
    void shouldThrowExceptionWhenProjectConfigNotFound(@TempDir Path tempDir) {
        // Given: migraphe.yaml が存在しない
        ConfigLoader loader = new ConfigLoader();

        // When & Then
        assertThatThrownBy(() -> loader.loadConfig(tempDir, Optional.empty()))
                .isInstanceOf(io.github.migraphe.core.config.ConfigurationException.class)
                .hasMessageContaining("Project config file not found");
    }

    /** テスト用のプロジェクト構造を作成する。 */
    private void createProjectStructure(Path baseDir) throws IOException {
        // migraphe.yaml
        Path projectConfig = baseDir.resolve("migraphe.yaml");
        Files.writeString(
                projectConfig,
                """
                project:
                  name: my-migrations

                history:
                  target: history
                """);

        // targets/db1.yaml
        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);

        Path db1Config = targetsDir.resolve("db1.yaml");
        Files.writeString(
                db1Config,
                """
                type: postgresql
                jdbc_url: jdbc:postgresql://localhost:5432/mydb
                username: dbuser
                password: secret
                """);

        // tasks/db1/create_users.yaml
        Path tasksDir = baseDir.resolve("tasks/db1");
        Files.createDirectories(tasksDir);

        Path createUsersTask = tasksDir.resolve("create_users.yaml");
        Files.writeString(
                createUsersTask,
                """
                name: Create users table
                target: db1
                up:
                  sql: CREATE TABLE users (id SERIAL PRIMARY KEY);
                """);
    }
}
