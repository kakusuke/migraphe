package io.github.kakusuke.migraphe.api.generator;

import java.nio.file.Path;

public record OutputContext(GeneratorDefinition definition, Path outputDir) {}
