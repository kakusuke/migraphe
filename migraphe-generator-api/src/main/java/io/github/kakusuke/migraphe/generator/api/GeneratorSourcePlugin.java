package io.github.kakusuke.migraphe.generator.api;

public interface GeneratorSourcePlugin<T> {
    String type();

    Class<T> dataClass();

    T extract(SourceContext context);
}
