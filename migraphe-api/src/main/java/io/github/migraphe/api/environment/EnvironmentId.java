package io.github.migraphe.api.environment;

import java.util.Objects;

/** 環境の一意識別子。 */
public record EnvironmentId(String value) {

    public EnvironmentId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("EnvironmentId value must not be blank");
        }
    }

    public static EnvironmentId of(String value) {
        return new EnvironmentId(value);
    }
}
