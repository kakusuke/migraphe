package io.github.kakusuke.migraphe.cli.resolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link LockFile} from resolved plugin groups by computing SHA-256 hashes.
 *
 * <p>For each {@link ResolvedPluginGroup} it hashes the root plugin JAR and every transitive
 * dependency JAR (via {@link Sha256Calculator}) and assembles the corresponding {@link
 * LockedPlugin}/{@link LockedDependency} pins. The result is stamped with {@link
 * LockFile#CURRENT_VERSION} and is ready to be serialized by {@link LockFileWriter}. This is the
 * model-producing step behind {@code migraphe pin}.
 */
public final class LockFileBuilder {

    /** Creates a new {@code LockFileBuilder}. */
    public LockFileBuilder() {}

    /**
     * Builds a lockfile by hashing every resolved JAR in the given groups.
     *
     * @param groups the resolved plugin groups, each pairing a root plugin artifact with its
     *     transitive dependency artifacts
     * @return a {@link LockFile} pinning every artifact to its coordinate and SHA-256 hash
     * @throws java.io.UncheckedIOException if any JAR file cannot be read while hashing
     */
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
