package io.github.migraphe.core.plugin;

import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.environment.EnvironmentId;
import java.util.Objects;

/** Environment のシンプルなリファレンス実装。 プラグイン開発者はこれを参考に独自の実装を作成できる。 */
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

    public static SimpleEnvironment create(EnvironmentId id, String name) {
        return new SimpleEnvironment(id, name);
    }

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
