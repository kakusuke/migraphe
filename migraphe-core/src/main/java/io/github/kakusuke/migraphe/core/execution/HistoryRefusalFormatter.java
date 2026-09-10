package io.github.kakusuke.migraphe.core.execution;

import io.github.kakusuke.migraphe.api.graph.NodeId;
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
 * <p>The repair is named per migration, because that is what exists to name: {@code amend <id>}
 * rebuilds a row from the definition that declares it, and withdraws one no task file declares any
 * more. Both halves are therefore reachable, one migration at a time.
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
        lines.add("Run '" + repair.named() + "' for each of these.");
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
