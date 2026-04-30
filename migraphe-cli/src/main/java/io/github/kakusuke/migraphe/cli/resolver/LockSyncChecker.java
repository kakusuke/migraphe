package io.github.kakusuke.migraphe.cli.resolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Verifies that {@code migraphe.lock.yaml} is in sync with {@code migraphe.yaml}. */
public final class LockSyncChecker {

    private static final String FIX_HINT = " Run 'migraphe pin' to update.";

    public void check(PluginConfigParseResult yaml, LockFile lock) {
        Map<String, PluginDeclaration> yamlByGa = byGroupArtifact(yaml.plugins());
        Map<String, LockedPlugin> lockByGa = lockByGroupArtifact(lock.plugins());

        List<String> issues = new ArrayList<>();
        for (var entry : yamlByGa.entrySet()) {
            String ga = entry.getKey();
            PluginDeclaration yamlPlugin = entry.getValue();
            LockedPlugin locked = lockByGa.get(ga);
            if (locked == null) {
                issues.add(
                        "added in migraphe.yaml but missing from lock: "
                                + formatCoordinate(yamlPlugin.coordinate()));
                continue;
            }
            if (!yamlPlugin.coordinate().version().equals(locked.coordinate().version())) {
                issues.add(
                        "version changed for "
                                + ga
                                + ": migraphe.yaml="
                                + yamlPlugin.coordinate().version()
                                + " but lock="
                                + locked.coordinate().version());
            }
        }
        for (String ga : lockByGa.keySet()) {
            if (!yamlByGa.containsKey(ga)) {
                issues.add(
                        "removed from migraphe.yaml but still in lock: "
                                + formatCoordinate(lockByGa.get(ga).coordinate()));
            }
        }
        if (!issues.isEmpty()) {
            throw new LockOutOfSyncException(
                    "migraphe.lock.yaml is out of sync with migraphe.yaml:\n  - "
                            + String.join("\n  - ", issues)
                            + "\n"
                            + FIX_HINT);
        }
    }

    private static Map<String, PluginDeclaration> byGroupArtifact(List<PluginDeclaration> plugins) {
        Map<String, PluginDeclaration> map = new LinkedHashMap<>();
        for (PluginDeclaration p : plugins) {
            map.put(groupArtifact(p.coordinate()), p);
        }
        return map;
    }

    private static Map<String, LockedPlugin> lockByGroupArtifact(List<LockedPlugin> plugins) {
        Map<String, LockedPlugin> map = new LinkedHashMap<>();
        for (LockedPlugin p : plugins) {
            map.put(groupArtifact(p.coordinate()), p);
        }
        return map;
    }

    private static String groupArtifact(MavenArtifactCoordinate c) {
        return c.groupId() + ":" + c.artifactId();
    }

    private static String formatCoordinate(MavenArtifactCoordinate c) {
        return c.groupId() + ":" + c.artifactId() + ":" + c.version();
    }
}
