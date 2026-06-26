package io.github.kakusuke.migraphe.api.generator;

/**
 * Service-provider interface for the rendering (output) half of Migraphe's generator system.
 *
 * <p>An output plugin consumes a data object produced by a {@link GeneratorSourcePlugin} and
 * renders it into a concrete artifact — for example Markdown documentation, JSON, or any other
 * format. This source/output split lets one source feed many output formats and one output format
 * consume many sources, provided the output plugin {@linkplain #canHandle(Class) accepts} the data
 * type the source produced.
 *
 * <p>Implementations are discovered at runtime through the {@link java.util.ServiceLoader}
 * mechanism. To register an output plugin, list its fully qualified class name in a {@code
 * META-INF/services/io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin} resource file
 * on the classpath. The runtime selects an implementation by matching the generator's {@code type}
 * value against {@link #type()}, then verifies the source data is acceptable via {@link
 * #canHandle(Class)} before invoking {@link #output(Object, OutputContext)}.
 *
 * <p>Implementors must provide a stable {@link #type()} identifier, declare which data types they
 * can render via {@link #canHandle(Class)}, expose the {@link GeneratorDefinition} subtype that
 * describes their configuration via {@link #definitionClass()}, and perform the rendering in {@link
 * #output(Object, OutputContext)}.
 *
 * @see GeneratorSourcePlugin
 * @see OutputContext
 * @see GeneratorDefinition
 */
public interface GeneratorOutputPlugin {

    /**
     * Returns the type identifier of this output plugin.
     *
     * <p>This value is matched against the {@code type} field of a generator's configuration to
     * select the output plugin (for example {@code "jdbc-markdown"} or {@code "output-json"}). It
     * must be unique among the output plugins on the classpath.
     *
     * @return the output-type identifier used to select this plugin from configuration
     */
    String type();

    /**
     * Reports whether this plugin can render data of the given type.
     *
     * <p>The runtime calls this with the {@linkplain GeneratorSourcePlugin#dataClass() data class}
     * produced by the chosen source plugin to confirm that the source and output plugins are
     * compatible before rendering.
     *
     * @param dataClass the concrete class of the data object the source plugin produced
     * @return {@code true} if this plugin is able to render data of {@code dataClass}, otherwise
     *     {@code false}
     */
    boolean canHandle(Class<?> dataClass);

    /**
     * Returns the {@link GeneratorDefinition} subtype that describes this plugin's configuration.
     *
     * <p>The runtime uses this class to materialize the plugin-specific configuration (typically a
     * {@code @ConfigMapping} interface) from the project's YAML, which the plugin can then obtain
     * through {@link OutputContext#definitionAs(Class)} during {@link #output(Object,
     * OutputContext)}.
     *
     * @return the {@link Class} of the {@link GeneratorDefinition} subtype this plugin expects
     */
    Class<? extends GeneratorDefinition> definitionClass();

    /**
     * Renders the given data into the destination described by the context.
     *
     * <p>The {@code data} argument is the object produced by the matching {@link
     * GeneratorSourcePlugin} and is guaranteed to be an instance the plugin {@linkplain
     * #canHandle(Class) accepts}; implementations typically cast it to the expected type. The
     * {@link OutputContext} supplies the resolved output directory and a {@link DefinitionResolver}
     * for obtaining the typed {@link GeneratorDefinition} configuration.
     *
     * @param data the data object produced by the source plugin, of a type this plugin can handle
     * @param context the output context carrying the destination directory and configuration
     *     resolver
     */
    void output(Object data, OutputContext context);
}
