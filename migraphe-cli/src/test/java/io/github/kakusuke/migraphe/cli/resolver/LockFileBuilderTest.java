package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LockFileBuilderTest {

    @TempDir Path tempDir;

    private Path writeJar(String name, String content) throws IOException {
        Path p = tempDir.resolve(name);
        Files.writeString(p, content);
        return p;
    }

    @Test
    void buildsLockFileFromGroupsWithSha256ForRootAndDependencies() throws IOException {
        Path pluginJar = writeJar("plugin.jar", "p");
        Path depJar = writeJar("dep.jar", "d");
        var pluginCoord = MavenArtifactCoordinate.parse("io.example:plugin-a:1.0");
        var depCoord = MavenArtifactCoordinate.parse("org.example:lib:2.0");
        var declaration = new PluginDeclaration(pluginCoord, Optional.empty());
        var group =
                new ResolvedPluginGroup(
                        declaration,
                        new ResolvedArtifact(pluginCoord, pluginJar),
                        List.of(new ResolvedArtifact(depCoord, depJar)));

        LockFile lock = new LockFileBuilder().build(List.of(group));

        assertThat(lock.version()).isEqualTo(1);
        assertThat(lock.plugins()).hasSize(1);
        LockedPlugin lockedPlugin = lock.plugins().get(0);
        assertThat(lockedPlugin.coordinate()).isEqualTo(pluginCoord);
        assertThat(lockedPlugin.sha256()).isEqualTo(Sha256Calculator.hash(pluginJar));
        assertThat(lockedPlugin.dependencies()).hasSize(1);
        LockedDependency lockedDep = lockedPlugin.dependencies().get(0);
        assertThat(lockedDep.coordinate()).isEqualTo(depCoord);
        assertThat(lockedDep.sha256()).isEqualTo(Sha256Calculator.hash(depJar));
    }
}
