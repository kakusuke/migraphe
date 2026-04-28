package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginIntegrityVerifierTest {

    @TempDir Path tempDir;

    private Path writeFile(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    @Test
    void passesWhenAllJarsMatchLockedPins() throws IOException {
        Path pluginJar = writeFile("plugin.jar", "hello");
        Path depJar = writeFile("dep.jar", "world");
        String pluginSha = Sha256Calculator.hash(pluginJar);
        String depSha = Sha256Calculator.hash(depJar);
        MavenArtifactCoordinate pluginCoord =
                MavenArtifactCoordinate.parse("io.example:plugin-a:1.0");
        MavenArtifactCoordinate depCoord = MavenArtifactCoordinate.parse("org.example:lib:2.0");

        LockFile lock =
                new LockFile(
                        1,
                        List.of(
                                new LockedPlugin(
                                        pluginCoord,
                                        "maven-central",
                                        pluginSha,
                                        List.of(new LockedDependency(depCoord, depSha)))));
        List<ResolvedArtifact> resolved =
                List.of(
                        new ResolvedArtifact(pluginCoord, pluginJar),
                        new ResolvedArtifact(depCoord, depJar));

        assertThatCode(() -> new PluginIntegrityVerifier().verify(resolved, lock))
                .doesNotThrowAnyException();
    }

    @Test
    void failsWithChecksumMismatchWhenJarContentChanged() throws IOException {
        Path pluginJar = writeFile("plugin.jar", "hello");
        String correctSha = Sha256Calculator.hash(pluginJar);
        Files.writeString(pluginJar, "tampered");
        MavenArtifactCoordinate coord = MavenArtifactCoordinate.parse("io.example:plugin-a:1.0");

        LockFile lock =
                new LockFile(
                        1,
                        List.of(new LockedPlugin(coord, "maven-central", correctSha, List.of())));
        List<ResolvedArtifact> resolved = List.of(new ResolvedArtifact(coord, pluginJar));

        assertThatThrownBy(() -> new PluginIntegrityVerifier().verify(resolved, lock))
                .isInstanceOf(ChecksumMismatchException.class)
                .hasMessageContaining("io.example:plugin-a:1.0")
                .hasMessageContaining(correctSha)
                .hasMessageContaining(pluginJar.toString());
    }

    @Test
    void failsWithMissingPinWhenResolvedArtifactNotInLock() throws IOException {
        Path pluginJar = writeFile("plugin.jar", "hello");
        Path extraJar = writeFile("extra.jar", "extra");
        String pluginSha = Sha256Calculator.hash(pluginJar);
        MavenArtifactCoordinate pluginCoord =
                MavenArtifactCoordinate.parse("io.example:plugin-a:1.0");
        MavenArtifactCoordinate extraCoord =
                MavenArtifactCoordinate.parse("org.example:unexpected:9.9");

        LockFile lock =
                new LockFile(
                        1,
                        List.of(
                                new LockedPlugin(
                                        pluginCoord, "maven-central", pluginSha, List.of())));
        List<ResolvedArtifact> resolved =
                List.of(
                        new ResolvedArtifact(pluginCoord, pluginJar),
                        new ResolvedArtifact(extraCoord, extraJar));

        assertThatThrownBy(() -> new PluginIntegrityVerifier().verify(resolved, lock))
                .isInstanceOf(MissingChecksumPinException.class)
                .hasMessageContaining("org.example:unexpected:9.9")
                .hasMessageContaining("migraphe pin");
    }

    @Test
    void distinguishesPinsByFullCoordinateNotJustGroupArtifact() throws IOException {
        Path jarV1 = writeFile("v1.jar", "v1-bytes");
        Path jarV2 = writeFile("v2.jar", "v2-bytes");
        String shaV1 = Sha256Calculator.hash(jarV1);
        MavenArtifactCoordinate coordV1 = MavenArtifactCoordinate.parse("io.example:plugin:1.0");
        MavenArtifactCoordinate coordV2 = MavenArtifactCoordinate.parse("io.example:plugin:2.0");

        LockFile lock =
                new LockFile(
                        1, List.of(new LockedPlugin(coordV1, "maven-central", shaV1, List.of())));
        // resolved version 2.0 instead, lock only knows 1.0 → must error as missing pin
        List<ResolvedArtifact> resolved = List.of(new ResolvedArtifact(coordV2, jarV2));

        assertThatThrownBy(() -> new PluginIntegrityVerifier().verify(resolved, lock))
                .isInstanceOf(MissingChecksumPinException.class)
                .hasMessageContaining("io.example:plugin:2.0");
    }
}
