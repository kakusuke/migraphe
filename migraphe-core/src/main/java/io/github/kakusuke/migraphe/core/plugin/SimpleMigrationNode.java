package io.github.kakusuke.migraphe.core.plugin;

import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.Task;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** MigrationNode のシンプルなリファレンス実装。 プラグイン開発者はこれを参考に独自の実装を作成できる。 */
public final class SimpleMigrationNode implements MigrationNode {
    private final NodeId id;
    private final String name;
    private final @Nullable String description;
    private final Environment environment;
    private final Set<NodeId> dependencies;
    private final Task upTask;
    private final @Nullable Task downTask;

    private SimpleMigrationNode(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.description = builder.description;
        this.environment =
                Objects.requireNonNull(builder.environment, "environment must not be null");
        this.dependencies = Set.copyOf(builder.dependencies);
        this.upTask = Objects.requireNonNull(builder.upTask, "upTask must not be null");
        this.downTask = builder.downTask;
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
    public @Nullable Task downTask() {
        return downTask;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private @Nullable NodeId id;
        private @Nullable String name;
        private @Nullable String description;
        private @Nullable Environment environment;
        private Set<NodeId> dependencies = Set.of();
        private @Nullable Task upTask;
        private @Nullable Task downTask;

        public Builder id(NodeId id) {
            this.id = id;
            return this;
        }

        public Builder id(String id) {
            this.id = NodeId.of(id);
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        public Builder environment(Environment environment) {
            this.environment = environment;
            return this;
        }

        public Builder dependencies(Set<NodeId> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public Builder dependencies(NodeId... dependencies) {
            this.dependencies = Set.of(dependencies);
            return this;
        }

        public Builder upTask(Task upTask) {
            this.upTask = upTask;
            return this;
        }

        public Builder downTask(@Nullable Task downTask) {
            this.downTask = downTask;
            return this;
        }

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
