package io.github.kakusuke.migraphe.core.execution.support;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 渡された推移的依存そのものを fingerprint として返すノード。
 *
 * <p>{@link FingerprintedNode} は固定のトークンを返すので、呼び出し側が閉包を渡し損ねても気づけない。こちらは引数を読んで返すため、「core
 * が計算した閉包が本当にプラグインまで 届いているか」を表明できる。
 */
public record DependencyEchoingNode(MigrationNode delegate) implements DelegatingMigrationNode {

    @Override
    public String fingerprint(List<NodeId> transitiveDependencies) {
        return transitiveDependencies.stream().map(NodeId::value).collect(Collectors.joining(","));
    }
}
