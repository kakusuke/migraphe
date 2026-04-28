package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryRegistryTest {

    @Test
    void defaultsContainsMavenCentralOnly() {
        RepositoryRegistry registry = RepositoryRegistry.defaults();

        assertThat(registry.all()).containsExactly(RepositoryConfig.mavenCentral());
    }

    @Test
    void resolveReturnsMatchingRepository() {
        RepositoryRegistry registry =
                RepositoryRegistry.of(
                        List.of(RepositoryConfig.mavenCentral(), RepositoryConfig.jitpack()));

        assertThat(registry.resolve("jitpack")).contains(RepositoryConfig.jitpack());
    }

    @Test
    void resolveReturnsEmptyWhenIdNotFound() {
        RepositoryRegistry registry = RepositoryRegistry.defaults();

        assertThat(registry.resolve("unknown")).isEmpty();
    }

    @Test
    void allPreservesInsertionOrder() {
        RepositoryConfig central = RepositoryConfig.mavenCentral();
        RepositoryConfig jitpack = RepositoryConfig.jitpack();

        RepositoryRegistry registry = RepositoryRegistry.of(List.of(jitpack, central));

        assertThat(registry.all()).containsExactly(jitpack, central);
    }

    @Test
    void rejectsDuplicateIds() {
        RepositoryConfig a = new RepositoryConfig("dup", "https://a.example.com");
        RepositoryConfig b = new RepositoryConfig("dup", "https://b.example.com");

        assertThatThrownBy(() -> RepositoryRegistry.of(List.of(a, b)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dup");
    }
}
