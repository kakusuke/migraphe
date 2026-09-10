package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.core.execution.support.FingerprintedNode;
import io.github.kakusuke.migraphe.core.execution.support.ThrowingFingerprintNode;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTarget;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GraphDifference")
class GraphDifferenceTest {

    private final Target db1 = SimpleTarget.create(TargetId.of("db1"), "db1");
    private final Target db2 = SimpleTarget.create(TargetId.of("db2"), "db2");

    @Test
    @DisplayName("定義側と履歴側を突き合わせ、片側だけのもの・内容が違うもの・比較できないものを分ける")
    void separatesWhatIsOnOneSideOnlyFromWhatDiffersAndFromWhatCannotBeCompared() {
        MigrationGraph definitions = MigrationGraph.create();
        definitions.addNode(node(db1, "db1/same", NodeId.of("db1/pending")));
        definitions.addNode(node(db1, "db1/edited"));
        definitions.addNode(node(db1, "db1/pending"));
        definitions.addNode(node(db2, "db2/legacy"));

        InMemoryHistoryRepository historyRepo = new InMemoryHistoryRepository();
        historyRepo.record(row(db1, "db1/same", tokenOf(definitions, "db1/same")));
        historyRepo.record(row(db1, "db1/edited", "stale-token"));
        historyRepo.record(row(db1, "db1/orphan", "orphan-token"));
        historyRepo.record(row(db2, "db2/legacy", null));
        MigrationGraph history = RecordedGraph.of(historyRepo, List.of(db1, db2)).graph();

        GraphDifference difference = GraphDifference.between(definitions, history);

        assertThat(difference.onlyInHistory()).containsExactly(NodeId.of("db1/orphan"));
        assertThat(difference.onlyInDefinitions()).containsExactly(NodeId.of("db1/pending"));
        assertThat(difference.contentDiffers()).containsExactly(NodeId.of("db1/edited"));
        assertThat(difference.cannotCompare()).containsExactly(NodeId.of("db2/legacy"));
    }

    @Test
    @DisplayName("target が動いたノードは、他の編集と同じく「内容が違う」ものとして一度だけ現れる")
    void treatsAMigrationWhoseTargetMovedAsContentThatDiffers() {
        MigrationGraph asApplied = MigrationGraph.create();
        asApplied.addNode(node(db1, "tasks/moved"));

        MigrationGraph definitions = MigrationGraph.create();
        definitions.addNode(node(db2, "tasks/moved"));

        InMemoryHistoryRepository historyRepo = new InMemoryHistoryRepository();
        historyRepo.record(row(db1, "tasks/moved", tokenOf(asApplied, "tasks/moved")));
        MigrationGraph history = RecordedGraph.of(historyRepo, List.of(db1, db2)).graph();

        GraphDifference difference = GraphDifference.between(definitions, history);

        assertThat(difference.contentDiffers()).containsExactly(NodeId.of("tasks/moved"));
        assertThat(difference.onlyInHistory()).isEmpty();
        assertThat(difference.onlyInDefinitions()).isEmpty();
        assertThat(difference.cannotCompare()).isEmpty();
    }

    @Test
    @DisplayName("投げた比較は、トークンが無いだけのものとは別に報告される")
    void tellsAThrownComparisonFromAnAbsentToken() {
        MigrationGraph definitions = MigrationGraph.create();
        definitions.addNode(new ThrowingFingerprintNode(node(db1, "db1/throwing")));
        definitions.addNode(node(db1, "db1/legacy"));

        InMemoryHistoryRepository historyRepo = new InMemoryHistoryRepository();
        historyRepo.record(row(db1, "db1/throwing", "recorded"));
        historyRepo.record(row(db1, "db1/legacy", null));
        MigrationGraph history = RecordedGraph.of(historyRepo, List.of(db1)).graph();

        GraphDifference difference = GraphDifference.between(definitions, history);

        assertThat(difference.unreadable()).containsExactly(NodeId.of("db1/throwing"));
        assertThat(difference.cannotCompare()).containsExactly(NodeId.of("db1/legacy"));
        assertThat(difference.contentDiffers()).isEmpty();
        assertThat(difference.onlyInHistory()).isEmpty();
        assertThat(difference.onlyInDefinitions()).isEmpty();
    }

    @Test
    @DisplayName("定義がトークンを出さないなら比較不能ではなく読めない — 記録側の null だけが比較不能")
    void treatsADefinitionThatSuppliesNoTokenAsUnreadableRatherThanUncomparable() {
        MigrationGraph definitions = MigrationGraph.create();
        definitions.addNode(new FingerprintedNode(node(db1, "db1/no_token"), null));

        InMemoryHistoryRepository historyRepo = new InMemoryHistoryRepository();
        historyRepo.record(row(db1, "db1/no_token", "recorded"));
        MigrationGraph history = RecordedGraph.of(historyRepo, List.of(db1)).graph();

        GraphDifference difference = GraphDifference.between(definitions, history);

        assertThat(difference.unreadable()).containsExactly(NodeId.of("db1/no_token"));
        assertThat(difference.cannotCompare()).isEmpty();
    }

    private MigrationNode node(Target target, String id, NodeId... dependencies) {
        return SimpleMigrationNode.builder()
                .id(id)
                .name(id)
                .target(target)
                .dependencies(dependencies)
                .upTask(SimpleTask.of("CREATE " + id))
                .build();
    }

    private static String tokenOf(MigrationGraph graph, String id) {
        NodeId nodeId = NodeId.of(id);
        String token =
                graph.getNode(nodeId).orElseThrow().fingerprint(graph.fingerprinterFor(nodeId));
        if (token == null) {
            throw new IllegalStateException("the reference node must supply a fingerprint");
        }
        return token;
    }

    private ExecutionRecord row(Target target, String id, @Nullable String fingerprint) {
        return ExecutionRecord.upSuccess(
                NodeId.of(id), target.id(), id, "DROP " + id, 1L, fingerprint, null, List.of());
    }
}
