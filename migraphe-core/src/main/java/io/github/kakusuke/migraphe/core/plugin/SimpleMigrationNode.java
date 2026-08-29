package io.github.kakusuke.migraphe.core.plugin;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.Task;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Simple, immutable reference implementation of {@link MigrationNode}.
 *
 * <p>This class is provided as a baseline implementation that plugin developers can use directly or
 * study when writing their own {@link MigrationNode}. Instances are constructed through the {@link
 * Builder} returned by {@link #builder()}; the node's identity (used by {@link #equals(Object)} and
 * {@link #hashCode()}) is its {@link NodeId} alone.
 *
 * @see MigrationNode
 * @see SimpleTask
 * @see SimpleEnvironment
 */
public final class SimpleMigrationNode implements MigrationNode {
    private final NodeId id;
    private final String name;
    private final @Nullable String description;
    private final Environment environment;
    private final Set<NodeId> dependencies;
    private final Task upTask;
    private final @Nullable Task downTask;
    private final @Nullable String noWayBack;

    private SimpleMigrationNode(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.description = builder.description;
        this.environment =
                Objects.requireNonNull(builder.environment, "environment must not be null");
        this.dependencies = Set.copyOf(builder.dependencies);
        this.upTask = Objects.requireNonNull(builder.upTask, "upTask must not be null");
        this.downTask = builder.downTask;
        this.noWayBack = builder.noWayBack;
    }

    @Override
    public NodeId id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public @Nullable String description() {
        return description;
    }

    @Override
    public Environment environment() {
        return environment;
    }

    @Override
    public Set<NodeId> dependencies() {
        return dependencies;
    }

    @Override
    public Task upTask() {
        return upTask;
    }

    @Override
    public @Nullable String noWayBack() {
        return noWayBack;
    }

    @Override
    public @Nullable Task downTask() {
        return downTask;
    }

    /**
     * Returns a new {@link Builder} for constructing a {@link SimpleMigrationNode}.
     *
     * @return a fresh, empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link SimpleMigrationNode}.
     *
     * <p>At minimum the {@linkplain #id(NodeId) id}, {@linkplain #name(String) name}, {@linkplain
     * #environment(Environment) environment}, and {@linkplain #upTask(Task) up task} must be set
     * before {@link #build()} is called; otherwise {@code build()} throws {@link
     * NullPointerException}. Dependencies default to the empty set and {@code description}/{@code
     * downTask} default to {@code null}.
     */
    public static class Builder {

        /** Creates a new {@code Builder}. */
        public Builder() {}

        private @Nullable NodeId id;
        private @Nullable String name;
        private @Nullable String description;
        private @Nullable Environment environment;
        private Set<NodeId> dependencies = Set.of();
        private @Nullable Task upTask;
        private @Nullable Task downTask;
        private @Nullable String noWayBack;

        /**
         * Sets the node's unique identifier.
         *
         * @param id the node ID
         * @return this builder
         */
        public Builder id(NodeId id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the node's unique identifier from its string form.
         *
         * @param id the node ID string, converted via {@link NodeId#of(String)}
         * @return this builder
         */
        public Builder id(String id) {
            this.id = NodeId.of(id);
            return this;
        }

        /**
         * Sets the node's human-readable name.
         *
         * @param name the node name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the node's optional description.
         *
         * @param description the description, or {@code null} for none
         * @return this builder
         */
        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the environment (target) the node runs against.
         *
         * @param environment the environment
         * @return this builder
         */
        public Builder environment(Environment environment) {
            this.environment = environment;
            return this;
        }

        /**
         * Sets the IDs of the nodes this node depends on.
         *
         * @param dependencies the set of dependency node IDs; copied defensively at build time
         * @return this builder
         */
        public Builder dependencies(Set<NodeId> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        /**
         * Sets the IDs of the nodes this node depends on.
         *
         * @param dependencies the dependency node IDs
         * @return this builder
         */
        public Builder dependencies(NodeId... dependencies) {
            this.dependencies = Set.of(dependencies);
            return this;
        }

        /**
         * Sets the task executed when migrating the node forward (UP).
         *
         * @param upTask the forward (UP) task
         * @return this builder
         */
        public Builder upTask(Task upTask) {
            this.upTask = upTask;
            return this;
        }

        /**
         * Sets the optional task executed when rolling the node back (DOWN).
         *
         * @param downTask the rollback (DOWN) task, or {@code null} if rollback is not supported
         * @return this builder
         */
        public Builder downTask(@Nullable Task downTask) {
            this.downTask = downTask;
            return this;
        }

        /**
         * Declares that this migration cannot be rolled back, and why.
         *
         * @param reason the author's reason
         * @return this builder
         */
        public Builder noWayBack(@Nullable String reason) {
            this.noWayBack = reason;
            return this;
        }

        /**
         * Builds the {@link SimpleMigrationNode} from this builder's current state.
         *
         * @return the constructed node
         * @throws NullPointerException if {@code id}, {@code name}, {@code environment}, or {@code
         *     upTask} has not been set
         */
        public SimpleMigrationNode build() {
            return new SimpleMigrationNode(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleMigrationNode that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SimpleMigrationNode{"
                + "id="
                + id
                + ", name='"
                + name
                + '\''
                + ", environment="
                + environment.name()
                + ", dependencies="
                + dependencies.size()
                + '}';
    }
}
