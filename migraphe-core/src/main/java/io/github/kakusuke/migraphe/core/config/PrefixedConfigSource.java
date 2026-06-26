package io.github.kakusuke.migraphe.core.config;

import io.smallrye.config.SmallRyeConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jspecify.annotations.Nullable;

/**
 * A {@link ConfigSource} that re-exposes a prefixed slice of another config with the prefix
 * removed.
 *
 * <p>Target and task configuration lives under dynamic, per-id prefixes inside the merged config
 * (for example {@code target.db1.*}). To map such a slice onto a plugin's {@code @ConfigMapping}
 * interface — which expects un-prefixed keys — {@link ConfigLoader} wraps the merged {@link
 * SmallRyeConfig} in this source with the relevant prefix. Properties whose values resolve to
 * {@code null} are skipped, so {@code ${...}} expansion has already been applied by the underlying
 * config.
 *
 * <p>Example, with {@code prefix = "target.db1."}:
 *
 * <ul>
 *   <li>{@code "target.db1.type"} &rarr; {@code "type"}
 *   <li>{@code "target.db1.jdbc_url"} &rarr; {@code "jdbc_url"}
 * </ul>
 */
public class PrefixedConfigSource implements ConfigSource {

    private final Map<String, String> properties;
    private final String name;

    /**
     * Builds the source by copying every property of {@code sourceConfig} whose name starts with
     * {@code prefix}, stripping that prefix from the key.
     *
     * @param sourceConfig the underlying config to slice (values are read fully resolved)
     * @param prefix the prefix to match and strip; must include the trailing {@code "."}
     */
    public PrefixedConfigSource(SmallRyeConfig sourceConfig, String prefix) {
        this.name = "PrefixedConfigSource[" + prefix + "]";
        this.properties = new HashMap<>();

        for (String propertyName : sourceConfig.getPropertyNames()) {
            if (propertyName.startsWith(prefix)) {
                String strippedName = propertyName.substring(prefix.length());
                String value = sourceConfig.getValue(propertyName, String.class);
                if (value != null) {
                    properties.put(strippedName, value);
                }
            }
        }
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
        return name;
    }

    @Override
    public Map<String, String> getProperties() {
        return Map.copyOf(properties);
    }
}
