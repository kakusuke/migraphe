package io.github.kakusuke.migraphe.cli.resolver;

import java.util.List;
import java.util.Optional;

public record PluginConfigParseResult(
        List<RepositoryConfig> repositories,
        List<PluginDeclaration> plugins,
        Optional<String> scanRoot) {

    public PluginConfigParseResult {
        repositories = List.copyOf(repositories);
        plugins = List.copyOf(plugins);
        scanRoot = scanRoot == null ? Optional.empty() : scanRoot;
    }
}
