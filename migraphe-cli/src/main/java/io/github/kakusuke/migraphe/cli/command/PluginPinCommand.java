package io.github.kakusuke.migraphe.cli.command;

import io.github.kakusuke.migraphe.cli.resolver.LockFile;
import io.github.kakusuke.migraphe.cli.resolver.LockFileBuilder;
import io.github.kakusuke.migraphe.cli.resolver.LockFileWriter;
import io.github.kakusuke.migraphe.cli.resolver.MavenPluginResolver;
import io.github.kakusuke.migraphe.cli.resolver.PluginConfigParseResult;
import io.github.kakusuke.migraphe.cli.resolver.PluginConfigPreParser;
import io.github.kakusuke.migraphe.cli.resolver.ResolvedPluginGroup;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Resolves plugins declared in {@code migraphe.yaml} and writes their SHA-256 pins to {@code
 * migraphe.lock.yaml}.
 */
public final class PluginPinCommand implements Command {

    private final Path baseDir;
    private final MavenPluginResolver resolver;

    public PluginPinCommand(Path baseDir, MavenPluginResolver resolver) {
        this.baseDir = baseDir;
        this.resolver = resolver;
    }

    @Override
    public int execute() {
        PluginConfigParseResult parsed =
                new PluginConfigPreParser().parse(baseDir.resolve("migraphe.yaml"));
        List<ResolvedPluginGroup> groups = resolver.resolveGroups(parsed.plugins());
        LockFile lock = new LockFileBuilder().build(groups);
        try {
            new LockFileWriter().write(baseDir.resolve("migraphe.lock.yaml"), lock);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return 0;
    }
}
