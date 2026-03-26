package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.SqlContentProvider;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JdbcMigrationNodeTest {

    private final JdbcEnvironment env =
            JdbcEnvironment.create(
                    "testdb", "jdbc:h2:mem:node_test", "sa", "", "org.h2.Driver", "H2");

    @Test
    void buildWithRequiredFields() {
        var node =
                JdbcMigrationNode.builder()
                        .id("node1")
                        .name("Create table")
                        .environment(env)
                        .upSql("CREATE TABLE t1 (id INT)")
                        .build();
        assertThat(node.id()).isEqualTo(NodeId.of("node1"));
        assertThat(node.name()).isEqualTo("Create table");
        assertThat(node.environment()).isEqualTo(env);
        assertThat(node.dependencies()).isEmpty();
        assertThat(node.description()).isNull();
    }

    @Test
    void buildWithAllFields() {
        var node =
                JdbcMigrationNode.builder()
                        .id("node1")
                        .name("Create table")
                        .description("Creates the main table")
                        .environment(env)
                        .dependencies(NodeId.of("dep1"), NodeId.of("dep2"))
                        .upSql("CREATE TABLE t1 (id INT)")
                        .downSql("DROP TABLE t1")
                        .autocommit(true)
                        .build();
        assertThat(node.dependencies())
                .containsExactlyInAnyOrder(NodeId.of("dep1"), NodeId.of("dep2"));
        assertThat(node.description()).isEqualTo("Creates the main table");
    }

    @Test
    void upTaskReturnsJdbcUpTask() {
        var node =
                JdbcMigrationNode.builder()
                        .id("node1")
                        .name("Create table")
                        .environment(env)
                        .upSql("CREATE TABLE t1 (id INT)")
                        .build();
        assertThat(node.upTask()).isInstanceOf(JdbcUpTask.class);
    }

    @Test
    void downTaskReturnsJdbcDownTask() {
        var node =
                JdbcMigrationNode.builder()
                        .id("node1")
                        .name("Create table")
                        .environment(env)
                        .upSql("CREATE TABLE t1 (id INT)")
                        .downSql("DROP TABLE t1")
                        .build();
        assertThat(node.downTask()).isInstanceOf(JdbcDownTask.class);
    }

    @Test
    void downTaskReturnsNullWhenNoDownSql() {
        var node =
                JdbcMigrationNode.builder()
                        .id("node1")
                        .name("Create table")
                        .environment(env)
                        .upSql("CREATE TABLE t1 (id INT)")
                        .build();
        assertThat(node.downTask()).isNull();
    }

    @Test
    void blankUpSqlThrows() {
        assertThatThrownBy(
                        () ->
                                JdbcMigrationNode.builder()
                                        .id("node1")
                                        .name("Bad node")
                                        .environment(env)
                                        .upSql("   ")
                                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalsAndHashCodeByNodeId() {
        var node1 =
                JdbcMigrationNode.builder()
                        .id("same")
                        .name("Node A")
                        .environment(env)
                        .upSql("SELECT 1")
                        .build();
        var node2 =
                JdbcMigrationNode.builder()
                        .id("same")
                        .name("Node B")
                        .environment(env)
                        .upSql("SELECT 2")
                        .build();
        assertThat(node1).isEqualTo(node2);
        assertThat(node1.hashCode()).isEqualTo(node2.hashCode());
    }

    @Test
    void upTaskExposesSqlContent() {
        var node =
                JdbcMigrationNode.builder()
                        .id("node1")
                        .name("Create table")
                        .environment(env)
                        .upSql("CREATE TABLE t1 (id INT)")
                        .build();
        assertThat(node.upTask()).isInstanceOf(SqlContentProvider.class);
        assertThat(((SqlContentProvider) node.upTask()).sqlContent())
                .isEqualTo("CREATE TABLE t1 (id INT)");
    }

    @Test
    void dependenciesAsSet() {
        var node =
                JdbcMigrationNode.builder()
                        .id("node1")
                        .name("Node")
                        .environment(env)
                        .dependencies(Set.of(NodeId.of("dep1")))
                        .upSql("SELECT 1")
                        .build();
        assertThat(node.dependencies()).containsExactly(NodeId.of("dep1"));
    }
}
