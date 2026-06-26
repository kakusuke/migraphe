package io.github.kakusuke.migraphe.core.config;

import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jspecify.annotations.Nullable;

/**
 * A {@link ConfigSource} backed by an in-memory {@link Map}.
 *
 * <p>Used to inject externally-supplied values into the SmallRye Config layering — for example
 * Gradle DSL {@code variables}, OS environment variables (namespaced under {@code env.}), and
 * system properties. The ordinal controls precedence relative to the other sources; the default
 * {@code 600} places explicitly-passed variables above the environment file ({@code 500}) and the
 * YAML configuration ({@code 100}), while {@link ConfigLoader} uses lower ordinals (300 for OS env,
 * 400 for system properties) when registering those sources. The wrapped map is defensively copied
 * so the source is immutable.
 */
public class MapConfigSource implements ConfigSource {

    private static final String NAME = "MapConfigSource";
    private static final int ORDINAL = 600;

    private final Map<String, String> properties;
    private final int ordinal;

    /**
     * Creates a source at the default ordinal ({@code 600}), the highest precedence used by
     * Migraphe.
     *
     * @param properties the key/value pairs to expose (copied defensively)
     */
    public MapConfigSource(Map<String, String> properties) {
        this(properties, ORDINAL);
    }

    /**
     * Creates a source at an explicit ordinal.
     *
     * @param properties the key/value pairs to expose (copied defensively)
     * @param ordinal the precedence of this source; higher values win over lower ones
     */
    public MapConfigSource(Map<String, String> properties, int ordinal) {
        this.properties = Map.copyOf(properties);
        this.ordinal = ordinal;
    }

    @Override
    public Set<String> getPropertyNames() {
        return properties.keySet();
    }

    @Override
    public @Nullable String getValue(String propertyName) {
        return properties.get(propertyName);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrdinal() {
        return ordinal;
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }
}
