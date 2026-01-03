package io.github.migraphe.core.environment;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 環境固有の設定。 */
public record EnvironmentConfig(Map<String, String> properties) {

    public EnvironmentConfig {
        Objects.requireNonNull(properties, "properties must not be null");
        properties = Map.copyOf(properties);
    }

    public Optional<String> getProperty(String key) {
        return Optional.ofNullable(properties.get(key));
    }

    public static EnvironmentConfig empty() {
        return new EnvironmentConfig(Map.of());
    }

    public static EnvironmentConfig of(Map<String, String> properties) {
        return new EnvironmentConfig(properties);
    }
}
