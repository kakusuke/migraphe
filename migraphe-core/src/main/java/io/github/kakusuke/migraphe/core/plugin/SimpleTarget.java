package io.github.kakusuke.migraphe.core.plugin;

import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import java.util.Objects;

/**
 * Simple, immutable reference implementation of {@link Target}.
 *
 * <p>This class carries only an {@link TargetId} and a display name, making it a convenient
 * baseline for plugins (such as the {@code noop} plugin) that do not need any backend-specific
 * connection state. Plugin developers can use it directly or study it when writing their own {@link
 * Target}. Instances are created via the {@code create} factory methods; the target's identity
 * (used by {@link #equals(Object)} and {@link #hashCode()}) is its {@link TargetId} alone.
 *
 * @see Target
 */
public final class SimpleTarget implements Target {
    private final TargetId id;
    private final String name;

    private SimpleTarget(TargetId id, String name) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    @Override
    public TargetId id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Creates an target with an explicit ID and name.
     *
     * @param id the target ID
     * @param name the human-readable environment name
     * @return the constructed target
     * @throws NullPointerException if {@code id} or {@code name} is {@code null}
     */
    public static SimpleTarget create(TargetId id, String name) {
        return new SimpleTarget(id, name);
    }

    /**
     * Creates an target whose ID is derived from its name.
     *
     * @param name the human-readable environment name, also used as the ID via {@link
     *     TargetId#of(String)}
     * @return the constructed target
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public static SimpleTarget create(String name) {
        return new SimpleTarget(TargetId.of(name), name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleTarget that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SimpleTarget{" + "id=" + id + ", name='" + name + '\'' + '}';
    }
}
