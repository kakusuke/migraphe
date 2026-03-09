package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import java.util.List;
import java.util.function.Function;

/**
 * DAG の実行グラフをテキスト表現するクラス。
 *
 * <p>トポロジカル順のプレーンテキスト出力。
 */
public final class ExecutionGraphView {

    private final List<MigrationNode> sortedNodes;

    /**
     * コンストラクタ。
     *
     * @param sortedNodes ソート済みノードリスト
     * @param reversed true の場合、逆順モード（DOWN用）。依存関係を逆に解釈する。
     */
    public ExecutionGraphView(List<MigrationNode> sortedNodes, boolean reversed) {
        this.sortedNodes = List.copyOf(sortedNodes);
    }

    /** 各ノードの行情報リストを取得する。 */
    public List<NodeLineInfo> lines() {
        return sortedNodes.stream().map(n -> new NodeLineInfo(n, 0)).toList();
    }

    /**
     * 各ノードのラベル付き行をリストとして生成する。
     *
     * @param labelFn 各ノードに対するフルラベルを返す関数
     * @return 表示行のリスト
     */
    public List<String> renderLines(Function<MigrationNode, String> labelFn) {
        return sortedNodes.stream().map(labelFn).toList();
    }

    /** プレーンテキストとしてグラフ全体を出力する（色なし）。 */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (MigrationNode n : sortedNodes) {
            sb.append("[ ] ").append(n.id().value()).append(" - ").append(n.name()).append("\n");
        }
        return sb.toString();
    }
}
