package io.github.kakusuke.migraphe.api.generator;

public interface GeneratorOutputPlugin {
    String type();

    boolean canHandle(Class<?> dataClass);

    Class<? extends GeneratorDefinition> definitionClass();

    void output(Object data, OutputContext context);
}
