package io.github.kakusuke.migraphe.api.generator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DefinitionResolverTest {

    @Test
    void resolveReturnsTypedDefinitionInstance() {
        GeneratorDefinition stub =
                new GeneratorDefinition() {
                    @Override
                    public String type() {
                        return "test-type";
                    }
                };
        DefinitionResolver resolver =
                new DefinitionResolver() {
                    @Override
                    public <T extends GeneratorDefinition> T resolve(Class<T> klass) {
                        return klass.cast(stub);
                    }
                };

        GeneratorDefinition result = resolver.resolve(GeneratorDefinition.class);

        assertThat(result).isSameAs(stub);
    }
}
