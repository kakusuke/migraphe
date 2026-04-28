package io.github.kakusuke.migraphe.cli.resolver;

public record RepositoryConfig(String id, String url) {

    public RepositoryConfig {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Repository id must not be blank");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Repository url must not be blank");
        }
        if (!url.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "Repository url must start with 'https://' but was: " + url);
        }
    }

    public static RepositoryConfig mavenCentral() {
        return new RepositoryConfig("maven-central", "https://repo.maven.apache.org/maven2");
    }

    public static RepositoryConfig jitpack() {
        return new RepositoryConfig("jitpack", "https://jitpack.io");
    }
}
