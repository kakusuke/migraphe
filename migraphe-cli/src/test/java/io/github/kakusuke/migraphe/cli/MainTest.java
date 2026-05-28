package io.github.kakusuke.migraphe.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.cli.resolver.LockFileNotFoundException;
import io.github.kakusuke.migraphe.cli.resolver.PluginConfigParseResult;
import io.github.kakusuke.migraphe.cli.resolver.PluginResolutionException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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
    void shouldResolvePluginsDirToBaseDirWhenScanRootIsAbsent() {
        Path baseDir = Path.of("/tmp/proj");
        PluginConfigParseResult parsed =
                new PluginConfigParseResult(List.of(), List.of(), Optional.empty());

        Path result = Main.resolvePluginsDir(baseDir, parsed);

        assertThat(result).isEqualTo(Path.of("/tmp/proj/plugins"));
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
