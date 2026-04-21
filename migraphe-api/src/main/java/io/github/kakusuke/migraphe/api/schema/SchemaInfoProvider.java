package io.github.kakusuke.migraphe.api.schema;

import io.github.kakusuke.migraphe.api.environment.Environment;

@FunctionalInterface
public interface SchemaInfoProvider<T> {

    T getSchemaInfo(Environment environment);
}
