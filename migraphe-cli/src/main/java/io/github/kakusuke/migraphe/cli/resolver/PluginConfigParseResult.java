package io.github.kakusuke.migraphe.cli.resolver;

import java.util.List;
import java.util.Optional;

/**
 * The subset of {@code migraphe.yaml} extracted by an early, dependency-light pre-parse.
 *
 * <p>{@link PluginConfigPreParser} reads {@code migraphe.yaml} before the plugin {@link
 * java.net.URLClassLoader URLClassLoader} exists — and therefore before the full MicroProfile-based
 * config loader can run — to learn which plugin JARs and repositories are needed. This record
 * carries exactly those bootstrap fields. All collections are defensively copied into immutable
 * lists.
 *
 * @param repositories the configured plugin repositories (immutable)
 * @param plugins the declared plugins to resolve (immutable)
 * @param scanRoot the value of {@code project.scan-root}, or empty when not set
 */
public record PluginConfigParseResult(
        List<RepositoryConfig> repositories,
        List<PluginDeclaration> plugins,
        Optional<String> scanRoot) {

    /**
     * Canonical constructor that defensively copies the collections and normalizes a {@code null}
     * {@code scanRoot} to {@link Optional#empty()}.
     *
     * @param repositories the configured plugin repositories
     * @param plugins the declared plugins to resolve
     * @param scanRoot the value of {@code project.scan-root}, or {@code null}/empty when not set
     */
    public PluginConfigParseResult {
        repositories = List.copyOf(repositories);
        plugins = List.copyOf(plugins);
        scanRoot = scanRoot == null ? Optional.empty() : scanRoot;
    }
}
