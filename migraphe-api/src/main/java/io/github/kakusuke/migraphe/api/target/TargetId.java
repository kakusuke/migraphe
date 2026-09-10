package io.github.kakusuke.migraphe.api.target;

import java.util.Objects;

/**
 * A unique identifier for an {@link Target}.
 *
 * <p>This value object wraps a non-blank string and is used throughout Migraphe to reference an
 * target, most notably to partition migration history so that each target's executions are tracked
 * independently.
 *
 * @param value the identifier string; must be non-{@code null} and non-blank
 * @see Target
 */
public record TargetId(String value) {

    /**
     * Canonical constructor that validates the identifier.
     *
     * @param value the identifier string; must be non-{@code null} and non-blank
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public TargetId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TargetId value must not be blank");
        }
    }

    /**
     * Creates an {@code TargetId} from the given string.
     *
     * @param value the identifier string; must be non-{@code null} and non-blank
     * @return a new {@code TargetId} wrapping {@code value}
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public static TargetId of(String value) {
        return new TargetId(value);
    }
}
