package io.github.kakusuke.migraphe.generator.api;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import org.jspecify.annotations.Nullable;

public record SourceContext(
        @Nullable Environment environment, @Nullable MigrationGraphView graph) {}
