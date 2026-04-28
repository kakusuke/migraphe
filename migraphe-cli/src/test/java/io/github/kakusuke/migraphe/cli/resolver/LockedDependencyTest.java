package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LockedDependencyTest {

    @Test
    void exposesCoordinateAndSha256() {
        MavenArtifactCoordinate coord = MavenArtifactCoordinate.parse("org.example:lib:1.0");

        LockedDependency dep =
                new LockedDependency(
                        coord, "1f3a000000000000000000000000000000000000000000000000000000000077");

        assertThat(dep.coordinate()).isEqualTo(coord);
        assertThat(dep.sha256())
                .isEqualTo("1f3a000000000000000000000000000000000000000000000000000000000077");
    }

    @Test
    void rejectsBlankSha256() {
        MavenArtifactCoordinate coord = MavenArtifactCoordinate.parse("org.example:lib:1.0");

        assertThatThrownBy(() -> new LockedDependency(coord, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sha256");
    }

    @Test
    void rejectsSha256OfWrongLength() {
        MavenArtifactCoordinate coord = MavenArtifactCoordinate.parse("org.example:lib:1.0");

        assertThatThrownBy(() -> new LockedDependency(coord, "abcd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64");
    }

    @Test
    void rejectsSha256WithNonHexCharacters() {
        MavenArtifactCoordinate coord = MavenArtifactCoordinate.parse("org.example:lib:1.0");
        String sixtyFourChars = "g".repeat(64);

        assertThatThrownBy(() -> new LockedDependency(coord, sixtyFourChars))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hex");
    }
}
