package io.github.kakusuke.migraphe.api.history;

/**
 * One step that brings a history written by an older release to the shape this version writes.
 *
 * <p>An upgrade is <strong>not</strong> a claim about the migrations. {@code amend} records what
 * the definitions say now because an operator decided that is right, and so it writes every column
 * the definitions determine. An upgrade carries no such decision: it completes what an older
 * version wrote incompletely, and touches nothing that already has a value. That difference is why
 * the two are separate commands rather than one flag.
 *
 * <p><strong>One unit may do both halves.</strong> {@link #apply(UpgradeContext)} receives the
 * definitions, so an upgrade that adds a column can fill it from the task files in the same step
 * rather than being a schema change plus a separate row repair. What it cannot fill it leaves
 * alone: a row for a migration the definitions no longer declare has no source for the missing
 * value, and withdrawing such a row is {@code amend}'s job, not this one's.
 *
 * <p>Upgrades are ordered — {@link HistoryRepository#upgrades()} returns a {@code List} — and each
 * is asked {@link #isPending()} before it is applied, so running the upgrade command repeatedly is
 * safe and no schema-version bookkeeping is needed.
 *
 * @see HistoryRepository#upgrades()
 */
public interface HistoryUpgrade {

    /**
     * Says what this upgrade does, in one line, for the report the upgrade command prints.
     *
     * @return a short human-readable description
     */
    String description();

    /**
     * Reports whether this history still needs this upgrade.
     *
     * <p>Called before {@link #apply(UpgradeContext)}, and again by any command that has to refuse
     * while an upgrade is outstanding. A failure to determine the answer is reported rather than
     * read as "not pending": mistaking a permission error for an applied upgrade would let a
     * command run against a history it cannot write correctly.
     *
     * @return {@code true} if this upgrade has not been applied to this history yet
     */
    boolean isPending();

    /**
     * Applies this upgrade.
     *
     * @param context what core lends for the run — the definitions, and its own fingerprint folding
     *     for an upgrade that fills columns from them; an upgrade that only changes the schema
     *     ignores it
     */
    void apply(UpgradeContext context);
}
