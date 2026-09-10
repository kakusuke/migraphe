package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import java.util.List;

/**
 * The one question every command asks before it touches the history: is this history usable.
 *
 * <p>Two ways it is not, and they are asked in this order because the second cannot be answered
 * while the first is true. A history that does not exist yet reports <em>every</em> upgrade as
 * pending — each upgrade's detection query finds nothing, which is indistinguishable from "the
 * change has not been made" — so asking about upgrades first would tell an operator with a fresh
 * database to run {@code upgrade-history}, which would then try to alter a table that is not there.
 *
 * <p>Neither check creates anything. Before this existed, every command called {@code
 * initialize()}, so {@code status --check} in CI brought a table into being merely by reporting
 * that nothing had been applied. Creating the history is now {@code init}'s job, and {@code up}'s —
 * the moment a project's history should come into being is the first migration, not the first
 * question.
 */
public final class HistoryReadiness {

    private HistoryReadiness() {}

    /**
     * Returns the lines a command refuses with, or nothing when the history is ready to use.
     *
     * @param historyRepository the history the command is about to use
     * @param repair how this front end is invoked, for the command the message names
     * @return the refusal lines, or an empty list when the command may proceed
     */
    public static List<String> refusal(
            HistoryRepository historyRepository, RepairVocabulary repair) {
        if (!historyRepository.isInitialized()) {
            return HistoryRefusalFormatter.notInitializedLines(repair);
        }
        return UpgradeHistoryService.pendingRefusal(historyRepository, repair);
    }
}
