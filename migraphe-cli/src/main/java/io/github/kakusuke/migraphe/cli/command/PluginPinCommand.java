package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.cli.resolver.LockFile;
import io.github.kakusuke.migraphe.cli.resolver.LockFileBuilder;
import io.github.kakusuke.migraphe.cli.resolver.LockFileReader;
import io.github.kakusuke.migraphe.cli.resolver.LockFileWriter;
import io.github.kakusuke.migraphe.cli.resolver.MavenPluginResolver;
import io.github.kakusuke.migraphe.cli.resolver.PluginConfigParseResult;
import io.github.kakusuke.migraphe.cli.resolver.PluginConfigPreParser;
import io.github.kakusuke.migraphe.cli.resolver.ResolvedPluginGroup;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Resolves plugins declared in {@code migraphe.yaml} and writes their SHA-256 pins to {@code
 * migraphe.lock.yaml}. With {@code checkOnly}, the existing lockfile is verified against the
 * current configuration without writing.
 */
public final class PluginPinCommand implements Command {

    private final Path baseDir;
    private final MavenPluginResolver resolver;
    private final boolean checkOnly;

    /**
     * Creates the pin command.
     *
     * @param baseDir the project base directory containing {@code migraphe.yaml} and where {@code
     *     migraphe.lock.yaml} is read from or written to
     * @param resolver the Maven plugin resolver used to resolve declared plugins to concrete
     *     artifacts and their checksums
     * @param checkOnly {@code true} to verify the existing lockfile against the current
     *     configuration without writing; {@code false} to (re)generate the lockfile
     */
    public PluginPinCommand(Path baseDir, MavenPluginResolver resolver, boolean checkOnly) {
        this.baseDir = baseDir;
        this.resolver = resolver;
        this.checkOnly = checkOnly;
    }

    @Override
    public int execute() {
        PluginConfigParseResult parsed =
                new PluginConfigPreParser().parse(baseDir.resolve("migraphe.yaml"));
        List<ResolvedPluginGroup> groups = resolver.resolveGroups(parsed.plugins());
        LockFile expected = new LockFileBuilder().build(groups);
        Path lockPath = baseDir.resolve("migraphe.lock.yaml");
        if (checkOnly) {
            return runCheck(expected, lockPath);
        }
        try {
            new LockFileWriter().write(lockPath, expected);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return 0;
    }

    private static int runCheck(LockFile expected, Path lockPath) {
        Optional<LockFile> existing;
        try {
            existing = new LockFileReader().read(lockPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (existing.isEmpty()) {
            System.err.println(
                    "migraphe.lock.yaml is missing at "
                            + lockPath
                            + ". Run 'migraphe pin' to generate it.");
            return 1;
        }
        if (!existing.get().equals(expected)) {
            System.err.println(
                    "migraphe.lock.yaml is out of date. Run 'migraphe pin' to update it.");
            return 1;
        }
        return 0;
    }
}
