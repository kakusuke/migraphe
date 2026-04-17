package io.github.kakusuke.migraphe.api.generator;

@FunctionalInterface
public interface DefinitionResolver {
    <T extends GeneratorDefinition> T resolve(Class<T> klass);
}
