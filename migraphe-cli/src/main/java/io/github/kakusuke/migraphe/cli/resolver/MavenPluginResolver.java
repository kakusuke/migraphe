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
    private final RepositoryRegistry registry;

    /** Maven Central を含むデフォルトコンストラクタ。 */
    public MavenPluginResolver() {
        this(defaultLocalRepo(), RepositoryRegistry.defaults());
    }

    /** RepositoryRegistry ベースのコンストラクタ。 */
    public MavenPluginResolver(Path localRepoPath, RepositoryRegistry registry) {
        this.localRepoPath = localRepoPath;
        this.registry = registry;
        this.remoteRepositories = toRemoteRepositories(registry.all());
    }

    /**
     * PluginDeclaration のリストを解決し、ResolvedArtifact のリストを返す。 各宣言の repositoryRef
     * を考慮して問い合わせ先のリポジトリを絞り込む。
     */
    public List<ResolvedArtifact> resolve(List<PluginDeclaration> plugins) {
        Set<Path> seenJars = new LinkedHashSet<>();
        List<ResolvedArtifact> resolved = new ArrayList<>();
        for (PluginDeclaration plugin : plugins) {
            List<RemoteRepository> repos = repositoriesFor(plugin);
            for (ArtifactResolution res : resolveOne(plugin.coordinate(), repos)) {
                if (seenJars.add(res.path)) {
                    resolved.add(new ResolvedArtifact(res.coordinate, res.path));
                }
            }
        }
        return resolved;
    }

    private List<RemoteRepository> repositoriesFor(PluginDeclaration plugin) {
        if (plugin.repositoryRef().isEmpty()) {
            return remoteRepositories;
        }
        String ref = plugin.repositoryRef().get();
        RepositoryConfig repo =
                registry.resolve(ref)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Plugin '"
                                                        + plugin.coordinate().groupId()
                                                        + ":"
                                                        + plugin.coordinate().artifactId()
                                                        + "' references unknown repository '"
                                                        + ref
                                                        + "'"));
        return List.of(toRemoteRepository(repo));
    }

    private List<ArtifactResolution> resolveOne(
            MavenArtifactCoordinate coordinate, List<RemoteRepository> repositories) {
        RepositorySystem system = newRepositorySystem();
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setSystemProperties(System.getProperties());
        LocalRepository localRepo = new LocalRepository(localRepoPath.toFile());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        var artifact =
                new DefaultArtifact(
                        coordinate.groupId(), coordinate.artifactId(), "jar", coordinate.version());
        var dependency = new Dependency(artifact, JavaScopes.RUNTIME);
        var collectRequest = new CollectRequest(dependency, repositories);
        var dependencyRequest = new DependencyRequest(collectRequest, null);

        try {
            DependencyResult result = system.resolveDependencies(session, dependencyRequest);
            List<ArtifactResolution> out = new ArrayList<>();
            result.getArtifactResults().stream()
                    .filter(r -> r.isResolved())
                    .forEach(
                            r -> {
                                var a = r.getArtifact();
                                out.add(
                                        new ArtifactResolution(
                                                new MavenArtifactCoordinate(
                                                        a.getGroupId(),
                                                        a.getArtifactId(),
                                                        a.getVersion()),
                                                a.getFile().toPath()));
                            });
            return out;
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

    private record ArtifactResolution(MavenArtifactCoordinate coordinate, Path path) {}

    private static List<RemoteRepository> toRemoteRepositories(List<RepositoryConfig> configs) {
        List<RemoteRepository> out = new ArrayList<>();
        for (RepositoryConfig c : configs) {
            out.add(toRemoteRepository(c));
        }
        return out;
    }

    private static RemoteRepository toRemoteRepository(RepositoryConfig config) {
        return new RemoteRepository.Builder(config.id(), "default", config.url()).build();
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
}
