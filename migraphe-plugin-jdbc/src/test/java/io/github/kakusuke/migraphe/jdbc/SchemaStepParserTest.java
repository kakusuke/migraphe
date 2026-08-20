package io.github.kakusuke.migraphe.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SchemaStepParserTest {

    @Nested
    @DisplayName("マーカーの無いリソース（後方互換）")
    class WithoutMarkers {

        @Test
        @DisplayName("マーカーが無いファイルは無条件の1ステップになる")
        void singleUnconditionalStep() {
            String resource =
                    """
                    CREATE TABLE migraphe_history (id VARCHAR(64) PRIMARY KEY);
                    """;

            List<SchemaStep> steps = SchemaStepParser.parse(resource);

            assertThat(steps).hasSize(1);
            assertThat(steps.get(0).checkSql()).isNull();
            assertThat(steps.get(0).applySql())
                    .containsExactly("CREATE TABLE migraphe_history (id VARCHAR(64) PRIMARY KEY)");
        }

        @Test
        @DisplayName("マーカーが無いファイルの複数文はそれぞれ独立した適用SQLになる")
        void multipleStatementsAreSplit() {
            String resource =
                    """
                    CREATE TABLE t (id INT);
                    CREATE INDEX i ON t(id);
                    """;

            List<SchemaStep> steps = SchemaStepParser.parse(resource);

            assertThat(steps).hasSize(1);
            assertThat(steps.get(0).applySql())
                    .containsExactly("CREATE TABLE t (id INT)", "CREATE INDEX i ON t(id)");
        }
    }

    @Nested
    @DisplayName("マーカー付きリソース")
    class WithMarkers {

        @Test
        @DisplayName("check/apply の組をステップとして順に取り出す")
        void parsesStepsInOrder() {
            String resource =
                    """
                    --@check history table
                    SELECT 1 FROM information_schema.tables
                     WHERE UPPER(table_name) = 'MIGRAPHE_HISTORY';
                    --@apply
                    CREATE TABLE migraphe_history (id VARCHAR(64) PRIMARY KEY);
                    --@check checksum column
                    SELECT 1 FROM information_schema.columns
                     WHERE UPPER(column_name) = 'CHECKSUM';
                    --@apply
                    ALTER TABLE migraphe_history ADD COLUMN checksum VARCHAR(64);
                    """;

            List<SchemaStep> steps = SchemaStepParser.parse(resource);

            assertThat(steps).hasSize(2);
            assertThat(steps.get(0).label()).isEqualTo("history table");
            assertThat(steps.get(0).checkSql())
                    .isEqualTo(
                            "SELECT 1 FROM information_schema.tables\n"
                                    + " WHERE UPPER(table_name) = 'MIGRAPHE_HISTORY'");
            assertThat(steps.get(0).applySql())
                    .containsExactly("CREATE TABLE migraphe_history (id VARCHAR(64) PRIMARY KEY)");
            assertThat(steps.get(1).label()).isEqualTo("checksum column");
            assertThat(steps.get(1).applySql())
                    .containsExactly(
                            "ALTER TABLE migraphe_history ADD COLUMN checksum VARCHAR(64)");
        }

        @Test
        @DisplayName("先頭のヘッダコメントは無視される")
        void headerCommentsAreIgnored() {
            String resource =
                    """
                    -- Why this table looks the way it does.
                    -- Another line of rationale.

                    --@check history table
                    SELECT 1 FROM information_schema.tables;
                    --@apply
                    CREATE TABLE migraphe_history (id VARCHAR(64) PRIMARY KEY);
                    """;

            List<SchemaStep> steps = SchemaStepParser.parse(resource);

            assertThat(steps).hasSize(1);
            assertThat(steps.get(0).applySql())
                    .containsExactly("CREATE TABLE migraphe_history (id VARCHAR(64) PRIMARY KEY)");
        }

        @Test
        @DisplayName("ラベルを省略しても位置ベースの既定ラベルが付く")
        void defaultLabelWhenOmitted() {
            String resource =
                    """
                    --@check
                    SELECT 1 FROM information_schema.tables;
                    --@apply
                    CREATE TABLE t (id INT);
                    """;

            List<SchemaStep> steps = SchemaStepParser.parse(resource);

            assertThat(steps.get(0).label()).isEqualTo("step 1");
        }

        @Test
        @DisplayName("1つの apply に複数文を書ける")
        void multipleApplyStatements() {
            String resource =
                    """
                    --@check
                    SELECT 1 FROM information_schema.tables;
                    --@apply
                    CREATE TABLE t (id INT);
                    CREATE INDEX i ON t(id);
                    """;

            List<SchemaStep> steps = SchemaStepParser.parse(resource);

            assertThat(steps.get(0).applySql())
                    .containsExactly("CREATE TABLE t (id INT)", "CREATE INDEX i ON t(id)");
        }
    }

    @Nested
    @DisplayName("検出SQLを省略したステップ")
    class WithoutCheck {

        @Test
        @DisplayName("--@apply だけのブロックは検出SQLを持たない（常に適用する）ステップになる")
        void applyOnlyStep() {
            String resource =
                    """
                    --@apply history table
                    CREATE TABLE IF NOT EXISTS migraphe_history (id VARCHAR(64) PRIMARY KEY);
                    """;

            List<SchemaStep> steps = SchemaStepParser.parse(resource);

            assertThat(steps).hasSize(1);
            assertThat(steps.get(0).label()).isEqualTo("history table");
            assertThat(steps.get(0).checkSql()).isNull();
            assertThat(steps.get(0).applySql())
                    .containsExactly(
                            "CREATE TABLE IF NOT EXISTS migraphe_history (id VARCHAR(64) PRIMARY"
                                    + " KEY)");
        }

        @Test
        @DisplayName("--@apply が連続すると独立したステップになる")
        void consecutiveApplyBlocksBecomeSeparateSteps() {
            String resource =
                    """
                    --@apply table
                    CREATE TABLE IF NOT EXISTS t (id INT);
                    --@apply index
                    CREATE INDEX IF NOT EXISTS i ON t(id);
                    """;

            List<SchemaStep> steps = SchemaStepParser.parse(resource);

            assertThat(steps).hasSize(2);
            assertThat(steps).allSatisfy(step -> assertThat(step.checkSql()).isNull());
            assertThat(steps.get(0).applySql())
                    .containsExactly("CREATE TABLE IF NOT EXISTS t (id INT)");
            assertThat(steps.get(1).applySql())
                    .containsExactly("CREATE INDEX IF NOT EXISTS i ON t(id)");
        }

        @Test
        @DisplayName("検出SQL有りと無しのステップを同じリソースに混在できる")
        void mixesCheckedAndUncheckedSteps() {
            String resource =
                    """
                    --@apply history table
                    CREATE TABLE IF NOT EXISTS migraphe_history (id VARCHAR(64) PRIMARY KEY);
                    --@check checksum column
                    SELECT 1 FROM information_schema.columns
                     WHERE UPPER(column_name) = 'CHECKSUM';
                    --@apply
                    ALTER TABLE migraphe_history ADD COLUMN checksum VARCHAR(64);
                    """;

            List<SchemaStep> steps = SchemaStepParser.parse(resource);

            assertThat(steps).hasSize(2);
            assertThat(steps.get(0).checkSql()).isNull();
            assertThat(steps.get(1).label()).isEqualTo("checksum column");
            assertThat(steps.get(1).checkSql()).isNotNull();
        }

        @Test
        @DisplayName("ラベルを省略した --@apply には位置ベースの既定ラベルが付く")
        void defaultLabelForApplyOnlyStep() {
            String resource =
                    """
                    --@apply
                    CREATE TABLE IF NOT EXISTS t (id INT);
                    """;

            List<SchemaStep> steps = SchemaStepParser.parse(resource);

            assertThat(steps.get(0).label()).isEqualTo("step 1");
        }
    }

    @Nested
    @DisplayName("ラベル規則")
    class Labels {

        @Test
        @DisplayName("check 側だけがラベルを宣言していればそれを採る")
        void labelFromCheck() {
            String resource =
                    """
                    --@check A
                    SELECT 1 FROM information_schema.tables;
                    --@apply
                    CREATE TABLE t (id INT);
                    """;

            assertThat(SchemaStepParser.parse(resource).get(0).label()).isEqualTo("A");
        }

        @Test
        @DisplayName("apply 側だけがラベルを宣言していればそれを採る")
        void labelFromApply() {
            String resource =
                    """
                    --@check
                    SELECT 1 FROM information_schema.tables;
                    --@apply B
                    CREATE TABLE t (id INT);
                    """;

            assertThat(SchemaStepParser.parse(resource).get(0).label()).isEqualTo("B");
        }

        @Test
        @DisplayName("両方が同じラベルを宣言するのは冗長だが合法")
        void identicalLabelsOnBothDirectives() {
            String resource =
                    """
                    --@check A
                    SELECT 1 FROM information_schema.tables;
                    --@apply A
                    CREATE TABLE t (id INT);
                    """;

            assertThat(SchemaStepParser.parse(resource).get(0).label()).isEqualTo("A");
        }

        @Test
        @DisplayName("1つの組が相異なるラベルを2つ宣言すると失敗する")
        void conflictingLabelsAreRejected() {
            String resource =
                    """
                    --@check A
                    SELECT 1 FROM information_schema.tables;
                    --@apply B
                    CREATE TABLE t (id INT);
                    """;

            assertThatThrownBy(() -> SchemaStepParser.parse(resource))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("A")
                    .hasMessageContaining("B");
        }

        @Test
        @DisplayName("どちらも宣言しなければ位置ベースの既定ラベルになる")
        void positionalDefaultWhenNeitherDeclares() {
            String resource =
                    """
                    --@apply
                    CREATE TABLE t (id INT);
                    --@check
                    SELECT 1 FROM information_schema.tables;
                    --@apply
                    CREATE TABLE u (id INT);
                    """;

            List<SchemaStep> steps = SchemaStepParser.parse(resource);

            assertThat(steps).hasSize(2);
            assertThat(steps.get(0).label()).isEqualTo("step 1");
            assertThat(steps.get(1).label()).isEqualTo("step 2");
        }

        @Test
        @DisplayName("別々の組が同じラベルを持ってよい（一意性は要求しない）")
        void duplicateLabelsAcrossStepsAreAllowed() {
            // label is only ever interpolated into exception messages, so nothing downstream
            // depends on it being unique.
            String resource =
                    """
                    --@apply A
                    CREATE TABLE t (id INT);
                    --@apply A
                    CREATE TABLE u (id INT);
                    """;

            List<SchemaStep> steps = SchemaStepParser.parse(resource);

            assertThat(steps).hasSize(2);
            assertThat(steps).allSatisfy(step -> assertThat(step.label()).isEqualTo("A"));
        }
    }

    @Nested
    @DisplayName("不正なリソース")
    class InvalidResources {

        @Test
        @DisplayName("マーカーより前にSQLがあると失敗する（黙って捨てない）")
        void sqlBeforeFirstMarkerIsRejected() {
            String resource =
                    """
                    CREATE TABLE forgotten (id INT);
                    --@check
                    SELECT 1 FROM information_schema.tables;
                    --@apply
                    CREATE TABLE t (id INT);
                    """;

            assertThatThrownBy(() -> SchemaStepParser.parse(resource))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("--@check");
        }

        @Test
        @DisplayName("check に対応する apply が無いと失敗する")
        void checkWithoutApplyIsRejected() {
            String resource =
                    """
                    --@check history table
                    SELECT 1 FROM information_schema.tables;
                    """;

            assertThatThrownBy(() -> SchemaStepParser.parse(resource))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("history table");
        }

        @Test
        @DisplayName("check が複数文だと失敗する")
        void multiStatementCheckIsRejected() {
            String resource =
                    """
                    --@check history table
                    SELECT 1 FROM information_schema.tables;
                    SELECT 2 FROM information_schema.columns;
                    --@apply
                    CREATE TABLE t (id INT);
                    """;

            assertThatThrownBy(() -> SchemaStepParser.parse(resource))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("single");
        }

        @Test
        @DisplayName("check が連続すると、1つ目の組が apply を欠くものとして失敗する")
        void consecutiveChecksRejectTheFirstGroup() {
            // --@check always opens a new group, so the first one is closed unfinished. The label
            // in
            // the message identifies which group was left open.
            String resource =
                    """
                    --@check A
                    SELECT 1 FROM information_schema.tables;
                    --@check B
                    SELECT 2 FROM information_schema.tables;
                    --@apply
                    CREATE TABLE t (id INT);
                    """;

            assertThatThrownBy(() -> SchemaStepParser.parse(resource))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("A");
        }

        @Test
        @DisplayName("check の本文が空だと、検出SQLが0文として失敗する")
        void emptyCheckBodyIsRejected() {
            String resource =
                    """
                    --@check
                    --@apply
                    CREATE TABLE t (id INT);
                    """;

            assertThatThrownBy(() -> SchemaStepParser.parse(resource))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("single");
        }

        @Test
        @DisplayName("apply の本文が空だと失敗する")
        void emptyApplyBodyIsRejected() {
            String resource =
                    """
                    --@apply
                    --@apply
                    CREATE TABLE t (id INT);
                    """;

            assertThatThrownBy(() -> SchemaStepParser.parse(resource))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }
    }
}
