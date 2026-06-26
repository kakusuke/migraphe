package io.github.kakusuke.migraphe.cli.resolver;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An ordered, id-unique collection of the repositories available for plugin resolution.
 *
 * <p>{@link PluginResolver} builds a registry from the {@code repositories:} section of {@code
 * migraphe.yaml} (with Maven Central prepended) and hands it to {@link MavenPluginResolver}, which
 * queries {@link #all()} for unrestricted plugins and uses {@link #resolve(String)} to look up the
 * single repository named by a {@link PluginDeclaration#repositoryRef()}. The registry is immutable
 * and rejects duplicate repository ids at construction time.
 */
public final class RepositoryRegistry {

    private final List<RepositoryConfig> repositories;

    private RepositoryRegistry(List<RepositoryConfig> repositories) {
        this.repositories = List.copyOf(repositories);
    }

    /**
     * Returns a registry containing only Maven Central.
     *
     * @return a registry whose sole entry is {@link RepositoryConfig#mavenCentral()}
     */
    public static RepositoryRegistry defaults() {
        return new RepositoryRegistry(List.of(RepositoryConfig.mavenCentral()));
    }

    /**
     * Returns a registry over the given repositories, preserving order.
     *
     * @param repositories the repositories to register, in query priority order
     * @return a registry containing exactly the given repositories
     * @throws IllegalArgumentException if two repositories share the same {@code id}
     */
    public static RepositoryRegistry of(List<RepositoryConfig> repositories) {
        Set<String> seen = new HashSet<>();
        for (RepositoryConfig repo : repositories) {
            if (!seen.add(repo.id())) {
                throw new IllegalArgumentException("Duplicate repository id: '" + repo.id() + "'");
            }
        }
        return new RepositoryRegistry(repositories);
    }

    /**
     * Returns all registered repositories in registration order.
     *
     * @return an immutable list of the registered repositories
     */
    public List<RepositoryConfig> all() {
        return repositories;
    }

    /**
     * Looks up a registered repository by its identifier.
     *
     * @param id the repository {@code id} to find
     * @return the matching repository, or {@link Optional#empty()} if no repository has that id
     */
    public Optional<RepositoryConfig> resolve(String id) {
        for (RepositoryConfig repo : repositories) {
            if (repo.id().equals(id)) {
                return Optional.of(repo);
            }
        }
        return Optional.empty();
    }
}
