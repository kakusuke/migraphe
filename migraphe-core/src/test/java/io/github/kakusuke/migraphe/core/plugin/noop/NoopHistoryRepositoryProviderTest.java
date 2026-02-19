package io.github.kakusuke.migraphe.core.plugin.noop;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.core.history.InMemoryHistoryRepository;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import org.junit.jupiter.api.Test;

class NoopHistoryRepositoryProviderTest {

    @Test
    void shouldReturnInMemoryHistoryRepository() {
        var provider = new NoopHistoryRepositoryProvider();
        var env = SimpleEnvironment.create("main");

        var repo = provider.createRepository(env);

        assertThat(repo).isInstanceOf(InMemoryHistoryRepository.class);
    }

    @Test
    void eachCallShouldReturnNewInstance() {
        var provider = new NoopHistoryRepositoryProvider();
        var env = SimpleEnvironment.create("main");

        var repo1 = provider.createRepository(env);
        var repo2 = provider.createRepository(env);

        assertThat(repo1).isNotSameAs(repo2);
    }
}
