package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpPlanFormatter")
class UpPlanFormatterTest {

    @Test
    @DisplayName("ドリフトの拒否は down を先に案内し、rebuild は一括手段として後に置く")
    void namesTheTargetedRemedyBeforeTheSweep() {
        List<String> lines =
                UpPlanFormatter.format(
                        new UpBlocker.EditedSinceApplied(Set.of(NodeId.of("db/002_add_email"))),
                        RepairVocabulary.CLI);

        String message = String.join("\n", lines);
        assertThat(message).contains("[!] db/002_add_email");
        // Taking the migration out and letting up re-apply it is the move for one drifted
        // migration; rebuilding is how you sweep every difference at once.
        assertThat(message).contains("migraphe down <id>").contains("migraphe amend <id>");
        assertThat(message.indexOf("migraphe down <id>"))
                .isLessThan(message.indexOf("migraphe rebuild"));
    }
}
