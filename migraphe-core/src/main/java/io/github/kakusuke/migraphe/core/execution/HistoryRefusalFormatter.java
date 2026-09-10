package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryUpgrade;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The words every command refuses an unreadable history in.
 *
 * <p>One home rather than one per command, because the design requires it: a row carrying no
 * fingerprint stops {@code up}, {@code down} and {@code rebuild} alike, and each of them "names the
 * repair in the same words". Three copies of a sentence are three chances for one of them to drift
 * while its own test stays green, and the drift would show up as three commands disagreeing about
 * what an operator has to do.
 *
 * <p><strong>Both halves are named</strong>, because neither reaches every row. {@code upgrade}
 * fills a row from the definition that names it, so it reaches only what the task files still
 * declare; a row they no longer declare is reached by naming it in {@code amend}, which withdraws
 * it. Naming only the first sends an operator holding an orphan into a loop with no exit.
 *
 * <p>The marker is {@code status}'s own {@code [?]}, so the listing reads as the thing {@code
 * status} already showed them.
 */
public final class HistoryRefusalFormatter {

    private HistoryRefusalFormatter() {}

    /**
     * Formats the refusal.
     *
     * @param nodes the migrations whose applied row carries no fingerprint
     * @param repair how this front end is invoked, for the commands the message names
     * @return the lines to report, sorted by id so two runs of one state read alike
     */
    public static List<String> incompleteLines(Set<NodeId> nodes, RepairVocabulary repair) {
        List<String> lines = new ArrayList<>();
        lines.add(
                "Error: "
                        + nodes.size()
                        + " applied migration(s) were recorded by a version that did not record"
                        + " what it applied, so nothing in those rows can be read:");
        nodes.stream().map(NodeId::value).sorted().forEach(id -> lines.add("  [?] " + id));
        lines.add(
                "Run '"
                        + repair.upgradeHistory()
                        + "', then '"
                        + repair.named()
                        + "' for any of these no task file declares any more.");
        return List.copyOf(lines);
    }

    /**
     * Formats the refusal for a store where the history has never been created.
     *
     * <p>Every command asks before it reads, so that reporting on migrations does not bring a table
     * into being as a side effect. {@code up} is the exception and creates it, because applying the
     * first migration is the moment a project's history should exist — but this message does not
     * offer that, since answering "I cannot report" with "then change the database" is not a
     * remedy.
     *
     * @param repair how this front end is invoked, for the command the message names
     * @return the lines to report
     */
    public static List<String> notInitializedLines(RepairVocabulary repair) {
        return List.of(
                "Error: the migration history has not been created here.",
                "Run '" + repair.init() + "'.");
    }

    /**
     * Formats the refusal for a history that still needs an upgrade.
     *
     * <p>Every other command refuses while one is outstanding, because the history is not yet in
     * the shape this version reads and writes — a column it selects may not exist, and the failure
     * an operator would otherwise see is a raw SQL error naming a column rather than a sentence
     * naming what to run.
     *
     * <p>The upgrades are listed rather than counted: they say what is about to change, and an
     * operator deciding when to schedule this needs to know whether it renames a column a second
     * deployment is still reading.
     *
     * @param pending the outstanding upgrades, in the order they will be applied
     * @param repair how this front end is invoked, for the command the message names
     * @return the lines to report
     */
    public static List<String> pendingUpgradeLines(
            List<HistoryUpgrade> pending, RepairVocabulary repair) {
        List<String> lines = new ArrayList<>();
        lines.add(
                "Error: the migration history was written by an older release and needs "
                        + pending.size()
                        + " upgrade(s) before this version can read it:");
        pending.forEach(upgrade -> lines.add("  " + upgrade.description()));
        lines.add("Run '" + repair.upgradeHistory() + "'.");
        return List.copyOf(lines);
    }

    /**
     * Formats the refusal for a row no {@code amend} can repair.
     *
     * <p>Named apart from {@link #incompleteLines} because prescribing a repair that cannot run is
     * worse than naming none: the plugin cannot say what its own migration applied, so there is
     * nothing to rebuild the row from, and an operator following the other message would run {@code
     * amend} and meet the identical refusal.
     *
     * @param nodes the migrations whose plugin cannot report a token
     * @param repair how this front end is invoked, for the command the message names
     * @return the lines to report, sorted by id
     */
    public static List<String> unreadableLines(Set<NodeId> nodes, RepairVocabulary repair) {
        List<String> lines = new ArrayList<>();
        lines.add(
                "Error: the plugin could not report what these migrations applied, so nothing can"
                        + " be compared:");
        nodes.stream().map(NodeId::value).sorted().forEach(id -> lines.add("  [E] " + id));
        lines.add("That is a fault in the plugin, not a state '" + repair.plain() + "' repairs.");
        return List.copyOf(lines);
    }
}
