package io.github.kakusuke.migraphe.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.SqlContentProvider;
import io.github.kakusuke.migraphe.jdbc.JdbcMigrationNode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PostgreSQLMigrationNodeTest {

    private final PostgreSQLTarget target =
            PostgreSQLTarget.create("testdb", "jdbc:postgresql://localhost/test", "user", "pass");

    @Test
    void buildWithRequiredFields() {
        var node =
                JdbcMigrationNode.builder()
                        .id("node1")
                        .name("Create table")
                        .target(target)
                        .upSql("CREATE TABLE t1 (id INT)")
                        .build();
        assertThat(node.id()).isEqualTo(NodeId.of("node1"));
        assertThat(node.name()).isEqualTo("Create table");
        assertThat(node.target()).isEqualTo(target);
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
                        .target(target)
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
    void downTaskReturnsNullWhenNoDownSql() {
        var node =
                JdbcMigrationNode.builder()
                        .id("node1")
                        .name("Create table")
                        .target(target)
                        .upSql("CREATE TABLE t1 (id INT)")
                        .build();
        assertThat(node.downTask()).isNull();
    }

    @Test
    void descriptionDefaultsToNull() {
        var node =
                JdbcMigrationNode.builder()
                        .id("node1")
                        .name("Create table")
                        .target(target)
                        .upSql("CREATE TABLE t1 (id INT)")
                        .build();
        assertThat(node.description()).isNull();
    }

    @Test
    void blankUpSqlThrows() {
        assertThatThrownBy(
                        () ->
                                JdbcMigrationNode.builder()
                                        .id("node1")
                                        .name("Bad node")
                                        .target(target)
                                        .upSql("   ")
                                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void autocommitFlag() {
        var node =
                JdbcMigrationNode.builder()
                        .id("node1")
                        .name("Create DB")
                        .target(target)
                        .upSql("CREATE DATABASE myapp")
                        .autocommit(true)
                        .build();
        assertThat(node.upTask().description()).contains("autocommit");
    }

    @Test
    void equalsAndHashCodeByNodeId() {
        var node1 =
                JdbcMigrationNode.builder()
                        .id("same")
                        .name("Node A")
                        .target(target)
                        .upSql("SELECT 1")
                        .build();
        var node2 =
                JdbcMigrationNode.builder()
                        .id("same")
                        .name("Node B")
                        .target(target)
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
                        .target(target)
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
                        .target(target)
                        .dependencies(Set.of(NodeId.of("dep1")))
                        .upSql("SELECT 1")
                        .build();
        assertThat(node.dependencies()).containsExactly(NodeId.of("dep1"));
    }
}
