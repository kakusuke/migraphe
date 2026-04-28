package io.github.kakusuke.migraphe.cli.resolver;

import java.nio.file.Path;

public record ResolvedArtifact(MavenArtifactCoordinate coordinate, Path jarPath) {}
