package io.github.kakusuke.migraphe.cli.resolver;

import java.util.List;

public record PluginConfigParseResult(
        List<RepositoryConfig> repositories, List<PluginDeclaration> plugins) {

    public PluginConfigParseResult {
        repositories = List.copyOf(repositories);
        plugins = List.copyOf(plugins);
    }
}
