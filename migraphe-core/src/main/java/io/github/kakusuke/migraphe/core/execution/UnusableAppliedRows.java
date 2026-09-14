package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The applied rows nothing can be read from, split by whether a repair exists.
 *
 * <p>Asked in one place because three commands stop on the answer and the design requires them to
 * say the same thing about one row. Each working it out for itself is how {@code up} came to call a
 * state a plugin fault while {@code down} and {@code rebuild} were still prescribing an {@code
 * amend} that skips it — an operator following that instruction met the identical refusal, one
 * command over.
 *
 * <p>A run holding both kinds is refused for the unrepairable one first, so the repairable rows are
 * named a run later rather than in the same listing. The design asks a refusal to count every
 * applied row; this splits that across two runs, and does so deliberately — a plugin that cannot
 * report a token has to be fixed before any {@code amend} the other list prescribes is worth
 * running, so naming both at once would be naming a step the operator cannot yet take.
 *
 * @param incomplete rows a repair reaches: the definition can still say what the row failed to
 *     record, so rebuilding it fills the row — and a row the definitions no longer declare belongs
 *     here too, because withdrawing it is a repair
 * @param unreadable rows no repair reaches: the plugin cannot report a token at all, so there is
 *     nothing to rebuild the row from and naming a command would send the operator round a loop
 * @param edited rows that can be read perfectly well and say something else: the migration was
 *     edited after it ran, so the database is known not to match the definitions. Which side is
 *     right is the operator's to decide, so this is reported and never repaired here
 */
record UnusableAppliedRows(Set<NodeId> incomplete, Set<NodeId> unreadable, Set<NodeId> edited) {

    /**
     * Reads the history and classifies every applied row that carries no fingerprint.
     *
     * <p>The fingerprint is the one marker of an incomplete row, so this is the whole question:
     * what the row failed to record cannot be recovered from the row itself, and the only other
     * place to look is the definition that names it.
     *
     * @param graph the definitions
     * @param historyRepository the repository consulted for what stands
     * @return the two sets, either or both of which may be empty
     */
    static UnusableAppliedRows of(MigrationGraph graph, HistoryRepository historyRepository) {
        Set<NodeId> incomplete = new LinkedHashSet<>();
        Set<NodeId> unreadable = new LinkedHashSet<>();
        Set<NodeId> edited = new LinkedHashSet<>();
        for (ExecutionRecord applied : historyRepository.latestApplies()) {
            NodeId id = applied.nodeId();
            MigrationNode node = graph.getNode(id).orElse(null);
            if (node == null) {
                // Only the history holds it. There is nothing to compare against, and withdrawing
                // it is a repair — so an absent token counts as answerable, and no token it could
                // equal exists.
                if (applied.fingerprint() == null) {
                    incomplete.add(id);
                }
                continue;
            }
            String declared = declaredToken(graph, node);
            if (applied.fingerprint() == null) {
                (declared != null ? incomplete : unreadable).add(id);
            } else if (declared != null && !declared.equals(applied.fingerprint())) {
                edited.add(id);
            }
            // A row that carries a token while the definition cannot supply one is left alone
            // here: `status` and `rebuild` report it through the two-graph comparison, and giving
            // it to this classifier is its own change.
        }
        return new UnusableAppliedRows(
                Set.copyOf(incomplete), Set.copyOf(unreadable), Set.copyOf(edited));
    }

    /** Whether anything here stops a run that reads what these rows record. */
    boolean any() {
        return !incomplete.isEmpty() || !unreadable.isEmpty();
    }

    /**
     * The migrations among {@code nodes} whose plugin cannot report what they hold.
     *
     * <p>Asked of nodes rather than of rows, for the run that has not happened yet: applying a
     * migration whose token cannot be folded writes a row nothing can read, and every command then
     * stops on it. The same question, one step earlier, is the difference between refusing for free
     * and refusing after a database has moved.
     *
     * @param graph the definitions
     * @param nodes the migrations a run would apply
     * @return those whose plugin threw or answered with none, in the order given
     */
    static Set<NodeId> nodesThatCannotReportTheirContent(MigrationGraph graph, Set<NodeId> nodes) {
        Set<NodeId> unreadable = new LinkedHashSet<>();
        for (NodeId id : nodes) {
            MigrationNode node = graph.getNode(id).orElse(null);
            if (node != null && declaredToken(graph, node) == null) {
                unreadable.add(id);
            }
        }
        return Set.copyOf(unreadable);
    }

    /**
     * What the definition says this node holds today, or {@code null} if it cannot say.
     *
     * <p>A node whose accessor throws cannot say, and neither can one that returns null — the type
     * reserves null for core's own adapter over a history row, so a declared node returning it is
     * out of contract, and no {@code amend} form fills a row from a definition with nothing to
     * give.
     */
    private static @Nullable String declaredToken(MigrationGraph graph, MigrationNode node) {
        try {
            return node.fingerprint(graph.fingerprinterFor(node.id()));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
