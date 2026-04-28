package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LockSyncCheckerTest {

    private static final String SHA1 = "a".repeat(64);
    private static final String SHA2 = "b".repeat(64);

    @Test
    void passesWhenYamlAndLockMatchExactly() {
        PluginConfigParseResult yaml =
                new PluginConfigParseResult(
                        List.of(),
                        List.of(PluginDeclaration.fromString("io.example:plugin-a:1.0")));
        LockFile lock =
                new LockFile(
                        1,
                        List.of(
                                new LockedPlugin(
                                        MavenArtifactCoordinate.parse("io.example:plugin-a:1.0"),
                                        "maven-central",
                                        SHA1,
                                        List.of())));

        // does not throw
        new LockSyncChecker().check(yaml, lock);
    }

    @Test
    void failsWhenYamlAddsCoordinate() {
        PluginConfigParseResult yaml =
                new PluginConfigParseResult(
                        List.of(),
                        List.of(
                                PluginDeclaration.fromString("io.example:plugin-a:1.0"),
                                PluginDeclaration.fromString("io.example:plugin-b:2.0")));
        LockFile lock =
                new LockFile(
                        1,
                        List.of(
                                new LockedPlugin(
                                        MavenArtifactCoordinate.parse("io.example:plugin-a:1.0"),
                                        "maven-central",
                                        SHA1,
                                        List.of())));

        assertThatThrownBy(() -> new LockSyncChecker().check(yaml, lock))
                .isInstanceOf(LockOutOfSyncException.class)
                .hasMessageContaining("io.example:plugin-b:2.0")
                .hasMessageContaining("migraphe pin");
    }

    @Test
    void failsWhenLockHasExtraCoordinate() {
        PluginConfigParseResult yaml =
                new PluginConfigParseResult(
                        List.of(),
                        List.of(PluginDeclaration.fromString("io.example:plugin-a:1.0")));
        LockFile lock =
                new LockFile(
                        1,
                        List.of(
                                new LockedPlugin(
                                        MavenArtifactCoordinate.parse("io.example:plugin-a:1.0"),
                                        "maven-central",
                                        SHA1,
                                        List.of()),
                                new LockedPlugin(
                                        MavenArtifactCoordinate.parse("io.example:plugin-b:2.0"),
                                        "maven-central",
                                        SHA2,
                                        List.of())));

        assertThatThrownBy(() -> new LockSyncChecker().check(yaml, lock))
                .isInstanceOf(LockOutOfSyncException.class)
                .hasMessageContaining("io.example:plugin-b:2.0")
                .hasMessageContaining("migraphe pin");
    }

    @Test
    void failsWhenVersionDiffers() {
        PluginConfigParseResult yaml =
                new PluginConfigParseResult(
                        List.of(),
                        List.of(PluginDeclaration.fromString("io.example:plugin-a:1.5")));
        LockFile lock =
                new LockFile(
                        1,
                        List.of(
                                new LockedPlugin(
                                        MavenArtifactCoordinate.parse("io.example:plugin-a:1.0"),
                                        "maven-central",
                                        SHA1,
                                        List.of())));

        assertThatThrownBy(() -> new LockSyncChecker().check(yaml, lock))
                .isInstanceOf(LockOutOfSyncException.class)
                .hasMessageContaining("io.example:plugin-a")
                .hasMessageContaining("1.5")
                .hasMessageContaining("1.0")
                .hasMessageContaining("migraphe pin");
    }

    @Test
    void failsWhenRepositoryRefDiffers() {
        PluginDeclaration yamlPlugin =
                new PluginDeclaration(
                        MavenArtifactCoordinate.parse("io.example:plugin-a:1.0"),
                        Optional.of("jitpack"));
        PluginConfigParseResult yaml = new PluginConfigParseResult(List.of(), List.of(yamlPlugin));
        LockFile lock =
                new LockFile(
                        1,
                        List.of(
                                new LockedPlugin(
                                        MavenArtifactCoordinate.parse("io.example:plugin-a:1.0"),
                                        "maven-central",
                                        SHA1,
                                        List.of())));

        assertThatThrownBy(() -> new LockSyncChecker().check(yaml, lock))
                .isInstanceOf(LockOutOfSyncException.class)
                .hasMessageContaining("jitpack")
                .hasMessageContaining("maven-central")
                .hasMessageContaining("migraphe pin");
    }

    @Test
    void treatsMissingRepositoryRefAsMavenCentral() {
        PluginConfigParseResult yaml =
                new PluginConfigParseResult(
                        List.of(),
                        List.of(PluginDeclaration.fromString("io.example:plugin-a:1.0")));
        LockFile lock =
                new LockFile(
                        1,
                        List.of(
                                new LockedPlugin(
                                        MavenArtifactCoordinate.parse("io.example:plugin-a:1.0"),
                                        "maven-central",
                                        SHA1,
                                        List.of())));

        // does not throw — empty repositoryRef means default repository (maven-central)
        new LockSyncChecker().check(yaml, lock);
        assertThat(true).isTrue();
    }
}
