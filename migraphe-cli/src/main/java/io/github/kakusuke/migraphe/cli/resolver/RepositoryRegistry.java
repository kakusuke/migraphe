package io.github.kakusuke.migraphe.cli.resolver;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class RepositoryRegistry {

    private final List<RepositoryConfig> repositories;

    private RepositoryRegistry(List<RepositoryConfig> repositories) {
        this.repositories = List.copyOf(repositories);
    }

    public static RepositoryRegistry defaults() {
        return new RepositoryRegistry(List.of(RepositoryConfig.mavenCentral()));
    }

    public static RepositoryRegistry of(List<RepositoryConfig> repositories) {
        Set<String> seen = new HashSet<>();
        for (RepositoryConfig repo : repositories) {
            if (!seen.add(repo.id())) {
                throw new IllegalArgumentException("Duplicate repository id: '" + repo.id() + "'");
            }
        }
        return new RepositoryRegistry(repositories);
    }

    public List<RepositoryConfig> all() {
        return repositories;
    }

    public Optional<RepositoryConfig> resolve(String id) {
        for (RepositoryConfig repo : repositories) {
            if (repo.id().equals(id)) {
                return Optional.of(repo);
            }
        }
        return Optional.empty();
    }
}
