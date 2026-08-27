package io.github.kakusuke.migraphe.api.history;

/**
 * Optional capability of a {@link HistoryRepository}: revising the fingerprint of a record that has
 * already been written.
 *
 * <p>Kept separate from {@link HistoryRepository} because that interface is otherwise append-only —
 * a repository that cannot revise a stored record simply does not implement this one, and callers
 * detect the capability with {@code instanceof} rather than being handed a method that throws.
 *
 * <p>The value written here is an <em>assertion by the operator</em> that the recorded node was
 * applied from the content the fingerprint was computed over. It is not an observation: nothing
 * re-read the database to confirm it. Implementations must therefore not present it any differently
 * from a fingerprint captured at apply time, and callers must not use this to paper over a
 * difference they have not looked at.
 *
 * @see HistoryRepository
 */
public interface HistoryFingerprintUpdater {

    /**
     * Replaces the fingerprint stored on one execution record.
     *
     * <p>Only the fingerprint changes: the record's timestamp, duration, serialized rollback and
     * every other field must be left exactly as they were, so that history keeps saying when the
     * node was really applied. The previous fingerprint is <strong>lost</strong> — there is no
     * column holding it — so after this call nothing distinguishes a node whose content once
     * differed from one that never did.
     *
     * <p>Both arguments are required. There is deliberately no way to <em>clear</em> a stored
     * fingerprint through this method: an absent token means "unknown", and turning a known token
     * back into an unknown one is not an operation anything needs. Passing {@code null} is a
     * programming error, not a request.
     *
     * @param recordId the id of the record to revise
     * @param fingerprint the fingerprint to store
     * @return {@code true} if a record with that id was revised, {@code false} if none matched
     * @throws NullPointerException if {@code recordId} or {@code fingerprint} is {@code null}
     */
    boolean updateFingerprint(String recordId, String fingerprint);
}
