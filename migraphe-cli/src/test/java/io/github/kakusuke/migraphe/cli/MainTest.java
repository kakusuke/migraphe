package io.github.kakusuke.migraphe.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.cli.command.Command;
import io.github.kakusuke.migraphe.cli.command.StatusCommand;
import io.github.kakusuke.migraphe.cli.resolver.LockFileNotFoundException;
import io.github.kakusuke.migraphe.cli.resolver.PluginResolutionException;
import io.github.kakusuke.migraphe.core.config.ConfigurationException;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    @Test
    void shouldReturnExitCode1WhenNoArgsGiven() {
        int exitCode = Main.run(new String[0]);

        assertThat(exitCode).isEqualTo(1);
    }

    @Test
    void shouldNotPrintStackTraceForIllegalArgumentException() {
        assertThat(Main.shouldPrintStackTrace(new IllegalArgumentException("bad config")))
                .isFalse();
    }

    @Test
    void shouldPrintStackTraceForRuntimeException() {
        assertThat(Main.shouldPrintStackTrace(new RuntimeException("unexpected"))).isTrue();
    }

    @Test
    void handleExceptionShouldSuppressStackTraceForIllegalArgumentException() {
        AtomicInteger exitCode = new AtomicInteger();
        String stderr =
                captureStderr(
                        () ->
                                exitCode.set(
                                        Main.handleException(
                                                new IllegalArgumentException("bad config"))));

        assertThat(exitCode.get()).isEqualTo(1);
        assertThat(stderr).contains("Error: bad config");
        assertThat(stderr).doesNotContain("\tat ");
    }

    @Test
    void shouldNotPrintStackTraceForPluginResolutionException() {
        assertThat(Main.shouldPrintStackTrace(new PluginResolutionException("missing lock")))
                .isFalse();
        assertThat(Main.shouldPrintStackTrace(new LockFileNotFoundException("missing lock")))
                .isFalse();
    }

    @Test
    void parseEnvOptionShouldReturnEnvNameOrNull() {
        assertThat(Main.parseEnvOption(new String[] {"up", "--env", "staging"}))
                .isEqualTo("staging");
        assertThat(Main.parseEnvOption(new String[] {"up", "--dry-run"})).isNull();
    }

    @Test
    void loadContextShouldPassEnvOptionToExecutionContextLoad(@TempDir Path tempDir)
            throws IOException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        writeNoopProject(tempDir);
        Path environmentsDir = Files.createDirectories(tempDir.resolve("environments"));
        Files.writeString(environmentsDir.resolve("staging.yaml"), "DB_HOST: staging-host\n");

        String[] argsWithEnv = {"up", "--env", "staging"};
        String[] argsWithoutEnv = {"up"};

        ExecutionContext contextWithEnv = Main.loadContext(tempDir, pluginRegistry, argsWithEnv);
        ExecutionContext contextWithoutEnv =
                Main.loadContext(tempDir, pluginRegistry, argsWithoutEnv);

        // --env が渡っていればオーバーレイの値が読める
        assertThat(contextWithEnv.config().getValue("DB_HOST", String.class))
                .isEqualTo("staging-host");
        assertThat(contextWithoutEnv.config().getOptionalValue("DB_HOST", String.class)).isEmpty();
        assertThat(contextWithEnv.targets()).containsKey("noop-db");
    }

    @Test
    void loadContextShouldFailWhenNamedEnvironmentOverlayIsMissing(@TempDir Path tempDir)
            throws IOException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        writeNoopProject(tempDir);

        String[] args = {"up", "--env", "staging"};

        // 打ち間違いを黙殺せず停止する
        assertThatThrownBy(() -> Main.loadContext(tempDir, pluginRegistry, args))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("staging");
    }

    @Test
    void firstPositionalArgShouldSkipFlagPairsWhenExtracting() {
        assertThat(Main.firstPositionalArg(new String[] {"up", "--env", "production", "db1/001"}))
                .isEqualTo("db1/001");
        assertThat(Main.firstPositionalArg(new String[] {"up", "--env", "production"})).isNull();
    }

    @Test
    void previewFlagShouldBeAcceptedAsDryRunAlias() {
        assertThat(Main.parseDryRun(new String[] {"up", "--preview"})).isTrue();
        assertThat(Main.parseDryRun(new String[] {"up", "--dry-run"})).isTrue();
        assertThat(Main.parseDryRun(new String[] {"up"})).isFalse();
        assertThat(Main.firstPositionalArg(new String[] {"up", "--preview"})).isNull();
        assertThat(Main.firstPositionalArg(new String[] {"down", "--preview", "db1/001"}))
                .isEqualTo("db1/001");
    }

    @Test
    void createUpCommandShouldRunPreviewWithoutExecuting(@TempDir Path tempDir) throws IOException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        writeNoopProject(tempDir);
        Path tasksDir = Files.createDirectories(tempDir.resolve("tasks"));
        Files.writeString(
                tasksDir.resolve("001_create_users.yaml"),
                """
                name: Create users
                target: noop-db
                up: Create the users table
                down: Drop the users table
                """);

        String[] args = {"up", "--preview", "-y"};
        ExecutionContext context = Main.loadContext(tempDir, pluginRegistry, args);

        String stdout = captureStdout(() -> Main.createUpCommand(args, context).execute());

        assertThat(stdout).contains("[DRY RUN]");
    }

    @Test
    void createDownCommandShouldRunPreviewWithoutExecuting(@TempDir Path tempDir)
            throws IOException, SQLException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        String jdbcUrl = "jdbc:h2:mem:preview_down;DB_CLOSE_DELAY=-1";
        Files.writeString(
                tempDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test
                history:
                  target: h2-db
                """);
        Path targetsDir = Files.createDirectories(tempDir.resolve("targets"));
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
        Path tasksDir = Files.createDirectories(tempDir.resolve("tasks"));
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

        ExecutionContext context = Main.loadContext(tempDir, pluginRegistry, new String[] {"up"});
        captureStdout(() -> Main.createUpCommand(new String[] {"up", "-y"}, context).execute());

        String[] downArgs = {"down", "--all", "--preview", "-y"};
        Command downCommand = Objects.requireNonNull(Main.createDownCommand(downArgs, context));

        String stdout = captureStdout(downCommand::execute);

        assertThat(stdout).contains("[DRY RUN]");
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                ResultSet tables = connection.getMetaData().getTables(null, null, "USERS", null)) {
            assertThat(tables.next()).isTrue();
        }
    }

    @Test
    void usageShouldListEveryCommandHonouringTheEnvOption() {
        String usage = captureStdout(() -> Main.run(new String[0]));

        assertThat(usage)
                .contains(
                        "Common options (init, up, down, status, amend, rebuild,"
                                + " upgrade-history, validate, generate):");
        // Each of those has to be listed as a command too, or the option line points at nothing.
        assertThat(usage)
                .contains("  amend ")
                .contains("  rebuild ")
                .contains("  upgrade-history ");
    }

    @Test
    void aMissingDependencyStopsApplyingButNotDiagnosing(@TempDir Path tempDir) throws IOException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        writeReversiblePair(tempDir, "jdbc:h2:mem:dangling;DB_CLOSE_DELAY=-1");
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        captureStdout(() -> Main.createUpCommand(new String[] {"up", "-y"}, context).execute());

        // 002_b が依存している 001_a のファイルを消す
        Files.delete(tempDir.resolve("tasks").resolve("001_a.yaml"));
        ExecutionContext reloaded = ExecutionContext.load(tempDir, pluginRegistry);

        AtomicInteger statusExit = new AtomicInteger();
        String statusOut =
                captureStdout(() -> statusExit.set(new StatusCommand(reloaded).execute()));

        assertThat(statusExit.get()).isZero();
        assertThat(statusOut).contains("001_a");

        Command up = Main.createUpCommand(new String[] {"up", "-y"}, reloaded);
        AtomicInteger upExit = new AtomicInteger();
        String upErr = captureStderr(() -> captureStdout(() -> upExit.set(up.execute())));

        assertThat(upExit.get()).isEqualTo(1);
        assertThat(upErr).contains("001_a");
    }

    @Test
    void previewShouldReportTheSameOutcomeAsRunningWould(@TempDir Path tempDir) throws IOException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        writeIrreversibleProject(tempDir, "down_preview", "the rows cannot be reconstructed");
        // 003_d は誰にも依存せず巻き戻せるので、凍結は一部にとどまる
        Files.writeString(
                tempDir.resolve("tasks").resolve("003_d.yaml"),
                """
                name: Create d
                target: h2-db
                autocommit: true
                up: |
                  CREATE TABLE t_d (id INT PRIMARY KEY);
                down: |
                  DROP TABLE IF EXISTS t_d;
                """);
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        captureStdout(() -> Main.createUpCommand(new String[] {"up", "-y"}, context).execute());

        String[] args = {"down", "-y", "--all", "--preview"};
        Command preview = Objects.requireNonNull(Main.createDownCommand(args, context));
        AtomicInteger exitCode = new AtomicInteger();
        captureStderr(() -> captureStdout(() -> exitCode.set(preview.execute())));

        assertThat(exitCode.get()).isEqualTo(1);
    }

    @Test
    void downShouldRollBackAMigrationTheDefinitionsNoLongerDeclare(@TempDir Path tempDir)
            throws IOException, SQLException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        String jdbcUrl = "jdbc:h2:mem:down_orphan;DB_CLOSE_DELAY=-1";
        writeReversiblePair(tempDir, jdbcUrl);

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        captureStdout(() -> Main.createUpCommand(new String[] {"up", "-y"}, context).execute());

        Files.delete(tempDir.resolve("tasks").resolve("002_b.yaml"));
        ExecutionContext reloaded = ExecutionContext.load(tempDir, pluginRegistry);

        String[] args = {"down", "-y", "--all"};
        Command down = Objects.requireNonNull(Main.createDownCommand(args, reloaded));
        AtomicInteger exitCode = new AtomicInteger();
        String stderr = captureStderr(() -> captureStdout(() -> exitCode.set(down.execute())));

        assertThat(exitCode.get()).isZero();
        assertThat(stderr).isEmpty();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet rs =
                        statement.executeQuery(
                                "SELECT node_id FROM migraphe_history WHERE direction = 'DOWN'"
                                        + " AND status = 'SUCCESS' ORDER BY node_id")) {
            List<String> rolledBack = new ArrayList<>();
            while (rs.next()) {
                rolledBack.add(rs.getString(1));
            }
            // The migration whose task file is gone comes down like any other, and first, because
            // that is the order its own row recorded.
            assertThat(rolledBack).containsExactly("001_a", "002_b");
        }
    }

    @Test
    void statusShouldListWhatIsAppliedButNoLongerDefined(@TempDir Path tempDir) throws IOException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        writeIrreversibleProject(tempDir, "status_orphan", "the rows cannot be reconstructed");
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        captureStdout(() -> Main.createUpCommand(new String[] {"up", "-y"}, context).execute());

        Files.delete(tempDir.resolve("tasks").resolve("002_b.yaml"));
        ExecutionContext reloaded = ExecutionContext.load(tempDir, pluginRegistry);

        String stdout = captureStdout(() -> new StatusCommand(reloaded).execute());

        assertThat(stdout).contains("002_b");
    }

    @Test
    void upShouldRefuseATaskThatNeitherRollsBackNorSaysWhyNot(@TempDir Path tempDir)
            throws IOException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        writeIrreversibleProject(tempDir, "up_undeclared");

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        Command up = Main.createUpCommand(new String[] {"up", "-y"}, context);
        AtomicInteger exitCode = new AtomicInteger();
        String stderr = captureStderr(() -> captureStdout(() -> exitCode.set(up.execute())));

        assertThat(exitCode.get()).isEqualTo(1);
        assertThat(stderr).contains("002_b").contains("no_way_back");
    }

    @Test
    void downShouldRefuseANodeThatHasNoRollback(@TempDir Path tempDir) throws IOException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        writeIrreversibleProject(tempDir, "down_frozen", "the rows cannot be reconstructed");

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        captureStdout(() -> Main.createUpCommand(new String[] {"up", "-y"}, context).execute());

        String[] args = {"down", "-y", "002_b"};
        Command down = Objects.requireNonNull(Main.createDownCommand(args, context));
        AtomicInteger exitCode = new AtomicInteger();
        String stderr = captureStderr(() -> exitCode.set(down.execute()));

        assertThat(exitCode.get()).isEqualTo(1);
        assertThat(stderr).contains("002_b");
    }

    @Test
    void downAllShouldRefuseWithoutRollingAnythingBack(@TempDir Path tempDir) throws IOException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        writeIrreversibleProject(tempDir, "down_all_frozen", "the rows cannot be reconstructed");

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        captureStdout(() -> Main.createUpCommand(new String[] {"up", "-y"}, context).execute());

        String[] args = {"down", "-y", "--all"};
        Command down = Objects.requireNonNull(Main.createDownCommand(args, context));
        AtomicInteger exitCode = new AtomicInteger();
        StringBuilder stdout = new StringBuilder();
        String stderr =
                captureStderr(
                        () -> stdout.append(captureStdout(() -> exitCode.set(down.execute()))));

        assertThat(exitCode.get()).isEqualTo(1);
        assertThat(stderr).contains("--all means all").contains("002_b");
        assertThat(stdout.toString()).doesNotContain("Executing rollback...");
    }

    @Test
    void rebuildShouldRefuseAMigrationArgumentRatherThanIgnoringIt(@TempDir Path tempDir)
            throws IOException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        writeReversiblePair(tempDir, "jdbc:h2:mem:rebuild_arg;DB_CLOSE_DELAY=-1");
        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);

        Command[] command = new Command[1];
        String stderr =
                captureStderr(
                        () ->
                                command[0] =
                                        Main.createRebuildCommand(
                                                new String[] {"rebuild", "001_a"}, context));

        assertThat(command[0]).isNull();
        assertThat(stderr).contains("rebuild takes no migration");
    }

    /** Writes a project of two reversible tasks, {@code 002_b} depending on {@code 001_a}. */
    private void writeReversiblePair(Path tempDir, String jdbcUrl) throws IOException {
        Files.writeString(
                tempDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test
                history:
                  target: h2-db
                """);
        Path targetsDir = Files.createDirectories(tempDir.resolve("targets"));
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
        Path tasksDir = Files.createDirectories(tempDir.resolve("tasks"));
        Files.writeString(
                tasksDir.resolve("001_a.yaml"),
                """
                name: Create a
                target: h2-db
                autocommit: true
                up: |
                  CREATE TABLE t_a (id INT PRIMARY KEY);
                down: |
                  DROP TABLE IF EXISTS t_a;
                """);
        Files.writeString(
                tasksDir.resolve("002_b.yaml"),
                """
                name: Create b
                target: h2-db
                autocommit: true
                dependencies:
                  - 001_a
                up: |
                  CREATE TABLE t_b (id INT PRIMARY KEY);
                down: |
                  DROP TABLE IF EXISTS t_b;
                """);
    }

    /**
     * Writes a project where {@code 002_b} depends on {@code 001_a} and has no {@code down:}, so
     * neither can be rolled back: 002_b has no way back, and 001_a is what it stands on.
     */
    private void writeIrreversibleProject(Path tempDir, String databaseName) throws IOException {
        writeIrreversibleProject(tempDir, databaseName, null);
    }

    private void writeIrreversibleProject(
            Path tempDir, String databaseName, @Nullable String noWayBack) throws IOException {
        Files.writeString(
                tempDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test
                history:
                  target: h2-db
                """);
        Path targetsDir = Files.createDirectories(tempDir.resolve("targets"));
        Files.writeString(
                targetsDir.resolve("h2-db.yaml"),
                """
                type: jdbc
                driver_class: org.h2.Driver
                db_label: H2
                jdbc_url: jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1
                username: sa
                """
                        .formatted(databaseName));
        Path tasksDir = Files.createDirectories(tempDir.resolve("tasks"));
        Files.writeString(
                tasksDir.resolve("001_a.yaml"),
                """
                name: Create a
                target: h2-db
                autocommit: true
                up: |
                  CREATE TABLE t_a (id INT PRIMARY KEY);
                down: |
                  DROP TABLE IF EXISTS t_a;
                """);
        Files.writeString(
                tasksDir.resolve("002_b.yaml"),
                """
                name: Create b
                target: h2-db
                autocommit: true
                dependencies:
                  - 001_a
                up: |
                  CREATE TABLE t_b (id INT PRIMARY KEY);
                """
                        + (noWayBack == null ? "" : "no_way_back: " + noWayBack + "\n"));
    }

    @Test
    void downShouldQuoteTheDeclaredReasonForANodeWithNoWayBack(@TempDir Path tempDir)
            throws IOException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        writeIrreversibleProject(tempDir, "down_declared", "DROP COLUMN discards the data");

        ExecutionContext context = ExecutionContext.load(tempDir, pluginRegistry);
        captureStdout(() -> Main.createUpCommand(new String[] {"up", "-y"}, context).execute());

        String[] args = {"down", "-y", "002_b"};
        Command down = Objects.requireNonNull(Main.createDownCommand(args, context));
        String stderr = captureStderr(down::execute);

        assertThat(stderr).contains("DROP COLUMN discards the data");
    }

    @Test
    void downUsageErrorShouldAdvertisePreviewFlag(@TempDir Path tempDir) throws IOException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        writeNoopProject(tempDir);

        String[] args = {"down"};
        ExecutionContext context = Main.loadContext(tempDir, pluginRegistry, args);

        AtomicReference<Command> created = new AtomicReference<>();
        String stderr = captureStderr(() -> created.set(Main.createDownCommand(args, context)));

        assertThat(created.get()).isNull();
        assertThat(stderr).contains("--preview");
        assertThat(stderr).doesNotContain("--dry-run");
    }

    @Test
    void amendShouldRequireAMigrationAndSendABulkRepairToUpgrade(@TempDir Path tempDir)
            throws IOException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        writeNoopProject(tempDir);

        String[] args = {"amend"};
        ExecutionContext context = Main.loadContext(tempDir, pluginRegistry, args);

        AtomicReference<Command> created = new AtomicReference<>();
        String stderr = captureStderr(() -> created.set(Main.createAmendCommand(args, context)));

        assertThat(created.get()).isNull();
        assertThat(stderr)
                .contains("Migration argument required")
                .contains("migraphe upgrade-history")
                .doesNotContain("--incomplete");
    }

    @Test
    void usageDescribesEachCommandsOwnOptionsAndSpellsAMigrationTheWayRefusalsDo() {
        String usage = captureStdout(() -> Main.run(new String[0]));

        // --check belongs to status. rebuild has none, and a line describing one under rebuild
        // reads as rebuild's.
        assertThat(usage).contains("Status options:");
        String rebuildBlock =
                usage.substring(usage.indexOf("  rebuild "), usage.indexOf("  amend "));
        assertThat(rebuildBlock).doesNotContain("--check");

        // Every refusal spells it <id>; the usage must not invent a second word for it.
        assertThat(usage).doesNotContain("<version>").doesNotContain("<v>");
        assertThat(usage).contains("down [-y] [--preview] [--all | <id>]");
    }

    @Test
    void upgradeHistoryIsACommandWordAndReachesTheCommand(@TempDir Path tempDir)
            throws IOException {
        writeNoopProject(tempDir);

        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        String stdout;
        String stderr;
        int exitCode;
        try {
            java.util.concurrent.atomic.AtomicInteger code =
                    new java.util.concurrent.atomic.AtomicInteger();
            StringBuilder err = new StringBuilder();
            stdout =
                    captureStdout(
                            () ->
                                    err.append(
                                            captureStderr(
                                                    () ->
                                                            code.set(
                                                                    Main.run(
                                                                            new String[] {
                                                                                "upgrade-history"
                                                                            })))));
            stderr = err.toString();
            exitCode = code.get();
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }

        assertThat(stderr).doesNotContain("Unknown command");
        // The noop backend declares no upgrades, so there is nothing to do and it says so.
        assertThat(stdout).contains("The history is already up to date.");
        assertThat(exitCode).isZero();
    }

    @Test
    void unknownCommandShouldBeReportedOnlyForUnrecognisedCommandWord(@TempDir Path tempDir)
            throws IOException {
        writeNoopProject(tempDir);

        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        String downStderr;
        String bogusStderr;
        try {
            downStderr = captureStderr(() -> captureStdout(() -> Main.run(new String[] {"down"})));
            bogusStderr =
                    captureStderr(() -> captureStdout(() -> Main.run(new String[] {"bogus"})));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }

        assertThat(downStderr).contains("Migration argument or --all required");
        assertThat(downStderr).doesNotContain("Unknown command");
        assertThat(bogusStderr).contains("Unknown command: bogus");
    }

    @Test
    void downWithoutVersionOrAllExitsWithOne(@TempDir Path tempDir) throws IOException {
        writeNoopProject(tempDir);

        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        AtomicInteger exitCode = new AtomicInteger();
        String stderr;
        try {
            stderr =
                    captureStderr(
                            () ->
                                    captureStdout(
                                            () -> exitCode.set(Main.run(new String[] {"down"}))));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }

        assertThat(exitCode.get()).isEqualTo(1);
        assertThat(stderr).isNotEmpty();
    }

    @Test
    void fullHelpShouldBePrintedOnlyForUnrecognisedCommandWord(@TempDir Path tempDir)
            throws IOException {
        writeNoopProject(tempDir);

        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        String downStdout;
        String bogusStdout;
        try {
            downStdout = captureStdout(() -> captureStderr(() -> Main.run(new String[] {"down"})));
            bogusStdout =
                    captureStdout(() -> captureStderr(() -> Main.run(new String[] {"bogus"})));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }

        assertThat(downStdout).doesNotContain("Migraphe - Database Migration Tool");
        assertThat(bogusStdout).contains("Migraphe - Database Migration Tool");
    }

    @Test
    void usageTextShouldAdvertisePreviewFlag() {
        String stdout = captureStdout(() -> Main.run(new String[0]));

        assertThat(stdout).contains("--preview");
        assertThat(stdout).doesNotContain("--dry-run");
    }

    @Test
    void usageOutputMentionsPinCommand() {
        String stdout = captureStdout(() -> Main.run(new String[0]));

        assertThat(stdout).contains("pin");
        assertThat(stdout).contains("--check");
    }

    /**
     * Writes the smallest project the CLI will load: one noop target, used as the history store.
     */
    private static void writeNoopProject(Path baseDir) throws IOException {
        Files.writeString(
                baseDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test
                history:
                  target: noop-db
                """);
        Path targetsDir = Files.createDirectories(baseDir.resolve("targets"));
        Files.writeString(targetsDir.resolve("noop-db.yaml"), "type: noop\n");
    }

    /** Runs {@code action} with {@code System.err} redirected and returns what it printed. */
    private static String captureStderr(Runnable action) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.err;
        System.setErr(new PrintStream(baos));
        try {
            action.run();
        } finally {
            System.setErr(original);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    /** Runs {@code action} with {@code System.out} redirected and returns what it printed. */
    private static String captureStdout(Runnable action) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(baos));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
