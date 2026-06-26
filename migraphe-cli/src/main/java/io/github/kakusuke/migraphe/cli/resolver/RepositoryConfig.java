package io.github.kakusuke.migraphe.cli.resolver;

/**
 * A named Maven repository that plugin artifacts may be resolved from.
 *
 * <p>Repositories come from the {@code repositories:} section of {@code migraphe.yaml} plus the
 * implicit Maven Central default, are collected in a {@link RepositoryRegistry}, and are converted
 * to Aether {@code RemoteRepository} instances by {@link MavenPluginResolver}. A {@link
 * PluginDeclaration} may target a repository by its {@code id}.
 *
 * <p>For security, only {@code https://} URLs are accepted from configuration; {@code file://} URLs
 * are permitted solely through the {@link #testOnly(String, String)} factory used by integration
 * tests.
 *
 * @param id the unique repository identifier, used both as the Aether repository id and as the
 *     reference target for {@link PluginDeclaration#repositoryRef()}
 * @param url the base URL of the repository, which must start with {@code https://} (or {@code
 *     file://} for test-only instances)
 */
public record RepositoryConfig(String id, String url) {

    /**
     * Canonical constructor that validates the identifier and URL.
     *
     * @param id the unique repository identifier
     * @param url the base URL of the repository
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank, if {@code url} is
     *     {@code null} or blank, or if {@code url} starts with neither {@code https://} nor {@code
     *     file://}
     */
    public RepositoryConfig {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Repository id must not be blank");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Repository url must not be blank");
        }
        if (!(url.startsWith("https://") || url.startsWith("file://"))) {
            throw new IllegalArgumentException(
                    "Repository url must start with 'https://' but was: " + url);
        }
    }

    /**
     * Returns the configuration for the Maven Central repository (id {@code maven-central}).
     *
     * @return the Maven Central repository configuration
     */
    public static RepositoryConfig mavenCentral() {
        return new RepositoryConfig("maven-central", "https://repo.maven.apache.org/maven2");
    }

    /**
     * Returns the configuration for the JitPack repository (id {@code jitpack}).
     *
     * @return the JitPack repository configuration
     */
    public static RepositoryConfig jitpack() {
        return new RepositoryConfig("jitpack", "https://jitpack.io");
    }

    /**
     * Test-only factory that allows {@code file://} URLs for integration tests.
     *
     * @param id the repository identifier
     * @param url the repository URL, which may be a {@code file://} URL
     * @return a repository configuration that bypasses the {@code https://}-only restriction
     * @throws IllegalArgumentException if {@code id} or {@code url} is blank, or if {@code url}
     *     starts with neither {@code https://} nor {@code file://}
     */
    public static RepositoryConfig testOnly(String id, String url) {
        return new RepositoryConfig(id, url);
    }
}
