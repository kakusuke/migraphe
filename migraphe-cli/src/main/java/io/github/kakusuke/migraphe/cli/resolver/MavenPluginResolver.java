package io.github.kakusuke.migraphe.cli.resolver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
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

/**
 * Resolves plugin artifacts and their transitive dependencies using Maven Resolver (Aether).
 *
 * <p>Given the {@link PluginDeclaration}s from {@code migraphe.yaml}, this resolver downloads (or
 * locates in the local {@code ~/.m2} cache) each plugin JAR and its runtime-scoped dependency
 * closure, returning the JAR paths as {@link ResolvedArtifact}s. {@link PluginResolver} then turns
 * those paths into the plugin {@link java.net.URLClassLoader URLClassLoader}, and {@code migraphe
 * pin} uses {@link #resolveGroups(List)} to record SHA-256 pins per plugin. A declaration's {@link
 * PluginDeclaration#repositoryRef() repositoryRef} restricts resolution to a single named
 * repository; otherwise every repository in the {@link RepositoryRegistry} is queried.
 */
public final class MavenPluginResolver {

    private final Path localRepoPath;
    private final List<RemoteRepository> remoteRepositories;
    private final RepositoryRegistry registry;

    /**
     * Creates a resolver using the default local repository and a registry containing only Maven
     * Central.
     *
     * <p>The local repository defaults to the {@code maven.repo.local} system property if set,
     * otherwise {@code ~/.m2/repository}.
     */
    public MavenPluginResolver() {
        this(defaultLocalRepo(), RepositoryRegistry.defaults());
    }

    /**
     * Creates a resolver over an explicit local repository and repository registry.
     *
     * @param localRepoPath the local Maven repository directory used both as a cache and a
     *     resolution source
     * @param registry the repositories to query, in priority order
     */
    public MavenPluginResolver(Path localRepoPath, RepositoryRegistry registry) {
        this.localRepoPath = localRepoPath;
        this.registry = registry;
        this.remoteRepositories = toRemoteRepositories(registry.all());
    }

    /**
     * Resolves the given plugin declarations into a flat, de-duplicated list of artifacts.
     *
     * <p>Each declaration's {@link PluginDeclaration#repositoryRef() repositoryRef} narrows the
     * repositories queried for that plugin. The root artifact and all transitive dependencies are
     * flattened together, and artifacts that resolve to the same JAR path (shared across plugins)
     * are included only once.
     *
     * @param plugins the plugin declarations to resolve, in declaration order
     * @return the resolved artifacts (roots and transitive dependencies), de-duplicated by JAR path
     * @throws IllegalArgumentException if a declaration references an unknown repository id
     * @throws IllegalStateException if Maven resolution fails for any declaration
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

    /**
     * Resolves each plugin separately and groups its root artifact and transitive dependencies into
     * a {@link ResolvedPluginGroup}.
     *
     * <p>Unlike {@link #resolve(List)}, this keeps each plugin's dependency closure distinct, which
     * is what lets {@code migraphe pin} produce one lockfile entry per declared plugin while still
     * recording every transitive JAR.
     *
     * @param plugins the plugin declarations to resolve, in declaration order
     * @return one {@link ResolvedPluginGroup} per declaration, in declaration order
     * @throws IllegalArgumentException if a declaration references an unknown repository id
     * @throws IllegalStateException if Maven resolution fails, or if Maven does not return the
     *     requested root artifact for a declaration
     */
    public List<ResolvedPluginGroup> resolveGroups(List<PluginDeclaration> plugins) {
        List<ResolvedPluginGroup> groups = new ArrayList<>();
        for (PluginDeclaration plugin : plugins) {
            List<RemoteRepository> repos = repositoriesFor(plugin);
            List<ArtifactResolution> all = resolveOne(plugin.coordinate(), repos);
            ResolvedArtifact root = null;
            List<ResolvedArtifact> deps = new ArrayList<>();
            for (ArtifactResolution res : all) {
                ResolvedArtifact artifact = new ResolvedArtifact(res.coordinate, res.path);
                if (root == null && res.isRoot()) {
                    root = artifact;
                } else {
                    deps.add(artifact);
                }
            }
            if (root == null) {
                throw new IllegalStateException(
                        "Maven did not return the requested artifact for "
                                + plugin.coordinate().groupId()
                                + ":"
                                + plugin.coordinate().artifactId()
                                + ":"
                                + plugin.coordinate().version());
            }
            groups.add(new ResolvedPluginGroup(plugin, root, deps));
        }
        return groups;
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
            Artifact rootArtifact = result.getRoot().getArtifact();
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
                                                a.getFile().toPath(),
                                                a.equals(rootArtifact)));
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

    private record ArtifactResolution(
            MavenArtifactCoordinate coordinate, Path path, boolean isRoot) {}

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
