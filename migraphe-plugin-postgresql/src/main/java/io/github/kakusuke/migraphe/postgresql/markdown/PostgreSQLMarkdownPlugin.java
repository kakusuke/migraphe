package io.github.kakusuke.migraphe.postgresql.markdown;

import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;
import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownDefinition;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLSchemaInfo;

/**
 * {@link GeneratorOutputPlugin} that renders PostgreSQL schema information to Markdown ({@code
 * type="postgresql-markdown"}).
 *
 * <p>It consumes a {@link PostgreSQLSchemaInfo} (typically produced by the {@code
 * postgresql-schema} source plugin) and writes Markdown documentation via {@link
 * PostgreSQLMarkdownGenerator}, which extends the generic {@code jdbc-markdown} generator with
 * PostgreSQL extras (extensions, enums, sequences, functions, triggers, materialized views,
 * partitions, policies, owners). Discovered at runtime via {@link java.util.ServiceLoader}
 * (declared in {@code
 * META-INF/services/io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin}). It reuses
 * the generic {@link JdbcMarkdownDefinition} for its configuration.
 */
public final class PostgreSQLMarkdownPlugin implements GeneratorOutputPlugin {

    /** Creates a new {@code PostgreSQLMarkdownPlugin}. */
    public PostgreSQLMarkdownPlugin() {}

    /**
     * Returns the output-type discriminator.
     *
     * @return the string {@code "postgresql-markdown"}
     */
    @Override
    public String type() {
        return "postgresql-markdown";
    }

    /**
     * Returns the {@link GeneratorDefinition} subtype this plugin binds generator configuration to.
     *
     * @return {@link JdbcMarkdownDefinition}{@code .class}
     */
    @Override
    public Class<? extends GeneratorDefinition> definitionClass() {
        return JdbcMarkdownDefinition.class;
    }

    /**
     * Reports whether this plugin can render the given source-data type.
     *
     * @param dataClass the runtime type of the source data
     * @return {@code true} if {@code dataClass} is {@link PostgreSQLSchemaInfo} or a subtype
     */
    @Override
    public boolean canHandle(Class<?> dataClass) {
        return PostgreSQLSchemaInfo.class.isAssignableFrom(dataClass);
    }

    /**
     * Renders the given PostgreSQL schema information to Markdown under the context's output
     * directory.
     *
     * @param data the source data; must be a {@link PostgreSQLSchemaInfo}
     * @param context the output context providing the {@link JdbcMarkdownDefinition} configuration
     *     and the target output directory
     */
    @Override
    public void output(Object data, OutputContext context) {
        var schemaInfo = (PostgreSQLSchemaInfo) data;
        var definition = context.definitionAs(JdbcMarkdownDefinition.class);
        var excludes = definition.excludes().orElse(java.util.List.of());
        var generator = new PostgreSQLMarkdownGenerator(definition.name(), schemaInfo, excludes);
        generator.generate(context.outputDir());
    }
}
