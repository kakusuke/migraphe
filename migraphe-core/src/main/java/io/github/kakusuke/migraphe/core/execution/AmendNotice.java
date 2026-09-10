package io.github.kakusuke.migraphe.core.execution;

import java.util.List;

/**
 * What {@code amend} tells the operator before it writes, in both front ends.
 *
 * <p>It is unconditional because {@code amend} is unconditional: for every migration in the plan a
 * row is appended carrying every attribute the definitions determine, so what the history reports
 * about it becomes what they say now. That is as true of the bulk form as of the named one — the
 * bulk form selects on an absent token, but it does not append only the token. A per-row warning
 * would be worse than none, because the rows without one would read as untouched, which is false
 * for every row in the plan.
 */
public final class AmendNotice {

    /** The notice, one entry per output line. */
    public static final List<String> LINES =
            List.of(
                    "For each migration listed, a row is appended saying what the definition says",
                    "now, so what the history reports about it becomes the current definition. The",
                    "rows already there are neither changed nor removed.");

    /**
     * What both front ends say before withdrawing an orphan.
     *
     * <p>Separate from {@link #LINES} because it states the one thing withdrawing does not do:
     * remove the objects. After it migraphe knows nothing about them.
     */
    public static final List<String> WITHDRAWAL_LINES =
            List.of(
                    "These migrations are applied but no longer defined, so the row appended for",
                    "each says it is not applied. Their objects are left in the database, and",
                    "migraphe will no longer know about them — this is right only if you have",
                    "already removed them another way. To remove them, run migraphe down instead.");

    private AmendNotice() {}
}
