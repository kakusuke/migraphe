package io.github.migraphe.cli.config;

import static org.assertj.core.api.Assertions.*;

import io.github.migraphe.api.graph.NodeId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MultiFileYamlConfigSourceTest {

    @Test
    void shouldLoadProjectConfigWithoutPrefix(@TempDir Path tempDir) throws IOException {
        // Given: migraphe.yaml
        Path projectConfig = tempDir.resolve("migraphe.yaml");
        Files.writeString(
                projectConfig,
                """
                project:
                  name: my-migrations

                history:
                  target: history
                """);

        // When
        MultiFileYamlConfigSource configSource =
                new MultiFileYamlConfigSource(projectConfig, List.of(), Map.of());

        // Then: プレフィックスなしでロード
        assertThat(configSource.getValue("project.name")).isEqualTo("my-migrations");
        assertThat(configSource.getValue("history.target")).isEqualTo("history");
    }

    @Test
    void shouldLoadTargetConfigsWithPrefix(@TempDir Path tempDir) throws IOException {
        // Given: targets/db1.yaml
        Path targetsDir = tempDir.resolve("targets");
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

        Path projectConfig = tempDir.resolve("migraphe.yaml");
        Files.writeString(projectConfig, "project:\n  name: test\n");

        // When
        MultiFileYamlConfigSource configSource =
                new MultiFileYamlConfigSource(projectConfig, List.of(db1Config), Map.of());

        // Then: target.db1.* プレフィックスでロード
        assertThat(configSource.getValue("target.db1.type")).isEqualTo("postgresql");
        assertThat(configSource.getValue("target.db1.jdbc_url"))
                .isEqualTo("jdbc:postgresql://localhost:5432/mydb");
        assertThat(configSource.getValue("target.db1.username")).isEqualTo("dbuser");
        assertThat(configSource.getValue("target.db1.password")).isEqualTo("secret");
    }

    @Test
    void shouldLoadMultipleTargetConfigs(@TempDir Path tempDir) throws IOException {
        // Given: targets/db1.yaml と targets/db2.yaml
        Path targetsDir = tempDir.resolve("targets");
        Files.createDirectories(targetsDir);

        Path db1Config = targetsDir.resolve("db1.yaml");
        Files.writeString(db1Config, "type: postgresql\njdbc_url: jdbc:postgresql://db1\n");

        Path db2Config = targetsDir.resolve("db2.yaml");
        Files.writeString(db2Config, "type: postgresql\njdbc_url: jdbc:postgresql://db2\n");

        Path projectConfig = tempDir.resolve("migraphe.yaml");
        Files.writeString(projectConfig, "project:\n  name: test\n");

        // When
        MultiFileYamlConfigSource configSource =
                new MultiFileYamlConfigSource(
                        projectConfig, List.of(db1Config, db2Config), Map.of());

        // Then: 両方のターゲットがロードされる
        assertThat(configSource.getValue("target.db1.jdbc_url")).isEqualTo("jdbc:postgresql://db1");
        assertThat(configSource.getValue("target.db2.jdbc_url")).isEqualTo("jdbc:postgresql://db2");
    }

    @Test
    void shouldLoadTaskConfigsWithPrefix(@TempDir Path tempDir) throws IOException {
        // Given: tasks/db1/create_users.yaml
        Path tasksDir = tempDir.resolve("tasks/db1");
        Files.createDirectories(tasksDir);

        Path taskConfig = tasksDir.resolve("create_users.yaml");
        Files.writeString(
                taskConfig,
                """
                name: Create users table
                target: db1
                up:
                  sql: CREATE TABLE users;
                """);

        NodeId taskId = NodeId.of("db1/create_users");
        Path projectConfig = tempDir.resolve("migraphe.yaml");
        Files.writeString(projectConfig, "project:\n  name: test\n");

        // When
        MultiFileYamlConfigSource configSource =
                new MultiFileYamlConfigSource(projectConfig, List.of(), Map.of(taskId, taskConfig));

        // Then: task.{taskId}.* プレフィックスでロード
        assertThat(configSource.getValue("task.\"db1/create_users\".name"))
                .isEqualTo("Create users table");
        assertThat(configSource.getValue("task.\"db1/create_users\".target")).isEqualTo("db1");
        assertThat(configSource.getValue("task.\"db1/create_users\".up.sql"))
                .isEqualTo("CREATE TABLE users;");
    }

    @Test
    void shouldReturnNullForNonExistentProperty(@TempDir Path tempDir) throws IOException {
        // Given
        Path projectConfig = tempDir.resolve("migraphe.yaml");
        Files.writeString(projectConfig, "project:\n  name: test\n");

        // When
        MultiFileYamlConfigSource configSource =
                new MultiFileYamlConfigSource(projectConfig, List.of(), Map.of());

        // Then
        assertThat(configSource.getValue("non.existent.property")).isNull();
    }

    @Test
    void shouldReturnCorrectSourceName(@TempDir Path tempDir) throws IOException {
        // Given
        Path projectConfig = tempDir.resolve("migraphe.yaml");
        Files.writeString(projectConfig, "project:\n  name: test\n");

        // When
        MultiFileYamlConfigSource configSource =
                new MultiFileYamlConfigSource(projectConfig, List.of(), Map.of());

        // Then
        assertThat(configSource.getName()).isEqualTo("MultiFileYamlConfigSource");
    }

    @Test
    void shouldReturnCorrectOrdinal(@TempDir Path tempDir) throws IOException {
        // Given
        Path projectConfig = tempDir.resolve("migraphe.yaml");
        Files.writeString(projectConfig, "project:\n  name: test\n");

        // When
        MultiFileYamlConfigSource configSource =
                new MultiFileYamlConfigSource(projectConfig, List.of(), Map.of());

        // Then: デフォルト ordinal は 100
        assertThat(configSource.getOrdinal()).isEqualTo(100);
    }

    @Test
    void shouldHandleSlashInTaskId(@TempDir Path tempDir) throws IOException {
        // Given: Task ID にスラッシュが含まれる場合
        Path tasksDir = tempDir.resolve("tasks/db1/subfolder");
        Files.createDirectories(tasksDir);

        Path taskConfig = tasksDir.resolve("add_index.yaml");
        Files.writeString(taskConfig, "name: Add index\ntarget: db1\n");

        NodeId taskId = NodeId.of("db1/subfolder/add_index");
        Path projectConfig = tempDir.resolve("migraphe.yaml");
        Files.writeString(projectConfig, "project:\n  name: test\n");

        // When
        MultiFileYamlConfigSource configSource =
                new MultiFileYamlConfigSource(projectConfig, List.of(), Map.of(taskId, taskConfig));

        // Then: スラッシュを含むキーも正しくロードできる
        assertThat(configSource.getValue("task.\"db1/subfolder/add_index\".name"))
                .isEqualTo("Add index");
    }
}
