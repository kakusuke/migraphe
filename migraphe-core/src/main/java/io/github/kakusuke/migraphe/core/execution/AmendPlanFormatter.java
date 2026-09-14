package io.github.kakusuke.migraphe.core.execution;

import java.util.List;

/**
 * Turns an {@link AmendBlocker} into the lines a front end prints.
 *
 * <p>Lives in core rather than in either front end so that the CLI and the Gradle task refuse in
 * the same words, the way {@link UpPlanFormatter} and {@link DownPlanFormatter} already do.
 */
public final class AmendPlanFormatter {

    private AmendPlanFormatter() {}

    /**
     * Renders why amending was refused.
     *
     * <p>Switches without a {@code default} arm: adding a kind of blocker should stop this
     * compiling until someone decides how it reads.
     *
     * @param blocker what stopped the command
     * @return the lines to print, first one prefixed {@code Error:}
     */
    public static List<String> format(AmendBlocker blocker) {
        return switch (blocker) {
            case AmendBlocker.NoSuchMigration noSuch ->
                    List.of(
                            "Error: No such migration: " + noSuch.nodeId().value(),
                            "Nothing in this project defines it, and no applied migration"
                                    + " goes by that name.");
            case AmendBlocker.NoFingerprint noFingerprint ->
                    List.of(
                            "Error: "
                                    + noFingerprint.nodeId().value()
                                    + " cannot be amended: its plugin reports no fingerprint for"
                                    + " it.",
                            "A row appended without one would read as a row whose recorded content"
                                    + " cannot be trusted, which is the opposite of what amending"
                                    + " writes.");
            case AmendBlocker.CannotReportRollbackPayload cannotReport ->
                    List.of(
                            "Error: "
                                    + cannotReport.nodeId().value()
                                    + " cannot be amended: its plugin cannot say what rollback"
                                    + " it would record.",
                            "Amend runs nothing, so it has to ask the up task for that value"
                                    + " instead of getting it from a run.",
                            "The task that cannot report it is "
                                    + cannotReport.upTaskClassName()
                                    + ".",
                            "Rolling the migration back and applying it again records the"
                                    + " rollback the ordinary way.");
        };
    }
}
