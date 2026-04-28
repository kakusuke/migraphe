package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ResolvedArtifactTest {

    @Test
    void exposesCoordinateAndJarPath() {
        MavenArtifactCoordinate coord = MavenArtifactCoordinate.parse("com.example:foo:1.0");
        Path jarPath = Path.of("/tmp/foo-1.0.jar");

        ResolvedArtifact artifact = new ResolvedArtifact(coord, jarPath);

        assertThat(artifact.coordinate()).isEqualTo(coord);
        assertThat(artifact.jarPath()).isEqualTo(jarPath);
    }
}
