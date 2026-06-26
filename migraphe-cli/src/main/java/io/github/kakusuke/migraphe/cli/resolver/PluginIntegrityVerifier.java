package io.github.kakusuke.migraphe.cli.resolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies that resolved JARs match their SHA-256 pins in {@link LockFile}.
 *
 * <p>Run by {@link PluginResolver} after Maven resolution and before the plugin {@link
 * java.net.URLClassLoader URLClassLoader} is built, this is the supply-chain integrity gate of the
 * JitPack distribution model: every JAR that will be loaded must have a matching {@code sha256} pin
 * in {@code migraphe.lock.yaml}, and the on-disk JAR's hash must equal that pin. Either an unpinned
 * artifact or a hash mismatch aborts startup.
 */
public final class PluginIntegrityVerifier {

    /** Creates a new {@code PluginIntegrityVerifier}. */
    public PluginIntegrityVerifier() {}

    /**
     * Verifies every resolved artifact against the SHA-256 pins recorded in the lockfile.
     *
     * @param resolved the artifacts produced by Maven resolution that are about to be loaded
     * @param lockFile the parsed {@code migraphe.lock.yaml} supplying the expected SHA-256 hashes
     * @throws MissingChecksumPinException if a resolved artifact has no pin in the lockfile
     * @throws ChecksumMismatchException if a resolved artifact's on-disk hash differs from its pin
     * @throws java.io.UncheckedIOException if a JAR cannot be read to compute its hash
     */
    public void verify(List<ResolvedArtifact> resolved, LockFile lockFile) {
        Map<String, String> expectedByCoord = collectExpectedHashes(lockFile);
        for (ResolvedArtifact artifact : resolved) {
            String key = formatCoordinate(artifact.coordinate());
            String expected = expectedByCoord.get(key);
            if (expected == null) {
                throw new MissingChecksumPinException(
                        "Resolved artifact has no SHA-256 pin in migraphe.lock.yaml: "
                                + key
                                + " ("
                                + artifact.jarPath()
                                + "). Run 'migraphe pin' to update.");
            }
            String actual;
            try {
                actual = Sha256Calculator.hash(artifact.jarPath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (!expected.equals(actual)) {
                throw new ChecksumMismatchException(
                        "SHA-256 mismatch for "
                                + key
                                + ": expected="
                                + expected
                                + " actual="
                                + actual
                                + " path="
                                + artifact.jarPath()
                                + ". Run 'migraphe pin' if the change was intentional.");
            }
        }
    }

    private static Map<String, String> collectExpectedHashes(LockFile lockFile) {
        Map<String, String> map = new HashMap<>();
        for (LockedPlugin plugin : lockFile.plugins()) {
            map.put(formatCoordinate(plugin.coordinate()), plugin.sha256());
            for (LockedDependency dep : plugin.dependencies()) {
                map.put(formatCoordinate(dep.coordinate()), dep.sha256());
            }
        }
        return map;
    }

    private static String formatCoordinate(MavenArtifactCoordinate c) {
        return c.groupId() + ":" + c.artifactId() + ":" + c.version();
    }
}
