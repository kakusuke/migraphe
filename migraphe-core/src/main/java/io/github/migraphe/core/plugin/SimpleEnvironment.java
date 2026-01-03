package io.github.migraphe.core.plugin;

import io.github.migraphe.core.environment.Environment;
import io.github.migraphe.core.environment.EnvironmentConfig;
import io.github.migraphe.core.environment.EnvironmentId;
import java.util.Objects;

/** Environment のシンプルなリファレンス実装。 プラグイン開発者はこれを参考に独自の実装を作成できる。 */
public final class SimpleEnvironment implements Environment {
    private final EnvironmentId id;
    private final String name;
    private final EnvironmentConfig config;

    private SimpleEnvironment(EnvironmentId id, String name, EnvironmentConfig config) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public EnvironmentId id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public EnvironmentConfig config() {
        return config;
    }

    public static SimpleEnvironment create(
            EnvironmentId id, String name, EnvironmentConfig config) {
        return new SimpleEnvironment(id, name, config);
    }

    public static SimpleEnvironment create(String name) {
        return new SimpleEnvironment(EnvironmentId.of(name), name, EnvironmentConfig.empty());
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
