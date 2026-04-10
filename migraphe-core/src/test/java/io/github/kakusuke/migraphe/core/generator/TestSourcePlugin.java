package io.github.kakusuke.migraphe.core.generator;

import io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin;
import io.github.kakusuke.migraphe.api.generator.SourceContext;

public final class TestSourcePlugin implements GeneratorSourcePlugin<String> {

    @Override
    public String type() {
        return "test-source";
    }

    @Override
    public Class<String> dataClass() {
        return String.class;
    }

    @Override
    public String extract(SourceContext context) {
        return "";
    }
}
