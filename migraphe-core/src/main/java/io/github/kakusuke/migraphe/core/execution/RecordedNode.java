package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.Fingerprinter;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.task.Task;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A history record presented as a {@link MigrationNode}, so what the history holds can be traversed
 * and rolled back through the paths built for the definitions.
 *
 * <p>Every path that runs a migration is driven by the graph — the execution plan, the ready-node
 * tracker, failure propagation. Rather than teach each of them about a node that is not there, the
 * row is adapted into one. That is the same reasoning that made {@link
 * io.github.kakusuke.migraphe.api.target.DownTaskRestorer} return a {@code Task}: a rollback driven
 * from the history then travels the executor, the listener and the history writer unchanged.
 *
 * <p><strong>Not necessarily an orphan.</strong> Most recorded rows have a counterpart in the
 * definitions; "orphan" names only the ones that do not. What every node of this kind has in common
 * is that it reports <em>history</em> — the dependencies, the token and the one-way reason its row
 * recorded, not anything derived from the definitions as they stand now — and so can only be rolled
 * back, never applied.
 *
 * <p>These nodes populate the graph {@link RecordedGraph} builds, and the graph a rollback of an
 * orphan is planned over. They are deliberately never added to the graph {@code status}, {@code up}
 * or {@code validate} read: those answer questions about the definitions, and a node built from a
 * row answers about the history.
 */
final class RecordedNode implements MigrationNode {

    private final ExecutionRecord record;
    private final Target target;

    private RecordedNode(ExecutionRecord record, Target target) {
        this.record = Objects.requireNonNull(record, "record must not be null");
        this.target = Objects.requireNonNull(target, "target must not be null");
    }

    /**
     * Adapts one applied record into a node.
     *
     * @param record the record that says the migration was applied
     * @param target the target the record names, resolved by the caller
     * @return a node standing for that record
     */
    static RecordedNode of(ExecutionRecord record, Target target) {
        return new RecordedNode(record, target);
    }

    @Override
    public NodeId id() {
        return record.nodeId();
    }

    @Override
    public String name() {
        return record.description();
    }

    @Override
    public @Nullable String description() {
        return null;
    }

    @Override
    public Target target() {
        return target;
    }

    /**
     * What the record says this migration directly stood on, or empty when it recorded nothing.
     *
     * <p>Empty is not a claim that it stood on nothing — a row written before the column existed
     * records no dependencies at all, and the two are indistinguishable here. Whoever decides what
     * else has to come down is the party that has to tell them apart, and it reads the record for
     * that rather than asking this node.
     */
    @Override
    public Set<NodeId> dependencies() {
        List<NodeId> recorded = record.dependencies();
        return recorded == null ? Set.of() : Set.copyOf(recorded);
    }

    /*
     * A null here reads as "stood on nothing", and a rollback ordered by it would take a migration
     * out from under one still built on it. That is safe only because no released version writes
     * such a row: the column arrives with `fingerprint` in one schema step list, an insert names
     * every column so a half-migrated table fails loudly instead of writing a partial row, and the
     * rows that predate all of them carry no fingerprint — which `upgrade` fills
     * whole, dependencies and the one-way reason included. What is left needs a hand-edited row,
     * and the same is true of a row carrying a fingerprint but no `no_way_back` for a task that
     * declares one: the refusal then reads "recorded neither", which is what the design says a
     * hand-edited row should read as.
     */

    /**
     * The reason the record carries for the migration being one-way, or {@code null} if it carries
     * none.
     *
     * <p>Read from the row, because for a migration the definitions no longer contain there is
     * nowhere else to look. Without it an absent rollback payload would answer two questions at
     * once — the author declared this one-way, or the row simply carries nothing — and those want
     * opposite responses: one is quoted back and stops for good, the other is one {@code amend
     * upgrade} away.
     *
     * @return the author's reason as it was recorded, or {@code null} when none was
     */
    @Override
    public @Nullable String noWayBack() {
        return record.noWayBack();
    }

    /**
     * The fingerprint the record carries, whatever closure the caller supplies.
     *
     * <p>A node built from a history row reports history: the token was computed when the migration
     * was applied, over the definition as it stood then, and recomputing it here from anything
     * available now would answer a different question. The argument is therefore ignored — which is
     * the one thing an ordinary node must not do, and the one thing this node must.
     *
     * @param fingerprinter ignored; the recorded token was folded when the migration was applied,
     *     over the definition as it stood then, not over anything a caller can supply now
     * @return the recorded fingerprint, or {@code null} when the row predates the column
     */
    @Override
    public @Nullable String fingerprint(Fingerprinter fingerprinter) {
        return record.fingerprint();
    }

    /**
     * Never available: a node built from a history row is only ever rolled back.
     *
     * @return never returns
     * @throws UnsupportedOperationException always
     */
    @Override
    public Task upTask() {
        throw new UnsupportedOperationException(
                record.nodeId().value()
                        + " is here as a history row, so it can only be rolled back, never"
                        + " applied");
    }

    /**
     * Always {@code null}: a row is not a definition, so there is nothing here to build a task
     * from.
     *
     * <p>This is not "cannot be rolled back". What a rollback runs is the payload the row kept,
     * rebuilt by the target; nothing ever falls back to this, for a node of this kind or any other.
     * Whether this migration can come down is answered by the row and by whether the target can
     * read that payload back, which is what {@code DownService} asks.
     */
    @Override
    public @Nullable Task downTask() {
        return null;
    }
}
