package io.github.kakusuke.migraphe.core.graph;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import java.util.List;
import java.util.function.Function;

/**
 * DAG の実行グラフをテキスト表現するクラス。
 *
 * <p>LayoutSort → LayoutTree → GridCanvas パイプラインによるテキスト出力。
 */
public final class ExecutionGraphView {

    private final GridCanvas canvas;

    /**
     * MigrationGraph からパイプラインを構築するコンストラクタ。
     *
     * @param graph マイグレーショングラフ
     * @param reversed true の場合、逆順モード（DOWN用）
     */
    public ExecutionGraphView(MigrationGraph graph, boolean reversed) {
        LayoutSort.LayoutOrder order = LayoutSort.sort(graph);
        LayoutTree tree = LayoutTree.build(graph, order);
        this.canvas = new GridCanvas();
        this.canvas.addStream(tree.rootStream());
    }

    /**
     * ソート済みノードリストから構築するコンストラクタ（フィルタ済みサブセット用）。
     *
     * @param sortedNodes ソート済みノードリスト
     */
    public ExecutionGraphView(List<MigrationNode> sortedNodes) {
        MigrationGraph subGraph = MigrationGraph.create();
        for (MigrationNode node : sortedNodes) {
            subGraph.addNode(node);
        }
        LayoutSort.LayoutOrder order = LayoutSort.sort(subGraph);
        LayoutTree tree = LayoutTree.build(subGraph, order);
        this.canvas = new GridCanvas();
        this.canvas.addStream(tree.rootStream());
    }

    /** 各ノードの行情報リストを取得する。 */
    public List<NodeLineInfo> lines() {
        return canvas.toNodeLineInfos();
    }

    /**
     * 各ノードのラベル付き行をリストとして生成する。
     *
     * @param labelFn 各ノードに対するフルラベルを返す関数
     * @return 表示行のリスト
     */
    public List<String> renderLines(Function<MigrationNode, String> labelFn) {
        String rendered = canvas.render(labelFn);
        if (rendered.isEmpty()) {
            return List.of();
        }
        // render() は末尾に \n を付けるので、最後の空要素を除去
        String trimmed =
                rendered.endsWith("\n") ? rendered.substring(0, rendered.length() - 1) : rendered;
        return List.of(trimmed.split("\n", -1));
    }

    /** グラフ全体をテキスト表現で出力する。 */
    @Override
    public String toString() {
        return canvas.render(n -> n.id().value() + " - " + n.name());
    }
}
