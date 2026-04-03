package io.github.kakusuke.migraphe.cli.resolver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;

/** Maven Resolver を使用してプラグインの依存を解決する。 */
public final class MavenPluginResolver {

    private final Path localRepoPath;
    private final List<RemoteRepository> remoteRepositories;

    /** Maven Central を含むデフォルトコンストラクタ。 */
    public MavenPluginResolver() {
        this(defaultLocalRepo(), defaultRemoteRepositories());
    }

    /** テスト用コンストラクタ。 */
    public MavenPluginResolver(Path localRepoPath, List<RemoteRepository> remoteRepositories) {
        this.localRepoPath = localRepoPath;
        this.remoteRepositories = remoteRepositories;
    }

    /** 単一アーティファクトとその推移的依存を解決する。 */
    public List<Path> resolve(MavenArtifactCoordinate coordinate) {
        RepositorySystem system = newRepositorySystem();
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setSystemProperties(System.getProperties());
        LocalRepository localRepo = new LocalRepository(localRepoPath.toFile());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        var artifact =
                new DefaultArtifact(
                        coordinate.groupId(), coordinate.artifactId(), "jar", coordinate.version());
        var dependency = new Dependency(artifact, JavaScopes.RUNTIME);
        var collectRequest = new CollectRequest(dependency, remoteRepositories);
        var dependencyRequest = new DependencyRequest(collectRequest, null);

        try {
            DependencyResult result = system.resolveDependencies(session, dependencyRequest);
            List<Path> jars = new ArrayList<>();
            result.getArtifactResults().stream()
                    .filter(r -> r.isResolved())
                    .forEach(r -> jars.add(r.getArtifact().getFile().toPath()));
            return jars;
        } catch (DependencyResolutionException e) {
            throw new IllegalStateException(
                    "Failed to resolve plugin: "
                            + coordinate.groupId()
                            + ":"
                            + coordinate.artifactId()
                            + ":"
                            + coordinate.version(),
                    e);
        }
    }

    /** 複数アーティファクトを解決し、重複を除去する。 */
    public List<Path> resolveAll(List<MavenArtifactCoordinate> coordinates) {
        Set<Path> allJars = new LinkedHashSet<>();
        for (MavenArtifactCoordinate coord : coordinates) {
            allJars.addAll(resolve(coord));
        }
        return new ArrayList<>(allJars);
    }

    @SuppressWarnings("deprecation")
    private static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        return locator.getService(RepositorySystem.class);
    }

    private static Path defaultLocalRepo() {
        String m2Home = System.getProperty("maven.repo.local");
        if (m2Home != null) {
            return Path.of(m2Home);
        }
        return Path.of(System.getProperty("user.home"), ".m2", "repository");
    }

    private static List<RemoteRepository> defaultRemoteRepositories() {
        return List.of(
                new RemoteRepository.Builder(
                                "central", "default", "https://repo.maven.apache.org/maven2")
                        .build());
    }
}
