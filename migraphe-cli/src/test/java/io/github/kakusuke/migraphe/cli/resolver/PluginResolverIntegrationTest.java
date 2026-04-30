package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.cli.command.PluginPinCommand;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test that mimics a JitPack-style Maven repository served from a {@code file://} URL.
 * Walks the full {@link PluginResolver} pipeline: yaml parse → lock read → sync check → resolve →
 * SHA-256 verify → URLClassLoader.
 */
class PluginResolverIntegrationTest {

    @TempDir Path tempDir;

    private Path repoDir;
    private Path projectDir;
    private Path jarPath;

    @BeforeEach
    void setUp() throws IOException {
        repoDir = tempDir.resolve("repo");
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
        jarPath = artifactDir.resolve("test-plugin-1.0.jar");
        Files.write(jarPath, new byte[] {0x50, 0x4B, 0x03, 0x04});

        projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);
        Files.writeString(
                projectDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test
                plugins:
                  - com.example:test-plugin:1.0
                """);
    }

    /** Run {@code migraphe pin} against the file:// repo to write a valid lockfile. */
    private void writeValidLockfile() {
        var resolver =
                new MavenPluginResolver(
                        repoDir,
                        RepositoryRegistry.of(
                                List.of(
                                        RepositoryConfig.testOnly(
                                                "local", repoDir.toUri().toString()))));
        new PluginPinCommand(projectDir, resolver, false).execute();
    }

    @Test
    void resolvesSuccessfullyWhenLockfileMatches() throws IOException {
        writeValidLockfile();

        URLClassLoader classLoader = new TestablePluginResolver(repoDir).resolve(projectDir);

        assertThat(classLoader).isNotNull();
        assertThat(classLoader.getURLs()).hasSize(1);
        assertThat(classLoader.getURLs()[0].toString()).endsWith("test-plugin-1.0.jar");
    }

    @Test
    void throwsLockFileNotFoundWhenLockfileMissing() {
        // No writeValidLockfile() — lockfile absent.
        assertThatThrownBy(() -> new TestablePluginResolver(repoDir).resolve(projectDir))
                .isInstanceOf(LockFileNotFoundException.class);
    }

    @Test
    void throwsLockOutOfSyncWhenYamlAndLockDiverge() throws IOException {
        writeValidLockfile();
        // Bump version in migraphe.yaml after lock was generated for 1.0.
        Files.writeString(
                projectDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test
                plugins:
                  - com.example:test-plugin:2.0
                """);

        assertThatThrownBy(() -> new TestablePluginResolver(repoDir).resolve(projectDir))
                .isInstanceOf(LockOutOfSyncException.class);
    }

    @Test
    void throwsChecksumMismatchWhenJarContentChangedAfterPin() throws IOException {
        writeValidLockfile();
        // Tamper with the resolved JAR after pin.
        Files.write(jarPath, new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});

        assertThatThrownBy(() -> new TestablePluginResolver(repoDir).resolve(projectDir))
                .isInstanceOf(ChecksumMismatchException.class);
    }

    /**
     * Subclass-style adapter that overrides {@link PluginResolver}'s default {@link
     * MavenPluginResolver} construction to point at a local {@code @TempDir} repo.
     */
    private static final class TestablePluginResolver {
        private final Path repoDir;

        TestablePluginResolver(Path repoDir) {
            this.repoDir = repoDir;
        }

        URLClassLoader resolve(Path projectDir) {
            String saved = System.getProperty("maven.repo.local");
            System.setProperty("maven.repo.local", repoDir.toString());
            try {
                return new PluginResolver().resolve(projectDir);
            } finally {
                if (saved == null) {
                    System.clearProperty("maven.repo.local");
                } else {
                    System.setProperty("maven.repo.local", saved);
                }
            }
        }
    }
}
