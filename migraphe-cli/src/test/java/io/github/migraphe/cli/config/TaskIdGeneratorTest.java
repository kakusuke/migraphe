package io.github.migraphe.cli.config;

import static org.assertj.core.api.Assertions.*;

import io.github.migraphe.api.graph.NodeId;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class TaskIdGeneratorTest {

    @Test
    void shouldGenerateTaskIdFromPath() {
        // Given
        Path baseDir = Paths.get("/project");
        Path taskFile = Paths.get("/project/tasks/db1/create_users.yaml");

        TaskIdGenerator generator = new TaskIdGenerator();

        // When
        NodeId taskId = generator.generateTaskId(baseDir, taskFile);

        // Then
        assertThat(taskId.value()).isEqualTo("db1/create_users");
    }

    @Test
    void shouldHandleNestedDirectories() {
        // Given
        Path baseDir = Paths.get("/project");
        Path taskFile = Paths.get("/project/tasks/db1/subfolder/add_index.yaml");

        TaskIdGenerator generator = new TaskIdGenerator();

        // When
        NodeId taskId = generator.generateTaskId(baseDir, taskFile);

        // Then
        assertThat(taskId.value()).isEqualTo("db1/subfolder/add_index");
    }

    @Test
    void shouldHandleSingleLevelPath() {
        // Given
        Path baseDir = Paths.get("/project");
        Path taskFile = Paths.get("/project/tasks/init.yaml");

        TaskIdGenerator generator = new TaskIdGenerator();

        // When
        NodeId taskId = generator.generateTaskId(baseDir, taskFile);

        // Then
        assertThat(taskId.value()).isEqualTo("init");
    }

    @Test
    void shouldRemoveYamlExtension() {
        // Given
        Path baseDir = Paths.get("/project");
        Path taskFile = Paths.get("/project/tasks/db1/create_table.yaml");

        TaskIdGenerator generator = new TaskIdGenerator();

        // When
        NodeId taskId = generator.generateTaskId(baseDir, taskFile);

        // Then
        assertThat(taskId.value()).isEqualTo("db1/create_table");
        assertThat(taskId.value()).doesNotContain(".yaml");
    }

    @Test
    void shouldHandleRelativePaths() {
        // Given: 相対パスでも動作する
        Path baseDir = Paths.get(".");
        Path taskFile = Paths.get("./tasks/db1/create_users.yaml");

        TaskIdGenerator generator = new TaskIdGenerator();

        // When
        NodeId taskId = generator.generateTaskId(baseDir, taskFile);

        // Then
        assertThat(taskId.value()).isEqualTo("db1/create_users");
    }

    @Test
    void shouldThrowExceptionWhenFileNotUnderTasksDirectory() {
        // Given: tasks/ 配下ではないファイル
        Path baseDir = Paths.get("/project");
        Path taskFile = Paths.get("/project/targets/db1.yaml");

        TaskIdGenerator generator = new TaskIdGenerator();

        // When & Then
        assertThatThrownBy(() -> generator.generateTaskId(baseDir, taskFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Task file must be under tasks/ directory");
    }

    @Test
    void shouldExtractTargetIdFromFileName() {
        // Given
        Path targetsDir = Paths.get("/project/targets");
        Path targetFile = Paths.get("/project/targets/db1.yaml");

        TaskIdGenerator generator = new TaskIdGenerator();

        // When
        String targetId = generator.extractTargetId(targetsDir, targetFile);

        // Then
        assertThat(targetId).isEqualTo("db1");
    }

    @Test
    void shouldExtractTargetIdWithoutExtension() {
        // Given
        Path targetsDir = Paths.get("/project/targets");
        Path targetFile = Paths.get("/project/targets/history.yaml");

        TaskIdGenerator generator = new TaskIdGenerator();

        // When
        String targetId = generator.extractTargetId(targetsDir, targetFile);

        // Then
        assertThat(targetId).isEqualTo("history");
        assertThat(targetId).doesNotContain(".yaml");
    }
}
