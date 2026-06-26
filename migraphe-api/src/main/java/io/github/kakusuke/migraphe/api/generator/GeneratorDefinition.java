package io.github.kakusuke.migraphe.api.generator;

/**
 * Base interface for the configuration of a single generator.
 *
 * <p>Each {@link GeneratorOutputPlugin} declares a concrete subtype of this interface through
 * {@link GeneratorOutputPlugin#definitionClass()}. Plugins typically implement that subtype as a
 * SmallRye {@code @ConfigMapping} interface so its properties are bound directly from the project's
 * YAML configuration. At render time the output plugin obtains its typed definition via {@link
 * OutputContext#definitionAs(Class)}, which delegates to a {@link DefinitionResolver}.
 *
 * <p>The only contract guaranteed by this base interface is {@link #type()}; plugin-specific
 * subtypes add whatever additional configuration properties they require.
 *
 * @see GeneratorOutputPlugin#definitionClass()
 * @see OutputContext#definitionAs(Class)
 * @see DefinitionResolver
 */
public interface GeneratorDefinition {

    /**
     * Returns the generator's output-type identifier.
     *
     * <p>This corresponds to the {@code type} field of the generator's configuration and matches
     * the {@link GeneratorOutputPlugin#type()} of the plugin that consumes this definition (for
     * example {@code "jdbc-markdown"} or {@code "output-json"}).
     *
     * @return the output-type identifier of this generator
     */
    String type();
}
