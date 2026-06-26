package io.github.kakusuke.migraphe.api.generator;

/**
 * Resolves a typed {@link GeneratorDefinition} on demand for an output plugin.
 *
 * <p>A {@link GeneratorOutputPlugin} does not parse its own configuration; instead it asks the
 * runtime to materialize a strongly typed view of the current generator's settings. This functional
 * interface is the abstraction the runtime supplies — carried inside the {@link OutputContext} — so
 * that the plugin can obtain its plugin-specific {@link GeneratorDefinition} subtype (typically a
 * SmallRye {@code @ConfigMapping} interface) through {@link OutputContext#definitionAs(Class)}.
 *
 * <p>Implementations decide how the requested type is produced. A full implementation
 * re-materializes the requested {@code @ConfigMapping} interface from the parsed project
 * configuration; a minimal fallback may satisfy only the base {@link GeneratorDefinition} contract
 * and reject other types. Output plugins should therefore request only the definition type they
 * declared via {@link GeneratorOutputPlugin#definitionClass()}.
 *
 * @see OutputContext#definitionAs(Class)
 * @see GeneratorDefinition
 */
@FunctionalInterface
public interface DefinitionResolver {

    /**
     * Resolves the generator's configuration as the requested {@link GeneratorDefinition} subtype.
     *
     * @param <T> the {@link GeneratorDefinition} subtype to resolve
     * @param klass the {@link Class} of the definition subtype to materialize
     * @return an instance of {@code klass} populated from the current generator's configuration
     * @throws RuntimeException if the resolver cannot produce the requested type (for example a
     *     minimal resolver asked for a plugin-specific subtype it does not understand)
     */
    <T extends GeneratorDefinition> T resolve(Class<T> klass);
}
