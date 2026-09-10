package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.core.plugin.SimpleTarget;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RecordedNode")
class RecordedNodeTest {

    private final Target target = SimpleTarget.create(TargetId.of("db1"), "db1");

    @Test
    @DisplayName("履歴行を、記録された直接依存を持つノードとして提示する")
    void presentsTheRecordAsANodeStandingOnTheRecordedDependencies() {
        ExecutionRecord applied =
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_removed"),
                        target.id(),
                        "Add index",
                        "DROP INDEX idx",
                        100L,
                        "token",
                        "autocommit.down=true\n",
                        List.of(NodeId.of("db1/001_a")));

        var node = RecordedNode.of(applied, target);

        assertThat(node.id()).isEqualTo(NodeId.of("db1/002_removed"));
        assertThat(node.name()).isEqualTo("Add index");
        assertThat(node.target()).isSameAs(target);
        assertThat(node.dependencies()).containsExactly(NodeId.of("db1/001_a"));
    }

    @Test
    @DisplayName("down タスクは null — ロールバックは記録から復元される")
    void hasNoDownTaskOfItsOwnBecauseTheRollbackComesFromTheRecord() {
        var node = RecordedNode.of(recordWith(List.of()), target);

        assertThat(node.downTask()).isNull();
    }

    @Test
    @DisplayName("up タスクは要求されない — 履歴の行はロールバックされるだけ")
    void refusesToBeAppliedBecauseARecordedNodeIsOnlyEverRolledBack() {
        var node = RecordedNode.of(recordWith(List.of()), target);

        assertThatThrownBy(node::upTask)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("db1/002_removed");
    }

    @Test
    @DisplayName("依存が記録されていない行は、何も主張しない")
    void reportsNoDependenciesWhenTheRowRecordedNone() {
        var node = RecordedNode.of(recordWith(null), target);

        assertThat(node.dependencies()).isEmpty();
    }

    @Test
    @DisplayName("fingerprint は記録された値そのもの — 計算し直さない")
    void reportsTheFingerprintTheRowRecordedRatherThanComputingOne() {
        ExecutionRecord applied =
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_removed"),
                        target.id(),
                        "Add index",
                        "DROP INDEX idx",
                        100L,
                        "abc",
                        null,
                        List.of(NodeId.of("db1/001_a")));

        var node = RecordedNode.of(applied, target);

        assertThat(node.fingerprint(signatures -> "computed")).isEqualTo("abc");
        assertThat(node.fingerprint(signatures -> "computed")).isEqualTo("abc");
    }

    @Test
    @DisplayName("列が出来る前の行は fingerprint を持たない")
    void reportsNoFingerprintWhenTheRowPredatesTheColumn() {
        var node = RecordedNode.of(recordWith(List.of()), target);

        assertThat(node.fingerprint(signatures -> "computed")).isNull();
    }

    @Test
    @DisplayName("一方通行の理由は行から読む — 定義にはもう無い")
    void reportsTheOneWayReasonTheRowRecorded() {
        ExecutionRecord oneWay =
                ExecutionRecord.upSuccess(
                        NodeId.of("db1/002_removed"),
                        target.id(),
                        "Drop the column",
                        null,
                        100L,
                        "token",
                        null,
                        List.of(),
                        "DROP COLUMN discards the data");

        assertThat(RecordedNode.of(oneWay, target).noWayBack())
                .isEqualTo("DROP COLUMN discards the data");
        assertThat(RecordedNode.of(recordWith(List.of()), target).noWayBack()).isNull();
    }

    private ExecutionRecord recordWith(@Nullable List<NodeId> dependencies) {
        return ExecutionRecord.upSuccess(
                NodeId.of("db1/002_removed"),
                target.id(),
                "Add index",
                "DROP INDEX idx",
                100L,
                null,
                null,
                dependencies);
    }
}
