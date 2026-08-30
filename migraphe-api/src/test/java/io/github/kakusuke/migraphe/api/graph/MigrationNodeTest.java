package io.github.kakusuke.migraphe.api.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.environment.Environment;
import io.github.kakusuke.migraphe.api.environment.EnvironmentId;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class MigrationNodeTest {

    @Test
    void fingerprintShouldDefaultToNullWhenNotOverridden() {
        // given: a node implementing only the abstract members, as a plugin author might
        MigrationNode node =
                new MigrationNode() {
                    @Override
                    public NodeId id() {
                        return NodeId.of("node1");
                    }

                    @Override
                    public String name() {
                        return "Create table";
                    }

                    @Override
                    public @Nullable String description() {
                        return null;
                    }

                    @Override
                    public Environment environment() {
                        return new Environment() {
                            @Override
                            public EnvironmentId id() {
                                return EnvironmentId.of("test");
                            }

                            @Override
                            public String name() {
                                return "test";
                            }
                        };
                    }

                    @Override
                    public Set<NodeId> dependencies() {
                        return Set.of();
                    }

                    @Override
                    public Task upTask() {
                        return new Task() {
                            @Override
                            public Result<TaskResult, String> execute() {
                                return Result.ok(new TaskResult("applied", null));
                            }

                            @Override
                            public String description() {
                                return "up";
                            }
                        };
                    }

                    @Override
                    public @Nullable Task downTask() {
                        return null;
                    }
                };

        // when / then
        assertThat(node.fingerprint(List.of())).isNull();
        assertThat(node.fingerprint(List.of(NodeId.of("db1/001_a")))).isNull();
    }
}
