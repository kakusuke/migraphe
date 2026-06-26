package io.github.kakusuke.migraphe.api.graph;

import java.util.Objects;
import java.util.UUID;

/**
 * A unique identifier for a {@link MigrationNode} within a migration graph.
 *
 * <p>This value object wraps a non-blank string. Node identifiers are used to declare dependencies
 * between nodes and to correlate execution history with the node that produced it. In the CLI,
 * identifiers are typically derived from a task's file path (for example, {@code "db1/create"}).
 *
 * @param value the identifier string; must be non-{@code null} and non-blank
 * @see MigrationNode
 */
public record NodeId(String value) {

    /**
     * Canonical constructor that validates the identifier.
     *
     * @param value the identifier string; must be non-{@code null} and non-blank
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public NodeId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("NodeId value must not be blank");
        }
    }

    /**
     * Generates a {@code NodeId} backed by a random {@link UUID}.
     *
     * @return a new {@code NodeId} whose value is a freshly generated UUID string
     */
    public static NodeId generate() {
        return new NodeId(UUID.randomUUID().toString());
    }

    /**
     * Creates a {@code NodeId} from the given string.
     *
     * @param value the identifier string; must be non-{@code null} and non-blank
     * @return a new {@code NodeId} wrapping {@code value}
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public static NodeId of(String value) {
        return new NodeId(value);
    }
}
