package io.github.kakusuke.migraphe.api.schema;

import io.github.kakusuke.migraphe.api.target.Target;

/**
 * Extracts schema information from a connected {@link Target}.
 *
 * <p>This functional interface lets plugins introspect a live target (for example, by reading
 * database metadata) and return a typed snapshot of its schema. The resulting object is typically
 * consumed by generators to render documentation or other schema-derived output.
 *
 * <p>Plugins implement this interface to produce a dialect-specific schema model; the type
 * parameter {@code T} is the concrete schema representation that the implementation returns.
 *
 * @param <T> the type of the schema information produced
 * @see Target
 */
@FunctionalInterface
public interface SchemaInfoProvider<T> {

    /**
     * Extracts schema information from the given target.
     *
     * @param target the target to introspect
     * @return the schema information extracted from {@code target}
     */
    T getSchemaInfo(Target target);
}
