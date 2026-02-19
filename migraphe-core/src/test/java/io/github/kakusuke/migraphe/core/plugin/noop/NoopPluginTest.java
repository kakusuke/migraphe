package io.github.kakusuke.migraphe.core.plugin.noop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoopPluginTest {

    @Test
    void typeShouldBeNoop() {
        var plugin = new NoopPlugin();
        assertThat(plugin.type()).isEqualTo("noop");
    }

    @Test
    void shouldProvideCorrectDefinitionClasses() {
        var plugin = new NoopPlugin();
        assertThat(plugin.taskDefinitionClass()).isEqualTo(NoopTaskDefinition.class);
        assertThat(plugin.environmentDefinitionClass()).isEqualTo(NoopEnvironmentDefinition.class);
    }

    @Test
    void shouldProvideProviders() {
        var plugin = new NoopPlugin();
        assertThat(plugin.environmentProvider()).isNotNull();
        assertThat(plugin.migrationNodeProvider()).isNotNull();
        assertThat(plugin.historyRepositoryProvider()).isNotNull();
    }
}
