package io.github.kakusuke.migraphe.core.generator.tree;

import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.generator.api.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.generator.api.SourceContext;
import java.util.Objects;

public final class MigrationTreeSourcePlugin implements GeneratorSourcePlugin<MigrationGraphView> {

    @Override
    public String type() {
        return "migration-tree";
    }

    @Override
    public Class<MigrationGraphView> dataClass() {
        return MigrationGraphView.class;
    }

    @Override
    public MigrationGraphView extract(SourceContext context) {
        return Objects.requireNonNull(
                context.graph(),
                "MigrationGraphView (graph) is required for migration-tree source");
    }
}
