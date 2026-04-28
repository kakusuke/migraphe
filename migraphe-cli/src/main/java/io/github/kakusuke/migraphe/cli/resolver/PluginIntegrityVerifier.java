package io.github.kakusuke.migraphe.cli.resolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Verifies that resolved JARs match their SHA-256 pins in {@link LockFile}. */
public final class PluginIntegrityVerifier {

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
