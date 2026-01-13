package io.github.migraphe.core.graph;

import io.github.migraphe.api.common.Result;
import io.github.migraphe.api.environment.Environment;
import io.github.migraphe.api.environment.EnvironmentId;
import io.github.migraphe.api.graph.MigrationNode;
import io.github.migraphe.api.graph.NodeId;
import io.github.migraphe.api.task.Task;
import io.github.migraphe.api.task.TaskResult;
import java.util.Objects;
import java.util.Set;

/** テスト用ヘルパークラス */
class TestHelpers {

    /** テスト用環境実装 */
    static class TestEnvironment implements Environment {
        private final EnvironmentId id;
        private final String name;

        TestEnvironment(String name) {
            this.name = name;
            this.id = EnvironmentId.of(name);
        }

        @Override
        public EnvironmentId id() {
            return id;
        }

        @Override
        public String name() {
            return name;
        }
    }

    /** テスト用タスク実装 */
    static class TestTask implements Task {
        private final String description;

        TestTask(String description) {
            this.description = description;
        }

        @Override
        public Result<TaskResult, String> execute() {
            return Result.ok(TaskResult.withoutDownTask("Executed: " + description));
        }

        @Override
        public String description() {
            return description;
        }
    }

    /** テスト用ノード実装 */
    static class TestMigrationNode implements MigrationNode {
        private final NodeId id;
        private final String name;
        private final Environment environment;
        private final Set<NodeId> dependencies;

        TestMigrationNode(
                NodeId id, String name, Environment environment, Set<NodeId> dependencies) {
            this.id = id;
            this.name = name;
            this.environment = environment;
            this.dependencies = dependencies;
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
        public String description() {
            return "Test node: " + name;
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
            return new TestTask("UP: " + name);
        }

        @Override
        public Task downTask() {
            return new TestTask("DOWN: " + name);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestMigrationNode that)) return false;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    /** ビルダーパターンでテストノードを作成 */
    static TestNodeBuilder node(String id) {
        return new TestNodeBuilder(NodeId.of(id));
    }

    static class TestNodeBuilder {
        private final NodeId id;
        private String name;
        private Environment environment = new TestEnvironment("test");
        private Set<NodeId> dependencies = Set.of();

        TestNodeBuilder(NodeId id) {
            this.id = id;
            this.name = id.value();
        }

        TestNodeBuilder name(String name) {
            this.name = name;
            return this;
        }

        TestNodeBuilder environment(Environment environment) {
            this.environment = environment;
            return this;
        }

        TestNodeBuilder dependencies(NodeId... deps) {
            this.dependencies = Set.of(deps);
            return this;
        }

        TestMigrationNode build() {
            return new TestMigrationNode(id, name, environment, dependencies);
        }
    }
}
