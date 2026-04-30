package io.github.kakusuke.migraphe.cli.resolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Builds a {@link LockFile} from resolved plugin groups by computing SHA-256 hashes. */
public final class LockFileBuilder {

    public LockFile build(List<ResolvedPluginGroup> groups) {
        List<LockedPlugin> plugins = new ArrayList<>();
        for (ResolvedPluginGroup group : groups) {
            String rootSha = hash(group.root());
            List<LockedDependency> deps = new ArrayList<>();
            for (ResolvedArtifact dep : group.dependencies()) {
                deps.add(new LockedDependency(dep.coordinate(), hash(dep)));
            }
            plugins.add(new LockedPlugin(group.root().coordinate(), rootSha, deps));
        }
        return new LockFile(LockFile.CURRENT_VERSION, plugins);
    }

    private static String hash(ResolvedArtifact artifact) {
        try {
            return Sha256Calculator.hash(artifact.jarPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
