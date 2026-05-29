package io.github.kakusuke.migraphe.core.execution.support;

import io.github.kakusuke.migraphe.api.execution.ExecutionListener;
import io.github.kakusuke.migraphe.api.execution.ExecutionPlanInfo;
import io.github.kakusuke.migraphe.api.execution.ExecutionSummary;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * テスト用の {@link ExecutionListener} 実装。
 *
 * <p>並列実行テストでも安全に利用できるよう、コレクション類はすべて synchronized ラッパーで保護する。直列テストでも追加コストは無視できる。
 */
public class MockExecutionListener implements ExecutionListener {
    public final List<NodeId> startedNodes = Collections.synchronizedList(new ArrayList<>());
    public final List<NodeId> succeededNodes = Collections.synchronizedList(new ArrayList<>());
    public final List<NodeId> skippedNodes = Collections.synchronizedList(new ArrayList<>());
    public final Map<NodeId, String> skipReasons = Collections.synchronizedMap(new HashMap<>());
    public final List<NodeId> failedNodes = Collections.synchronizedList(new ArrayList<>());
    public volatile boolean completedCalled = false;

    @Override
    public void onPlanCreated(ExecutionPlanInfo plan) {}

    @Override
    public void onNodeStarted(MigrationNode node, ExecutionDirection direction) {
        startedNodes.add(node.id());
    }

    @Override
    public void onNodeSucceeded(MigrationNode node, ExecutionDirection direction, long durationMs) {
        succeededNodes.add(node.id());
    }

    @Override
    public void onNodeSkipped(MigrationNode node, ExecutionDirection direction, String reason) {
        skippedNodes.add(node.id());
        skipReasons.put(node.id(), reason);
    }

    @Override
    public void onNodeFailed(
            MigrationNode node,
            ExecutionDirection direction,
            @Nullable String sqlContent,
            String errorMessage) {
        failedNodes.add(node.id());
    }

    @Override
    public void onCompleted(ExecutionSummary summary) {
        completedCalled = true;
    }
}
