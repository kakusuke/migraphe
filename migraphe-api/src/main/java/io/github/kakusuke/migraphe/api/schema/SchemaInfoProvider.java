package io.github.kakusuke.migraphe.api.schema;

import io.github.kakusuke.migraphe.api.environment.Environment;

/**
 * Extracts schema information from a connected {@link Environment}.
 *
 * <p>This functional interface lets plugins introspect a live environment (for example, by reading
 * database metadata) and return a typed snapshot of its schema. The resulting object is typically
 * consumed by generators to render documentation or other schema-derived output.
 *
 * <p>Plugins implement this interface to produce a dialect-specific schema model; the type
 * parameter {@code T} is the concrete schema representation that the implementation returns.
 *
 * @param <T> the type of the schema information produced
 * @see Environment
 */
@FunctionalInterface
public interface SchemaInfoProvider<T> {

    /**
     * Extracts schema information from the given environment.
     *
     * @param environment the environment to introspect
     * @return the schema information extracted from {@code environment}
     */
    T getSchemaInfo(Environment environment);
}
