package io.github.kakusuke.migraphe.postgresql.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.jdbc.markdown.JdbcMarkdownDefinition;
import io.github.kakusuke.migraphe.postgresql.PostgreSQLEnvironment;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLSchemaInfo;
import io.github.kakusuke.migraphe.postgresql.schema.PostgreSQLSchemaInfoProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * End-to-end integration test that exercises schema-document generation against a real PostgreSQL
 * database via Testcontainers: real schema extraction → Markdown generation → generated-output
 * verification, in a single flow.
 *
 * <p>Verifies the following behaviors with the real PostgreSQL JDBC driver:
 *
 * <ol>
 *   <li>Enum column types render with their base name (no collapsed {@code _..._}, no sentinel size
 *       {@code (2147483647)}, no {@code "schema"."type"}).
 *   <li>ER-diagram entity IDs are schema-qualified plus a hash, so same-named tables in different
 *       schemas do not collide.
 *   <li>Cross-schema foreign-key relationships are drawn in the ER diagram.
 *   <li>Columns that are both PK and FK are marked {@code PK, FK}.
 *   <li>Per-table documents link cross-schema FKs as {@code ../../<refSchema>/tables/<t>.md}.
 * </ol>
 */
@Testcontainers
class PostgreSQLSchemaDocE2ETest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine")
                    .waitingFor(new HostPortWaitStrategy().forPorts(5432));

    @Test
    void generatesSchemaDocFromRealPostgreSQL(@TempDir Path tempDir) throws Exception {
        // Idempotent reset before building fixtures.
        executeSql("DROP SCHEMA IF EXISTS account CASCADE");
        executeSql("DROP SCHEMA IF EXISTS sales CASCADE");

        executeSql("CREATE SCHEMA account");
        executeSql("CREATE SCHEMA sales");
        executeSql("CREATE TYPE account.user_account_status AS ENUM ('active','suspended')");
        executeSql(
                "CREATE TABLE account.user_accounts (id bigserial PRIMARY KEY, status"
                        + " account.user_account_status NOT NULL DEFAULT 'active')");
        // Same table name in a different schema — cross-schema collision fixture.
        executeSql("CREATE TABLE sales.user_accounts (id bigserial PRIMARY KEY)");
        // Cross-schema foreign key.
        executeSql(
                "CREATE TABLE sales.orders (id bigserial PRIMARY KEY, user_id bigint NOT NULL"
                        + " REFERENCES account.user_accounts(id))");
        // PK that is also an FK.
        executeSql(
                "CREATE TABLE sales.order_items (order_id bigint PRIMARY KEY REFERENCES"
                        + " sales.orders(id))");

        PostgreSQLSchemaInfo info = new PostgreSQLSchemaInfoProvider().getSchemaInfo(createEnv());
        new PostgreSQLMarkdownGenerator(
                        "maindb", info, List.<JdbcMarkdownDefinition.ExcludePattern>of())
                .generate(tempDir);
        String index = Files.readString(tempDir.resolve("index.md"));

        // (1) Enum base name in ER diagram, no collapsed/sentinel/quoted artifacts.
        assertThat(index).contains("user_account_status status");
        assertThat(index).doesNotContain("_account___user_account_status_");
        assertThat(index).doesNotContain("(2147483647)");
        assertThat(index).doesNotContain("\"account\".\"user_account_status\"");

        // (2) Same-named tables in different schemas do not collide: two entities, distinct IDs.
        int userAccountsEntityCount = index.split("\\[\"user_accounts\"\\]", -1).length - 1;
        assertThat(userAccountsEntityCount).isGreaterThanOrEqualTo(2);
        assertThat(index).contains("account_user_accounts_");
        assertThat(index).contains("sales_user_accounts_");

        // (3) Cross-schema relationship: sales.orders → account.user_accounts.
        assertThat(index).contains(" ||--o{ ");
        boolean crossSchemaRelationLine =
                index.lines()
                        .anyMatch(
                                line ->
                                        line.contains("||--o{")
                                                && line.contains("account_user_accounts_")
                                                && line.contains("sales_orders_"));
        assertThat(crossSchemaRelationLine)
                .as("an ER relationship line links sales.orders to account.user_accounts")
                .isTrue();

        // (4) PK + FK column marked "PK, FK".
        assertThat(index).contains("order_id PK, FK");

        // Per-table documents.
        String accountUserAccounts =
                Files.readString(tempDir.resolve("maindb/account/tables/user_accounts.md"));
        assertThat(accountUserAccounts).contains("user_account_status");
        assertThat(accountUserAccounts).doesNotContain("(2147483647)");
        assertThat(accountUserAccounts).doesNotContain("\"account\".\"user_account_status\"");

        // (5) Cross-schema FK link in the per-table document.
        String salesOrders = Files.readString(tempDir.resolve("maindb/sales/tables/orders.md"));
        assertThat(salesOrders).contains("[user_accounts](../../account/tables/user_accounts.md)");
    }

    private PostgreSQLEnvironment createEnv() {
        return PostgreSQLEnvironment.create(
                "test", postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private void executeSql(String sql) throws Exception {
        try (var conn = createEnv().createConnection();
                var stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
