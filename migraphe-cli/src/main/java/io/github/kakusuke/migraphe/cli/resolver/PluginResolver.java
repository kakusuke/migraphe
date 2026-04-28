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

/** migraphe.yaml の plugins / repositories セクションを読み取り、URLClassLoader を構築する。 */
public final class PluginResolver {

    private final PluginConfigPreParser preParser;
    private final LockFileReader lockFileReader;
    private final LockSyncChecker lockSyncChecker;

    public PluginResolver() {
        this.preParser = new PluginConfigPreParser();
        this.lockFileReader = new LockFileReader();
        this.lockSyncChecker = new LockSyncChecker();
    }

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
