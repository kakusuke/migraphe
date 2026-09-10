package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AmendPlanFormatter")
class AmendPlanFormatterTest {

    @Test
    @DisplayName("断る理由ごとに、何が起きたかと次の一手を示す")
    void shouldSayWhatHappenedAndWhatToDoNext() {
        // When
        var noSuch =
                AmendPlanFormatter.format(
                        new AmendBlocker.NoSuchMigration(NodeId.of("db1/001_typo")));
        var cannotReport =
                AmendPlanFormatter.format(
                        new AmendBlocker.CannotReportRollbackPayload(
                                NodeId.of("db1/003_c"), "com.example.SilentUpTask"));

        // Then: an id that names nothing says so, and does not claim it is applied
        assertThat(noSuch.get(0)).startsWith("Error: ").contains("db1/001_typo");
        assertThat(String.join("\n", noSuch))
                .contains("Nothing in this project defines it")
                .contains("no applied migration goes by that name")
                .doesNotContain("recorded in the history");

        // Then: a plugin that cannot report its rollback is named, so the operator knows which one
        assertThat(cannotReport.get(0)).startsWith("Error: ").contains("db1/003_c");
        assertThat(String.join("\n", cannotReport))
                .contains("com.example.SilentUpTask")
                .contains("Amend runs nothing")
                .doesNotContain("No such migration");
    }
}
