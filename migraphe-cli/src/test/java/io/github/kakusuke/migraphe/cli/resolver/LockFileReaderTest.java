package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LockFileReaderTest {

    @TempDir Path tempDir;

    private static final String SHA1 =
            "3a7f000000000000000000000000000000000000000000000000000000000e21";
    private static final String SHA2 =
            "8c2e000000000000000000000000000000000000000000000000000000004b91";

    @Test
    void returnsEmptyWhenFileDoesNotExist() throws IOException {
        Path missing = tempDir.resolve("missing.yaml");

        Optional<LockFile> result = new LockFileReader().read(missing);

        assertThat(result).isEmpty();
    }

    @Test
    void parsesValidLockFile() throws IOException {
        Path lock = tempDir.resolve("migraphe.lock.yaml");
        Files.writeString(
                lock,
                """
                lockfile-version: 1
                plugins:
                  - coordinate: io.example:plugin-a:1.0
                    repository: maven-central
                    sha256: %s
                    dependencies:
                      - coordinate: org.example:lib:2.3
                        sha256: %s
                """
                        .formatted(SHA1, SHA2));

        LockFile result = new LockFileReader().read(lock).orElseThrow();

        assertThat(result.version()).isEqualTo(1);
        assertThat(result.plugins()).hasSize(1);
        LockedPlugin plugin = result.plugins().get(0);
        assertThat(plugin.coordinate().artifactId()).isEqualTo("plugin-a");
        assertThat(plugin.repositoryId()).isEqualTo("maven-central");
        assertThat(plugin.sha256()).isEqualTo(SHA1);
        assertThat(plugin.dependencies()).hasSize(1);
        assertThat(plugin.dependencies().get(0).coordinate().artifactId()).isEqualTo("lib");
        assertThat(plugin.dependencies().get(0).sha256()).isEqualTo(SHA2);
    }

    @Test
    void rejectsUnsupportedLockfileVersion() throws IOException {
        Path lock = tempDir.resolve("migraphe.lock.yaml");
        Files.writeString(
                lock,
                """
                lockfile-version: 2
                plugins: []
                """);

        assertThatThrownBy(() -> new LockFileReader().read(lock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lockfile-version");
    }

    @Test
    void rejectsMissingLockfileVersion() throws IOException {
        Path lock = tempDir.resolve("migraphe.lock.yaml");
        Files.writeString(lock, """
                plugins: []
                """);

        assertThatThrownBy(() -> new LockFileReader().read(lock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lockfile-version");
    }

    @Test
    void rejectsPluginEntryMissingCoordinate() throws IOException {
        Path lock = tempDir.resolve("migraphe.lock.yaml");
        Files.writeString(
                lock,
                """
                lockfile-version: 1
                plugins:
                  - repository: maven-central
                    sha256: %s
                """
                        .formatted(SHA1));

        assertThatThrownBy(() -> new LockFileReader().read(lock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("coordinate");
    }
}
