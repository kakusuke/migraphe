package io.github.kakusuke.migraphe.output.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin;
import io.github.kakusuke.migraphe.api.generator.OutputContext;

/**
 * {@link GeneratorOutputPlugin} that renders any generator data as pretty-printed JSON to standard
 * output.
 *
 * <p>Registered under the {@code "output-json"} type, this is a universal output plugin: it
 * {@linkplain #canHandle(Class) accepts} data of any type and serializes it with Jackson, so it can
 * be paired with any {@link io.github.kakusuke.migraphe.api.generator.GeneratorSourcePlugin}. The
 * JSON is configured for human-readable output (indented, with a space after object field-name
 * colons) and is written to {@link System#out} rather than to the output directory.
 *
 * <p>Discovered at runtime via {@link java.util.ServiceLoader} through the {@code
 * META-INF/services/io.github.kakusuke.migraphe.api.generator.GeneratorOutputPlugin} resource.
 *
 * @see GeneratorOutputPlugin
 */
public final class JsonOutputPlugin implements GeneratorOutputPlugin {

    /** Creates a new {@code JsonOutputPlugin}. */
    public JsonOutputPlugin() {}

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

    /**
     * {@inheritDoc}
     *
     * @return the literal {@code "output-json"}
     */
    @Override
    public String type() {
        return "output-json";
    }

    /**
     * {@inheritDoc}
     *
     * <p>This plugin can serialize any object, so it always accepts the data type.
     *
     * @param dataClass the concrete class of the data object the source plugin produced
     * @return always {@code true}
     */
    @Override
    public boolean canHandle(Class<?> dataClass) {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This plugin requires no plugin-specific configuration, so it uses the base {@link
     * GeneratorDefinition} type.
     *
     * @return {@link GeneratorDefinition}, the base configuration type
     */
    @Override
    public Class<? extends GeneratorDefinition> definitionClass() {
        return GeneratorDefinition.class;
    }

    /**
     * Serializes the given data to pretty-printed JSON and writes it to {@link System#out}.
     *
     * <p>The context's output directory is not used; output always goes to standard output.
     *
     * @param data the data object to serialize as JSON
     * @param context the output context (its destination directory is ignored by this plugin)
     * @throws IllegalStateException if the data cannot be serialized to JSON
     */
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
