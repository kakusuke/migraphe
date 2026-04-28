package io.github.kakusuke.migraphe.cli.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Sha256CalculatorTest {

    @TempDir Path tempDir;

    @Test
    void hashesKnownVectorForHello() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello");

        String hash = Sha256Calculator.hash(file);

        assertThat(hash)
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void hashesEmptyFile() throws IOException {
        Path file = tempDir.resolve("empty.bin");
        Files.write(file, new byte[0]);

        String hash = Sha256Calculator.hash(file);

        assertThat(hash)
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void streamsLargeFileWithoutLoadingFully() throws IOException {
        Path file = tempDir.resolve("large.bin");
        byte[] chunk = new byte[8192];
        for (int i = 0; i < chunk.length; i++) {
            chunk[i] = (byte) (i & 0xFF);
        }
        try (var out = Files.newOutputStream(file)) {
            for (int i = 0; i < 200; i++) {
                out.write(chunk);
            }
        }

        String hash = Sha256Calculator.hash(file);

        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }
}
