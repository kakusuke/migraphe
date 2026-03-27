package io.github.kakusuke.migraphe.generator.api;

import io.github.kakusuke.migraphe.api.environment.Environment;

public interface GeneratorPlugin {
    String type();

    Class<? extends GeneratorDefinition> definitionClass();

    Generator createGenerator(Environment environment, GeneratorDefinition definition);
}
