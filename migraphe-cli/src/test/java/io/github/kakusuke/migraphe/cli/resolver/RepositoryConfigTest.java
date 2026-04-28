package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RepositoryConfigTest {

    @Test
    void mavenCentralFactoryReturnsExpectedIdAndUrl() {
        RepositoryConfig config = RepositoryConfig.mavenCentral();

        assertThat(config.id()).isEqualTo("maven-central");
        assertThat(config.url()).isEqualTo("https://repo.maven.apache.org/maven2");
    }

    @Test
    void jitpackFactoryReturnsExpectedIdAndUrl() {
        RepositoryConfig config = RepositoryConfig.jitpack();

        assertThat(config.id()).isEqualTo("jitpack");
        assertThat(config.url()).isEqualTo("https://jitpack.io");
    }

    @Test
    void rejectsHttpUrl() {
        assertThatThrownBy(() -> new RepositoryConfig("internal", "http://example.com/repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }

    @Test
    void rejectsNullId() {
        assertThatThrownBy(() -> new RepositoryConfig(null, "https://example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void rejectsBlankId() {
        assertThatThrownBy(() -> new RepositoryConfig("", "https://example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void rejectsNullUrl() {
        assertThatThrownBy(() -> new RepositoryConfig("repo", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }
}
