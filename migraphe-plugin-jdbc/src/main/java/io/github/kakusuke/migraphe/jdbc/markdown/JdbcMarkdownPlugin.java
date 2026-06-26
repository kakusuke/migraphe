package io.github.kakusuke.migraphe.jdbc.markdown;

import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;
import io.github.kakusuke.migraphe.jdbc.schema.JdbcSchemaInfo;
import java.util.List;

/**
 * Generator output plugin that renders {@link JdbcSchemaInfo} into Markdown documentation.
 *
 * <p>This is the {@code "jdbc-markdown"} implementation of {@link GeneratorOutputPlugin}. It is
 * discovered at runtime via {@link java.util.ServiceLoader} through the {@code
 * META-INF/services/io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin} resource. When
 * the generator framework pairs a schema source plugin (which produces a {@link JdbcSchemaInfo})
 * with this output plugin, it invokes {@link #output(Object, OutputContext)}, which delegates the
 * actual rendering to {@link JdbcMarkdownGenerator}. The PostgreSQL and MySQL plugins reuse this
 * pattern with their own {@code GeneratorOutputPlugin} registrations.
 */
public final class JdbcMarkdownPlugin implements GeneratorOutputPlugin {

    /** Creates a new {@code JdbcMarkdownPlugin}. */
    public JdbcMarkdownPlugin() {}

    /**
     * Returns the type identifier used to select this output plugin from configuration.
     *
     * @return the string {@code "jdbc-markdown"}
     */
    @Override
    public String type() {
        return "jdbc-markdown";
    }

    /**
     * Returns the configuration type this plugin expects.
     *
     * @return {@link JdbcMarkdownDefinition}{@code .class}
     */
    @Override
    public Class<? extends GeneratorDefinition> definitionClass() {
        return JdbcMarkdownDefinition.class;
    }

    /**
     * Reports whether this plugin can render data of the given type.
     *
     * @param dataClass the concrete class of the data object produced by the source plugin
     * @return {@code true} if {@code dataClass} is {@link JdbcSchemaInfo} or a subtype, otherwise
     *     {@code false}
     */
    @Override
    public boolean canHandle(Class<?> dataClass) {
        return JdbcSchemaInfo.class.isAssignableFrom(dataClass);
    }

    /**
     * Renders the supplied schema information into Markdown files under the context's output
     * directory.
     *
     * <p>The {@code data} argument is cast to {@link JdbcSchemaInfo}; it is guaranteed to be
     * acceptable because the framework checks {@link #canHandle(Class)} first. The {@link
     * JdbcMarkdownDefinition} configuration is resolved from the context and used to build a {@link
     * JdbcMarkdownGenerator}, which writes the documentation.
     *
     * @param data the schema information produced by the source plugin (a {@link JdbcSchemaInfo})
     * @param context the output context supplying the destination directory and configuration
     *     resolver
     */
    @Override
    public void output(Object data, OutputContext context) {
        var schemaInfo = (JdbcSchemaInfo) data;
        var definition = context.definitionAs(JdbcMarkdownDefinition.class);
        var generator =
                new JdbcMarkdownGenerator(
                        definition.name(), schemaInfo, resolveExcludes(definition));
        generator.generate(context.outputDir());
    }

    private static List<JdbcMarkdownDefinition.ExcludePattern> resolveExcludes(
            JdbcMarkdownDefinition definition) {
        return definition.excludes().orElse(List.of());
    }
}
