package io.github.migraphe.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.migraphe.api.graph.NodeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostgreSQLMigrationNodeTest {

    private PostgreSQLEnvironment environment;

    @BeforeEach
    void setUp() {
        environment =
                PostgreSQLEnvironment.create(
                        "test", "jdbc:postgresql://localhost:5432/test", "user", "pass");
    }

    @Test
    void shouldCreateNodeWithBuilder() {
        // given
        String upSql = "CREATE TABLE users (id SERIAL PRIMARY KEY);";
        String downSql = "DROP TABLE IF EXISTS users;";

        // when
        PostgreSQLMigrationNode node =
                PostgreSQLMigrationNode.builder()
                        .id("V001")
                        .name("Create users table")
                        .description("Initial schema")
                        .environment(environment)
                        .upSql(upSql)
                        .downSql(downSql)
                        .build();

        // then
        assertThat(node.id()).isEqualTo(NodeId.of("V001"));
        assertThat(node.name()).isEqualTo("Create users table");
        assertThat(node.description()).isEqualTo("Initial schema");
        assertThat(node.environment()).isEqualTo(environment);
        assertThat(node.dependencies()).isEmpty();
        assertThat(node.upTask()).isNotNull();
        assertThat(node.downTask()).isNotNull();
    }

    @Test
    void shouldCreateNodeWithDependencies() {
        // given
        NodeId dep1 = NodeId.of("V001");
        NodeId dep2 = NodeId.of("V002");

        // when
        PostgreSQLMigrationNode node =
                PostgreSQLMigrationNode.builder()
                        .id("V003")
                        .name("Migration with dependencies")
                        .environment(environment)
                        .dependencies(dep1, dep2)
                        .upSql("ALTER TABLE users ADD COLUMN email VARCHAR(255);")
                        .build();

        // then
        assertThat(node.dependencies()).containsExactlyInAnyOrder(dep1, dep2);
    }

    @Test
    void shouldCreateNodeWithoutDownSql() {
        // when
        PostgreSQLMigrationNode node =
                PostgreSQLMigrationNode.builder()
                        .id("V001")
                        .name("Irreversible migration")
                        .environment(environment)
                        .upSql("INSERT INTO config VALUES ('key', 'value');")
                        .build();

        // then
        assertThat(node.upTask()).isNotNull();
        assertThat(node.downTask()).isNull();
    }

    @Test
    void shouldUseEmptyDescriptionByDefault() {
        // when
        PostgreSQLMigrationNode node =
                PostgreSQLMigrationNode.builder()
                        .id("V001")
                        .name("Test")
                        .environment(environment)
                        .upSql("SELECT 1;")
                        .build();

        // then
        assertThat(node.description()).isNullOrEmpty();
    }

    @Test
    void shouldThrowExceptionWhenUpSqlIsBlank() {
        // when & then
        assertThatThrownBy(
                        () ->
                                PostgreSQLMigrationNode.builder()
                                        .id("V001")
                                        .name("Test")
                                        .environment(environment)
                                        .upSql("   ")
                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("upSql must not be blank");
    }

    @Test
    void shouldImplementEqualsBasedOnId() {
        // given
        PostgreSQLMigrationNode node1 =
                PostgreSQLMigrationNode.builder()
                        .id("V001")
                        .name("Node 1")
                        .environment(environment)
                        .upSql("SELECT 1;")
                        .build();

        PostgreSQLMigrationNode node2 =
                PostgreSQLMigrationNode.builder()
                        .id("V001")
                        .name("Node 2")
                        .environment(environment)
                        .upSql("SELECT 2;")
                        .build();

        PostgreSQLMigrationNode node3 =
                PostgreSQLMigrationNode.builder()
                        .id("V002")
                        .name("Node 3")
                        .environment(environment)
                        .upSql("SELECT 3;")
                        .build();

        // then
        assertThat(node1).isEqualTo(node2);
        assertThat(node1).isNotEqualTo(node3);
        assertThat(node1.hashCode()).isEqualTo(node2.hashCode());
    }
}
