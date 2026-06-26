package io.github.kakusuke.migraphe.cli.resolver;

import java.util.List;

/**
 * A top-level plugin together with its transitive dependency artifacts.
 *
 * <p>Produced by {@link MavenPluginResolver#resolveGroups(List)}, a group keeps each declared
 * plugin distinct from its dependency closure. This grouping is what lets {@code migraphe pin}
 * record a lockfile entry per plugin while still pinning every transitive JAR. The {@code root}
 * artifact is the JAR named directly by the {@link PluginDeclaration}; {@code dependencies} holds
 * everything else Maven pulled in transitively.
 *
 * @param plugin the original plugin declaration that triggered this resolution
 * @param root the artifact resolved for the declaration's own coordinate
 * @param dependencies the transitive dependency artifacts, excluding {@code root} (defensively
 *     copied into an immutable list)
 */
public record ResolvedPluginGroup(
        PluginDeclaration plugin, ResolvedArtifact root, List<ResolvedArtifact> dependencies) {

    /**
     * Canonical constructor that defensively copies {@code dependencies} into an immutable list.
     *
     * @param plugin the original plugin declaration that triggered this resolution
     * @param root the artifact resolved for the declaration's own coordinate
     * @param dependencies the transitive dependency artifacts, excluding {@code root}
     */
    public ResolvedPluginGroup {
        dependencies = List.copyOf(dependencies);
    }
}
