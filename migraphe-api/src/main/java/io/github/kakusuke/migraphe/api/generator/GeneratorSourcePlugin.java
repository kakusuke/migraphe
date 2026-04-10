package io.github.kakusuke.migraphe.api.generator;

public interface GeneratorSourcePlugin<T> {
    String type();

    Class<T> dataClass();

    T extract(SourceContext context);
}
