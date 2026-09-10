package io.github.kakusuke.migraphe.api.history;

import io.github.kakusuke.migraphe.api.graph.Fingerprinter;
import io.github.kakusuke.migraphe.api.graph.MigrationGraphView;
import io.github.kakusuke.migraphe.api.graph.NodeId;

/**
 * What core lends a {@link HistoryUpgrade} while it runs.
 *
 * <p>An upgrade that adds a column often has to fill it, and what belongs in a row comes from the
 * definitions — so it needs both the migrations the project declares now and, for the columns core
 * signs, core's own way of arriving at the value.
 *
 * <p><strong>Why this is not on {@link MigrationGraphView}.</strong> {@link
 * #fingerprinterFor(NodeId)} is not a fact about the graph's shape; it is a computation core owns,
 * and the same rule that decides where a default belongs decides this: "nothing" is a correct
 * answer for a backend with no older shapes, so {@link HistoryRepository#upgrades()} has a default
 * — but a view that cannot fold a fingerprint is not a view with nothing to fold, it is a broken
 * one. Most things that implement a graph view — the generators, the layout, a test double — have
 * no business folding tokens and nothing honest to return, so the capability travels to the one
 * place that needs it instead of standing in front of everyone.
 *
 * <p>Passed as one object rather than as arguments, matching {@code SourceContext} and {@code
 * OutputContext} in the generator SPI: a later release's upgrade can be given what it needs without
 * changing {@link HistoryUpgrade#apply} again.
 *
 * @see HistoryUpgrade#apply(UpgradeContext)
 */
public interface UpgradeContext {

    /**
     * The migrations the project declares now.
     *
     * <p>An upgrade fills a row from the definition that names it, so a row whose node this no
     * longer holds is left alone: there is no source for its missing values, and withdrawing such a
     * row is {@code amend}'s job.
     *
     * @return the definitions, never {@code null}
     */
    MigrationGraphView definitions();

    /**
     * Core's fingerprint folding for one node, ready to be handed to that node.
     *
     * <p>The upgrade never computes a token itself. It asks the node — {@code
     * node.fingerprint(context.fingerprinterFor(node.id()))} — exactly as every other caller does,
     * so a filled row carries the same token a fresh apply would have written. Folding it any other
     * way would make every filled row read as edited on the next run.
     *
     * @param nodeId the node whose fingerprint is about to be folded; must be one the definitions
     *     hold
     * @return a fingerprinter for that node
     * @throws NullPointerException if the definitions hold no node with that identifier
     */
    Fingerprinter fingerprinterFor(NodeId nodeId);
}
