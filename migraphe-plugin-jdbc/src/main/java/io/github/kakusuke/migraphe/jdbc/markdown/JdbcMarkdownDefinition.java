package io.github.kakusuke.migraphe.jdbc.markdown;

import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;

/**
 * Configuration for the JDBC Markdown documentation generator.
 *
 * <p>This is a SmallRye {@code @ConfigMapping} interface mapped from the project's YAML; it
 * supplies the settings that {@link JdbcMarkdownPlugin} uses when rendering JDBC schema information
 * into Markdown. It extends {@link GeneratorDefinition} so the runtime can materialize it through
 * {@code OutputContext.definitionAs(...)} when the generator {@code type} resolves to {@code
 * "jdbc-markdown"}. PostgreSQL and MySQL Markdown generators reuse the same configuration shape.
 */
@ConfigMapping(prefix = "")
public interface JdbcMarkdownDefinition extends GeneratorDefinition {

    /**
     * Returns the generator type identifier from configuration.
     *
     * @return the configured generator {@code type} value (for example {@code "jdbc-markdown"})
     */
    @Override
    String type();

    /**
     * Returns the database name used to title the generated documentation and to namespace the
     * output directory layout.
     *
     * @return the database name from configuration
     */
    String name();

    /**
     * Returns the directory into which the Markdown files are written.
     *
     * @return the output directory path, defaulting to {@code "docs/schema"}
     */
    @WithDefault("docs/schema")
    String outputDir();

    /**
     * Returns whether the ER Diagram section is emitted in the generated {@code index.md}.
     *
     * @return {@code true} to include the ER Diagram section, defaulting to {@code true}
     */
    @WithDefault("true")
    boolean erDiagram();

    /**
     * Returns whether the ER Diagram limits entity columns to primary-key and foreign-key columns.
     *
     * <p>When {@code false} (the default) every column is rendered; when {@code true} only columns
     * that participate in a primary key or a foreign key are shown, yielding a more compact
     * diagram.
     *
     * @return {@code true} to show only PK/FK columns, defaulting to {@code false} (all columns)
     */
    @WithDefault("false")
    boolean erDiagramKeysOnly();

    /**
     * Returns the schema/table exclusion patterns to skip during generation.
     *
     * @return the exclusion patterns, or an empty {@link Optional} when none are configured
     */
    Optional<List<ExcludePattern>> excludes();

    /**
     * A single exclusion rule matching schemas and/or tables to omit from the generated
     * documentation.
     *
     * <p>The {@link #schema()} and {@link #table()} values are treated as case-insensitive regular
     * expressions by {@link JdbcMarkdownGenerator}. A rule with only {@link #schema()} present
     * excludes whole schemas; a rule with {@link #table()} present excludes matching tables,
     * optionally scoped to a matching {@link #schema()}.
     */
    interface ExcludePattern {

        /**
         * Returns the schema-name pattern to exclude.
         *
         * @return the schema pattern, or an empty {@link Optional} if the rule is not scoped to a
         *     schema
         */
        Optional<String> schema();

        /**
         * Returns the table-name pattern to exclude.
         *
         * @return the table pattern, or an empty {@link Optional} if the rule targets a whole
         *     schema rather than specific tables
         */
        Optional<String> table();
    }
}
