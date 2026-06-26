package io.github.kakusuke.migraphe.mysql.markdown;

import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;
import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownDefinition;
import io.github.kakusuke.migraphe.mysql.schema.MySQLSchemaInfo;
import java.util.List;

/**
 * {@link GeneratorOutputPlugin} that renders {@link MySQLSchemaInfo} into Markdown documentation.
 *
 * <p>Registered under the {@code "mysql-markdown"} output type, this plugin consumes the MySQL
 * schema data produced by the MySQL schema source plugin and delegates rendering to {@link
 * MySQLMarkdownGenerator}, which augments the generic JDBC Markdown output with MySQL-specific
 * sections (storage engines, table properties, triggers, routines, events, and partitions).
 *
 * <p>Discovered at runtime via {@link java.util.ServiceLoader} through the {@code
 * META-INF/services/io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin} resource.
 *
 * @see MySQLMarkdownGenerator
 * @see JdbcMarkdownDefinition
 */
public final class MySQLMarkdownPlugin implements GeneratorOutputPlugin {

    /** Creates a new {@code MySQLMarkdownPlugin}. */
    public MySQLMarkdownPlugin() {}

    /**
     * {@inheritDoc}
     *
     * @return the literal {@code "mysql-markdown"}
     */
    @Override
    public String type() {
        return "mysql-markdown";
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link JdbcMarkdownDefinition}, the configuration type shared with the JDBC Markdown
     *     output
     */
    @Override
    public Class<? extends GeneratorDefinition> definitionClass() {
        return JdbcMarkdownDefinition.class;
    }

    /**
     * {@inheritDoc}
     *
     * @param dataClass the concrete class of the data object the source plugin produced
     * @return {@code true} if {@code dataClass} is a {@link MySQLSchemaInfo} (or a subtype),
     *     otherwise {@code false}
     */
    @Override
    public boolean canHandle(Class<?> dataClass) {
        return MySQLSchemaInfo.class.isAssignableFrom(dataClass);
    }

    /**
     * Renders the given {@link MySQLSchemaInfo} into Markdown files under the context's output
     * directory.
     *
     * @param data the schema data to render; must be a {@link MySQLSchemaInfo}
     * @param context the output context supplying the destination directory and the {@link
     *     JdbcMarkdownDefinition} configuration
     */
    @Override
    public void output(Object data, OutputContext context) {
        var schemaInfo = (MySQLSchemaInfo) data;
        var definition = context.definitionAs(JdbcMarkdownDefinition.class);
        var excludes = definition.excludes().orElse(List.of());
        var generator = new MySQLMarkdownGenerator(definition.name(), schemaInfo, excludes);
        generator.generate(context.outputDir());
    }
}
