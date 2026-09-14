package io.github.kakusuke.migraphe.core.config;

import static org.assertj.core.api.Assertions.*;

import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("NullAway.Init")
class ConfigValidatorTest {

    @TempDir Path tempDir;

    private PluginRegistry pluginRegistry;
    private ConfigValidator validator;

    @BeforeEach
    void setUp() {
        pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();
        validator = new ConfigValidator(pluginRegistry);
    }

    @Test
    void shouldReturnValidForCorrectConfiguration() throws IOException {
        // Given: 正しい設定ファイル
        createValidProject(tempDir);

        // When
        ConfigValidator.ValidationOutput result = validator.validate(tempDir);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void shouldDetectMissingProjectConfig() throws IOException {
        // Given: migraphe.yaml が存在しない
        Files.createDirectories(tempDir.resolve("targets"));
        Files.createDirectories(tempDir.resolve("tasks"));

        // When
        ConfigValidator.ValidationOutput result = validator.validate(tempDir);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("migraphe.yaml"));
    }

    @Test
    void shouldDetectMissingTargetType() throws IOException {
        // Given: target の type が欠落
        createProjectWithMissingTargetType(tempDir);

        // When
        ConfigValidator.ValidationOutput result = validator.validate(tempDir);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("type"));
    }

    @Test
    void shouldDetectATaskThatNeitherRollsBackNorSaysWhyNot() throws IOException {
        // Given: down: も no_way_back: も無いタスク
        createValidProject(tempDir);
        Files.writeString(
                tempDir.resolve("tasks").resolve("test-db").resolve("002_drop_legacy.yaml"),
                """
                name: Drop legacy column
                target: test-db
                up: ALTER TABLE users DROP COLUMN legacy;
                """);

        // When
        ConfigValidator.ValidationOutput result = validator.validate(tempDir);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("no_way_back"));
    }

    @Test
    void shouldDetectMissingTaskName() throws IOException {
        // Given: task の name が欠落
        createProjectWithMissingTaskName(tempDir);

        // When
        ConfigValidator.ValidationOutput result = validator.validate(tempDir);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("name"));
    }

    @Test
    void shouldDetectNonExistentTarget() throws IOException {
        // Given: task が存在しない target を参照
        createProjectWithNonExistentTarget(tempDir);

        // When
        ConfigValidator.ValidationOutput result = validator.validate(tempDir);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors())
                .anyMatch(e -> e.contains("nonexistent") && e.contains("Target"));
    }

    @Test
    void shouldDetectNonExistentDependency() throws IOException {
        // Given: task が存在しない dependency を参照
        createProjectWithNonExistentDependency(tempDir);

        // When
        ConfigValidator.ValidationOutput result = validator.validate(tempDir);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors())
                .anyMatch(e -> e.contains("missing-task") && e.contains("Dependency"));
    }

    @Test
    void shouldDetectCircularDependency() throws IOException {
        // Given: 循環依存
        createProjectWithCircularDependency(tempDir);

        // When
        ConfigValidator.ValidationOutput result = validator.validate(tempDir);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.toLowerCase(Locale.ROOT).contains("circular"));
    }

    @Test
    void shouldAccumulateMultipleErrors() throws IOException {
        // Given: 複数のエラー
        createProjectWithMultipleErrors(tempDir);

        // When
        ConfigValidator.ValidationOutput result = validator.validate(tempDir);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldValidateUsingScanRootForTargetsAndTasks() throws IOException {
        Files.writeString(
                tempDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test
                  scan-root: subdir
                history:
                  target: db1
                """);
        Path subTargets = tempDir.resolve("subdir/targets");
        Files.createDirectories(subTargets);
        Files.writeString(subTargets.resolve("db1.yaml"), "type: noop\n");

        Path subTasks = tempDir.resolve("subdir/tasks/db1");
        Files.createDirectories(subTasks);
        Files.writeString(
                subTasks.resolve("create.yaml"),
                """
                name: Create
                target: db1
                up: "Create table"
                down: "Drop table"
                """);

        ConfigValidator.ValidationOutput result = validator.validate(tempDir);

        assertThat(result.errors()).isEmpty();
        assertThat(result.isValid()).isTrue();
    }

    // Helper methods to create test projects

    @Test
    void shouldReportMissingEnvironmentOverlayAsValidationError() throws IOException {
        // Given: environments/ が無い正しいプロジェクト
        createValidProject(tempDir);

        // When: 存在しないオーバーレイ名で検証（例外ではなく検証エラーとして報告する）
        ConfigValidator.ValidationOutput result = validator.validate(tempDir, "prodction");

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("prodction"));
    }

    @Test
    void shouldValidateTargetTypeIntroducedByEnvironmentOverlay() throws IOException {
        // Given: ベースは正しいが、オーバーレイが未知のプラグイン型に差し替える
        createValidProject(tempDir);
        Path environmentsDir = tempDir.resolve("environments");
        Files.createDirectories(environmentsDir);
        Files.writeString(
                environmentsDir.resolve("production.yaml"),
                """
                target:
                  test-db:
                    type: not-a-registered-plugin
                """);

        // 対照群: --env なしならベースの type: postgresql で妥当
        assertThat(validator.validate(tempDir).isValid()).isTrue();

        // When: オーバーレイ適用時は実際に使われる型を検証する
        ConfigValidator.ValidationOutput result = validator.validate(tempDir, "production");

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("not-a-registered-plugin"));
    }

    @Test
    void shouldAcceptTargetTypeFixedByEnvironmentOverlay() throws IOException {
        // Given: ベースの type は未登録だが、オーバーレイが正しい型へ差し替える
        createValidProject(tempDir);
        Files.writeString(
                tempDir.resolve("targets").resolve("test-db.yaml"),
                """
                type: not-a-registered-plugin
                jdbc_url: jdbc:postgresql://localhost:5432/test
                """);
        Path environmentsDir = tempDir.resolve("environments");
        Files.createDirectories(environmentsDir);
        Files.writeString(
                environmentsDir.resolve("production.yaml"),
                """
                target:
                  test-db:
                    type: postgresql
                """);

        // When
        ConfigValidator.ValidationOutput result = validator.validate(tempDir, "production");

        // Then: オーバーレイが勝つので妥当（ファイル単体の検証で終わっていない証拠）
        assertThat(result.errors()).noneMatch(e -> e.contains("not-a-registered-plugin"));
    }

    private void createValidProject(Path baseDir) throws IOException {
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                """;
        Files.writeString(baseDir.resolve("migraphe.yaml"), projectYaml);

        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);
        String targetYaml =
                """
                type: postgresql
                jdbc_url: jdbc:postgresql://localhost:5432/test
                username: test
                password: test
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);

        Path tasksDir = baseDir.resolve("tasks").resolve("test-db");
        Files.createDirectories(tasksDir);
        String taskYaml =
                """
                name: Create users table
                target: test-db
                up: CREATE TABLE users (id SERIAL PRIMARY KEY);
                down: DROP TABLE users;
                """;
        Files.writeString(tasksDir.resolve("001_create_users.yaml"), taskYaml);
    }

    private void createProjectWithMissingTargetType(Path baseDir) throws IOException {
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                """;
        Files.writeString(baseDir.resolve("migraphe.yaml"), projectYaml);

        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);
        // type が欠落
        String targetYaml =
                """
                jdbc_url: jdbc:postgresql://localhost:5432/test
                username: test
                password: test
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);
    }

    private void createProjectWithMissingTaskName(Path baseDir) throws IOException {
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                """;
        Files.writeString(baseDir.resolve("migraphe.yaml"), projectYaml);

        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);
        String targetYaml =
                """
                type: postgresql
                jdbc_url: jdbc:postgresql://localhost:5432/test
                username: test
                password: test
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);

        Path tasksDir = baseDir.resolve("tasks").resolve("test-db");
        Files.createDirectories(tasksDir);
        // name が欠落
        String taskYaml =
                """
                target: test-db
                up: CREATE TABLE users (id SERIAL PRIMARY KEY);
                down: DROP TABLE users;
                """;
        Files.writeString(tasksDir.resolve("001_create_users.yaml"), taskYaml);
    }

    private void createProjectWithNonExistentTarget(Path baseDir) throws IOException {
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                """;
        Files.writeString(baseDir.resolve("migraphe.yaml"), projectYaml);

        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);
        String targetYaml =
                """
                type: postgresql
                jdbc_url: jdbc:postgresql://localhost:5432/test
                username: test
                password: test
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);

        Path tasksDir = baseDir.resolve("tasks").resolve("test-db");
        Files.createDirectories(tasksDir);
        // 存在しない target を参照
        String taskYaml =
                """
                name: Create users table
                target: nonexistent
                up: CREATE TABLE users (id SERIAL PRIMARY KEY);
                down: DROP TABLE users;
                """;
        Files.writeString(tasksDir.resolve("001_create_users.yaml"), taskYaml);
    }

    private void createProjectWithNonExistentDependency(Path baseDir) throws IOException {
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                """;
        Files.writeString(baseDir.resolve("migraphe.yaml"), projectYaml);

        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);
        String targetYaml =
                """
                type: postgresql
                jdbc_url: jdbc:postgresql://localhost:5432/test
                username: test
                password: test
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);

        Path tasksDir = baseDir.resolve("tasks").resolve("test-db");
        Files.createDirectories(tasksDir);
        // 存在しない dependency を参照
        String taskYaml =
                """
                name: Create users table
                target: test-db
                dependencies:
                  - missing-task
                up: CREATE TABLE users (id SERIAL PRIMARY KEY);
                down: DROP TABLE users;
                """;
        Files.writeString(tasksDir.resolve("001_create_users.yaml"), taskYaml);
    }

    private void createProjectWithCircularDependency(Path baseDir) throws IOException {
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                """;
        Files.writeString(baseDir.resolve("migraphe.yaml"), projectYaml);

        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);
        String targetYaml =
                """
                type: postgresql
                jdbc_url: jdbc:postgresql://localhost:5432/test
                username: test
                password: test
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);

        Path tasksDir = baseDir.resolve("tasks").resolve("test-db");
        Files.createDirectories(tasksDir);

        // a -> b
        String taskAYaml =
                """
                name: Task A
                target: test-db
                dependencies:
                  - test-db/b
                up: SELECT 1;
                down: SELECT 1;
                """;
        Files.writeString(tasksDir.resolve("a.yaml"), taskAYaml);

        // b -> a (循環)
        String taskBYaml =
                """
                name: Task B
                target: test-db
                dependencies:
                  - test-db/a
                up: SELECT 1;
                down: SELECT 1;
                """;
        Files.writeString(tasksDir.resolve("b.yaml"), taskBYaml);
    }

    private void createProjectWithMultipleErrors(Path baseDir) throws IOException {
        String projectYaml =
                """
                project:
                  name: test-project
                history:
                  target: test-db
                """;
        Files.writeString(baseDir.resolve("migraphe.yaml"), projectYaml);

        Path targetsDir = baseDir.resolve("targets");
        Files.createDirectories(targetsDir);
        String targetYaml =
                """
                type: postgresql
                jdbc_url: jdbc:postgresql://localhost:5432/test
                username: test
                password: test
                """;
        Files.writeString(targetsDir.resolve("test-db.yaml"), targetYaml);

        Path tasksDir = baseDir.resolve("tasks").resolve("test-db");
        Files.createDirectories(tasksDir);

        // エラー1: 存在しない dependency
        String task1Yaml =
                """
                name: Task 1
                target: test-db
                dependencies:
                  - missing-dep
                up: SELECT 1;
                down: SELECT 1;
                """;
        Files.writeString(tasksDir.resolve("task1.yaml"), task1Yaml);

        // エラー2: 存在しない target
        String task2Yaml =
                """
                name: Task 2
                target: nonexistent-target
                up: SELECT 1;
                down: SELECT 1;
                """;
        Files.writeString(tasksDir.resolve("task2.yaml"), task2Yaml);
    }
}
