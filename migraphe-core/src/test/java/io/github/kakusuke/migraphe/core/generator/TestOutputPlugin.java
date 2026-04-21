package io.github.kakusuke.migraphe.core.generator;

import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;

public final class TestOutputPlugin implements GeneratorOutputPlugin {

    @Override
    public String type() {
        return "test-output";
    }

    @Override
    public boolean canHandle(Class<?> dataClass) {
        return true;
    }

    @Override
    public Class<? extends GeneratorDefinition> definitionClass() {
        return GeneratorDefinition.class;
    }

    @Override
    public void output(Object data, OutputContext context) {}
}
