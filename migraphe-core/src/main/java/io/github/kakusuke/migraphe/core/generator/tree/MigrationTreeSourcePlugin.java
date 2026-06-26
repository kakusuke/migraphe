package io.github.kakusuke.migraphe.core.generator.tree;

import io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.api.generator.SourceContext;
import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import java.util.List;
import java.util.Objects;

/**
 * Built-in generator source that extracts the migration graph as serializable tree data.
 *
 * <p>This {@link GeneratorSourcePlugin} is registered under the {@code "migration-tree"} type and
 * produces a {@link MigrationTreeData} object: one entry per node with its id, name, target
 * environment, dependencies, and execution status. The status is derived from the optional {@link
 * HistoryRepository} in the {@link SourceContext} ({@code "executed"} when a record exists for the
 * node/environment pair, otherwise {@code "pending"}). Entries and each entry's dependency list are
 * sorted for deterministic output. The resulting data can be rendered by any compatible output
 * plugin (for example the JSON output plugin).
 *
 * <p>Like other source plugins it is discovered via {@link java.util.ServiceLoader}; this core
 * built-in is listed in the module's {@code
 * META-INF/services/io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin} resource.
 */
public final class MigrationTreeSourcePlugin implements GeneratorSourcePlugin<MigrationTreeData> {

    /** Creates a new {@code MigrationTreeSourcePlugin}. */
    public MigrationTreeSourcePlugin() {}

    /**
     * {@inheritDoc}
     *
     * @return the fixed type identifier {@code "migration-tree"}
     */
    @Override
    public String type() {
        return "migration-tree";
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code MigrationTreeData.class}, the data type this source produces
     */
    @Override
    public Class<MigrationTreeData> dataClass() {
        return MigrationTreeData.class;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads the migration graph from the context and maps each node to a {@link
     * MigrationTreeData.NodeEntry}, sorting both the entries and each node's dependencies by id,
     * and marking each node {@code "executed"} or {@code "pending"} based on the optional history
     * repository.
     *
     * @throws NullPointerException if {@code context.graph()} is {@code null}; a migration graph is
     *     required by this source
     */
    @Override
    public MigrationTreeData extract(SourceContext context) {
        MigrationGraphView graph =
                Objects.requireNonNull(
                        context.graph(),
                        "MigrationGraphView (graph) is required for migration-tree source");
        HistoryRepository historyRepository = context.historyRepository();
        List<MigrationTreeData.NodeEntry> entries =
                graph.allNodes().stream()
                        .map(
                                node -> {
                                    List<String> deps =
                                            graph.getDependencies(node.id()).stream()
                                                    .map(id -> id.value())
                                                    .sorted()
                                                    .toList();
                                    String status =
                                            historyRepository != null
                                                            && historyRepository.wasExecuted(
                                                                    node.id(),
                                                                    node.environment().id())
                                                    ? "executed"
                                                    : "pending";
                                    return new MigrationTreeData.NodeEntry(
                                            node.id().value(),
                                            node.name(),
                                            node.environment().id().value(),
                                            status,
                                            deps);
                                })
                        .sorted((a, b) -> a.id().compareTo(b.id()))
                        .toList();
        return new MigrationTreeData(entries);
    }
}
