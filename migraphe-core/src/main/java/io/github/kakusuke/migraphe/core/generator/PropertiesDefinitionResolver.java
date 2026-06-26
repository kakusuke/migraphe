package io.github.kakusuke.migraphe.core.generator;

import io.github.kakusuke.migraphe.api.generator.DefinitionResolver;
import io.github.kakusuke.migraphe.api.generator.GeneratorDefinition;
import io.github.kakusuke.migraphe.core.config.MapConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A {@link DefinitionResolver} that re-materializes typed generator definitions on a plugin's class
 * loader from a snapshot of SmallRye Config properties.
 *
 * <p>On construction it captures a flat snapshot of the configuration properties under a given
 * prefix (for example {@code "generators[0]"}). When {@link #resolve(Class)} is later called, it
 * builds a fresh {@link SmallRyeConfig} backed only by that snapshot and produces the requested
 * {@code @ConfigMapping} proxy. Because {@link SmallRyeConfigBuilder#withMapping(Class)} uses the
 * passed class's own class loader to generate the proxy, the resulting typed definition can be
 * handed across class-loader boundaries (for example into a plugin loaded from a separate JAR)
 * without triggering a {@link ClassCastException}.
 */
public final class PropertiesDefinitionResolver implements DefinitionResolver {

    private final Map<String, String> properties;

    /**
     * Builds a resolver from a snapshot of the properties under the given prefix.
     *
     * @param source the source configuration to snapshot from
     * @param prefix the property prefix to capture, without a trailing dot (for example {@code
     *     "generators[0]"}); keys are stored with the prefix stripped
     */
    public PropertiesDefinitionResolver(SmallRyeConfig source, String prefix) {
        this.properties = extractProperties(source, prefix);
    }

    private static Map<String, String> extractProperties(SmallRyeConfig source, String prefix) {
        String prefixDot = prefix + ".";
        Map<String, String> snapshot = new HashMap<>();
        for (String key : source.getPropertyNames()) {
            if (key.startsWith(prefixDot)) {
                String stripped = key.substring(prefixDot.length());
                try {
                    String value = source.getRawValue(key);
                    if (value != null) {
                        snapshot.put(stripped, value);
                    }
                } catch (NoSuchElementException ignored) {
                    // skip
                }
            }
        }
        return snapshot;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Builds a {@link SmallRyeConfig} backed solely by the captured property snapshot and
     * returns the {@code @ConfigMapping} proxy for {@code klass}, generated on {@code klass}'s
     * class loader. Unknown-property validation is disabled so that unrelated keys in the snapshot
     * are tolerated.
     */
    @Override
    public <T extends GeneratorDefinition> T resolve(Class<T> klass) {
        SmallRyeConfig config =
                new SmallRyeConfigBuilder()
                        .withSources(new MapConfigSource(properties))
                        .withMapping(klass)
                        .withValidateUnknown(false)
                        .build();
        return config.getConfigMapping(klass);
    }
}
