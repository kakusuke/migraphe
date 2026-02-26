package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import java.util.List;
import java.util.function.Function;

/**
 * DAG の実行グラフをテキスト表現するクラス。
 *
 * <p>支配木 (Dominator Tree) ベースの描画方式。
 */
public final class ExecutionGraphView {

    private final GraphCanvas canvas;

    /**
     * コンストラクタ。
     *
     * @param sortedNodes ソート済みノードリスト
     * @param reversed true の場合、逆順モード（DOWN用）。依存関係を逆に解釈する。
     */
    public ExecutionGraphView(List<MigrationNode> sortedNodes, boolean reversed) {
        DominatorTree dt = new DominatorTree(sortedNodes, reversed);
        this.canvas = new GraphCanvas();
        canvas.layout(dt);
    }

    /** 各ノードの行情報リストを取得する。 */
    public List<NodeLineInfo> lines() {
        return canvas.getNodeLineInfos();
    }

    /**
     * 各ノードのラベル付き行をリストとして生成する。
     *
     * @param labelFn 各ノードに対するフルラベルを返す関数
     * @return 表示行のリスト
     */
    public List<String> renderLines(Function<MigrationNode, String> labelFn) {
        return canvas.render(labelFn);
    }

    /** プレーンテキストとしてグラフ全体を出力する（色なし）。 */
    @Override
    public String toString() {
        List<String> rendered =
                renderLines(node -> "[ ] " + node.id().value() + " - " + node.name());
        StringBuilder sb = new StringBuilder();
        for (String line : rendered) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }
}
