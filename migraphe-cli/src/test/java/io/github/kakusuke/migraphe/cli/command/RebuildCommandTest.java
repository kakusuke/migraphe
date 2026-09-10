package io.github.kakusuke.migraphe.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.Fingerprinter;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.graph.MigrationGraph;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RebuildCommandTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("migraphe_test")
                    .withUsername("test")
                    .withPassword("test")
                    .waitingFor(new HostPortWaitStrategy().forPorts(5432));

    @TempDir Path tempDir;

    private PluginRegistry pluginRegistry;
    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() throws SQLException {
        pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        // The container is shared across methods and every method uses the same node ids, so a
        // leftover table or history row would let one method decide what another one measures.
        try (Connection connection =
                        DriverManager.getConnection(postgres.getJdbcUrl(), "test", "test");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("DROP TABLE IF EXISTS audit");
            statement.execute("DROP TABLE IF EXISTS migraphe_history");
        }
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void rebuildsWhatDriftedAndLeavesNothingDiffering() throws Exception {
        writeProject();
        new UpCommand(ExecutionContext.load(tempDir, pluginRegistry), null, true, false).execute();

        // The migration is edited after it was applied.
        Files.writeString(
                tempDir.resolve("tasks/db/002_add_email.yaml"),
                """
                name: Add email
                target: db
                dependencies:
                  - db/001_create_users
                up: |
                  ALTER TABLE users ADD COLUMN email VARCHAR(255) DEFAULT 'none';
                down: |
                  ALTER TABLE users DROP COLUMN email;
                """);
        assertThat(
                        new StatusCommand(ExecutionContext.load(tempDir, pluginRegistry), true)
                                .execute())
                .isNotZero();
        outputStream.reset();

        int exitCode =
                new RebuildCommand(ExecutionContext.load(tempDir, pluginRegistry), true, false)
                        .execute();

        assertThat(exitCode).isZero();
        assertThat(outputStream.toString(StandardCharsets.UTF_8)).contains("db/002_add_email");
        // Nothing differs any more.
        assertThat(
                        new StatusCommand(ExecutionContext.load(tempDir, pluginRegistry), true)
                                .execute())
                .isZero();
    }

    @Test
    void thePlanNamesEveryMigrationThatComesDownNotOnlyTheOnesThatDiffer() throws Exception {
        writeProject();
        Files.writeString(
                tempDir.resolve("tasks/db/003_seed.yaml"),
                """
                name: Seed a user
                target: db
                dependencies:
                  - db/002_add_email
                up: |
                  INSERT INTO users (email) VALUES ('k@example.com');
                down: |
                  DELETE FROM users;
                """);
        new UpCommand(ExecutionContext.load(tempDir, pluginRegistry), null, true, false).execute();

        // Only 002 is edited, but 003 stands on it and so comes down with it.
        Files.writeString(
                tempDir.resolve("tasks/db/002_add_email.yaml"),
                """
                name: Add email
                target: db
                dependencies:
                  - db/001_create_users
                up: |
                  ALTER TABLE users ADD COLUMN email VARCHAR(255) DEFAULT 'none';
                down: |
                  ALTER TABLE users DROP COLUMN email;
                """);
        outputStream.reset();

        new RebuildCommand(ExecutionContext.load(tempDir, pluginRegistry), true, true).execute();

        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("db/002_add_email");
        // The count already says two; the listing has to name the second one.
        assertThat(output).contains("db/003_seed");
        assertThat(output).contains("Rolling back 2 migration(s)");
    }

    @Test
    void refusesBeforeTouchingTheDatabaseWhenTheGraphCouldNotBeAppliedAgain() throws Exception {
        writeProject();
        new UpCommand(ExecutionContext.load(tempDir, pluginRegistry), null, true, false).execute();

        Files.writeString(
                tempDir.resolve("tasks/db/002_add_email.yaml"),
                """
                name: Add email
                target: db
                dependencies:
                  - db/001_create_users
                up: |
                  ALTER TABLE users ADD COLUMN email VARCHAR(255) DEFAULT 'none';
                down: |
                  ALTER TABLE users DROP COLUMN email;
                """);
        Files.writeString(
                tempDir.resolve("tasks/db/003_add_notes.yaml"),
                """
                name: Add notes
                target: db
                dependencies:
                  - db/002_add_email
                up: |
                  ALTER TABLE users ADD COLUMN notes TEXT;
                """);

        ByteArrayOutputStream previewErrors = new ByteArrayOutputStream();
        ByteArrayOutputStream runErrors = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        int previewExitCode;
        int exitCode;
        try {
            System.setErr(new PrintStream(previewErrors));
            previewExitCode =
                    new RebuildCommand(ExecutionContext.load(tempDir, pluginRegistry), true, true)
                            .execute();
            System.setErr(new PrintStream(runErrors));
            exitCode =
                    new RebuildCommand(ExecutionContext.load(tempDir, pluginRegistry), true, false)
                            .execute();
        } finally {
            System.setErr(originalErr);
        }

        assertThat(previewExitCode).isNotZero();
        assertThat(exitCode).isNotZero();
        assertThat(previewErrors.toString(StandardCharsets.UTF_8)).contains("db/003_add_notes");
        assertThat(runErrors.toString(StandardCharsets.UTF_8)).contains("db/003_add_notes");
        assertThat(emailColumnExists()).isTrue();
    }

    @Test
    void refusesWhileTheHistoryHoldsARowForATargetTheProjectNoLongerConfigures() throws Exception {
        writeProject();
        Files.writeString(
                tempDir.resolve("targets/archive.yaml"),
                """
                type: postgresql
                jdbc_url: %s
                username: test
                password: test
                """
                        .formatted(postgres.getJdbcUrl()));
        Path archiveTasks = tempDir.resolve("tasks/archive");
        Files.createDirectories(archiveTasks);
        Files.writeString(
                archiveTasks.resolve("001_audit.yaml"),
                """
                name: Create audit
                target: archive
                up: |
                  CREATE TABLE audit (id SERIAL PRIMARY KEY);
                down: |
                  DROP TABLE audit;
                """);
        new UpCommand(ExecutionContext.load(tempDir, pluginRegistry), null, true, false).execute();

        // The target is removed from the configuration while its migration is still applied.
        Files.delete(archiveTasks.resolve("001_audit.yaml"));
        Files.delete(tempDir.resolve("targets/archive.yaml"));

        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        int exitCode;
        try {
            System.setErr(new PrintStream(errors));
            exitCode =
                    new RebuildCommand(ExecutionContext.load(tempDir, pluginRegistry), true, false)
                            .execute();
        } finally {
            System.setErr(originalErr);
        }

        assertThat(exitCode).isNotZero();
        assertThat(errors.toString(StandardCharsets.UTF_8))
                .contains("name a target this project no longer configures")
                .contains("archive/001_audit");
        assertThat(auditTableExists()).isTrue();
    }

    private boolean auditTableExists() throws SQLException {
        try (Connection connection =
                        DriverManager.getConnection(postgres.getJdbcUrl(), "test", "test");
                Statement statement = connection.createStatement();
                ResultSet rs =
                        statement.executeQuery(
                                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name ="
                                        + " 'audit'")) {
            rs.next();
            return rs.getInt(1) == 1;
        }
    }

    @Test
    void refusesWhenTheComparisonThrewRatherThanProceeding() throws Exception {
        writeProject();
        new UpCommand(ExecutionContext.load(tempDir, pluginRegistry), null, true, false).execute();

        // The plugin can no longer say what it applied: every node's token accessor throws.
        ExecutionContext loaded = ExecutionContext.load(tempDir, pluginRegistry);
        MigrationGraph throwing = MigrationGraph.create();
        for (MigrationNode node : loaded.graph().allNodes()) {
            throwing.addNode(new ThrowingFingerprint(node));
        }
        ExecutionContext context =
                new ExecutionContext(
                        loaded.baseDir(),
                        loaded.scanRoot(),
                        loaded.config(),
                        loaded.pluginRegistry(),
                        loaded.targets(),
                        loaded.nodes(),
                        throwing);

        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        int exitCode;
        try {
            System.setErr(new PrintStream(errors));
            exitCode = new RebuildCommand(context, true, false).execute();
        } finally {
            System.setErr(originalErr);
        }

        assertThat(exitCode).isNotZero();
        // Named as a plugin fault, not as the state amend repairs: folding the two together is
        // what loses the only thing that tells them apart.
        assertThat(errors.toString(StandardCharsets.UTF_8))
                // The marker status shows for it, so the listing reads as the thing status showed.
                .contains("[E] db/002_add_email")
                .contains("fault in the plugin")
                .doesNotContain("migraphe upgrade-history");
        assertThat(emailColumnExists()).isTrue();
    }

    @Test
    void namesBothHalvesOfTheRepairWhenARowTheDefinitionsLostCannotBeRead() throws Exception {
        writeProject();
        new UpCommand(ExecutionContext.load(tempDir, pluginRegistry), null, true, false).execute();

        // The state an upgrade leaves behind for a migration whose task file was then deleted: the
        // row records no fingerprint, and no definition names it any more.
        try (Connection connection =
                        DriverManager.getConnection(postgres.getJdbcUrl(), "test", "test");
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "UPDATE migraphe_history SET fingerprint = NULL"
                            + " WHERE node_id = 'db/002_add_email'");
        }
        Files.delete(tempDir.resolve("tasks/db/002_add_email.yaml"));

        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        int exitCode;
        try {
            System.setErr(new PrintStream(errors));
            exitCode =
                    new RebuildCommand(ExecutionContext.load(tempDir, pluginRegistry), true, false)
                            .execute();
        } finally {
            System.setErr(originalErr);
        }

        assertThat(exitCode).isNotZero();
        // upgrade fills a row from the definition that names it, so it never reaches
        // one no definition names. Sending the operator there alone is a loop with no exit.
        assertThat(errors.toString(StandardCharsets.UTF_8))
                .contains("[?] db/002_add_email")
                .contains("migraphe upgrade-history")
                .contains("amend <id>");
    }

    @Test
    void refusalNamesWhyEachMigrationCannotComeDownRatherThanBlamingThemAll() throws Exception {
        writeProject();
        // 003 is one-way and stands on 002, so rebuilding 002 is blocked by 003 — not by anything
        // wrong with 002, which declares a perfectly good rollback.
        Files.writeString(
                tempDir.resolve("tasks/db/003_seed.yaml"),
                """
                name: Seed a row
                target: db
                dependencies:
                  - db/002_add_email
                up: |
                  INSERT INTO users (email) VALUES ('k@example.com');
                no_way_back: the row cannot be reconstructed once removed
                """);
        new UpCommand(ExecutionContext.load(tempDir, pluginRegistry), null, true, false).execute();

        Files.writeString(
                tempDir.resolve("tasks/db/002_add_email.yaml"),
                """
                name: Add email
                target: db
                dependencies:
                  - db/001_create_users
                up: |
                  ALTER TABLE users ADD COLUMN email VARCHAR(320);
                down: |
                  ALTER TABLE users DROP COLUMN email;
                """);

        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        int exitCode;
        try {
            System.setErr(new PrintStream(errors));
            exitCode =
                    new RebuildCommand(ExecutionContext.load(tempDir, pluginRegistry), true, false)
                            .execute();
        } finally {
            System.setErr(originalErr);
        }

        assertThat(exitCode).isNotZero();
        assertThat(errors.toString(StandardCharsets.UTF_8))
                .contains("db/003_seed — no way back: the row cannot be reconstructed once removed")
                // 002 has a rollback. Telling its author to write one sends them to a correct file.
                .doesNotContain("Declare a rollback");
    }

    /** A node whose plugin cannot report what it applied. */
    private record ThrowingFingerprint(MigrationNode delegate) implements MigrationNode {

        @Override
        public NodeId id() {
            return delegate.id();
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public @Nullable String description() {
            return delegate.description();
        }

        @Override
        public Target target() {
            return delegate.target();
        }

        @Override
        public Set<NodeId> dependencies() {
            return delegate.dependencies();
        }

        @Override
        public @Nullable String noWayBack() {
            return delegate.noWayBack();
        }

        @Override
        public String fingerprint(Fingerprinter fingerprinter) {
            throw new IllegalStateException("the plugin cannot report what it applied");
        }

        @Override
        public Task upTask() {
            return delegate.upTask();
        }

        @Override
        public @Nullable Task downTask() {
            return delegate.downTask();
        }
    }

    private boolean emailColumnExists() throws SQLException {
        try (Connection connection =
                        DriverManager.getConnection(postgres.getJdbcUrl(), "test", "test");
                Statement statement = connection.createStatement();
                ResultSet rs =
                        statement.executeQuery(
                                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name ="
                                        + " 'users' AND column_name = 'email'")) {
            rs.next();
            return rs.getInt(1) == 1;
        }
    }

    @Test
    void runsWhenOnlyTheHistoryHasSomethingToTakeOut() throws Exception {
        writeProject();
        new UpCommand(ExecutionContext.load(tempDir, pluginRegistry), null, true, false).execute();

        // The task file is deleted: nothing differs any more, but the migration it applied is
        // still in the database and nothing declares it.
        Files.delete(tempDir.resolve("tasks/db/002_add_email.yaml"));
        outputStream.reset();

        int exitCode =
                new RebuildCommand(ExecutionContext.load(tempDir, pluginRegistry), true, false)
                        .execute();

        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(exitCode).isZero();
        assertThat(output).doesNotContain("Nothing to rebuild.");
        assertThat(output).contains("db/002_add_email");
        try (Connection connection =
                        DriverManager.getConnection(postgres.getJdbcUrl(), "test", "test");
                Statement statement = connection.createStatement();
                ResultSet rs =
                        statement.executeQuery(
                                "SELECT count(*) FROM information_schema.columns WHERE table_name"
                                        + " = 'users' AND column_name = 'email'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    private void writeProject() throws IOException {
        Files.writeString(
                tempDir.resolve("migraphe.yaml"),
                """
                project:
                  name: rebuild-test
                history:
                  target: db
                """);
        Path targets = tempDir.resolve("targets");
        Files.createDirectories(targets);
        Files.writeString(
                targets.resolve("db.yaml"),
                """
                type: postgresql
                jdbc_url: %s
                username: test
                password: test
                """
                        .formatted(postgres.getJdbcUrl()));
        Path tasks = tempDir.resolve("tasks/db");
        Files.createDirectories(tasks);
        Files.writeString(
                tasks.resolve("001_create_users.yaml"),
                """
                name: Create users
                target: db
                up: |
                  CREATE TABLE users (id SERIAL PRIMARY KEY);
                down: |
                  DROP TABLE users;
                """);
        Files.writeString(
                tasks.resolve("002_add_email.yaml"),
                """
                name: Add email
                target: db
                dependencies:
                  - db/001_create_users
                up: |
                  ALTER TABLE users ADD COLUMN email VARCHAR(255);
                down: |
                  ALTER TABLE users DROP COLUMN email;
                """);
    }
}
