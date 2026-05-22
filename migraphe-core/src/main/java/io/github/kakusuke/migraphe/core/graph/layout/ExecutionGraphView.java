package io.github.kakusuke.migraphe.core.graph.layout;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
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
     */
    public ExecutionGraphView(MigrationGraph graph) {
        this.canvas = buildCanvas(graph);
    }

    /**
     * ソート済みノードリストから構築するコンストラクタ（フィルタ済みサブセット用、UP 方向）。
     *
     * @param sortedNodes ソート済みノードリスト
     */
    public ExecutionGraphView(List<MigrationNode> sortedNodes) {
        this(sortedNodes, false);
    }

    /**
     * ソート済みノードリストから構築するコンストラクタ（方向指定版）。
     *
     * @param sortedNodes ソート済みノードリスト
     * @param reversed true の場合、DOWN 方向（reversed adjacency + リスト外フィルタ）でサブグラフを構築
     */
    public ExecutionGraphView(List<MigrationNode> sortedNodes, boolean reversed) {
        MigrationGraph subGraph =
                reversed
                        ? MigrationGraph.fromNodesDown(sortedNodes)
                        : MigrationGraph.fromNodesUp(sortedNodes);
        this.canvas = buildCanvas(subGraph);
    }

    /** LayoutSort → LayoutTree → GridCanvas パイプラインを実行して描画用 canvas を組み立てる。 */
    private static GridCanvas buildCanvas(MigrationGraph graph) {
        LayoutSort.LayoutOrder order = LayoutSort.sort(graph);
        LayoutTree tree = LayoutTree.build(graph, order);
        GridCanvas canvas = new GridCanvas();
        canvas.addStream(tree.rootStream());
        for (NonTreeEdge edge : tree.nonTreeEdges()) {
            canvas.addNonTreeEdge(edge.source(), edge.target());
        }
        canvas.removeRedundantRows();
        return canvas;
    }

    /** 各ノードの行情報リストを取得する（VirtualNode を除外、column を -1 調整）。 */
    public List<NodeLineInfo> lines() {
        List<NodeLineInfo> raw = canvas.toNodeLineInfos();
        return raw.stream()
                .filter(info -> !(info.node() instanceof LayoutTree.VirtualNode))
                .map(info -> new NodeLineInfo(info.node(), info.column() - 1))
                .toList();
    }

    /**
     * 各ノードのラベル付き行をリストとして生成する。
     *
     * @param labelFn 各ノードに対するフルラベルを返す関数
     * @return 表示行のリスト
     */
    public List<String> renderLines(Function<MigrationNode, String> labelFn) {
        // VirtualNode には labelFn を適用しない（副作用の回避）
        Function<MigrationNode, String> safeLabelFn =
                n -> n instanceof LayoutTree.VirtualNode ? "" : labelFn.apply(n);
        String rendered = canvas.render(safeLabelFn);
        if (rendered.isEmpty()) {
            return List.of();
        }
        // render() は末尾に \n を付けるので、最後の空要素を除去
        String trimmed =
                rendered.endsWith("\n") ? rendered.substring(0, rendered.length() - 1) : rendered;
        String[] allLines = trimmed.split("\n", -1);
        // Row 0 は VR ノード行なのでスキップ、残りは col 0（VR 列）を除去
        return java.util.Arrays.stream(allLines)
                .skip(1)
                .map(line -> line.length() > 1 ? line.substring(1) : "")
                .filter(line -> !line.isEmpty())
                .toList();
    }

    /** グラフ全体をテキスト表現で出力する。 */
    @Override
    public String toString() {
        List<String> lines = renderLines(n -> n.id().value() + " - " + n.name());
        if (lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }
}
