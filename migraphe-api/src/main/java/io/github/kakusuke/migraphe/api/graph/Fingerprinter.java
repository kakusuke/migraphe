package io.github.kakusuke.migraphe.api.graph;

/**
 * Folds the signatures a node supplies into its fingerprint.
 *
 * <p>A node is handed one of these and answers with what it returns. That split is the point: the
 * node decides <em>what</em> describes it, and this decides <em>how</em> that becomes a token — the
 * framing, the digest, and the dependency closure the node stands on, none of which a node has to
 * know about. A node cannot get the closure wrong because it never sees it, and cannot get the
 * framing wrong because it never writes one.
 *
 * <p>The number of signatures is part of the answer. Passing one is a different input from passing
 * two, so a node with no rollback and a node whose rollback is empty do not have to agree — they
 * differ by arity rather than by a marker either of them has to remember.
 *
 * @see MigrationNode#fingerprint
 */
public interface Fingerprinter {

    /**
     * Returns the token for these signatures, together with the closure this instance was made for.
     *
     * <p>Signatures are taken in the order given: an order that carries meaning is preserved, so a
     * caller that means "up, then down" gets a different token from one that means the reverse.
     *
     * @param signatures what the node says describes it, in an order the node chose
     * @return the fingerprint
     */
    String over(String... signatures);
}
