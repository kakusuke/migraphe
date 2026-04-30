package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class LockedPluginTest {

    private static final String SHA = "a".repeat(64);
    private static final String SHA2 = "b".repeat(64);

    @Test
    void exposesAllFieldsAndDefensivelyCopiesDependencies() {
        MavenArtifactCoordinate coord = MavenArtifactCoordinate.parse("io.example:plugin:1.0");
        MavenArtifactCoordinate depCoord = MavenArtifactCoordinate.parse("org.example:lib:1.0");
        LockedDependency dep = new LockedDependency(depCoord, SHA2);

        LockedPlugin plugin = new LockedPlugin(coord, SHA, List.of(dep));

        assertThat(plugin.coordinate()).isEqualTo(coord);
        assertThat(plugin.sha256()).isEqualTo(SHA);
        assertThat(plugin.dependencies()).containsExactly(dep);
    }

    @Test
    void rejectsInvalidSha256() {
        MavenArtifactCoordinate coord = MavenArtifactCoordinate.parse("io.example:plugin:1.0");

        assertThatThrownBy(() -> new LockedPlugin(coord, "abc", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sha256");
    }
}
