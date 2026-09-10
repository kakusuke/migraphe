package io.github.kakusuke.migraphe.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.history.HistoryUpgrade;
import io.github.kakusuke.migraphe.api.history.UpgradeContext;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.core.execution.support.DelegatingHistoryRepository;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.SimpleMigrationNode;
import io.github.kakusuke.migraphe.core.plugin.SimpleTarget;
import io.github.kakusuke.migraphe.core.plugin.SimpleTask;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpgradeHistoryService")
class UpgradeHistoryServiceTest {

    @Test
    @DisplayName("plan() は残っているものだけを申告順に並べる")
    void planListsOnlyWhatIsStillPendingInDeclaredOrder() {
        StubUpgrade first = new StubUpgrade("add the fingerprint column", true);
        StubUpgrade second = new StubUpgrade("add the origin column", false);
        StubUpgrade third = new StubUpgrade("add the no_way_back column", true);

        UpgradeHistoryService service =
                new UpgradeHistoryService(
                        MigrationGraph.create(), repositoryDeclaring(first, second, third));

        assertThat(service.plan().pending()).containsExactly(first, third);
    }

    @Test
    @DisplayName("apply() は残っているものだけを申告順に適用し、定義を渡す")
    void applyRunsThePendingOnesInOrderAndHandsThemTheDefinitions() {
        StubUpgrade first = new StubUpgrade("add the fingerprint column", true);
        StubUpgrade second = new StubUpgrade("add the origin column", false);
        StubUpgrade third = new StubUpgrade("add the no_way_back column", true);
        MigrationGraph definitions = MigrationGraph.create();

        UpgradeHistoryService service =
                new UpgradeHistoryService(definitions, repositoryDeclaring(first, second, third));
        UpgradeHistoryService.UpgradeOutcome outcome = service.apply(service.plan());

        assertThat(outcome.applied()).isEqualTo(2);
        assertThat(applicationOrder).containsExactly(first.description(), third.description());
        assertThat(first.receivedContext).isNotNull();
        assertThat(first.receivedContext.definitions()).isSameAs(definitions);
        assertThat(second.receivedContext).isNull();
        assertThat(service.plan().isUpToDate()).isTrue();
    }

    @Test
    @DisplayName("保留があれば拒否文を返し、無ければ空 — 文言は upgrade コマンドを名指す")
    void refusesWhileAnUpgradeIsPendingAndNamesTheCommandThatClearsIt() {
        StubUpgrade pending = new StubUpgrade("add the origin column", true);
        StubUpgrade done = new StubUpgrade("add the fingerprint column", false);

        List<String> refusal =
                UpgradeHistoryService.pendingRefusal(
                        repositoryDeclaring(done, pending), RepairVocabulary.CLI);

        assertThat(refusal).isNotEmpty();
        assertThat(String.join("\n", refusal))
                .contains("add the origin column")
                .doesNotContain("add the fingerprint column")
                .contains("migraphe upgrade-history");

        assertThat(
                        UpgradeHistoryService.pendingRefusal(
                                repositoryDeclaring(done), RepairVocabulary.CLI))
                .isEmpty();
    }

    @Test
    @DisplayName("apply() が渡す文脈は、名指したノードの畳み込みを core から借りている")
    void theContextLendsCoreFoldingForTheNodeItIsAskedAbout() {
        MigrationGraph definitions = MigrationGraph.create();
        Target target = SimpleTarget.create(TargetId.of("test"), "Test Target");
        definitions.addNode(
                SimpleMigrationNode.builder()
                        .id(NodeId.of("first"))
                        .name("First")
                        .target(target)
                        .upTask(SimpleTask.of("UP: first"))
                        .build());
        definitions.addNode(
                SimpleMigrationNode.builder()
                        .id(NodeId.of("second"))
                        .name("Second")
                        .target(target)
                        .upTask(SimpleTask.of("UP: second"))
                        .build());

        StubUpgrade upgrade = new StubUpgrade("add the fingerprint column", true);
        new UpgradeHistoryService(definitions, repositoryDeclaring(upgrade))
                .apply(new UpgradeHistoryService(definitions, repositoryDeclaring(upgrade)).plan());

        UpgradeContext context = upgrade.receivedContext;
        assertThat(context).isNotNull();
        assertThat(context.definitions()).isSameAs(definitions);
        assertThat(context.fingerprinterFor(NodeId.of("first")).over("up"))
                .isEqualTo(definitions.fingerprinterFor(NodeId.of("first")).over("up"))
                .isNotEqualTo(definitions.fingerprinterFor(NodeId.of("second")).over("up"));
    }

    private static HistoryRepository repositoryDeclaring(HistoryUpgrade... upgrades) {
        return new DelegatingHistoryRepository(new InMemoryHistoryRepository()) {
            @Override
            public List<HistoryUpgrade> upgrades() {
                return List.of(upgrades);
            }
        };
    }

    private final List<String> applicationOrder = new ArrayList<>();

    private final class StubUpgrade implements HistoryUpgrade {

        private final String description;
        private boolean pending;
        private @Nullable UpgradeContext receivedContext;

        StubUpgrade(String description, boolean pending) {
            this.description = description;
            this.pending = pending;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public boolean isPending() {
            return pending;
        }

        @Override
        public void apply(UpgradeContext context) {
            applicationOrder.add(description);
            receivedContext = context;
            pending = false;
        }
    }
}
