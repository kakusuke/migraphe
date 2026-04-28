package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class LockFileTest {

    private static final String SHA = "a".repeat(64);

    @Test
    void exposesVersionAndPlugins() {
        MavenArtifactCoordinate coord = MavenArtifactCoordinate.parse("io.example:plugin:1.0");
        LockedPlugin plugin = new LockedPlugin(coord, "maven-central", SHA, List.of());

        LockFile lockFile = new LockFile(1, List.of(plugin));

        assertThat(lockFile.version()).isEqualTo(1);
        assertThat(lockFile.plugins()).containsExactly(plugin);
    }

    @Test
    void rejectsUnsupportedVersion() {
        assertThatThrownBy(() -> new LockFile(2, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lockfile-version");
    }

    @Test
    void exposesCurrentVersionConstant() {
        assertThat(LockFile.CURRENT_VERSION).isEqualTo(1);
    }
}
