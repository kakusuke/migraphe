package io.github.kakusuke.migraphe.mysql.statement;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.jdbc.statement.StatementSplitter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MySqlGrammarTest {

    private final StatementSplitter splitter = MySqlGrammar.splitter();

    @Nested
    @DisplayName("クォート・コメント")
    class QuotesAndComments {

        @Test
        @DisplayName("バッククォート識別子内の ; では分割しない")
        void backtickIdentifier() {
            List<String> result = splitter.split("CREATE TABLE `a;b` (id int);\nSELECT 1;\n");
            assertThat(result).containsExactly("CREATE TABLE `a;b` (id int)", "SELECT 1");
        }

        @Test
        @DisplayName("# コメント内の ; は無視する")
        void hashComment() {
            List<String> result = splitter.split("SELECT 1; # drop; everything\nSELECT 2;\n");
            assertThat(result).containsExactly("SELECT 1", "SELECT 2");
        }

        @Test
        @DisplayName("-- は直後の空白を要求し、--x はコメントにならない")
        void dashCommentRequiresSpace() {
            List<String> result = splitter.split("SELECT 1 --x;\nSELECT 2;\n");
            assertThat(result).containsExactly("SELECT 1 --x", "SELECT 2");
        }

        @Test
        @DisplayName("-- (空白あり) はコメントになり ; を無視する")
        void dashCommentWithSpace() {
            List<String> result = splitter.split("SELECT 1; -- drop; everything\nSELECT 2;\n");
            assertThat(result).containsExactly("SELECT 1", "SELECT 2");
        }

        @Test
        @DisplayName("バックスラッシュエスケープ文字列内の ; は無視する")
        void backslashEscapedString() {
            List<String> result = splitter.split("INSERT INTO t VALUES ('a\\';b');\nSELECT 1;\n");
            assertThat(result).containsExactly("INSERT INTO t VALUES ('a\\';b')", "SELECT 1");
        }

        @Test
        @DisplayName("二重化エスケープ文字列内の ; は無視する")
        void doubledQuoteString() {
            List<String> result = splitter.split("INSERT INTO t VALUES ('a'';b');\nSELECT 1;\n");
            assertThat(result).containsExactly("INSERT INTO t VALUES ('a'';b')", "SELECT 1");
        }

        @Test
        @DisplayName("ブロックコメント内の ; は無視する")
        void blockComment() {
            List<String> result = splitter.split("SELECT 1 /* a;b */;\nSELECT 2;\n");
            assertThat(result).containsExactly("SELECT 1 /* a;b */", "SELECT 2");
        }
    }

    @Nested
    @DisplayName("再帰ブロック")
    class RecursiveBlocks {

        @Test
        @DisplayName("BEGIN...END 内の ; では分割しない")
        void beginEndIsSingleStatement() {
            String sql =
                    "CREATE PROCEDURE p() BEGIN INSERT INTO t VALUES(1); INSERT INTO t VALUES(2);"
                            + " END";
            List<String> result = splitter.split(sql);
            assertThat(result).containsExactly(sql);
        }

        @Test
        @DisplayName("IF...END IF を含む BEGIN...END は1文")
        void ifBlock() {
            String sql =
                    "CREATE PROCEDURE p() BEGIN IF x > 0 THEN SET y = 1; END IF; SET z = 2; END";
            List<String> result = splitter.split(sql);
            assertThat(result).containsExactly(sql);
        }

        @Test
        @DisplayName("LOOP...END LOOP を含むルーチンは1文")
        void loopBlock() {
            String sql =
                    "CREATE PROCEDURE p() BEGIN my_loop: LOOP SET x = x + 1; LEAVE my_loop; END"
                            + " LOOP; END";
            List<String> result = splitter.split(sql);
            assertThat(result).containsExactly(sql);
        }

        @Test
        @DisplayName("WHILE...END WHILE を含むルーチンは1文")
        void whileBlock() {
            String sql = "CREATE PROCEDURE p() BEGIN WHILE x > 0 DO SET x = x - 1; END WHILE; END";
            List<String> result = splitter.split(sql);
            assertThat(result).containsExactly(sql);
        }

        @Test
        @DisplayName("REPEAT...UNTIL...END REPEAT を含むルーチンは1文")
        void repeatBlock() {
            String sql =
                    "CREATE PROCEDURE p() BEGIN REPEAT SET x = x + 1; UNTIL x > 10 END REPEAT; END";
            List<String> result = splitter.split(sql);
            assertThat(result).containsExactly(sql);
        }

        @Test
        @DisplayName("CASE 文 (END CASE) を含むルーチンは1文")
        void caseStatementBlock() {
            String sql =
                    "CREATE PROCEDURE p() BEGIN CASE x WHEN 1 THEN SET y = 1; ELSE SET y = 0; END"
                            + " CASE; END";
            List<String> result = splitter.split(sql);
            assertThat(result).containsExactly(sql);
        }

        @Test
        @DisplayName("CASE 式 (END) を含むルーチンは1文")
        void caseExpressionBlock() {
            String sql =
                    "CREATE PROCEDURE p() BEGIN SET y = CASE WHEN x = 1 THEN 'a' ELSE 'b' END; END";
            List<String> result = splitter.split(sql);
            assertThat(result).containsExactly(sql);
        }

        @Test
        @DisplayName("ネストした BEGIN...END は1文")
        void nestedBeginEnd() {
            String sql =
                    "CREATE PROCEDURE p() BEGIN BEGIN INSERT INTO t VALUES(1); END; INSERT INTO t"
                            + " VALUES(2); END";
            List<String> result = splitter.split(sql);
            assertThat(result).containsExactly(sql);
        }

        @Test
        @DisplayName("ルーチン定義の後に通常文が続くと2文に分割する")
        void routineFollowedByStatement() {
            String sql = "CREATE PROCEDURE p() BEGIN INSERT INTO t VALUES(1); END;\nSELECT 1;\n";
            List<String> result = splitter.split(sql);
            assertThat(result)
                    .containsExactly(
                            "CREATE PROCEDURE p() BEGIN INSERT INTO t VALUES(1); END", "SELECT 1");
        }
    }

    @Nested
    @DisplayName("DELIMITER")
    class Delimiter {

        @Test
        @DisplayName("DELIMITER $$ で procedure を1文として扱い、行は出力しない")
        void dollarDelimiter() {
            String sql =
                    "DELIMITER $$\n"
                            + "CREATE PROCEDURE p() BEGIN INSERT INTO t VALUES(1); END$$\n"
                            + "DELIMITER ;\n";
            List<String> result = splitter.split(sql);
            assertThat(result)
                    .containsExactly("CREATE PROCEDURE p() BEGIN INSERT INTO t VALUES(1); END");
        }

        @Test
        @DisplayName("多文字デリミタ // が使える")
        void slashDelimiter() {
            String sql = "DELIMITER //\nCREATE PROCEDURE p() BEGIN SELECT 1; END//\nDELIMITER ;\n";
            List<String> result = splitter.split(sql);
            assertThat(result).containsExactly("CREATE PROCEDURE p() BEGIN SELECT 1; END");
        }

        @Test
        @DisplayName("DELIMITER 切替後に通常の ; 区切り文が続く")
        void delimiterSwitchThenNormalStatements() {
            String sql =
                    "DELIMITER $$\n"
                            + "CREATE PROCEDURE p() BEGIN SELECT 1; END$$\n"
                            + "DELIMITER ;\n"
                            + "SELECT 2;\n"
                            + "SELECT 3;\n";
            List<String> result = splitter.split(sql);
            assertThat(result)
                    .containsExactly(
                            "CREATE PROCEDURE p() BEGIN SELECT 1; END", "SELECT 2", "SELECT 3");
        }
    }

    @Nested
    @DisplayName("標準領域の継承")
    class StandardRegions {

        @Test
        @DisplayName("文字列内の ; は無視する")
        void semicolonInStringLiteral() {
            List<String> result = splitter.split("INSERT INTO t VALUES ('a;b');\nSELECT 1;\n");
            assertThat(result).containsExactly("INSERT INTO t VALUES ('a;b')", "SELECT 1");
        }

        @Test
        @DisplayName("複数の通常文を分割する")
        void multipleStatements() {
            List<String> result =
                    splitter.split("CREATE TABLE t1 (id INT);\nCREATE TABLE t2 (id INT);\n");
            assertThat(result)
                    .containsExactly("CREATE TABLE t1 (id INT)", "CREATE TABLE t2 (id INT)");
        }
    }

    @Nested
    @DisplayName("実践的なマイグレーションSQL")
    class RealWorldMigrations {

        @Test
        @DisplayName("CREATE PROCEDURE BEGIN...END (内部 IF/複数文) は1文")
        void createProcedure() {
            String sql =
                    "CREATE PROCEDURE add_user(IN nm VARCHAR(100))\n"
                            + "BEGIN\n"
                            + "  IF nm IS NOT NULL THEN\n"
                            + "    INSERT INTO users(name) VALUES(nm);\n"
                            + "  END IF;\n"
                            + "  INSERT INTO audit(msg) VALUES('added');\n"
                            + "END";
            assertThat(splitter.split(sql)).containsExactly(sql);
        }

        @Test
        @DisplayName("CREATE FUNCTION BEGIN...END (DETERMINISTIC) は1文")
        void createFunction() {
            String sql =
                    "CREATE FUNCTION inc(x INT) RETURNS INT DETERMINISTIC\n"
                            + "BEGIN\n"
                            + "  DECLARE r INT;\n"
                            + "  SET r = x + 1;\n"
                            + "  RETURN r;\n"
                            + "END";
            assertThat(splitter.split(sql)).containsExactly(sql);
        }

        @Test
        @DisplayName("CREATE TRIGGER BEGIN...END は1文")
        void createTrigger() {
            String sql =
                    "CREATE TRIGGER trg_before_ins BEFORE INSERT ON users FOR EACH ROW\n"
                            + "BEGIN\n"
                            + "  SET NEW.created_at = NOW();\n"
                            + "  SET NEW.updated_at = NOW();\n"
                            + "END";
            assertThat(splitter.split(sql)).containsExactly(sql);
        }

        @Test
        @DisplayName("CREATE EVENT BEGIN...END は1文")
        void createEvent() {
            String sql =
                    "CREATE EVENT purge_old ON SCHEDULE EVERY 1 DAY DO\n"
                            + "BEGIN\n"
                            + "  DELETE FROM sessions WHERE expired_at < NOW();\n"
                            + "  DELETE FROM tokens WHERE expired_at < NOW();\n"
                            + "END";
            assertThat(splitter.split(sql)).containsExactly(sql);
        }

        @Test
        @DisplayName("CASE/LOOP/WHILE/REPEAT を全て含む深いネストブロックは1文")
        void deeplyNestedBlocks() {
            String sql =
                    "CREATE PROCEDURE complex()\n"
                            + "BEGIN\n"
                            + "  DECLARE i INT DEFAULT 0;\n"
                            + "  my_loop: LOOP\n"
                            + "    SET i = i + 1;\n"
                            + "    CASE i\n"
                            + "      WHEN 1 THEN SET @a = 1;\n"
                            + "      ELSE SET @a = 0;\n"
                            + "    END CASE;\n"
                            + "    WHILE i < 3 DO SET i = i + 1; END WHILE;\n"
                            + "    REPEAT SET i = i + 1; UNTIL i > 5 END REPEAT;\n"
                            + "    IF i > 10 THEN LEAVE my_loop; END IF;\n"
                            + "  END LOOP;\n"
                            + "END";
            assertThat(splitter.split(sql)).containsExactly(sql);
        }

        @Test
        @DisplayName("DELIMITER $$ ... END$$ DELIMITER ; スクリプトを1文として扱う")
        void delimiterDollarScript() {
            String sql =
                    "DELIMITER $$\n"
                            + "CREATE PROCEDURE p()\n"
                            + "BEGIN\n"
                            + "  INSERT INTO t VALUES(1);\n"
                            + "  INSERT INTO t VALUES(2);\n"
                            + "END$$\n"
                            + "DELIMITER ;\n";
            List<String> result = splitter.split(sql);
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).startsWith("CREATE PROCEDURE p()").endsWith("END");
        }

        @Test
        @DisplayName("// デリミタのスクリプトで複数ルーチンを分割")
        void slashDelimiterMultipleRoutines() {
            String sql =
                    "DELIMITER //\n"
                            + "CREATE PROCEDURE p1() BEGIN SELECT 1; END//\n"
                            + "CREATE PROCEDURE p2() BEGIN SELECT 2; END//\n"
                            + "DELIMITER ;\n";
            List<String> result = splitter.split(sql);
            assertThat(result)
                    .containsExactly(
                            "CREATE PROCEDURE p1() BEGIN SELECT 1; END",
                            "CREATE PROCEDURE p2() BEGIN SELECT 2; END");
        }

        @Test
        @DisplayName("バッククォート識別子内の ;, # / -- コメント, \\' / '' エスケープが混在しても正しく分割")
        void mixedQuotingAndComments() {
            String sql =
                    "CREATE TABLE `weird;name` (id INT); # trailing; comment\n"
                            + "INSERT INTO `weird;name` VALUES (1); -- a; comment\n"
                            + "INSERT INTO `weird;name` (note) VALUES ('it\\'s; ok');\n"
                            + "INSERT INTO `weird;name` (note) VALUES ('two'';semis');\n";
            List<String> result = splitter.split(sql);
            assertThat(result).hasSize(4);
            assertThat(result.get(0)).isEqualTo("CREATE TABLE `weird;name` (id INT)");
            assertThat(result.get(2)).contains("'it\\'s; ok'");
            assertThat(result.get(3)).contains("'two'';semis'");
        }

        @Test
        @DisplayName("複数の CREATE TABLE / INSERT を分割")
        void multipleCreateAndInsert() {
            String sql =
                    "CREATE TABLE a (id INT);\n"
                            + "CREATE TABLE b (id INT);\n"
                            + "INSERT INTO a VALUES (1);\n"
                            + "INSERT INTO b VALUES (2);\n";
            assertThat(splitter.split(sql))
                    .containsExactly(
                            "CREATE TABLE a (id INT)",
                            "CREATE TABLE b (id INT)",
                            "INSERT INTO a VALUES (1)",
                            "INSERT INTO b VALUES (2)");
        }
    }

    @Nested
    @DisplayName("サンプルCLIタスクの回帰")
    class SampleTaskRegression {

        @Test
        @DisplayName("sample mysql/02_catalog/003_products の up (インラインCOMMENT付きCREATE TABLE) は1文")
        void productsTableUp() {
            String sql =
                    "CREATE TABLE products (\n"
                        + "  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Product surrogate"
                        + " key',\n"
                        + "  sku VARCHAR(64) NOT NULL UNIQUE COMMENT 'Unique product-level SKU',\n"
                        + "  name VARCHAR(255) NOT NULL COMMENT 'Product display name',\n"
                        + "  category_id BIGINT NOT NULL COMMENT 'Category the product belongs"
                        + " to',\n"
                        + "  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES"
                        + " categories (id)\n"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Product catalog"
                        + " entries';\n";
            List<String> result = splitter.split(sql);
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).startsWith("CREATE TABLE products");
            assertThat(result.get(0)).endsWith("'Product catalog entries'");
        }
    }
}
