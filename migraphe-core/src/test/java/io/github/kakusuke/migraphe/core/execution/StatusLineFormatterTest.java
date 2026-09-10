package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.Fingerprinter;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.core.execution.StatusService.NodeStatus;
import io.github.kakusuke.migraphe.core.execution.support.FingerprintedNode;
import io.github.kakusuke.migraphe.core.execution.support.ThrowingFingerprintNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTarget;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StatusLineFormatter")
class StatusLineFormatterTest {

    private static final Fingerprinter FIXED = signatures -> "abc";

    private final Target testTarget = SimpleTarget.create(TargetId.of("test"), "Test Target");

    @Test
    @DisplayName("実行済みは所要時間と日時を括弧で添え、未実行はマーカーとラベルだけを出す")
    void shouldRenderExecutedAndPendingNodes() {
        MigrationNode executedNode =
                new FingerprintedNode(createNode("db1/001_create", "Create users"), "abc");
        ExecutionRecord record =
                ExecutionRecord.upSuccess(
                        executedNode.id(), testTarget.id(), executedNode.name(), null, 250L, "abc");

        assertThat(
                        StatusLineFormatter.format(
                                new NodeStatus(executedNode, true, record, record, FIXED)))
                .startsWith("[✓] db1/001_create - Create users (250ms, ")
                .endsWith(")");

        MigrationNode pendingNode = createNode("db1/002_index", "Add index");

        assertThat(
                        StatusLineFormatter.format(
                                new NodeStatus(pendingNode, false, null, null, FIXED)))
                .isEqualTo("[ ] db1/002_index - Add index");
    }

    @Test
    @DisplayName("直近の操作が失敗したノードは、その失敗を注記に出す")
    void shouldAnnotateAFailedLastOperation() {
        MigrationNode node =
                new FingerprintedNode(createNode("db1/001_create", "Create users"), "abc");

        ExecutionRecord rollbackFailed =
                ExecutionRecord.failure(
                        node.id(),
                        testTarget.id(),
                        ExecutionDirection.DOWN,
                        node.name(),
                        "constraint violation");

        ExecutionRecord applied =
                ExecutionRecord.upSuccess(node.id(), testTarget.id(), node.name(), null, 8L, "abc");

        assertThat(
                        StatusLineFormatter.format(
                                new NodeStatus(node, true, rollbackFailed, applied, FIXED)))
                .startsWith("[✓] db1/001_create - Create users (rollback failed ")
                .endsWith(")");

        ExecutionRecord applyFailed =
                ExecutionRecord.failure(
                        node.id(),
                        testTarget.id(),
                        ExecutionDirection.UP,
                        node.name(),
                        "syntax error");

        assertThat(
                        StatusLineFormatter.format(
                                new NodeStatus(node, true, applyFailed, applied, FIXED)))
                .startsWith("[✓] db1/001_create - Create users (apply failed ");
    }

    @Test
    @DisplayName("変更あり・不明・読めない はマーカーで区別される")
    void shouldMarkChangedUnknownAndUnreadableContent() {
        MigrationNode changedNode = createNode("db1/002_index", "Add index");
        ExecutionRecord changedRecord =
                ExecutionRecord.upSuccess(
                        changedNode.id(), testTarget.id(), changedNode.name(), null, 5L, "xyz");
        NodeStatus changed =
                new NodeStatus(
                        new FingerprintedNode(changedNode, "abc"),
                        true,
                        changedRecord,
                        changedRecord,
                        FIXED);

        MigrationNode unknownNode = createNode("db1/003_posts", "Create posts");
        ExecutionRecord unknownRecord =
                ExecutionRecord.upSuccess(
                        unknownNode.id(), testTarget.id(), unknownNode.name(), null, 5L);
        NodeStatus unknown =
                new NodeStatus(
                        new FingerprintedNode(unknownNode, "abc"),
                        true,
                        unknownRecord,
                        unknownRecord,
                        FIXED);

        MigrationNode unreadableNode = createNode("db1/004_tags", "Create tags");
        ExecutionRecord unreadableRecord =
                ExecutionRecord.upSuccess(
                        unreadableNode.id(),
                        testTarget.id(),
                        unreadableNode.name(),
                        null,
                        5L,
                        "abc");
        NodeStatus unreadable =
                new NodeStatus(
                        new ThrowingFingerprintNode(unreadableNode),
                        true,
                        unreadableRecord,
                        unreadableRecord,
                        FIXED);

        assertThat(StatusLineFormatter.format(changed))
                .startsWith("[!] db1/002_index - Add index (");
        assertThat(StatusLineFormatter.format(unknown))
                .startsWith("[?] db1/003_posts - Create posts (");
        assertThat(StatusLineFormatter.format(unreadable))
                .startsWith("[E] db1/004_tags - Create tags (");
    }

    @Test
    @DisplayName("fingerprint が一致していれば適用済みマーカーになる")
    void shouldMarkUnchangedContentAsApplied() {
        MigrationNode node = createNode("db1/005_roles", "Create roles");
        ExecutionRecord record =
                ExecutionRecord.upSuccess(node.id(), testTarget.id(), node.name(), null, 5L, "abc");
        NodeStatus unchanged =
                new NodeStatus(new FingerprintedNode(node, "abc"), true, record, record, FIXED);

        assertThat(StatusLineFormatter.format(unchanged))
                .startsWith("[✓] db1/005_roles - Create roles (");
    }

    private MigrationNode createNode(String id, String name) {
        Task upTask = SimpleTask.of("UP: " + name);
        Task downTask = SimpleTask.of("DOWN: " + name);
        return SimpleMigrationNode.builder()
                .id(NodeId.of(id))
                .name(name)
                .target(testTarget)
                .dependencies(Set.of())
                .upTask(upTask)
                .downTask(downTask)
                .build();
    }
}
