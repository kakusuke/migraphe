package io.github.kakusuke.migraphe.cli.resolver;

import java.util.List;

/** A top-level plugin together with its transitive dependency artifacts. */
public record ResolvedPluginGroup(
        PluginDeclaration plugin, ResolvedArtifact root, List<ResolvedArtifact> dependencies) {

    public ResolvedPluginGroup {
        dependencies = List.copyOf(dependencies);
    }
}
