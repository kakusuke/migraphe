package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenPluginResolverTest {

    @TempDir Path tempDir;

    @Test
    void shouldResolveArtifactFromLocalRepo() throws IOException {
        // テスト用ローカルリポジトリに最小限のアーティファクトを配置
        Path repoDir = tempDir.resolve("repo");
        Path artifactDir = repoDir.resolve("com/example/test-plugin/1.0");
        Files.createDirectories(artifactDir);

        // 最小 POM（依存なし）
        Files.writeString(
                artifactDir.resolve("test-plugin-1.0.pom"),
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>test-plugin</artifactId>
                  <version>1.0</version>
                </project>
                """);

        // ダミー JAR
        Files.write(
                artifactDir.resolve("test-plugin-1.0.jar"), new byte[] {0x50, 0x4B, 0x03, 0x04});

        var resolver = new MavenPluginResolver(repoDir, RepositoryRegistry.of(List.of()));
        PluginDeclaration plugin = PluginDeclaration.fromString("com.example:test-plugin:1.0");

        List<ResolvedArtifact> result = resolver.resolve(List.of(plugin));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).jarPath().getFileName().toString())
                .isEqualTo("test-plugin-1.0.jar");
    }

    @Test
    void shouldThrowForMissingArtifact() {
        Path repoDir = tempDir.resolve("empty-repo");
        var resolver = new MavenPluginResolver(repoDir, RepositoryRegistry.of(List.of()));
        PluginDeclaration plugin = PluginDeclaration.fromString("com.example:nonexistent:1.0");

        assertThatThrownBy(() -> resolver.resolve(List.of(plugin)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resolveDeclarationsReturnsResolvedArtifactsTaggedWithCoordinate() throws IOException {
        Path repoDir = tempDir.resolve("repo");
        Path artifactDir = repoDir.resolve("com/example/test-plugin/1.0");
        Files.createDirectories(artifactDir);
        Files.writeString(
                artifactDir.resolve("test-plugin-1.0.pom"),
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>test-plugin</artifactId>
                  <version>1.0</version>
                </project>
                """);
        Files.write(
                artifactDir.resolve("test-plugin-1.0.jar"), new byte[] {0x50, 0x4B, 0x03, 0x04});

        var resolver = new MavenPluginResolver(repoDir, RepositoryRegistry.defaults());
        PluginDeclaration plugin = PluginDeclaration.fromString("com.example:test-plugin:1.0");

        List<ResolvedArtifact> result = resolver.resolve(List.of(plugin));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).coordinate().artifactId()).isEqualTo("test-plugin");
        assertThat(result.get(0).jarPath().getFileName().toString())
                .isEqualTo("test-plugin-1.0.jar");
    }

    @Test
    void resolveDeclarationsRejectsUnknownRepositoryRef() {
        Path repoDir = tempDir.resolve("repo");
        var resolver = new MavenPluginResolver(repoDir, RepositoryRegistry.defaults());
        PluginDeclaration plugin =
                PluginDeclaration.fromMap(
                        java.util.Map.of(
                                "coordinate",
                                "com.example:test-plugin:1.0",
                                "repository",
                                "jitpack"));

        assertThatThrownBy(() -> resolver.resolve(List.of(plugin)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jitpack");
    }

    @Test
    void shouldResolveAllDeduplicatesResults() throws IOException {
        Path repoDir = tempDir.resolve("repo");
        Path artifactDirA = repoDir.resolve("com/example/plugin-a/1.0");
        Path artifactDirB = repoDir.resolve("com/example/plugin-b/1.0");
        Files.createDirectories(artifactDirA);
        Files.createDirectories(artifactDirB);

        Files.writeString(
                artifactDirA.resolve("plugin-a-1.0.pom"),
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>plugin-a</artifactId>
                  <version>1.0</version>
                </project>
                """);
        Files.write(artifactDirA.resolve("plugin-a-1.0.jar"), new byte[] {0x50, 0x4B, 0x03, 0x04});

        Files.writeString(
                artifactDirB.resolve("plugin-b-1.0.pom"),
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>plugin-b</artifactId>
                  <version>1.0</version>
                </project>
                """);
        Files.write(artifactDirB.resolve("plugin-b-1.0.jar"), new byte[] {0x50, 0x4B, 0x03, 0x04});

        var resolver = new MavenPluginResolver(repoDir, RepositoryRegistry.of(List.of()));
        var plugins =
                List.of(
                        PluginDeclaration.fromString("com.example:plugin-a:1.0"),
                        PluginDeclaration.fromString("com.example:plugin-b:1.0"));

        List<ResolvedArtifact> result = resolver.resolve(plugins);

        assertThat(result).hasSize(2);
    }
}
