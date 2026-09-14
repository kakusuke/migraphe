package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What the definitions say against what the history says, as a full outer join of two graphs.
 *
 * <p>A rebuild is the difference between two DAGs. Taking the cascade from the current declarations
 * instead cannot see a migration the definitions no longer contain, which is the case the whole
 * family exists for.
 *
 * <p>The join is on the identifier alone. A migration whose {@code target:} was edited is held by
 * both sides, so it lands in {@code contentDiffers} like any other edit — the token covers the
 * target, so the move moves the token and nothing has to detect it specially. Each side keeps its
 * own node, which is what a repair needs: the objects stand where the recorded row says, and the
 * definition says where they belong now.
 *
 * @param onlyInHistory applied migrations the definitions no longer declare — orphans
 * @param onlyInDefinitions declared migrations the history does not hold as applied
 * @param contentDiffers migrations both sides hold, whose recorded token is not the one the current
 *     definition folds
 * @param cannotCompare migrations both sides hold where the <em>recorded</em> token is absent and
 *     the definition can still supply one. Not a difference: "not known to differ" is not "known to
 *     agree", and treating a row that predates the fingerprint column as changed would make the
 *     first rebuild after an upgrade tear down and re-create the whole database. It is one {@code
 *     upgrade} away from being answerable
 * @param unreadable migrations both sides hold where the <em>definition</em> could not supply a
 *     token — its accessor threw, or answered with none. Not evidence of agreement either, so it
 *     stops the same commands — but a different state, and reported as one: a row that predates a
 *     column is an upgrade to finish, a plugin that cannot report what its own migration applied is
 *     a fault, and no repair the operator can run turns the second into the first
 */
record GraphDifference(
        Set<NodeId> onlyInHistory,
        Set<NodeId> onlyInDefinitions,
        Set<NodeId> contentDiffers,
        Set<NodeId> cannotCompare,
        Set<NodeId> unreadable) {

    /**
     * Joins the two sides.
     *
     * @param definitions the graph the task files describe
     * @param history the graph the history describes, from {@link RecordedGraph}
     * @return which migrations sit on one side only, differ, or cannot be compared
     */
    static GraphDifference between(MigrationGraph definitions, MigrationGraph history) {
        Set<NodeId> onlyInHistory = new LinkedHashSet<>();
        Set<NodeId> onlyInDefinitions = new LinkedHashSet<>();
        Set<NodeId> contentDiffers = new LinkedHashSet<>();
        Set<NodeId> cannotCompare = new LinkedHashSet<>();
        Set<NodeId> unreadable = new LinkedHashSet<>();

        for (MigrationNode declared : definitions.allNodes()) {
            NodeId id = declared.id();
            MigrationNode recorded = history.getNode(id).orElse(null);
            if (recorded == null) {
                onlyInDefinitions.add(id);
                continue;
            }
            String declaredToken;
            String recordedToken;
            try {
                declaredToken = tokenOf(definitions, declared);
                recordedToken = tokenOf(history, recorded);
            } catch (RuntimeException e) {
                unreadable.add(id);
                continue;
            }
            if (declaredToken == null) {
                // The definition supplied nothing, which is the same fault as throwing: supplying a
                // token is the plugin's contract, and the one null the design keeps belongs to the
                // recorded side below. Calling it "cannot compare" would put the row among the
                // states an amend reaches, and no amend fills a row from a definition with nothing
                // to give.
                unreadable.add(id);
            } else if (recordedToken == null) {
                cannotCompare.add(id);
            } else if (!declaredToken.equals(recordedToken)) {
                contentDiffers.add(id);
            }
        }
        for (MigrationNode recorded : history.allNodes()) {
            if (definitions.getNode(recorded.id()).isEmpty()) {
                onlyInHistory.add(recorded.id());
            }
        }

        return new GraphDifference(
                Set.copyOf(onlyInHistory),
                Set.copyOf(onlyInDefinitions),
                Set.copyOf(contentDiffers),
                Set.copyOf(cannotCompare),
                Set.copyOf(unreadable));
    }

    /**
     * A node's token, folded through the fingerprinter of the graph the node came from.
     *
     * <p>The pairing is what makes the comparison mean anything: a graph's fingerprinter captures
     * that graph's dependency closure, so handing a node the other side's would fold the current
     * declarations into the recorded token, or the recorded edges into the declared one.
     *
     * <p>This may throw — the token comes from a plugin — and the caller catches it rather than
     * letting it escape. An accessor that failed is not evidence of agreement, so the migration is
     * one the comparison cannot answer about, which is a state the difference already has.
     */
    private static @Nullable String tokenOf(MigrationGraph graph, MigrationNode node) {
        return node.fingerprint(graph.fingerprinterFor(node.id()));
    }
}
