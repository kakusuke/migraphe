package io.github.kakusuke.migraphe.api.generator;

import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.target.Target;
import org.jspecify.annotations.Nullable;

/**
 * Inputs handed to a {@link GeneratorSourcePlugin} when it extracts data.
 *
 * <p>The runtime assembles this context for each generator run and passes it to {@link
 * GeneratorSourcePlugin#extract(SourceContext)}. Every component is optional because different
 * sources need different inputs: a schema source needs a {@link Target} to connect to, while a
 * migration-tree source needs the {@link MigrationGraphView}. A source plugin should read only the
 * components it requires and treat the others as potentially absent.
 *
 * @param target the target to extract from, or {@code null} when the generator is not bound to a
 *     specific target
 * @param graph a read-only view of the migration graph, or {@code null} when the generator does not
 *     operate on the graph structure
 * @param historyRepository the execution-history repository, or {@code null} when execution history
 *     is not relevant to the generator
 * @see GeneratorSourcePlugin#extract(SourceContext)
 */
public record SourceContext(
        @Nullable Target target,
        @Nullable MigrationGraphView graph,
        @Nullable HistoryRepository historyRepository) {

    /**
     * Creates a context without a history repository.
     *
     * <p>Convenience constructor for sources that only need the target and/or the migration graph;
     * {@link #historyRepository()} is left {@code null}.
     *
     * @param target the target to extract from, or {@code null} if not applicable
     * @param graph a read-only view of the migration graph, or {@code null} if not applicable
     */
    public SourceContext(@Nullable Target target, @Nullable MigrationGraphView graph) {
        this(target, graph, null);
    }
}
