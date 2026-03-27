package io.github.kakusuke.migraphe.generator.api;

import java.nio.file.Path;

@FunctionalInterface
public interface Generator {
    void generate(Path outputDir);
}
