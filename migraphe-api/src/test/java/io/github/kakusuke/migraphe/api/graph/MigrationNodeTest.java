package io.github.kakusuke.migraphe.api.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.common.Result;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import io.github.kakusuke.migraphe.api.task.Task;
import io.github.kakusuke.migraphe.api.task.TaskResult;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class MigrationNodeTest {

    @Test
    void defaultsDescribeAPluginThatModelsNothingBeyondTheAbstractMembers() {
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
                    public Target target() {
                        return new Target() {
                            @Override
                            public TargetId id() {
                                return TargetId.of("test");
                            }

                            @Override
                            public String name() {
                                return "test";
                            }
                        };
                    }

                    @Override
                    public Set<NodeId> dependencies() {
                        return Set.of(NodeId.of("db1/001_a"));
                    }

                    @Override
                    public String fingerprint(Fingerprinter fingerprinter) {
                        return fingerprinter.over("token");
                    }

                    @Override
                    public Task upTask() {
                        return new Task() {
                            @Override
                            public Result<TaskResult, String> execute() {
                                return Result.ok(new TaskResult("applied", null, null));
                            }

                            @Override
                            public String description() {
                                return "up";
                            }

                            @Override
                            public String signature() {

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
        assertThat(node.noWayBack()).isNull();
        assertThat(node.fingerprint(signatures -> String.join("|", signatures))).isEqualTo("token");
        assertThat(node.hasNoDependencies()).isFalse();
        assertThat(node.dependsOn(NodeId.of("db1/001_a"))).isTrue();
        assertThat(node.dependsOn(NodeId.of("db1/900_z"))).isFalse();
    }

    @Test
    void fingerprintIsNotInheritable() throws NoSuchMethodException {
        assertThat(MigrationNode.class.getMethod("fingerprint", Fingerprinter.class).isDefault())
                .isFalse();
    }
}
