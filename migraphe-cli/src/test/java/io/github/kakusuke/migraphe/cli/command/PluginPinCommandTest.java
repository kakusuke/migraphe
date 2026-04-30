package io.github.kakusuke.migraphe.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.cli.resolver.LockFile;
import io.github.kakusuke.migraphe.cli.resolver.LockFileReader;
import io.github.kakusuke.migraphe.cli.resolver.MavenPluginResolver;
import io.github.kakusuke.migraphe.cli.resolver.RepositoryRegistry;
import io.github.kakusuke.migraphe.cli.resolver.Sha256Calculator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginPinCommandTest {

    @TempDir Path tempDir;

    @Test
    void writesLockfileWithSha256ForResolvedPlugin() throws IOException {
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
        Path jarPath = artifactDir.resolve("test-plugin-1.0.jar");
        Files.write(jarPath, new byte[] {0x50, 0x4B, 0x03, 0x04});

        Path projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);
        Files.writeString(
                projectDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test
                plugins:
                  - com.example:test-plugin:1.0
                """);

        var resolver = new MavenPluginResolver(repoDir, RepositoryRegistry.of(List.of()));
        var command = new PluginPinCommand(projectDir, resolver);

        int exit = command.execute();

        assertThat(exit).isEqualTo(0);
        Path lockPath = projectDir.resolve("migraphe.lock.yaml");
        assertThat(lockPath).exists();
        Optional<LockFile> read = new LockFileReader().read(lockPath);
        assertThat(read).isPresent();
        LockFile lock = read.get();
        assertThat(lock.version()).isEqualTo(1);
        assertThat(lock.plugins()).hasSize(1);
        assertThat(lock.plugins().get(0).coordinate().artifactId()).isEqualTo("test-plugin");
        assertThat(lock.plugins().get(0).repositoryId()).isEqualTo("maven-central");
        assertThat(lock.plugins().get(0).sha256()).isEqualTo(Sha256Calculator.hash(jarPath));
    }
}
