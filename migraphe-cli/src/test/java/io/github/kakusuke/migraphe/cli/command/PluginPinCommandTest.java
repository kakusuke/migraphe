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

    private Path setupRepo() throws IOException {
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
        return repoDir;
    }

    private Path setupProjectYaml() throws IOException {
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
        return projectDir;
    }

    @Test
    void writesLockfileWithSha256ForResolvedPlugin() throws IOException {
        Path repoDir = setupRepo();
        Path projectDir = setupProjectYaml();
        Path jarPath = repoDir.resolve("com/example/test-plugin/1.0/test-plugin-1.0.jar");

        var resolver = new MavenPluginResolver(repoDir, RepositoryRegistry.of(List.of()));
        var command = new PluginPinCommand(projectDir, resolver, false);

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
        assertThat(lock.plugins().get(0).sha256()).isEqualTo(Sha256Calculator.hash(jarPath));
    }

    @Test
    void checkModeFailsWhenLockfileMissing() throws IOException {
        Path repoDir = setupRepo();
        Path projectDir = setupProjectYaml();

        var resolver = new MavenPluginResolver(repoDir, RepositoryRegistry.of(List.of()));
        var command = new PluginPinCommand(projectDir, resolver, true);

        int exit = command.execute();

        assertThat(exit).isNotZero();
        assertThat(projectDir.resolve("migraphe.lock.yaml")).doesNotExist();
    }

    @Test
    void checkModeFailsWhenLockfileDiffersFromCurrentResolution() throws IOException {
        Path repoDir = setupRepo();
        Path projectDir = setupProjectYaml();
        Files.writeString(
                projectDir.resolve("migraphe.lock.yaml"),
                """
                lockfile-version: 1
                plugins:
                  - coordinate: com.example:test-plugin:1.0
                    sha256: "0000000000000000000000000000000000000000000000000000000000000000"
                    dependencies: []
                """);

        var resolver = new MavenPluginResolver(repoDir, RepositoryRegistry.of(List.of()));
        var command = new PluginPinCommand(projectDir, resolver, true);

        int exit = command.execute();

        assertThat(exit).isNotZero();
    }

    @Test
    void checkModeSucceedsWhenLockfileIsUpToDate() throws IOException {
        Path repoDir = setupRepo();
        Path projectDir = setupProjectYaml();

        var resolver = new MavenPluginResolver(repoDir, RepositoryRegistry.of(List.of()));
        new PluginPinCommand(projectDir, resolver, false).execute();
        var checkCommand = new PluginPinCommand(projectDir, resolver, true);

        int exit = checkCommand.execute();

        assertThat(exit).isEqualTo(0);
    }
}
