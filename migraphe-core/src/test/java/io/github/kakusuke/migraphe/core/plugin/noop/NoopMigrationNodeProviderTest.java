package io.github.kakusuke.migraphe.core.plugin.noop;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.Fingerprinter;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.RollbackPayloadProvider;
import io.github.kakusuke.migraphe.core.plugin.SimpleTarget;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NoopMigrationNodeProviderTest {

    private static final Fingerprinter CONCATENATING = signatures -> String.join("|", signatures);

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
        var target = SimpleTarget.create("main");
        var nodeId = NodeId.of("test/node");
        var taskDef = createTaskDef("Test Node", "create table");

        var node = provider.createNode(nodeId, taskDef, Set.of(), target);

        assertThat(node.id()).isEqualTo(nodeId);
        assertThat(node.name()).isEqualTo("Test Node");
        assertThat(node.target()).isEqualTo(target);
        assertThat(node.dependencies()).isEmpty();
    }

    @Test
    void shouldCreateNodeWithDependencies() {
        var provider = new NoopMigrationNodeProvider();
        var target = SimpleTarget.create("main");
        var nodeId = NodeId.of("test/node");
        var dep1 = NodeId.of("test/dep1");
        var dep2 = NodeId.of("test/dep2");
        var taskDef = createTaskDef("Test Node", "create table");

        var node = provider.createNode(nodeId, taskDef, Set.of(dep1, dep2), target);

        assertThat(node.dependencies()).containsExactlyInAnyOrder(dep1, dep2);
    }

    @Test
    void upTaskShouldReturnSuccess() {
        var provider = new NoopMigrationNodeProvider();
        var target = SimpleTarget.create("main");
        var nodeId = NodeId.of("test/node");
        var taskDef = createTaskDef("Test Node", "create table");

        var node = provider.createNode(nodeId, taskDef, Set.of(), target);
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
        var target = SimpleTarget.create("main");
        var node = provider.createNode(NodeId.of("test/node"), taskDef, Set.of(), target);

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
                                SimpleTarget.create("main"));

        assertThat(node.noWayBack()).isEqualTo("the data is discarded");
        assertThat(node.downTask()).isNull();
    }

    @Test
    void upTaskReportsTheRollbackTheDefinitionDeclares() {
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
        var node =
                provider.createNode(
                        NodeId.of("test/node"), taskDef, Set.of(), SimpleTarget.create("main"));

        // The row a successful UP writes carries what the up task reports, not what down: holds.
        assertThat(node.upTask()).isInstanceOf(RollbackPayloadProvider.class);
        assertThat(((RollbackPayloadProvider) node.upTask()).serializedDownTask())
                .isEqualTo("drop table");
    }

    @Test
    void downTaskShouldBeNullWhenDownNotDefined() {
        var provider = new NoopMigrationNodeProvider();
        var target = SimpleTarget.create("main");
        var taskDef = createTaskDef("Test Node", "create table");

        var node = provider.createNode(NodeId.of("test/node"), taskDef, Set.of(), target);

        assertThat(node.downTask()).isNull();
    }

    @Test
    void fingerprintFollowsTheTaskTextSoAnEditIsVisible() {
        var original = nodeFor("CREATE TABLE t", "DROP TABLE t");
        var same = nodeFor("CREATE TABLE t", "DROP TABLE t");
        var editedUp = nodeFor("CREATE TABLE t2", "DROP TABLE t");
        var editedDown = nodeFor("CREATE TABLE t", "DROP TABLE t2");

        assertThat(original.fingerprint(CONCATENATING)).isEqualTo(same.fingerprint(CONCATENATING));
        assertThat(original.fingerprint(CONCATENATING))
                .isNotEqualTo(editedUp.fingerprint(CONCATENATING));
        assertThat(original.fingerprint(CONCATENATING))
                .isNotEqualTo(editedDown.fingerprint(CONCATENATING));
    }

    private MigrationNode nodeFor(String up, String down) {
        var config =
                new SmallRyeConfigBuilder()
                        .withMapping(NoopTaskDefinition.class)
                        .withDefaultValue("name", "Test Node")
                        .withDefaultValue("target", "main")
                        .withDefaultValue("up", up)
                        .withDefaultValue("down", down)
                        .build();
        return new NoopMigrationNodeProvider()
                .createNode(
                        NodeId.of("test/node"),
                        config.getConfigMapping(NoopTaskDefinition.class),
                        Set.of(),
                        SimpleTarget.create("main"));
    }
}
