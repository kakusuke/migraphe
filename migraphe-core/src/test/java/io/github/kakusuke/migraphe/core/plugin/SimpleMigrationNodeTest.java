package io.github.kakusuke.migraphe.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.api.graph.Fingerprinter;
import io.github.kakusuke.migraphe.api.graph.MigrationNode;
import io.github.kakusuke.migraphe.api.graph.NodeId;
import io.github.kakusuke.migraphe.api.target.Target;
import io.github.kakusuke.migraphe.api.target.TargetId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimpleMigrationNodeTest {

    private Target target;

    @BeforeEach
    void setUp() {
        target = SimpleTarget.create(TargetId.of("test"), "Test");
    }

    @Test
    void fingerprintHandsOverOneSignatureWhenThereIsNoRollback() {
        var recorder = new RecordingFingerprinter();
        node("CREATE TABLE t", null).fingerprint(recorder);

        assertThat(recorder.signatures).containsExactly("14:CREATE TABLE t");
    }

    @Test
    void fingerprintHandsOverTheUpSignatureThenTheDownOne() {
        var recorder = new RecordingFingerprinter();
        node("CREATE TABLE t", "DROP TABLE t").fingerprint(recorder);

        assertThat(recorder.signatures).containsExactly("14:CREATE TABLE t", "12:DROP TABLE t");
    }

    @Test
    void fingerprintTellsAnAbsentRollbackFromABlankedOneByHowManySignaturesItHandsOver() {
        var forAbsent = new RecordingFingerprinter();
        node("CREATE TABLE t", null).fingerprint(forAbsent);
        var forBlanked = new RecordingFingerprinter();
        node("CREATE TABLE t", "").fingerprint(forBlanked);

        assertThat(forAbsent.signatures).hasSize(1);
        assertThat(forBlanked.signatures).containsExactly("14:CREATE TABLE t", "0:");
    }

    private MigrationNode node(String up, String down) {
        SimpleMigrationNode.Builder builder =
                SimpleMigrationNode.builder()
                        .id(NodeId.of("n"))
                        .name("N")
                        .target(target)
                        .upTask(SimpleTask.of(up));
        if (down != null) {
            builder.downTask(SimpleTask.withDownTask(down, down));
        }
        return builder.build();
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
}
