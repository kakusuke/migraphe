package io.github.kakusuke.migraphe.core.plugin;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import java.util.Objects;

/**
 * Simple, immutable reference implementation of {@link Environment}.
 *
 * <p>This class carries only an {@link EnvironmentId} and a display name, making it a convenient
 * baseline for plugins (such as the {@code noop} plugin) that do not need any backend-specific
 * connection state. Plugin developers can use it directly or study it when writing their own {@link
 * Environment}. Instances are created via the {@code create} factory methods; the environment's
 * identity (used by {@link #equals(Object)} and {@link #hashCode()}) is its {@link EnvironmentId}
 * alone.
 *
 * @see Environment
 */
public final class SimpleEnvironment implements Environment {
    private final EnvironmentId id;
    private final String name;

    private SimpleEnvironment(EnvironmentId id, String name) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    @Override
    public EnvironmentId id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Creates an environment with an explicit ID and name.
     *
     * @param id the environment ID
     * @param name the human-readable environment name
     * @return the constructed environment
     * @throws NullPointerException if {@code id} or {@code name} is {@code null}
     */
    public static SimpleEnvironment create(EnvironmentId id, String name) {
        return new SimpleEnvironment(id, name);
    }

    /**
     * Creates an environment whose ID is derived from its name.
     *
     * @param name the human-readable environment name, also used as the ID via {@link
     *     EnvironmentId#of(String)}
     * @return the constructed environment
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public static SimpleEnvironment create(String name) {
        return new SimpleEnvironment(EnvironmentId.of(name), name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleEnvironment that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SimpleEnvironment{" + "id=" + id + ", name='" + name + '\'' + '}';
    }
}
