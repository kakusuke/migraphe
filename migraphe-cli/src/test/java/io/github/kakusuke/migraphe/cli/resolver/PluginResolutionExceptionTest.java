package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PluginResolutionExceptionTest {

    @Test
    void lockFileNotFoundExceptionIsPluginResolutionException() {
        assertThat(new LockFileNotFoundException("x"))
                .isInstanceOf(PluginResolutionException.class);
    }

    @Test
    void lockOutOfSyncExceptionIsPluginResolutionException() {
        assertThat(new LockOutOfSyncException("x")).isInstanceOf(PluginResolutionException.class);
    }

    @Test
    void checksumMismatchExceptionIsPluginResolutionException() {
        assertThat(new ChecksumMismatchException("x"))
                .isInstanceOf(PluginResolutionException.class);
    }

    @Test
    void missingChecksumPinExceptionIsPluginResolutionException() {
        assertThat(new MissingChecksumPinException("x"))
                .isInstanceOf(PluginResolutionException.class);
    }
}
