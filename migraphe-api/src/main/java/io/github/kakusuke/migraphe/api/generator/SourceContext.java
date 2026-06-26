package io.github.kakusuke.migraphe.api.generator;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import org.jspecify.annotations.Nullable;

/**
 * Inputs handed to a {@link GeneratorSourcePlugin} when it extracts data.
 *
 * <p>The runtime assembles this context for each generator run and passes it to {@link
 * GeneratorSourcePlugin#extract(SourceContext)}. Every component is optional because different
 * sources need different inputs: a schema source needs an {@link Environment} to connect to, while
 * a migration-tree source needs the {@link MigrationGraphView}. A source plugin should read only
 * the components it requires and treat the others as potentially absent.
 *
 * @param environment the target environment to extract from, or {@code null} when the generator is
 *     not bound to a specific target
 * @param graph a read-only view of the migration graph, or {@code null} when the generator does not
 *     operate on the graph structure
 * @param historyRepository the execution-history repository, or {@code null} when execution history
 *     is not relevant to the generator
 * @see GeneratorSourcePlugin#extract(SourceContext)
 */
public record SourceContext(
        @Nullable Environment environment,
        @Nullable MigrationGraphView graph,
        @Nullable HistoryRepository historyRepository) {

    /**
     * Creates a context without a history repository.
     *
     * <p>Convenience constructor for sources that only need the environment and/or the migration
     * graph; {@link #historyRepository()} is left {@code null}.
     *
     * @param environment the target environment to extract from, or {@code null} if not applicable
     * @param graph a read-only view of the migration graph, or {@code null} if not applicable
     */
    public SourceContext(@Nullable Environment environment, @Nullable MigrationGraphView graph) {
        this(environment, graph, null);
    }
}
