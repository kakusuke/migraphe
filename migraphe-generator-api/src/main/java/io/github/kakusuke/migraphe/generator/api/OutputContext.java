package io.github.kakusuke.migraphe.generator.api;

import java.nio.file.Path;

public record OutputContext(GeneratorDefinition definition, Path outputDir) {}
