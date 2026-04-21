package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MavenArtifactCoordinateTest {

    @Test
    void shouldParseValidCoordinate() {
        MavenArtifactCoordinate result = MavenArtifactCoordinate.parse("g:a:1.0");

        assertThat(result.groupId()).isEqualTo("g");
        assertThat(result.artifactId()).isEqualTo("a");
        assertThat(result.version()).isEqualTo("1.0");
    }

    @Test
    void shouldParseSnapshotCoordinate() {
        var result =
                MavenArtifactCoordinate.parse(
                        "io.github.kakusuke:migraphe-plugin-generator-json:0.1.0-SNAPSHOT");

        assertThat(result.groupId()).isEqualTo("io.github.kakusuke");
        assertThat(result.artifactId()).isEqualTo("migraphe-plugin-generator-json");
        assertThat(result.version()).isEqualTo("0.1.0-SNAPSHOT");
    }

    @Test
    void shouldThrowForInvalidFormat() {
        assertThatThrownBy(() -> MavenArtifactCoordinate.parse("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowForEmptyString() {
        assertThatThrownBy(() -> MavenArtifactCoordinate.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowForNull() {
        assertThatThrownBy(() -> MavenArtifactCoordinate.parse(null))
                .isInstanceOf(NullPointerException.class);
    }
}
