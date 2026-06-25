package io.github.kakusuke.migraphe.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.cli.resolver.LockFileNotFoundException;
import io.github.kakusuke.migraphe.cli.resolver.PluginConfigParseResult;
import io.github.kakusuke.migraphe.cli.resolver.PluginResolutionException;
import io.github.kakusuke.migraphe.core.execution.ExecutionContext;
import io.github.kakusuke.migraphe.core.plugin.PluginRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.err;
        System.setErr(new PrintStream(baos));
        try {
            int code = Main.handleException(new IllegalArgumentException("bad config"));
            assertThat(code).isEqualTo(1);
            String stderr = baos.toString(StandardCharsets.UTF_8);
            assertThat(stderr).contains("Error: bad config");
            assertThat(stderr).doesNotContain("\tat ");
        } finally {
            System.setErr(original);
        }
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

        Files.writeString(
                tempDir.resolve("migraphe.yaml"),
                """
                project:
                  name: test
                history:
                  target: noop-db
                """);
        Path targetsDir = Files.createDirectories(tempDir.resolve("targets"));
        Files.writeString(targetsDir.resolve("noop-db.yaml"), "type: noop\n");
        Files.createDirectories(tempDir.resolve("environments"));

        String[] argsWithEnv = {"up", "--env", "staging"};
        String[] argsWithoutEnv = {"up"};

        ExecutionContext contextWithEnv = Main.loadContext(tempDir, pluginRegistry, argsWithEnv);
        ExecutionContext contextWithoutEnv =
                Main.loadContext(tempDir, pluginRegistry, argsWithoutEnv);
        ExecutionContext directLoad = ExecutionContext.load(tempDir, pluginRegistry, "staging");

        assertThat(contextWithEnv.environments()).containsKey("noop-db");
        assertThat(contextWithoutEnv.environments()).containsKey("noop-db");
        assertThat(contextWithEnv.environments().keySet())
                .isEqualTo(directLoad.environments().keySet());
    }

    @Test
    void firstPositionalArgShouldSkipFlagPairsWhenExtracting() {
        assertThat(Main.firstPositionalArg(new String[] {"up", "--env", "production", "db1/001"}))
                .isEqualTo("db1/001");
        assertThat(Main.firstPositionalArg(new String[] {"up", "--env", "production"})).isNull();
    }

    @Test
    void usageOutputMentionsPinCommand() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(baos));
        try {
            Main.run(new String[0]);
            String stdout = baos.toString(StandardCharsets.UTF_8);
            assertThat(stdout).contains("pin");
            assertThat(stdout).contains("--check");
        } finally {
            System.setOut(original);
        }
    }
}
