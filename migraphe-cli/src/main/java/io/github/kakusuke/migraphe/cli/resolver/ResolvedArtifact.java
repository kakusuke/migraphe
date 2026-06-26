package io.github.kakusuke.migraphe.cli.resolver;

import java.nio.file.Path;

/**
 * A single artifact that Maven resolution has located on the local filesystem.
 *
 * <p>Produced by {@link MavenPluginResolver}, a resolved artifact pairs a {@link
 * MavenArtifactCoordinate} with the concrete JAR file it resolved to (typically inside the local
 * {@code ~/.m2/repository} cache). {@link PluginResolver} turns these JAR paths into the {@link
 * java.net.URL URLs} of the plugin {@link java.net.URLClassLoader URLClassLoader}, and {@link
 * PluginIntegrityVerifier} hashes each JAR to check it against its SHA-256 pin.
 *
 * @param coordinate the coordinate that resolved to this artifact
 * @param jarPath the filesystem path to the resolved JAR
 */
public record ResolvedArtifact(MavenArtifactCoordinate coordinate, Path jarPath) {}
