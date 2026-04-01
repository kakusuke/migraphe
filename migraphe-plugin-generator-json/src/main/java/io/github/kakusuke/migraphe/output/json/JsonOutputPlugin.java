package io.github.kakusuke.migraphe.output.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.kakusuke.migraphe.generator.api.GeneratorDefinition;
import io.github.kakusuke.migraphe.generator.api.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.generator.api.OutputContext;

public final class JsonOutputPlugin implements GeneratorOutputPlugin {

    private static final ObjectMapper MAPPER = createMapper();

    private static ObjectMapper createMapper() {
        var separators =
                Separators.createDefaultInstance()
                        .withObjectFieldValueSpacing(Separators.Spacing.AFTER);
        var printer =
                new DefaultPrettyPrinter(separators)
                        .withObjectIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
                        .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        var mapper = new ObjectMapper();
        mapper.setDefaultPrettyPrinter(printer);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    @Override
    public String type() {
        return "output-json";
    }

    @Override
    public boolean canHandle(Class<?> dataClass) {
        return true;
    }

    @Override
    public Class<? extends GeneratorDefinition> definitionClass() {
        return GeneratorDefinition.class;
    }

    @Override
    public void output(Object data, OutputContext context) {
        try {
            String json = MAPPER.writeValueAsString(data);
            System.out.println(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize data to JSON", e);
        }
    }
}
