package io.github.kakusuke.migraphe.cli.resolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Reads the {@code plugins:} and {@code repositories:} sections of {@code migraphe.yaml} and builds
 * the plugin {@link URLClassLoader}.
 *
 * <p>This is the top-level entry point for the CLI's plugin bootstrap and orchestrates the full
 * chain: pre-parse the config ({@link PluginConfigPreParser}), require and read {@code
 * migraphe.lock.yaml} ({@code LockFileReader}), confirm the lockfile is in sync with the config
 * ({@code LockSyncChecker}), resolve all artifacts from {@code ~/.m2} plus the configured
 * repositories ({@link MavenPluginResolver}), verify each JAR against its SHA-256 pin ({@link
 * PluginIntegrityVerifier}), and finally expose the resolved JARs through a {@link URLClassLoader}.
 * Maven Central is always prepended to the configured repositories.
 */
public final class PluginResolver {

    private final PluginConfigPreParser preParser;
    private final LockFileReader lockFileReader;
    private final LockSyncChecker lockSyncChecker;
    private final PluginIntegrityVerifier integrityVerifier;

    /** Creates a resolver wired with the default pre-parser, lockfile reader, and verifiers. */
    public PluginResolver() {
        this.preParser = new PluginConfigPreParser();
        this.lockFileReader = new LockFileReader();
        this.lockSyncChecker = new LockSyncChecker();
        this.integrityVerifier = new PluginIntegrityVerifier();
    }

    /**
     * Resolves the plugins declared in {@code <baseDir>/migraphe.yaml} and returns a class loader
     * over their JARs.
     *
     * @param baseDir the project base directory containing {@code migraphe.yaml} and {@code
     *     migraphe.lock.yaml}
     * @return a {@link URLClassLoader} (parented to the current thread's context class loader)
     *     exposing the resolved plugin JARs, or {@code null} if no plugins are declared
     * @throws LockFileNotFoundException if plugins are declared but {@code migraphe.lock.yaml} is
     *     missing
     * @throws MissingChecksumPinException if a resolved artifact has no SHA-256 pin in the lockfile
     * @throws ChecksumMismatchException if a resolved JAR's hash does not match its pin
     * @throws java.io.UncheckedIOException if the lockfile cannot be read
     */
    public @Nullable URLClassLoader resolve(Path baseDir) {
        PluginConfigParseResult parsed = preParser.parse(baseDir.resolve("migraphe.yaml"));
        if (parsed.plugins().isEmpty()) {
            return null;
        }
        Path lockPath = baseDir.resolve("migraphe.lock.yaml");
        Optional<LockFile> lock;
        try {
            lock = lockFileReader.read(lockPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (lock.isEmpty()) {
            throw new LockFileNotFoundException(
                    "migraphe.lock.yaml not found at "
                            + lockPath
                            + ". Run 'migraphe pin' to generate it.");
        }
        lockSyncChecker.check(parsed, lock.get());
        RepositoryRegistry registry =
                RepositoryRegistry.of(withDefaultsPrepended(parsed.repositories()));
        MavenPluginResolver resolver = new MavenPluginResolver(defaultLocalRepo(), registry);
        List<ResolvedArtifact> artifacts = resolver.resolve(parsed.plugins());
        integrityVerifier.verify(artifacts, lock.get());
        URL[] urls =
                artifacts.stream()
                        .map(
                                a -> {
                                    try {
                                        return a.jarPath().toUri().toURL();
                                    } catch (MalformedURLException e) {
                                        throw new IllegalStateException(e);
                                    }
                                })
                        .toArray(URL[]::new);
        return new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
    }

    private static List<RepositoryConfig> withDefaultsPrepended(List<RepositoryConfig> extras) {
        List<RepositoryConfig> all = new ArrayList<>();
        all.add(RepositoryConfig.mavenCentral());
        for (RepositoryConfig r : extras) {
            if (!"maven-central".equals(r.id())) {
                all.add(r);
            }
        }
        return all;
    }

    private static Path defaultLocalRepo() {
        String m2Home = System.getProperty("maven.repo.local");
        if (m2Home != null) {
            return Path.of(m2Home);
        }
        return Path.of(System.getProperty("user.home"), ".m2", "repository");
    }
}
