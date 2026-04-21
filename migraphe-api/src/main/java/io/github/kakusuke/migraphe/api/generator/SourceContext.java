package io.github.kakusuke.migraphe.api.generator;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import org.jspecify.annotations.Nullable;

public record SourceContext(
        @Nullable Environment environment,
        @Nullable MigrationGraphView graph,
        @Nullable HistoryRepository historyRepository) {

    /** HistoryRepository なしのコンストラクタ（後方互換性）。 */
    public SourceContext(@Nullable Environment environment, @Nullable MigrationGraphView graph) {
        this(environment, graph, null);
    }
}
