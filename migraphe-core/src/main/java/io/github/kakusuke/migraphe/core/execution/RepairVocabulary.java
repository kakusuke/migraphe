package io.github.kakusuke.migraphe.core.execution;

/**
 * How a front end names the repair it is telling the operator to run.
 *
 * <p>The refusals themselves are shared, because one state must not be described two ways. What the
 * operator types to answer them is not shared, because it differs: a message that names a command
 * the reader cannot run is as much a dead end as naming no command at all, and the design's "in the
 * same words" is about not describing one state as two — not about pretending both front ends are
 * invoked alike.
 *
 * <p>So the words stay in one place and the invocation travels in from the caller, which is the
 * only thing that knows how it was invoked.
 *
 * <p>Two per-node reason strings still spell the CLI form without asking: {@code DagExecutor}'s
 * refusal for a row with no fingerprint, and the matching arm of {@code DownPlanFormatter}'s {@code
 * why}. Neither is reachable through a command — both {@code down} and {@code rebuild} refuse the
 * whole run before a node in that state is planned or executed — so threading a vocabulary down to
 * them would add an argument to the executor for a message nothing prints. If a caller ever reaches
 * one, it needs this enum, not a second wording.
 */
public enum RepairVocabulary {

    /** {@code migraphe} on a command line. */
    CLI(
            "migraphe amend <id>",
            "migraphe amend",
            "migraphe rebuild",
            "migraphe down <id>",
            "migraphe upgrade-history",
            "migraphe init"),

    /**
     * The Gradle tasks.
     *
     * <p>The named form is not the id on its own: {@code migrapheAmend} takes it as {@code
     * --migration=<id>}, and an operator copying {@code amend <id>} into a build would get a task
     * name Gradle cannot resolve.
     */
    GRADLE(
            "./gradlew migrapheAmend --migration=<id>",
            "the migrapheAmend task",
            "./gradlew migrapheRebuild",
            "./gradlew migrapheDown --target=<id>",
            "./gradlew migrapheUpgradeHistory",
            "./gradlew migrapheInit");

    private final String named;
    private final String plain;
    private final String rebuild;
    private final String down;
    private final String upgradeHistory;
    private final String init;

    RepairVocabulary(
            String named,
            String plain,
            String rebuild,
            String down,
            String upgradeHistory,
            String init) {
        this.named = named;
        this.plain = plain;
        this.rebuild = rebuild;
        this.down = down;
        this.upgradeHistory = upgradeHistory;
        this.init = init;
    }

    /**
     * How to bring the history to the shape this version writes.
     *
     * <p>The one invocation that does not change between releases: whatever a given version has to
     * do to a history it did not write, this is what the operator runs.
     *
     * @return the invocation, as an operator would type it
     */
    public String upgradeHistory() {
        return upgradeHistory;
    }

    /**
     * How to create the history in a store that has none.
     *
     * <p>Named on its own, never alongside {@code up}. {@code up} does create the history when it
     * finds none, but a refusal from a command that only reports must not offer applying migrations
     * as the way to make it report — that answers a reading problem with a write to the database.
     *
     * @return the invocation, as an operator would type it
     */
    public String init() {
        return init;
    }

    /**
     * How to repair one named migration, including withdrawing one the definitions lost.
     *
     * @return the invocation, with the identifier left as a placeholder
     */
    public String named() {
        return named;
    }

    /**
     * How to refer to amending at all, where no particular invocation is being prescribed.
     *
     * @return the name to use in prose
     */
    public String plain() {
        return plain;
    }

    /**
     * How to sweep every difference at once, when repairing them one at a time is not wanted.
     *
     * @return the invocation, as an operator would type it
     */
    public String rebuild() {
        return rebuild;
    }

    /**
     * How to take one migration out, which is how a single drifted one is usually dealt with.
     *
     * @return the invocation, with the identifier left as a placeholder
     */
    public String down() {
        return down;
    }
}
