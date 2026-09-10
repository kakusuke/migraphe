package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kakusuke.migraphe.api.graph.Fingerprinter;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.task.SqlContentProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JdbcMigrationNodeTest {

    private final JdbcTarget target =
            JdbcTarget.create("testdb", "jdbc:h2:mem:node_test", "sa", "", "org.h2.Driver", "H2");

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
    void fingerprintHandsOverOneSignatureWhenThereIsNoRollback() {
        var node = nodeBuilder().upSql("CREATE TABLE users (id INT);\n").build();

        var recorder = new RecordingFingerprinter();
        node.fingerprint(recorder);

        assertThat(recorder.signatures).containsExactly("28:CREATE TABLE users (id INT);1:f");
    }

    @Test
    void fingerprintHandsOverTheUpSignatureThenTheDownOne() {
        var node =
                nodeBuilder()
                        .upSql("CREATE TABLE users (id INT);")
                        .downSql("  DROP TABLE users;\n")
                        .build();

        var recorder = new RecordingFingerprinter();
        node.fingerprint(recorder);

        assertThat(recorder.signatures)
                .containsExactly("28:CREATE TABLE users (id INT);1:f", "17:DROP TABLE users;1:f");
    }

    @Test
    void fingerprintRefusesToSignARollbackThatCannotBeRun() {
        var blanked = nodeBuilder().upSql("CREATE TABLE users (id INT);").downSql("").build();

        assertThatThrownBy(() -> blanked.fingerprint(new RecordingFingerprinter()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("downSql must not be blank");
    }

    @Test
    void fingerprintCarriesEachDirectionsAutocommitInThatDirectionsSignature() {
        var node =
                nodeBuilder()
                        .upSql("CREATE TABLE users (id INT);")
                        .downSql("DROP TABLE users;")
                        .autocommitUp(true)
                        .autocommitDown(false)
                        .build();

        var recorder = new RecordingFingerprinter();
        node.fingerprint(recorder);

        assertThat(recorder.signatures)
                .containsExactly("28:CREATE TABLE users (id INT);1:t", "17:DROP TABLE users;1:f");
    }

    @Test
    void fingerprintKeepsWhatIsInsideTheSqlIncludingCommentsAndIndentation() {
        assertThat(upSignatureOf("-- create users\nCREATE TABLE users (id INT);"))
                .isNotEqualTo(upSignatureOf("CREATE TABLE users (id INT);"));
        assertThat(upSignatureOf("CREATE TABLE users (\n    id INT\n);"))
                .isNotEqualTo(upSignatureOf("CREATE TABLE users (\nid INT\n);"));
        assertThat(upSignatureOf("CREATE TABLE users (\r\nid INT\r\n);"))
                .isNotEqualTo(upSignatureOf("CREATE TABLE users (\nid INT\n);"));
    }

    private String upSignatureOf(String upSql) {
        var recorder = new RecordingFingerprinter();
        nodeBuilder().upSql(upSql).build().fingerprint(recorder);
        return recorder.signatures.get(0);
    }

    /** Captures what a node hands over, which is the node's whole contribution. */
    private static final class RecordingFingerprinter implements Fingerprinter {
        private final List<String> signatures = new ArrayList<>();

        @Override
        public String over(String... signatures) {
            this.signatures.addAll(List.of(signatures));
            return "recorded";
        }
    }

    @Test
    void upTaskReturnsJdbcUpTask() {
        var node =
                JdbcMigrationNode.builder()
                        .id("node1")
                        .name("Create table")
                        .target(target)
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
                        .target(target)
                        .upSql("CREATE TABLE t1 (id INT)")
                        .downSql("DROP TABLE t1")
                        .build();
        assertThat(node.downTask()).isInstanceOf(JdbcDownTask.class);
    }

    @Test
    void aNodeDeclaredOneWayCarriesItsReasonAndHasNoDownTask() {
        var node =
                JdbcMigrationNode.builder()
                        .id("node1")
                        .name("Drop legacy column")
                        .target(target)
                        .upSql("ALTER TABLE users DROP COLUMN legacy")
                        .noWayBack("DROP COLUMN discards the data")
                        .build();

        assertThat(node.noWayBack()).isEqualTo("DROP COLUMN discards the data");
        assertThat(node.downTask()).isNull();
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

    /** Builder pre-filled with the identity fields the fingerprint deliberately ignores. */
    private JdbcMigrationNode.Builder nodeBuilder() {
        return JdbcMigrationNode.builder().id("node1").name("Create table").target(target);
    }
}
