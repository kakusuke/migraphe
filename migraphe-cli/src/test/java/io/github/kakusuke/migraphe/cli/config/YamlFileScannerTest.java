package io.github.kakusuke.migraphe.cli.config;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlFileScannerTest {

    @Test
    void shouldFindProjectConfig(@TempDir Path tempDir) throws IOException {
        // Given: プロジェクトルートに migraphe.yaml が存在
        Path projectConfig = tempDir.resolve("migraphe.yaml");
        Files.writeString(projectConfig, "project:\n  name: test\n");

        YamlFileScanner scanner = new YamlFileScanner();

        // When
        Path found = scanner.findProjectConfig(tempDir);

        // Then
        assertThat(found).isNotNull();
        assertThat(found).isEqualTo(projectConfig);
    }

    @Test
    void shouldReturnNullWhenProjectConfigNotFound(@TempDir Path tempDir) {
        // Given: migraphe.yaml が存在しない
        YamlFileScanner scanner = new YamlFileScanner();

        // When
        Path found = scanner.findProjectConfig(tempDir);

        // Then
        assertThat(found).isNull();
    }

    @Test
    void shouldScanTargetFiles(@TempDir Path tempDir) throws IOException {
        // Given: targets/ ディレクトリに複数の .yaml ファイルが存在
        Path targetsDir = tempDir.resolve("targets");
        Files.createDirectories(targetsDir);

        Path db1 = targetsDir.resolve("db1.yaml");
        Path db2 = targetsDir.resolve("db2.yaml");
        Path history = targetsDir.resolve("history.yaml");

        Files.writeString(db1, "type: postgresql\n");
        Files.writeString(db2, "type: postgresql\n");
        Files.writeString(history, "type: postgresql\n");

        YamlFileScanner scanner = new YamlFileScanner();

        // When
        List<Path> found = scanner.scanTargetFiles(tempDir);

        // Then
        assertThat(found).hasSize(3);
        assertThat(found).containsExactlyInAnyOrder(db1, db2, history);
    }

    @Test
    void shouldReturnEmptyListWhenTargetsDirectoryNotExists(@TempDir Path tempDir) {
        // Given: targets/ ディレクトリが存在しない
        YamlFileScanner scanner = new YamlFileScanner();

        // When
        List<Path> found = scanner.scanTargetFiles(tempDir);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldScanTaskFilesRecursively(@TempDir Path tempDir) throws IOException {
        // Given: tasks/ ディレクトリに再帰的に .yaml ファイルが存在
        Path tasksDir = tempDir.resolve("tasks");
        Files.createDirectories(tasksDir);

        Path db1Dir = tasksDir.resolve("db1");
        Files.createDirectories(db1Dir);

        Path createUsers = db1Dir.resolve("create_users.yaml");
        Path createPosts = db1Dir.resolve("create_posts.yaml");

        Path subDir = db1Dir.resolve("subfolder");
        Files.createDirectories(subDir);
        Path addIndex = subDir.resolve("add_index.yaml");

        Files.writeString(createUsers, "name: Create users\n");
        Files.writeString(createPosts, "name: Create posts\n");
        Files.writeString(addIndex, "name: Add index\n");

        YamlFileScanner scanner = new YamlFileScanner();

        // When
        List<Path> found = scanner.scanTaskFiles(tempDir);

        // Then
        assertThat(found).hasSize(3);
        assertThat(found).containsExactlyInAnyOrder(createUsers, createPosts, addIndex);
    }

    @Test
    void shouldReturnEmptyListWhenTasksDirectoryNotExists(@TempDir Path tempDir) {
        // Given: tasks/ ディレクトリが存在しない
        YamlFileScanner scanner = new YamlFileScanner();

        // When
        List<Path> found = scanner.scanTaskFiles(tempDir);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindEnvironmentFile(@TempDir Path tempDir) throws IOException {
        // Given: environments/ ディレクトリに development.yaml が存在
        Path environmentsDir = tempDir.resolve("environments");
        Files.createDirectories(environmentsDir);

        Path devEnv = environmentsDir.resolve("development.yaml");
        Files.writeString(devEnv, "DB_HOST: localhost\n");

        YamlFileScanner scanner = new YamlFileScanner();

        // When
        Path found = scanner.findEnvironmentFile(tempDir, "development");

        // Then
        assertThat(found).isNotNull();
        assertThat(found).isEqualTo(devEnv);
    }

    @Test
    void shouldReturnNullWhenEnvironmentFileNotFound(@TempDir Path tempDir) throws IOException {
        // Given: environments/ ディレクトリは存在するが、指定された環境ファイルがない
        Path environmentsDir = tempDir.resolve("environments");
        Files.createDirectories(environmentsDir);

        YamlFileScanner scanner = new YamlFileScanner();

        // When
        Path found = scanner.findEnvironmentFile(tempDir, "production");

        // Then
        assertThat(found).isNull();
    }

    @Test
    void shouldIgnoreNonYamlFiles(@TempDir Path tempDir) throws IOException {
        // Given: targets/ ディレクトリに .yaml 以外のファイルも存在
        Path targetsDir = tempDir.resolve("targets");
        Files.createDirectories(targetsDir);

        Path yamlFile = targetsDir.resolve("db1.yaml");
        Path txtFile = targetsDir.resolve("readme.txt");
        Path noExtFile = targetsDir.resolve("config");

        Files.writeString(yamlFile, "type: postgresql\n");
        Files.writeString(txtFile, "This is a readme\n");
        Files.writeString(noExtFile, "Some config\n");

        YamlFileScanner scanner = new YamlFileScanner();

        // When
        List<Path> found = scanner.scanTargetFiles(tempDir);

        // Then: .yaml ファイルのみ収集される
        assertThat(found).hasSize(1);
        assertThat(found).containsExactly(yamlFile);
    }
}
