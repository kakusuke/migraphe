package io.github.kakusuke.migraphe.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AmendCommandTest {

    private static final String JDBC_URL = "jdbc:h2:mem:amend_cmd;DB_CLOSE_DELAY=-1";

    @Test
    void theNamedFormTakesTheEditedMigrationAndNothingElse(@TempDir Path tempDir)
            throws IOException, SQLException {
        PluginRegistry registry = new PluginRegistry();
        registry.loadFromClasspath();

        writeProject(tempDir);
        ExecutionContext applied = ExecutionContext.load(tempDir, registry);
        captureStdout(() -> new UpCommand(applied, null, true, false).execute());

        // 002 is edited after having been applied.
        Files.writeString(
                tempDir.resolve("tasks/002_add_index.yaml"),
                """
                name: Add index
                target: h2-db
                autocommit: true
                dependencies:
                  - 001_create_users
                up: |
                  CREATE INDEX idx_users_id ON users(id); -- reformatted
                down: |
                  DROP INDEX IF EXISTS idx_users_id;
                """);

        ExecutionContext reloaded = ExecutionContext.load(tempDir, registry);

        Command named =
                new AmendCommand(
                        reloaded,
                        NodeId.of("002_add_index"),
                        false,
                        true,
                        new ByteArrayInputStream(new byte[0]));
        String namedStdout = captureStdout(named::execute);

        assertThat(namedStdout).contains("[!] → [✓]  002_add_index");
        assertThat(namedStdout).doesNotContain("001_create_users");
        assertThat(namedStdout).contains("a row is appended saying what the definition says");
    }

    @Test
    void theNamedFormShowsAMigrationTheHistoryNeverRecordedAsNotApplied(@TempDir Path tempDir)
            throws IOException {
        PluginRegistry registry = new PluginRegistry();
        registry.loadFromClasspath();

        writeProject(tempDir, "jdbc:h2:mem:amend_bootstrap;DB_CLOSE_DELAY=-1");
        ExecutionContext context = ExecutionContext.load(tempDir, registry);

        Command named =
                new AmendCommand(
                        context,
                        NodeId.of("001_create_users"),
                        false,
                        true,
                        new ByteArrayInputStream(new byte[0]));
        String stdout = captureStdout(named::execute);

        assertThat(stdout).contains("[ ] → [✓]  001_create_users");
    }

    @Test
    void anOrphanOnlyPlanCountsWithdrawalsRatherThanFingerprints(@TempDir Path tempDir)
            throws IOException {
        PluginRegistry registry = new PluginRegistry();
        registry.loadFromClasspath();

        String jdbcUrl = "jdbc:h2:mem:amend_counts;DB_CLOSE_DELAY=-1";
        writeProject(tempDir, jdbcUrl);
        ExecutionContext applied = ExecutionContext.load(tempDir, registry);
        captureStdout(() -> new UpCommand(applied, null, true, false).execute());

        Files.delete(tempDir.resolve("tasks/002_add_index.yaml"));
        ExecutionContext reloaded = ExecutionContext.load(tempDir, registry);

        Command withdraw =
                new AmendCommand(
                        reloaded,
                        NodeId.of("002_add_index"),
                        false,
                        false,
                        new ByteArrayInputStream("y\n".getBytes(StandardCharsets.UTF_8)));
        String stdout = captureStdout(withdraw::execute);

        assertThat(stdout).contains("1 migration will be withdrawn.");
        assertThat(stdout).contains("Withdraw 1 migration? [y/N]: ");
        assertThat(stdout).contains("Withdrew 1 migration.");
        assertThat(stdout).doesNotContain("fingerprint");
        assertThat(stdout).doesNotContain("a row is appended saying what the definition says");
    }

    @Test
    void anAmendThatAppendedNothingSaysSo(@TempDir Path tempDir) throws IOException {
        PluginRegistry registry = new PluginRegistry();
        registry.loadFromClasspath();

        String jdbcUrl = "jdbc:h2:mem:amend_vanished;DB_CLOSE_DELAY=-1";
        writeProject(tempDir, jdbcUrl);
        ExecutionContext applied = ExecutionContext.load(tempDir, registry);
        captureStdout(() -> new UpCommand(applied, null, true, false).execute());

        Files.delete(tempDir.resolve("tasks/002_add_index.yaml"));
        ExecutionContext reloaded = ExecutionContext.load(tempDir, registry);

        Command withdraw =
                new AmendCommand(
                        reloaded,
                        NodeId.of("002_add_index"),
                        false,
                        false,
                        answeringYesAfterDeleting(jdbcUrl, "002_add_index"));
        String stdout = captureStdout(withdraw::execute);

        assertThat(stdout).contains("Nothing was written.");
        assertThat(stdout).doesNotContain("Withdrew");
    }

    @Test
    void everyAmendPlanSaysAllFourRecordedAttributesAreReplaced(@TempDir Path tempDir)
            throws IOException, SQLException {
        PluginRegistry registry = new PluginRegistry();
        registry.loadFromClasspath();

        String jdbcUrl = "jdbc:h2:mem:amend_notice;DB_CLOSE_DELAY=-1";
        writeProject(tempDir, jdbcUrl);
        ExecutionContext applied = ExecutionContext.load(tempDir, registry);
        captureStdout(() -> new UpCommand(applied, null, true, false).execute());

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE migraphe_history SET fingerprint = NULL");
        }

        ExecutionContext reloaded = ExecutionContext.load(tempDir, registry);
        Command named =
                new AmendCommand(
                        reloaded,
                        NodeId.of("001_create_users"),
                        true,
                        true,
                        new ByteArrayInputStream(new byte[0]));

        String stdout = captureStdout(named::execute);

        assertThat(stdout).contains("a row is appended saying what the definition says");
        assertThat(stdout).contains("what the history reports about it becomes the current");
        assertThat(stdout).contains("rows already there are neither changed nor removed");
    }

    private static void writeProject(Path baseDir) throws IOException {
        writeProject(baseDir, JDBC_URL);
    }

    private static void writeProject(Path baseDir, String jdbcUrl) throws IOException {
        Files.writeString(
                baseDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test
                history:
                  target: h2-db
                """);
        Path targetsDir = Files.createDirectories(baseDir.resolve("targets"));
        Files.writeString(
                targetsDir.resolve("h2-db.yaml"),
                """
                type: jdbc
                driver_class: org.h2.Driver
                db_label: H2
                jdbc_url: %s
                username: sa
                """
                        .formatted(jdbcUrl));
        Path tasksDir = Files.createDirectories(baseDir.resolve("tasks"));
        Files.writeString(
                tasksDir.resolve("001_create_users.yaml"),
                """
                name: Create users
                target: h2-db
                autocommit: true
                up: |
                  CREATE TABLE users (id INT PRIMARY KEY);
                down: |
                  DROP TABLE IF EXISTS users;
                """);
        Files.writeString(
                tasksDir.resolve("002_add_index.yaml"),
                """
                name: Add index
                target: h2-db
                autocommit: true
                dependencies:
                  - 001_create_users
                up: |
                  CREATE INDEX idx_users_id ON users(id);
                down: |
                  DROP INDEX IF EXISTS idx_users_id;
                """);
    }

    @Test
    void shouldRefuseAnIdItCannotActOnAndStillSucceedWhenThereIsNothingToDo(@TempDir Path tempDir)
            throws IOException, SQLException {
        PluginRegistry registry = new PluginRegistry();
        registry.loadFromClasspath();

        writeProject(tempDir, "jdbc:h2:mem:amend_blocked;DB_CLOSE_DELAY=-1");
        ExecutionContext applied = ExecutionContext.load(tempDir, registry);
        captureStdout(() -> new UpCommand(applied, null, true, false).execute());

        // 002 becomes an orphan; 001 stays defined and agreeing with its record.
        Files.delete(tempDir.resolve("tasks/002_add_index.yaml"));
        ExecutionContext reloaded = ExecutionContext.load(tempDir, registry);

        AtomicInteger exitCode = new AtomicInteger();

        // An id that names nothing anywhere
        String unknownErr =
                captureStderr(() -> exitCode.set(amend(reloaded, "001_typo").execute()));
        assertThat(exitCode.get()).isNotZero();
        assertThat(unknownErr).contains("No such migration").contains("001_typo");

        // An id the history holds but the definitions no longer declare is withdrawn, and the
        // operator is told the objects stay behind before it happens
        String orphanOut =
                captureStdout(() -> exitCode.set(amend(reloaded, "002_add_index").execute()));
        assertThat(exitCode.get()).isZero();
        assertThat(orphanOut)
                .contains("[✓] → [ ]  002_add_index")
                .contains("no longer defined")
                .contains("objects")
                .contains("migraphe down");

        // A migration that is defined and already agrees is claimed too: the command states what
        // the definitions say, and that is the same sentence whether or not the row already said
        // it. Drift is not a condition on a claim the operator made deliberately.
        String agreeingOut =
                captureStdout(() -> exitCode.set(amend(reloaded, "001_create_users").execute()));
        assertThat(exitCode.get()).isZero();
        assertThat(agreeingOut).contains("001_create_users").doesNotContain("Nothing to amend.");
    }

    private static Command amend(ExecutionContext context, String migration) {
        return new AmendCommand(
                context, NodeId.of(migration), true, false, new ByteArrayInputStream(new byte[0]));
    }

    /** Answers the prompt with yes, having removed the row the plan was built from. */
    private static InputStream answeringYesAfterDeleting(String jdbcUrl, String nodeId) {
        return new ByteArrayInputStream("y\n".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                        Statement statement = connection.createStatement()) {
                    statement.executeUpdate(
                            "DELETE FROM migraphe_history WHERE node_id = '" + nodeId + "'");
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
                return super.read(buffer, offset, length);
            }
        };
    }

    private static String captureStderr(Runnable action) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.err;
        System.setErr(new PrintStream(baos, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setErr(original);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private static String captureStdout(Runnable action) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
