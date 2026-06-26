package io.github.kakusuke.migraphe.api.environment;

import java.util.Objects;

/**
 * A unique identifier for an {@link Environment}.
 *
 * <p>This value object wraps a non-blank string and is used throughout Migraphe to reference an
 * environment, most notably to partition migration history so that each environment's executions
 * are tracked independently.
 *
 * @param value the identifier string; must be non-{@code null} and non-blank
 * @see Environment
 */
public record EnvironmentId(String value) {

    /**
     * Canonical constructor that validates the identifier.
     *
     * @param value the identifier string; must be non-{@code null} and non-blank
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public EnvironmentId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("EnvironmentId value must not be blank");
        }
    }

    /**
     * Creates an {@code EnvironmentId} from the given string.
     *
     * @param value the identifier string; must be non-{@code null} and non-blank
     * @return a new {@code EnvironmentId} wrapping {@code value}
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public static EnvironmentId of(String value) {
        return new EnvironmentId(value);
    }
}
