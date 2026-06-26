package io.github.kakusuke.migraphe.api.generator;

import java.nio.file.Path;

/**
 * Inputs handed to a {@link GeneratorOutputPlugin} when it renders data.
 *
 * <p>The runtime assembles this context for each generator run and passes it to {@link
 * GeneratorOutputPlugin#output(Object, OutputContext)}. It carries the already-resolved destination
 * directory for produced artifacts and a {@link DefinitionResolver} the plugin can use to obtain
 * its typed {@link GeneratorDefinition} configuration via the {@link #definitionAs(Class)}
 * convenience method.
 *
 * @param resolver the resolver used to materialize the typed {@link GeneratorDefinition} for this
 *     generator
 * @param outputDir the resolved directory into which the output plugin should write its artifacts
 * @see GeneratorOutputPlugin#output(Object, OutputContext)
 * @see DefinitionResolver
 */
public record OutputContext(DefinitionResolver resolver, Path outputDir) {

    /**
     * Resolves this generator's configuration as the requested {@link GeneratorDefinition} subtype.
     *
     * <p>Convenience wrapper that delegates to {@link DefinitionResolver#resolve(Class)} on the
     * context's {@link #resolver()}. Output plugins typically pass the type they declared via
     * {@link GeneratorOutputPlugin#definitionClass()}.
     *
     * @param <T> the {@link GeneratorDefinition} subtype to resolve
     * @param klass the {@link Class} of the definition subtype to materialize
     * @return an instance of {@code klass} populated from this generator's configuration
     * @throws RuntimeException if the underlying resolver cannot produce the requested type
     */
    public <T extends GeneratorDefinition> T definitionAs(Class<T> klass) {
        return resolver.resolve(klass);
    }
}
