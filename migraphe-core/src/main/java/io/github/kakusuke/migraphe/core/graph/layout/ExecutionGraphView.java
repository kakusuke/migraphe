package io.github.kakusuke.migraphe.core.graph.layout;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.List;
import java.util.function.Function;

/**
 * Renders a migration DAG as an ASCII text diagram.
 *
 * <p>This is the final stage of the layout pipeline ({@code MigrationGraph -> LayoutSort ->
 * LayoutTree -> GridCanvas -> ExecutionGraphView}). The view builds a {@link GridCanvas} once at
 * construction time and exposes it as rendered text lines and as per-node placement info. It is
 * used by the CLI and Gradle plugin to display the execution graph (e.g. {@code migraphe status}).
 */
public final class ExecutionGraphView {

    private final GridCanvas canvas;

    /**
     * Builds a view that renders the entire graph.
     *
     * @param graph the migration graph to render
     */
    public ExecutionGraphView(MigrationGraph graph) {
        this.canvas = buildCanvas(graph);
    }

    /**
     * Builds a view from a pre-sorted node list, treating it as an UP-direction subgraph.
     *
     * <p>Equivalent to {@link #ExecutionGraphView(List, boolean)} with {@code reversed = false}.
     *
     * @param sortedNodes the topologically sorted nodes to render (a filtered subset)
     */
    public ExecutionGraphView(List<MigrationNode> sortedNodes) {
        this(sortedNodes, false);
    }

    /**
     * Builds a view from a pre-sorted node list in the given direction.
     *
     * @param sortedNodes the topologically sorted nodes to render (a filtered subset)
     * @param reversed if {@code true}, builds the subgraph in DOWN direction (reversed adjacency,
     *     edges to nodes outside the list filtered out) via {@link
     *     MigrationGraph#fromNodesDown(List)}; otherwise UP direction via {@link
     *     MigrationGraph#fromNodesUp(List)}
     */
    public ExecutionGraphView(List<MigrationNode> sortedNodes, boolean reversed) {
        MigrationGraph subGraph =
                reversed
                        ? MigrationGraph.fromNodesDown(sortedNodes)
                        : MigrationGraph.fromNodesUp(sortedNodes);
        this.canvas = buildCanvas(subGraph);
    }

    /**
     * Runs the {@code LayoutSort -> LayoutTree -> GridCanvas} pipeline to assemble the render
     * canvas.
     */
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

    /**
     * Returns per-node placement info for the rendered graph.
     *
     * <p>The synthetic virtual root is excluded and every remaining node's column is shifted left
     * by one to account for the dropped virtual-root column.
     *
     * @return the list of node line infos, one per real node
     */
    public List<NodeLineInfo> lines() {
        List<NodeLineInfo> raw = canvas.toNodeLineInfos();
        return raw.stream()
                .filter(info -> !(info.node() instanceof LayoutTree.VirtualNode))
                .map(info -> new NodeLineInfo(info.node(), info.column() - 1))
                .toList();
    }

    /**
     * Renders the graph as a list of labeled text lines.
     *
     * <p>The virtual-root row and column are stripped and any resulting empty lines are removed, so
     * the returned list contains only the real node rows.
     *
     * @param labelFn function returning the full label for each node (not invoked for the virtual
     *     root)
     * @return the rendered display lines, one per drawn row
     */
    public List<String> renderLines(Function<MigrationNode, String> labelFn) {
        // Do not apply labelFn to the VirtualNode (avoid side effects)
        Function<MigrationNode, String> safeLabelFn =
                n -> n instanceof LayoutTree.VirtualNode ? "" : labelFn.apply(n);
        String rendered = canvas.render(safeLabelFn);
        if (rendered.isEmpty()) {
            return List.of();
        }
        // render() appends a trailing \n, so drop the final empty element
        String trimmed =
                rendered.endsWith("\n") ? rendered.substring(0, rendered.length() - 1) : rendered;
        String[] allLines = trimmed.split("\n", -1);
        // Row 0 is the VR node row, so skip it; strip col 0 (the VR column) from the rest
        return java.util.Arrays.stream(allLines)
                .skip(1)
                .map(line -> line.length() > 1 ? line.substring(1) : "")
                .filter(line -> !line.isEmpty())
                .toList();
    }

    /**
     * Renders the entire graph as a single newline-terminated string, labeling each node as {@code
     * "<id> - <name>"}.
     *
     * @return the rendered diagram, or an empty string if there are no nodes
     */
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
