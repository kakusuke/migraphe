package io.github.kakusuke.migraphe.core.generator.tree;

import io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.api.generator.SourceContext;
import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import java.util.List;
import java.util.Objects;

public final class MigrationTreeSourcePlugin implements GeneratorSourcePlugin<MigrationTreeData> {

    @Override
    public String type() {
        return "migration-tree";
    }

    @Override
    public Class<MigrationTreeData> dataClass() {
        return MigrationTreeData.class;
    }

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
