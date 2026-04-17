package io.github.kakusuke.migraphe.api.generator;

import java.nio.file.Path;

public record OutputContext(DefinitionResolver resolver, Path outputDir) {

    public <T extends GeneratorDefinition> T definitionAs(Class<T> klass) {
        return resolver.resolve(klass);
    }
}
