package io.github.kakusuke.migraphe.core.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.history.ExecutionOrigin;
import io.github.kakusuke.migraphe.api.history.ExecutionRecord;
import io.github.kakusuke.migraphe.api.history.ExecutionStatus;
import io.github.kakusuke.migraphe.api.history.HistoryRepository;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.api.task.ExecutionDirection;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryHistoryRepositoryTest {

    private final TargetId targetId = TargetId.of("dev");
    private final TargetId stagingEnvId = TargetId.of("staging");
    private final NodeId node1 = NodeId.of("node-1");
    private final NodeId node2 = NodeId.of("node-2");

    private HistoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryHistoryRepository();
        repository.initialize();
    }

    @Test
    void shouldKeepANodeAppliedWhenItsRollbackFailed() {
        // given
        repository.record(ExecutionRecord.upSuccess(node1, targetId, "Create table", null, 100));
        repository.record(
                ExecutionRecord.failure(
                        node1,
                        targetId,
                        ExecutionDirection.DOWN,
                        "Rollback",
                        "constraint violation"));

        // when & then
        assertThat(repository.wasExecuted(node1)).isTrue();
        assertThat(repository.executedNodes()).containsExactly(node1);
    }

    @Test
    void allRecordsReturnsEveryTargetsRows() {
        // given
        ExecutionRecord onDev =
                ExecutionRecord.upSuccess(node1, targetId, "Create table", null, 100);
        ExecutionRecord onStaging =
                ExecutionRecord.upSuccess(node2, stagingEnvId, "Create index", null, 100);
        repository.record(onDev);
        repository.record(onStaging);

        // when & then
        assertThat(repository.allRecords()).containsExactlyInAnyOrder(onDev, onStaging);
    }

    @Test
    void latestAppliesKeepsTheNewestApplyOfEachMigration() {
        // given
        ExecutionRecord firstApply =
                ExecutionRecord.upSuccess(node1, targetId, "Create table", null, 100);
        ExecutionRecord rolledBack =
                ExecutionRecord.downSuccess(node1, targetId, "Create table", 100);
        ExecutionRecord reapplied =
                ExecutionRecord.upSuccess(node1, targetId, "Create table again", null, 100);
        ExecutionRecord onlyRolledBack =
                ExecutionRecord.downSuccess(node2, targetId, "Never applied here", 100);
        repository.record(firstApply);
        repository.record(rolledBack);
        repository.record(reapplied);
        repository.record(onlyRolledBack);

        // when & then
        assertThat(repository.latestApplies()).containsExactly(reapplied);
    }

    @Test
    void aRollbackClearsThePlacementWhateverTargetTheRollbackRowNames() {
        // given
        repository.record(ExecutionRecord.upSuccess(node1, targetId, "Create table", null, 100));
        repository.record(ExecutionRecord.downSuccess(node1, stagingEnvId, "Create table", 100));
        ExecutionRecord appliedThere =
                ExecutionRecord.upSuccess(node1, stagingEnvId, "Create table", null, 100);
        repository.record(appliedThere);

        // when & then
        assertThat(repository.latestApplies()).containsExactly(appliedThere);
    }

    @Test
    void latestAppliesRefusesASecondExecutedApplyWhileTheMigrationStands() {
        // given
        repository.record(ExecutionRecord.upSuccess(node1, targetId, "Create table", null, 100));
        repository.record(
                ExecutionRecord.upSuccess(node1, stagingEnvId, "Create table", null, 100));

        // when & then
        assertThatThrownBy(repository::latestApplies)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("node-1");
    }

    @Test
    void theRefusalNamesBothRowsAndClaimsNothingAboutHowTheyGotThere() {
        // given
        repository.record(
                new ExecutionRecord(
                        "row-first",
                        node1,
                        targetId,
                        ExecutionDirection.UP,
                        ExecutionStatus.SUCCESS,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "Create table",
                        null,
                        100L,
                        null,
                        null,
                        null,
                        null,
                        ExecutionOrigin.EXECUTED,
                        null));
        repository.record(
                new ExecutionRecord(
                        "row-second",
                        node1,
                        stagingEnvId,
                        ExecutionDirection.UP,
                        ExecutionStatus.SUCCESS,
                        Instant.parse("2026-02-02T00:00:00Z"),
                        "Create table",
                        null,
                        100L,
                        null,
                        null,
                        null,
                        null,
                        ExecutionOrigin.EXECUTED,
                        null));

        // when & then
        assertThatThrownBy(repository::latestApplies)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "\n  row-first  target=dev  at=2026-01-01T00:00:00Z  origin=EXECUTED"
                                + "\n  row-second  target=staging  at=2026-02-02T00:00:00Z"
                                + "  origin=EXECUTED\n")
                .hasMessageContaining("no migraphe command removes one")
                .hasMessageNotContaining("edited or damaged");
    }

    @Test
    void latestAppliesRefusesASecondExecutedApplyAgainstTheSameTargetToo() {
        // given
        repository.record(ExecutionRecord.upSuccess(node1, targetId, "Create table", null, 100));
        repository.record(ExecutionRecord.upSuccess(node1, targetId, "Create table", null, 100));

        // when & then
        assertThatThrownBy(repository::latestApplies)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("node-1");
    }

    @Test
    void latestAppliesTakesAnAmendedApplyOverTheStandingPlacement() {
        // given
        repository.record(ExecutionRecord.upSuccess(node1, targetId, "Create table", null, 100));
        ExecutionRecord claimed =
                ExecutionRecord.amendedUp(
                        node1, stagingEnvId, "Create table", null, "abc", null, List.of(), null);
        repository.record(claimed);

        // when & then
        assertThat(repository.latestApplies()).containsExactly(claimed);
    }

    @Test
    void latestAppliesLeavesOutAPlacementThatWasRolledBackAndNotReapplied() {
        // given
        repository.record(ExecutionRecord.upSuccess(node1, targetId, "Create table", null, 100));
        repository.record(ExecutionRecord.downSuccess(node1, targetId, "Create table", 100));
        ExecutionRecord movedTo =
                ExecutionRecord.upSuccess(node1, stagingEnvId, "Create table", null, 100);
        repository.record(movedTo);

        // when & then
        assertThat(repository.latestApplies()).containsExactly(movedTo);
    }

    @Test
    void wasExecutedAnswersForTheIdWhateverTargetAppliedIt() {
        // given: a migration id is unique across targets, so where it was applied is not the
        // question
        repository.record(
                ExecutionRecord.upSuccess(node1, stagingEnvId, "Applied elsewhere", null, 100));

        // when & then
        assertThat(repository.wasExecuted(node1)).isTrue();
        assertThat(repository.wasExecuted(node2)).isFalse();
    }

    @Test
    void executedNodesListsEveryAppliedMigrationWhateverTargetHoldsIt() {
        // given
        repository.record(ExecutionRecord.upSuccess(node1, targetId, "On dev", null, 100));
        repository.record(ExecutionRecord.upSuccess(node2, stagingEnvId, "On staging", null, 100));

        // when & then
        assertThat(repository.executedNodes()).containsExactlyInAnyOrder(node1, node2);
    }

    @Test
    void shouldStartWithNoRecords() {
        // when & then
        assertThat(repository.allRecords()).isEmpty();
        assertThat(repository.executedNodes()).isEmpty();
    }

    @Test
    void shouldRecordExecution() {
        // given
        ExecutionRecord record =
                ExecutionRecord.upSuccess(node1, targetId, "Create table", null, 100);

        // when
        repository.record(record);

        // then
        assertThat(repository.allRecords()).containsExactly(record);
    }

    @Test
    void shouldCheckIfNodeWasExecuted() {
        // given
        ExecutionRecord record =
                ExecutionRecord.upSuccess(node1, targetId, "Create table", null, 100);

        // when
        repository.record(record);

        // then
        assertThat(repository.wasExecuted(node1)).isTrue();
        assertThat(repository.wasExecuted(node2)).isFalse();
    }

    @Test
    void shouldReturnExecutedNodes() {
        // given
        ExecutionRecord record1 =
                ExecutionRecord.upSuccess(node1, targetId, "Create table", null, 100);
        ExecutionRecord record2 =
                ExecutionRecord.upSuccess(node2, targetId, "Add column", null, 50);

        // when
        repository.record(record1);
        repository.record(record2);

        // then
        List<NodeId> executed = repository.executedNodes();
        assertThat(executed).containsExactly(node1, node2);
    }

    @Test
    void shouldFindLatestRecordForNode() {
        // given
        ExecutionRecord record1 =
                ExecutionRecord.upSuccess(node1, targetId, "First execution", null, 100);
        ExecutionRecord record2 = ExecutionRecord.downSuccess(node1, targetId, "Rollback", 50);

        // when
        repository.record(record1);
        repository.record(record2);

        // then
        ExecutionRecord latest = repository.findLatestRecord(node1);
        assertThat(latest).isNotNull();
        assertThat(latest.direction()).isEqualTo(ExecutionDirection.DOWN);
    }

    @Test
    void shouldFindTheLatestRecordEvenWhenItWasRecordedUnderAnotherTarget() {
        // given
        repository.record(ExecutionRecord.upSuccess(node1, targetId, "First execution", null, 100));
        repository.record(ExecutionRecord.downSuccess(node1, stagingEnvId, "Rollback", 50));

        // when
        ExecutionRecord latest = repository.findLatestRecord(node1);

        // then
        assertThat(latest).isNotNull();
        assertThat(latest.direction()).isEqualTo(ExecutionDirection.DOWN);
        assertThat(latest.targetId()).isEqualTo(stagingEnvId);
    }

    @Test
    void shouldNotConsiderFailedExecutionAsExecuted() {
        // given
        ExecutionRecord failedRecord =
                ExecutionRecord.failure(node1, targetId, ExecutionDirection.UP, "Failed", "Error");

        // when
        repository.record(failedRecord);

        // then
        assertThat(repository.wasExecuted(node1)).isFalse();
    }

    @Test
    void shouldNotConsiderSkippedExecutionAsExecuted() {
        // given
        ExecutionRecord skippedRecord =
                ExecutionRecord.skipped(node1, targetId, "Skipped", "Already applied");

        // when
        repository.record(skippedRecord);

        // then
        assertThat(repository.wasExecuted(node1)).isFalse();
    }

    @Test
    void shouldReturnAllRecordsInOrder() {
        // given
        ExecutionRecord record1 = ExecutionRecord.upSuccess(node1, targetId, "First", null, 100);
        ExecutionRecord record2 = ExecutionRecord.upSuccess(node2, targetId, "Second", null, 50);

        // when
        repository.record(record1);
        repository.record(record2);

        // then
        assertThat(repository.allRecords()).containsExactly(record1, record2);
    }

    @Test
    void keepsEachRowsTargetWhileAppliedNessIsAboutTheIdAlone() {
        // given
        ExecutionRecord devRecord =
                ExecutionRecord.upSuccess(node1, targetId, "Dev migration", null, 100);
        ExecutionRecord stagingRecord =
                ExecutionRecord.upSuccess(node2, stagingEnvId, "Staging migration", null, 50);

        // when
        repository.record(devRecord);
        repository.record(stagingRecord);

        // then
        assertThat(repository.allRecords())
                .filteredOn(record -> record.targetId().equals(targetId))
                .containsExactly(devRecord);
        assertThat(repository.allRecords())
                .filteredOn(record -> record.targetId().equals(stagingEnvId))
                .containsExactly(stagingRecord);
        // Each row keeps the target it was written against, but a migration id is unique across
        // the project, so where it was applied is not part of asking whether it is applied.
        assertThat(repository.wasExecuted(node1)).isTrue();
        assertThat(repository.wasExecuted(node2)).isTrue();
    }

    @Test
    void shouldReturnEmptyForNonExistentNode() {
        // when
        ExecutionRecord latest = repository.findLatestRecord(node1);

        // then
        assertThat(latest).isNull();
    }

    @Test
    void aTiedTimestampIsBrokenByIdSoTheLaterRowWins() {
        Instant sameInstant = Instant.parse("2026-08-20T10:00:00Z");
        repository.record(
                recordAt(
                        "00000000-0000-7000-8000-000000000001",
                        sameInstant,
                        ExecutionDirection.DOWN));
        repository.record(
                recordAt(
                        "00000000-0000-7000-8000-000000000002",
                        sameInstant,
                        ExecutionDirection.UP));

        assertThat(repository.wasExecuted(node1)).isTrue();
        assertThat(repository.executedNodes()).containsExactly(node1);
        ExecutionRecord latest = repository.findLatestRecord(node1);
        assertThat(latest).isNotNull();
        assertThat(latest.id()).isEqualTo("00000000-0000-7000-8000-000000000002");
    }

    @Test
    void aTiedTimestampIsBrokenByIdEvenWhenTheRowsArriveInReverseOrder() {
        Instant sameInstant = Instant.parse("2026-08-20T10:00:00Z");
        repository.record(
                recordAt(
                        "00000000-0000-7000-8000-000000000002",
                        sameInstant,
                        ExecutionDirection.DOWN));
        repository.record(
                recordAt(
                        "00000000-0000-7000-8000-000000000001",
                        sameInstant,
                        ExecutionDirection.UP));

        assertThat(repository.wasExecuted(node1)).isFalse();
        assertThat(repository.executedNodes()).isEmpty();
        ExecutionRecord latest = repository.findLatestRecord(node1);
        assertThat(latest).isNotNull();
        assertThat(latest.id()).isEqualTo("00000000-0000-7000-8000-000000000002");
    }

    @Test
    void aBackendWithNoOlderShapesDeclaresNoUpgrades() {
        assertThat(repository.upgrades()).isEmpty();
    }

    private ExecutionRecord recordAt(String id, Instant executedAt, ExecutionDirection direction) {
        return new ExecutionRecord(
                id,
                node1,
                targetId,
                direction,
                ExecutionStatus.SUCCESS,
                executedAt,
                "test description",
                direction == ExecutionDirection.UP ? "DOWN SQL" : null,
                100L,
                null,
                null,
                null,
                null,
                ExecutionOrigin.EXECUTED,
                null);
    }
}
