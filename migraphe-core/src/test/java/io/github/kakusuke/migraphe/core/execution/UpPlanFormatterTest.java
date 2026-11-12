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

    @Test
    @DisplayName("無効なタスクへの依存は、未定義の依存とは別の言葉で報告する")
    void namesADisabledDependencyAsDisabledNotAsUndefined() {
        java.util.Map<NodeId, Set<NodeId>> byNode =
                java.util.Map.of(NodeId.of("db/users"), Set.of(NodeId.of("db/seed")));

        String disabled =
                String.join(
                        "\n",
                        UpPlanFormatter.format(
                                new UpBlocker.DisabledDependencies(byNode), RepairVocabulary.CLI));
        String undefined =
                String.join(
                        "\n",
                        UpPlanFormatter.format(
                                new UpBlocker.UnresolvedDependencies(byNode),
                                RepairVocabulary.CLI));

        assertThat(disabled)
                .contains("disabled")
                .contains("  db/users → db/seed")
                .doesNotContain("not defined");
        assertThat(undefined).contains("not defined").contains("  db/users → db/seed");
    }

    @Test
    @DisplayName("enabled: を宣言しないタスクの依存は、宣言を足すか依存を外すよう案内する")
    void namesBothWaysOutOfAnUnconditionalDependencyOnAConditionalTask() {
        String message =
                String.join(
                        "\n",
                        UpPlanFormatter.format(
                                new UpBlocker.ConditionalDependencies(
                                        java.util.Map.of(
                                                NodeId.of("db/report"),
                                                Set.of(NodeId.of("db/seed")))),
                                RepairVocabulary.CLI));

        assertThat(message)
                .contains("without enabled:")
                .contains("Declare enabled: on them too, or drop the dependency")
                .contains("  db/report → db/seed");
    }
}
