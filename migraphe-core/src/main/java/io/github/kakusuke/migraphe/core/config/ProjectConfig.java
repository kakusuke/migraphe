package io.github.kakusuke.migraphe.core.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;

/**
 * The top-level, project-wide configuration mapped from {@code migraphe.yaml}.
 *
 * <p>This is a SmallRye {@code @ConfigMapping} interface (empty prefix) bound against the merged
 * config produced by {@link ConfigLoader}: SmallRye proxies it and resolves each accessor against
 * the layered config sources, expanding {@code ${...}} references as it goes. It groups the stable,
 * statically-known sections of the configuration ({@code project}, {@code history}, {@code
 * execution}, {@code generators}); the dynamically-keyed {@code target.*} and {@code task.*}
 * entries are read programmatically elsewhere rather than through this mapping.
 */
@ConfigMapping(prefix = "")
public interface ProjectConfig {

    /**
     * The {@code project} section (name and scan-root).
     *
     * @return the project-information section
     */
    ProjectSection project();

    /**
     * The {@code history} section (where migration history is stored).
     *
     * @return the history-management section
     */
    HistorySection history();

    /**
     * The {@code execution} section (parallelism settings).
     *
     * @return the execution section
     */
    ExecutionSection execution();

    /**
     * The optional list of generator definitions.
     *
     * @return the generator sections, or an empty {@link Optional} if none are configured
     */
    Optional<List<GeneratorSection>> generators();

    /** The {@code project} section: project identity and the configuration scan root. */
    interface ProjectSection {
        /**
         * The human-readable project name.
         *
         * @return the project name
         */
        String name();

        /**
         * The root directory under which {@code tasks/}, {@code targets/}, {@code environments/}
         * and {@code plugins} are looked up.
         *
         * <p>May be a path relative to the directory containing {@code migraphe.yaml}, or an
         * absolute path. When absent, the scan root defaults to {@code migraphe.yaml}'s own parent
         * directory.
         *
         * @return the scan-root path string, or an empty {@link Optional} if unspecified
         */
        Optional<String> scanRoot();
    }

    /** The {@code history} section: where execution history is persisted. */
    interface HistorySection {
        /**
         * The id of the target used to store migration history.
         *
         * @return the history target id
         */
        String target();
    }

    /** The {@code execution} section: how migrations are scheduled. */
    interface ExecutionSection {
        /**
         * Whether ready nodes may be executed in parallel.
         *
         * @return {@code true} if parallel execution is enabled (defaults to {@code false})
         */
        @WithDefault("false")
        boolean parallel();

        /**
         * The maximum number of nodes to run concurrently; {@code 0} means unbounded. Only
         * meaningful when {@link #parallel()} is {@code true}.
         *
         * @return the maximum parallelism (defaults to {@code 0})
         */
        @WithDefault("0")
        int maxParallelism();
    }

    /** A single generator definition under the {@code generators} list. */
    interface GeneratorSection {
        /**
         * The unique name identifying this generator (selectable via {@code migraphe generate
         * --name}).
         *
         * @return the generator name
         */
        String name();

        /**
         * The output-plugin type that renders the extracted data (for example {@code jdbc-markdown}
         * or {@code output-json}).
         *
         * @return the output-plugin type identifier
         */
        String type();

        /**
         * The source sub-section describing where this generator's input data comes from.
         *
         * @return the source configuration
         */
        SourceSection source();

        /**
         * The directory, relative to the project, into which generated artifacts are written.
         *
         * @return the output directory (defaults to {@code docs/schema})
         */
        @WithDefault("docs/schema")
        String outputDir();

        /**
         * Optional patterns identifying schema objects to exclude from generation.
         *
         * @return the exclude rules, or an empty {@link Optional} if none are configured
         */
        Optional<List<ExcludeSection>> excludes();

        /** The {@code source} sub-section: which source plugin feeds the generator. */
        interface SourceSection {
            /**
             * The source-plugin type that extracts data (for example {@code jdbc-schema} or {@code
             * migration-tree}). When absent, the generator's output type may imply a default
             * source.
             *
             * @return the source-plugin type identifier, or an empty {@link Optional} if unset
             */
            Optional<String> type();

            /**
             * The id of the target the source plugin should read from (for schema-extracting
             * sources).
             *
             * @return the source target id, or an empty {@link Optional} if not applicable
             */
            Optional<String> target();
        }

        /** A single exclusion rule matching schema objects to skip. */
        interface ExcludeSection {
            /**
             * A schema-name pattern to exclude.
             *
             * @return the schema pattern, or an empty {@link Optional} if not set
             */
            Optional<String> schema();

            /**
             * A table-name pattern to exclude.
             *
             * @return the table pattern, or an empty {@link Optional} if not set
             */
            Optional<String> table();
        }
    }
}
