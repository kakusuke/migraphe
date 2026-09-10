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
    CLI("migraphe amend <id>", "migraphe amend"),

    /**
     * The Gradle tasks.
     *
     * <p>The named form is not the id on its own: {@code migrapheAmend} takes it as {@code
     * --migration=<id>}, and an operator copying {@code amend <id>} into a build would get a task
     * name Gradle cannot resolve.
     */
    GRADLE("./gradlew migrapheAmend --migration=<id>", "the migrapheAmend task");

    private final String named;
    private final String plain;

    RepairVocabulary(String named, String plain) {
        this.named = named;
        this.plain = plain;
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
}
