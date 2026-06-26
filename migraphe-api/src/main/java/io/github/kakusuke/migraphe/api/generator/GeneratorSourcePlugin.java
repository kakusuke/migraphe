package io.github.kakusuke.migraphe.api.generator;

/**
 * Service-provider interface for the data-extraction (source) half of Migraphe's generator system.
 *
 * <p>A generator in Migraphe is split into two cooperating plugins: a source plugin, which extracts
 * a typed data object from the migration project (for example database schema information or the
 * migration graph), and a {@link GeneratorOutputPlugin}, which renders that data into a concrete
 * artifact (for example Markdown documentation or JSON). This separation lets a single source feed
 * many output formats, and a single output format consume many sources, as long as the output
 * plugin {@linkplain GeneratorOutputPlugin#canHandle(Class) accepts} the source's data type.
 *
 * <p>Implementations are discovered at runtime through the {@link java.util.ServiceLoader}
 * mechanism. To register a source plugin, list its fully qualified class name in a {@code
 * META-INF/services/io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin} resource file
 * on the classpath. The runtime selects an implementation by matching the {@code source.type} value
 * from a generator's configuration against {@link #type()}.
 *
 * <p>Implementors must provide a stable {@link #type()} identifier, declare the concrete data type
 * they produce via {@link #dataClass()}, and perform the extraction in {@link
 * #extract(SourceContext)} using whatever inputs the supplied {@link SourceContext} carries.
 *
 * @param <T> the type of data object this source produces and hands to a compatible output plugin
 * @see GeneratorOutputPlugin
 * @see SourceContext
 */
public interface GeneratorSourcePlugin<T> {

    /**
     * Returns the type identifier of this source plugin.
     *
     * <p>This value is matched against the {@code source.type} field of a generator's configuration
     * to select the source plugin (for example {@code "jdbc-schema"} or {@code "migration-tree"}).
     * It must be unique among the source plugins on the classpath.
     *
     * @return the source-type identifier used to select this plugin from configuration
     */
    String type();

    /**
     * Returns the concrete class of the data object that {@link #extract(SourceContext)} produces.
     *
     * <p>The runtime uses this to route the extracted data to an output plugin that {@linkplain
     * GeneratorOutputPlugin#canHandle(Class) declares it can handle} this type.
     *
     * @return the {@link Class} of the data type produced by this source plugin
     */
    Class<T> dataClass();

    /**
     * Extracts the data object for this generator from the given context.
     *
     * <p>The {@link SourceContext} supplies the inputs a source may need — an {@link
     * io.github.kakusuke.migraphe.api.environment.Environment}, a {@link
     * io.github.kakusuke.migraphe.api.graph.MigrationGraphView}, and a {@link
     * io.github.kakusuke.migraphe.api.history.HistoryRepository} — any of which may be absent
     * depending on the kind of generator being run. Implementations should read only the fields
     * they require and validate their presence.
     *
     * @param context the extraction context carrying the optional environment, migration graph, and
     *     history repository
     * @return the extracted data object, to be passed to a compatible {@link GeneratorOutputPlugin}
     */
    T extract(SourceContext context);
}
