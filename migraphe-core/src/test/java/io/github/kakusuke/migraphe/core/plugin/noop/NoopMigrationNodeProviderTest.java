package io.github.kakusuke.migraphe.core.plugin.noop;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.core.plugin.SimpleEnvironment;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NoopMigrationNodeProviderTest {

    private NoopTaskDefinition createTaskDef(String name, String up) {
        var config =
                new SmallRyeConfigBuilder()
                        .withMapping(NoopTaskDefinition.class)
                        .withDefaultValue("name", name)
                        .withDefaultValue("target", "main")
                        .withDefaultValue("up", up)
                        .build();
        return config.getConfigMapping(NoopTaskDefinition.class);
    }

    @Test
    void shouldCreateNodeWithCorrectId() {
        var provider = new NoopMigrationNodeProvider();
        var env = SimpleEnvironment.create("main");
        var nodeId = NodeId.of("test/node");
        var taskDef = createTaskDef("Test Node", "create table");

        var node = provider.createNode(nodeId, taskDef, Set.of(), env);

        assertThat(node.id()).isEqualTo(nodeId);
        assertThat(node.name()).isEqualTo("Test Node");
        assertThat(node.environment()).isEqualTo(env);
        assertThat(node.dependencies()).isEmpty();
    }

    @Test
    void shouldCreateNodeWithDependencies() {
        var provider = new NoopMigrationNodeProvider();
        var env = SimpleEnvironment.create("main");
        var nodeId = NodeId.of("test/node");
        var dep1 = NodeId.of("test/dep1");
        var dep2 = NodeId.of("test/dep2");
        var taskDef = createTaskDef("Test Node", "create table");

        var node = provider.createNode(nodeId, taskDef, Set.of(dep1, dep2), env);

        assertThat(node.dependencies()).containsExactlyInAnyOrder(dep1, dep2);
    }

    @Test
    void upTaskShouldReturnSuccess() {
        var provider = new NoopMigrationNodeProvider();
        var env = SimpleEnvironment.create("main");
        var nodeId = NodeId.of("test/node");
        var taskDef = createTaskDef("Test Node", "create table");

        var node = provider.createNode(nodeId, taskDef, Set.of(), env);
        var result = node.upTask().execute();

        assertThat(result.isOk()).isTrue();
    }

    @Test
    void downTaskShouldReturnSuccessWhenDownIsDefined() {
        var config =
                new SmallRyeConfigBuilder()
                        .withMapping(NoopTaskDefinition.class)
                        .withDefaultValue("name", "Test Node")
                        .withDefaultValue("target", "main")
                        .withDefaultValue("up", "create table")
                        .withDefaultValue("down", "drop table")
                        .build();
        var taskDef = config.getConfigMapping(NoopTaskDefinition.class);

        var provider = new NoopMigrationNodeProvider();
        var env = SimpleEnvironment.create("main");
        var node = provider.createNode(NodeId.of("test/node"), taskDef, Set.of(), env);

        assertThat(node.downTask()).isNotNull();
        assertThat(node.downTask().execute().isOk()).isTrue();
    }

    @Test
    void noWayBackShouldReachTheNode() {
        var config =
                new SmallRyeConfigBuilder()
                        .withMapping(NoopTaskDefinition.class)
                        .withDefaultValue("name", "Drop legacy")
                        .withDefaultValue("target", "main")
                        .withDefaultValue("up", "drop the column")
                        .withDefaultValue("no_way_back", "the data is discarded")
                        .build();
        var taskDef = config.getConfigMapping(NoopTaskDefinition.class);

        var node =
                new NoopMigrationNodeProvider()
                        .createNode(
                                NodeId.of("test/node"),
                                taskDef,
                                Set.of(),
                                SimpleEnvironment.create("main"));

        assertThat(node.noWayBack()).isEqualTo("the data is discarded");
        assertThat(node.downTask()).isNull();
    }

    @Test
    void downTaskShouldBeNullWhenDownNotDefined() {
        var provider = new NoopMigrationNodeProvider();
        var env = SimpleEnvironment.create("main");
        var taskDef = createTaskDef("Test Node", "create table");

        var node = provider.createNode(NodeId.of("test/node"), taskDef, Set.of(), env);

        assertThat(node.downTask()).isNull();
    }
}
