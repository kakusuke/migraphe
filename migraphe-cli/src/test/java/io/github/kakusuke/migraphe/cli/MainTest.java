package io.github.kakusuke.migraphe.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.cli.command.Command;
import io.github.kakusuke.migraphe.cli.resolver.LockFileNotFoundException;
import io.github.kakusuke.migraphe.cli.resolver.PluginConfigParseResult;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    void shouldResolvePluginsDirRelativeToScanRoot() {
        Path baseDir = Path.of("/tmp/proj");
        PluginConfigParseResult parsed =
                new PluginConfigParseResult(List.of(), List.of(), Optional.of("subdir"));

        Path result = Main.resolvePluginsDir(baseDir, parsed);

        assertThat(result).isEqualTo(Path.of("/tmp/proj/subdir/plugins"));
    }

    @Test
    void parseEnvOptionShouldReturnEnvNameOrNull() {
        assertThat(Main.parseEnvOption(new String[] {"up", "--env", "staging"}))
                .isEqualTo("staging");
        assertThat(Main.parseEnvOption(new String[] {"up", "--dry-run"})).isNull();
    }

    @Test
    void shouldResolvePluginsDirToBaseDirWhenScanRootIsAbsent() {
        Path baseDir = Path.of("/tmp/proj");
        PluginConfigParseResult parsed =
                new PluginConfigParseResult(List.of(), List.of(), Optional.empty());

        Path result = Main.resolvePluginsDir(baseDir, parsed);

        assertThat(result).isEqualTo(Path.of("/tmp/proj/plugins"));
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
        assertThat(contextWithEnv.environments()).containsKey("noop-db");
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
    void amendShouldRecordTheMissingFingerprint(@TempDir Path tempDir)
            throws IOException, SQLException {
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginRegistry.loadFromClasspath();

        String jdbcUrl = "jdbc:h2:mem:amend_cli;DB_CLOSE_DELAY=-1";
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

        // Erase the recorded fingerprint, which is what an upgrade from before the column looks
        // like.
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE migraphe_history SET fingerprint = NULL");
        }

        Command amendCommand =
                Objects.requireNonNull(
                        Main.createAmendCommand(new String[] {"amend", "-y"}, context));
        AtomicInteger exitCode = new AtomicInteger();
        String stdout = captureStdout(() -> exitCode.set(amendCommand.execute()));

        assertThat(exitCode.get()).isZero();
        assertThat(stdout).contains("Recorded 1");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT fingerprint FROM migraphe_history")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isNotNull();
        }
    }

    @Test
    void usageShouldListAmendAmongTheCommandsHonouringTheEnvOption() {
        String usage = captureStdout(() -> Main.run(new String[0]));

        assertThat(usage).contains("Common options (up, down, status, amend, validate, generate):");
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

        assertThat(downStderr).contains("Version argument or --all required");
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
