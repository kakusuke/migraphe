package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PluginConfigParseResultTest {

    @Test
    void exposesEmptyRepositoriesAndPlugins() {
        PluginConfigParseResult result = new PluginConfigParseResult(List.of(), List.of());

        assertThat(result.repositories()).isEmpty();
        assertThat(result.plugins()).isEmpty();
    }

    @Test
    void exposesProvidedRepositoriesAndPlugins() {
        RepositoryConfig jitpack = RepositoryConfig.jitpack();
        PluginDeclaration plugin = PluginDeclaration.fromString("com.example:my-plugin:1.0.0");

        PluginConfigParseResult result =
                new PluginConfigParseResult(List.of(jitpack), List.of(plugin));

        assertThat(result.repositories()).containsExactly(jitpack);
        assertThat(result.plugins()).containsExactly(plugin);
    }
}
