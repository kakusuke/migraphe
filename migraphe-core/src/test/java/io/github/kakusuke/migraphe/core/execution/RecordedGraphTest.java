package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionOrigin;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.ExecutionStatus;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.core.execution.support.DelegatingHistoryRepository;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.SimpleTarget;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RecordedGraph")
class RecordedGraphTest {

    private final Target target = SimpleTarget.create(TargetId.of("db1"), "db1");

    @Test
    @DisplayName("適用済みの行だけを、記録された辺とトークンを持つグラフに組む")
    void buildsAGraphOfWhatTheHistorySaysIsAppliedWithTheEdgesAndTokensItRecorded() {
        InMemoryHistoryRepository historyRepo = new InMemoryHistoryRepository();
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/a"),
                        target.id(),
                        "A",
                        "DROP a",
                        1L,
                        "token-a",
                        null,
                        List.of()));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/b"),
                        target.id(),
                        "B",
                        "DROP b",
                        1L,
                        "token-b",
                        null,
                        List.of(NodeId.of("db1/a"), NodeId.of("db1/c"))));
        historyRepo.record(
                ExecutionRecord.failure(
                        NodeId.of("db1/b"), target.id(), ExecutionDirection.DOWN, "B", "boom"));
        historyRepo.record(
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/c"),
                        target.id(),
                        "C",
                        "DROP c",
                        1L,
                        "token-c",
                        null,
                        List.of()));
        historyRepo.record(ExecutionRecord.downSuccess(NodeId.of("db1/c"), target.id(), "C", 1L));

        MigrationGraph recorded = RecordedGraph.of(historyRepo, List.of(target)).graph();

        assertThat(recorded.allNodes())
                .extracting(MigrationNode::id)
                .containsExactlyInAnyOrder(NodeId.of("db1/a"), NodeId.of("db1/b"));
        assertThat(recorded.getAllDependents(NodeId.of("db1/a")))
                .containsExactly(NodeId.of("db1/b"));
        assertThat(recorded.getDependencies(NodeId.of("db1/b")))
                .containsExactlyInAnyOrder(NodeId.of("db1/a"), NodeId.of("db1/c"));
        assertThat(nodeIn(recorded, "db1/b").fingerprint(signatures -> "computed"))
                .isEqualTo("token-b");
        assertThat(nodeIn(recorded, "db1/b").target()).isSameAs(target);
    }

    @Test
    @DisplayName("適用行が複数あるノードは、最後の適用を報告する")
    void reportsTheLastApplyOfANodeThatWasAppliedMoreThanOnce() {
        InMemoryHistoryRepository historyRepo = new InMemoryHistoryRepository();
        Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
        Instant later = Instant.parse("2026-01-02T00:00:00Z");
        historyRepo.record(
                amendedRow(
                        "r2",
                        NodeId.of("db1/a"),
                        later,
                        "token-later",
                        List.of(NodeId.of("db1/z"))));
        historyRepo.record(
                appliedRow("r1", NodeId.of("db1/a"), earlier, "token-earlier", List.of()));
        historyRepo.record(
                amendedRow("bbb", NodeId.of("db1/b"), earlier, "token-higher-id", List.of()));
        historyRepo.record(
                appliedRow("aaa", NodeId.of("db1/b"), earlier, "token-lower-id", List.of()));

        MigrationGraph recorded = RecordedGraph.of(historyRepo, List.of(target)).graph();

        assertThat(nodeIn(recorded, "db1/a").fingerprint(signatures -> "computed"))
                .isEqualTo("token-later");
        assertThat(recorded.getDependencies(NodeId.of("db1/a")))
                .containsExactly(NodeId.of("db1/z"));
        assertThat(nodeIn(recorded, "db1/b").fingerprint(signatures -> "computed"))
                .isEqualTo("token-higher-id");
    }

    @Test
    @DisplayName("適用済みの行はリポジトリの latestApplies() から読む — 自前で畳み直さない")
    void readsTheAppliedRowsFromTheRepositorysOwnAnswer() {
        InMemoryHistoryRepository backing = new InMemoryHistoryRepository();
        backing.record(
                appliedRow(
                        "r1",
                        NodeId.of("db1/a"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "stored",
                        List.of()));

        ExecutionRecord asTheRepositoryAnswers =
                appliedRow(
                        "r2",
                        NodeId.of("db1/a"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "answered",
                        List.of());
        HistoryRepository history =
                new DelegatingHistoryRepository(backing) {
                    @Override
                    public List<ExecutionRecord> latestApplies() {
                        return List.of(asTheRepositoryAnswers);
                    }
                };

        MigrationGraph graph = RecordedGraph.of(history, List.of(target)).graph();

        assertThat(nodeIn(graph, "db1/a").fingerprint(id -> "unused")).isEqualTo("answered");
    }

    @Test
    @DisplayName("記録された辺が循環していれば、グラフを組まずに拒否する")
    void refusesWhenTheRecordedEdgesFormACycle() {
        Instant applied = Instant.parse("2026-01-01T00:00:00Z");
        InMemoryHistoryRepository historyRepo = new InMemoryHistoryRepository();
        historyRepo.record(
                appliedRow(
                        "r1", NodeId.of("db1/a"), applied, "token-a", List.of(NodeId.of("db1/b"))));
        historyRepo.record(
                appliedRow(
                        "r2", NodeId.of("db1/b"), applied, "token-b", List.of(NodeId.of("db1/a"))));
        historyRepo.record(appliedRow("r3", NodeId.of("db1/c"), applied, "token-c", List.of()));

        assertThatThrownBy(() -> RecordedGraph.of(historyRepo, List.of(target)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle")
                .hasMessageContaining(
                        "\n  db1/a  row=r1  target=db1  stands on db1/b"
                                + "\n  db1/b  row=r2  target=db1  stands on db1/a\n")
                .hasMessageNotContaining("db1/c")
                .hasMessageNotContaining("edited or damaged");
    }

    @Test
    @DisplayName("target_id が解決できない行は、黙って落とさず報告する")
    void reportsARowWhoseTargetNoLongerResolvesRatherThanDroppingIt() {
        Instant applied = Instant.parse("2026-01-01T00:00:00Z");
        InMemoryHistoryRepository historyRepo = new InMemoryHistoryRepository();
        historyRepo.record(appliedRow("r1", NodeId.of("db1/a"), applied, "token-a", List.of()));
        historyRepo.record(
                appliedRow(
                        TargetId.of("gone"),
                        "r2",
                        NodeId.of("gone/x"),
                        applied,
                        "token-x",
                        List.of()));

        RecordedGraph recorded = RecordedGraph.of(historyRepo, List.of(target));

        assertThat(recorded.graph().allNodes())
                .extracting(MigrationNode::id)
                .containsExactly(NodeId.of("db1/a"));
        assertThat(recorded.rowsWithNoTarget())
                .extracting(ExecutionRecord::nodeId)
                .containsExactly(NodeId.of("gone/x"));
    }

    private ExecutionRecord appliedRow(
            String id,
            NodeId nodeId,
            Instant executedAt,
            String fingerprint,
            List<NodeId> dependencies) {
        return appliedRow(target.id(), id, nodeId, executedAt, fingerprint, dependencies);
    }

    private ExecutionRecord appliedRow(
            TargetId targetId,
            String id,
            NodeId nodeId,
            Instant executedAt,
            String fingerprint,
            List<NodeId> dependencies) {
        return row(
                targetId,
                id,
                nodeId,
                executedAt,
                fingerprint,
                dependencies,
                ExecutionOrigin.EXECUTED);
    }

    private ExecutionRecord amendedRow(
            String id,
            NodeId nodeId,
            Instant executedAt,
            String fingerprint,
            List<NodeId> dependencies) {
        return row(
                target.id(),
                id,
                nodeId,
                executedAt,
                fingerprint,
                dependencies,
                ExecutionOrigin.AMENDED);
    }

    private static ExecutionRecord row(
            TargetId targetId,
            String id,
            NodeId nodeId,
            Instant executedAt,
            String fingerprint,
            List<NodeId> dependencies,
            ExecutionOrigin origin) {
        return new ExecutionRecord(
                id,
                nodeId,
                targetId,
                ExecutionDirection.UP,
                ExecutionStatus.SUCCESS,
                executedAt,
                nodeId.value(),
                "DROP " + nodeId.value(),
                1L,
                null,
                fingerprint,
                null,
                dependencies,
                origin,
                null);
    }

    private static MigrationNode nodeIn(MigrationGraph graph, String id) {
        return graph.getNode(NodeId.of(id)).orElseThrow();
    }
}
