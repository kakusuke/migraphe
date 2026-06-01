package io.github.kakusuke.migraphe.postgresql.statement;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.jdbc.statement.SqlParser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PostgreSqlGrammarTest {

    @Nested
    @DisplayName("dollarQuoted")
    class DollarQuoted {

        private final SqlParser parser = PostgreSqlGrammar.dollarQuoted();

        @Test
        @DisplayName("空タグ $$...$$ を1領域として消費する")
        void emptyTag() {
            String sql = "$$abc$$rest";
            int end = parser.parse(sql, 0);
            assertThat(end).isEqualTo("$$abc$$".length());
        }

        @Test
        @DisplayName("タグ付き $tag$...$tag$ を消費する")
        void taggedBody() {
            String sql = "$tag$body$tag$rest";
            int end = parser.parse(sql, 0);
            assertThat(end).isEqualTo("$tag$body$tag$".length());
        }

        @Test
        @DisplayName("内部に ; と改行を含んでも閉じタグまで消費する")
        void containsSemicolonAndNewline() {
            String sql = "$$ a; \n b; $$rest";
            int end = parser.parse(sql, 0);
            assertThat(end).isEqualTo("$$ a; \n b; $$".length());
        }

        @Test
        @DisplayName("内部に異なるドルタグ風の文字列があっても同一タグまで消費する")
        void nestedDifferentTag() {
            String sql = "$outer$ $inner$ x; $inner$ $outer$rest";
            int end = parser.parse(sql, 0);
            assertThat(end).isEqualTo("$outer$ $inner$ x; $inner$ $outer$".length());
        }

        @Test
        @DisplayName("閉じタグが無ければ終端まで消費する")
        void unterminated() {
            String sql = "$$ unterminated body";
            int end = parser.parse(sql, 0);
            assertThat(end).isEqualTo(sql.length());
        }

        @Test
        @DisplayName("$1 のようなパラメータは非ドルタグなので -1")
        void parameterPlaceholderIsNotDollarTag() {
            String sql = "$1 + $2";
            int end = parser.parse(sql, 0);
            assertThat(end).isEqualTo(-1);
        }

        @Test
        @DisplayName("$ 以外で始まれば -1")
        void notDollarStart() {
            assertThat(parser.parse("abc", 0)).isEqualTo(-1);
        }

        @Test
        @DisplayName("単独の $ で終端なら -1")
        void loneDollarAtEnd() {
            assertThat(parser.parse("$", 0)).isEqualTo(-1);
        }

        @Test
        @DisplayName("アンダースコア始まりのタグを扱える")
        void underscoreTag() {
            String sql = "$_t1$ body $_t1$rest";
            int end = parser.parse(sql, 0);
            assertThat(end).isEqualTo("$_t1$ body $_t1$".length());
        }
    }

    @Nested
    @DisplayName("splitter")
    class Splitter {

        private List<String> split(String sql) {
            return PostgreSqlGrammar.splitter().split(sql);
        }

        @Test
        @DisplayName("DO $$ ... $$ は内部の ; で割れず1文")
        void doBlockStaysSingle() {
            String sql = "DO $$ BEGIN PERFORM 1; END $$ LANGUAGE plpgsql;";
            assertThat(split(sql))
                    .containsExactly("DO $$ BEGIN PERFORM 1; END $$ LANGUAGE plpgsql");
        }

        @Test
        @DisplayName("CREATE FUNCTION ... AS $$ ... $$ は1文")
        void createFunctionStaysSingle() {
            String sql = "CREATE FUNCTION f() RETURNS int AS $$ SELECT 1; $$ LANGUAGE sql;";
            assertThat(split(sql))
                    .containsExactly(
                            "CREATE FUNCTION f() RETURNS int AS $$ SELECT 1; $$ LANGUAGE sql");
        }

        @Test
        @DisplayName("タグ付き本体 $tag$ ... ; ... $tag$ も1文")
        void taggedFunctionStaysSingle() {
            String sql = "CREATE FUNCTION g() RETURNS int AS $body$ SELECT 2; $body$ LANGUAGE sql;";
            assertThat(split(sql))
                    .containsExactly(
                            "CREATE FUNCTION g() RETURNS int AS $body$ SELECT 2; $body$ LANGUAGE"
                                    + " sql");
        }

        @Test
        @DisplayName("トランザクション制御は3文に分割される")
        void transactionControlSplits() {
            String sql = "BEGIN;\nCREATE TABLE t (id int);\nCOMMIT;\n";
            assertThat(split(sql)).containsExactly("BEGIN", "CREATE TABLE t (id int)", "COMMIT");
        }

        @Test
        @DisplayName("COMMENT ON と CREATE TABLE の混在を分割")
        void commentOnAndCreateMix() {
            String sql =
                    "CREATE TABLE t (id int);\n"
                            + "COMMENT ON TABLE t IS 'a table';\n"
                            + "COMMENT ON COLUMN t.id IS 'id col';\n";
            assertThat(split(sql))
                    .containsExactly(
                            "CREATE TABLE t (id int)",
                            "COMMENT ON TABLE t IS 'a table'",
                            "COMMENT ON COLUMN t.id IS 'id col'");
        }

        @Test
        @DisplayName("文字列内の ; は無視される")
        void semicolonInStringIgnored() {
            String sql = "INSERT INTO t VALUES ('a;b'); SELECT 1;";
            assertThat(split(sql)).containsExactly("INSERT INTO t VALUES ('a;b')", "SELECT 1");
        }

        @Test
        @DisplayName("行コメント内の ; は無視される")
        void semicolonInLineCommentIgnored() {
            String sql = "SELECT 1; -- a; b\nSELECT 2;";
            assertThat(split(sql)).containsExactly("SELECT 1", "SELECT 2");
        }

        @Test
        @DisplayName("ブロックコメント内の ; は無視される")
        void semicolonInBlockCommentIgnored() {
            String sql = "SELECT 1 /* a; b */; SELECT 2;";
            assertThat(split(sql)).containsExactly("SELECT 1 /* a; b */", "SELECT 2");
        }
    }

    @Nested
    @DisplayName("実践的なマイグレーションSQL")
    class RealWorldMigrations {

        private List<String> split(String sql) {
            return PostgreSqlGrammar.splitter().split(sql);
        }

        @Test
        @DisplayName("トリガー関数 ($$本体) + CREATE TRIGGER は2文に分割され、本体内の ; で割れない")
        void triggerFunctionAndTrigger() {
            String sql =
                    "CREATE FUNCTION trg() RETURNS trigger AS $$\n"
                            + "BEGIN\n"
                            + "  NEW.updated_at := now();\n"
                            + "  RETURN NEW;\n"
                            + "END;\n"
                            + "$$ LANGUAGE plpgsql;\n"
                            + "CREATE TRIGGER set_updated_at BEFORE UPDATE ON users\n"
                            + "  FOR EACH ROW EXECUTE FUNCTION trg();\n";
            List<String> result = split(sql);
            assertThat(result).hasSize(2);
            assertThat(result.get(0))
                    .startsWith("CREATE FUNCTION trg()")
                    .endsWith("LANGUAGE plpgsql");
            assertThat(result.get(0)).contains("NEW.updated_at := now();");
            assertThat(result.get(0)).contains("RETURN NEW;");
            assertThat(result.get(1)).startsWith("CREATE TRIGGER set_updated_at");
            assertThat(result.get(1)).endsWith("EXECUTE FUNCTION trg()");
        }

        @Test
        @DisplayName("$tag$ 付き本体で内部に $$ 風の文字列があっても1文")
        void taggedBodyContainingDollarDollar() {
            String sql =
                    "CREATE FUNCTION f() RETURNS text AS $func$\n"
                            + "BEGIN\n"
                            + "  RETURN 'literal with $$ inside; not a tag';\n"
                            + "END;\n"
                            + "$func$ LANGUAGE plpgsql;\n";
            List<String> result = split(sql);
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).contains("$$ inside; not a tag");
        }

        @Test
        @DisplayName("'...' 本体の SQL 関数 (AS 'SELECT 1') は1文")
        void singleQuotedFunctionBody() {
            String sql = "CREATE FUNCTION one() RETURNS int AS 'SELECT 1' LANGUAGE sql;";
            List<String> result = split(sql);
            assertThat(result)
                    .containsExactly(
                            "CREATE FUNCTION one() RETURNS int AS 'SELECT 1' LANGUAGE sql");
        }

        @Test
        @DisplayName("複数の COMMENT ON と CREATE TABLE 混在を正しく分割")
        void multipleCommentsAndCreateTable() {
            String sql =
                    "CREATE TABLE users (id bigserial PRIMARY KEY, email varchar(255) NOT NULL);\n"
                            + "COMMENT ON TABLE users IS 'Registered accounts';\n"
                            + "COMMENT ON COLUMN users.id IS 'surrogate; key';\n"
                            + "COMMENT ON COLUMN users.email IS 'login email';\n";
            List<String> result = split(sql);
            assertThat(result).hasSize(4);
            assertThat(result.get(0)).startsWith("CREATE TABLE users");
            assertThat(result.get(1)).isEqualTo("COMMENT ON TABLE users IS 'Registered accounts'");
            assertThat(result.get(2)).isEqualTo("COMMENT ON COLUMN users.id IS 'surrogate; key'");
            assertThat(result.get(3)).isEqualTo("COMMENT ON COLUMN users.email IS 'login email'");
        }

        @Test
        @DisplayName("BEGIN;/COMMIT; とその間の文が独立分割される")
        void explicitTransactionControl() {
            String sql =
                    "BEGIN;\n"
                            + "CREATE TABLE t (id int);\n"
                            + "INSERT INTO t VALUES (1);\n"
                            + "COMMIT;\n";
            List<String> result = split(sql);
            assertThat(result)
                    .containsExactly(
                            "BEGIN",
                            "CREATE TABLE t (id int)",
                            "INSERT INTO t VALUES (1)",
                            "COMMIT");
        }

        @Test
        @DisplayName("行/ブロックコメントと文字列内 ; が混在しても正しく分割")
        void mixedCommentsAndStringSemicolons() {
            String sql =
                    "-- create the table\n"
                            + "CREATE TABLE t (id int); /* trailing; comment */\n"
                            + "INSERT INTO t VALUES (1); -- value; here\n"
                            + "INSERT INTO t SELECT 2 WHERE 'a;b' <> 'c;d';\n";
            List<String> result = split(sql);
            assertThat(result).hasSize(3);
            assertThat(result.get(0)).startsWith("CREATE TABLE t (id int)");
            assertThat(result.get(1)).startsWith("INSERT INTO t VALUES (1)");
            assertThat(result.get(2)).contains("'a;b' <> 'c;d'");
        }

        @Test
        @DisplayName("DO $$ ... PERFORM ...; ... END $$ は1文")
        void doBlockWithInnerStatements() {
            String sql =
                    "DO $$\n"
                            + "BEGIN\n"
                            + "  PERFORM 1;\n"
                            + "  IF FALSE THEN RAISE NOTICE 'never'; END IF;\n"
                            + "END\n"
                            + "$$ LANGUAGE plpgsql;\n";
            List<String> result = split(sql);
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).startsWith("DO $$").endsWith("LANGUAGE plpgsql");
        }
    }

    @Nested
    @DisplayName("サンプルCLIタスクの回帰")
    class SampleTaskRegression {

        private List<String> split(String sql) {
            return PostgreSqlGrammar.splitter().split(sql);
        }

        @Test
        @DisplayName("sample pg/02_users/001_users の up (CREATE TABLE + 複数 COMMENT ON) を妥当な文数に分割")
        void usersTableUp() {
            String sql =
                    "CREATE TABLE users (\n"
                            + "  id BIGSERIAL PRIMARY KEY,\n"
                            + "  email VARCHAR(255) NOT NULL UNIQUE,\n"
                            + "  status VARCHAR(20) NOT NULL DEFAULT 'active',\n"
                            + "  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),\n"
                            + "  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()\n"
                            + ");\n"
                            + "COMMENT ON TABLE users IS 'Registered customer accounts';\n"
                            + "COMMENT ON COLUMN users.id IS 'User surrogate key';\n"
                            + "COMMENT ON COLUMN users.email IS 'Unique login email address';\n"
                            + "COMMENT ON COLUMN users.status IS 'Account lifecycle state';\n";
            List<String> result = split(sql);
            // 1 CREATE TABLE + 4 COMMENT ON
            assertThat(result).hasSize(5);
            assertThat(result.get(0)).startsWith("CREATE TABLE users");
            assertThat(result).filteredOn(s -> s.startsWith("COMMENT ON")).hasSize(4);
        }
    }
}
