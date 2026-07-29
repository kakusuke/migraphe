package io.github.kakusuke.migraphe.jdbc.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kakusuke.migraphe.jdbc.JdbcEnvironment;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcSchemaInfoProviderTest {

    private JdbcEnvironment env;
    private JdbcSchemaInfoProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        env =
                JdbcEnvironment.create(
                        "schema_info_test",
                        "jdbc:h2:mem:schema_info_test;DB_CLOSE_DELAY=-1",
                        "sa",
                        "",
                        "org.h2.Driver",
                        "H2");
        try (Connection conn = env.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP VIEW IF EXISTS active_users");
            stmt.execute("DROP TABLE IF EXISTS orders");
            stmt.execute("DROP TABLE IF EXISTS users");
            stmt.execute(
                    "CREATE TABLE users ("
                            + "id INTEGER PRIMARY KEY, "
                            + "name VARCHAR(100) NOT NULL, "
                            + "email VARCHAR(200) DEFAULT 'unknown'"
                            + ")");
            stmt.execute(
                    "CREATE TABLE orders ("
                            + "id INTEGER PRIMARY KEY, "
                            + "user_id INTEGER NOT NULL, "
                            + "amount DECIMAL(10,2), "
                            + "CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)"
                            + ")");
            stmt.execute("CREATE INDEX idx_orders_user_id ON orders(user_id)");
            stmt.execute(
                    "CREATE VIEW active_users AS SELECT id, name FROM users WHERE name IS NOT"
                            + " NULL");
        }
        provider = new JdbcSchemaInfoProvider();
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = env.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP VIEW IF EXISTS active_users");
            stmt.execute("DROP TABLE IF EXISTS orders");
            stmt.execute("DROP TABLE IF EXISTS users");
        }
    }

    @Test
    void getSchemaInfoReturnsTablesWithColumns() {
        JdbcSchemaInfo schemaInfo = provider.getSchemaInfo(env);

        assertThat(schemaInfo.schemas()).isNotEmpty();
        JdbcSchemaDetail schema = schemaInfo.schemas().get(0);
        JdbcTableInfo usersTable =
                schema.tables().stream()
                        .filter(t -> t.name().equalsIgnoreCase("users"))
                        .findFirst()
                        .orElseThrow();
        assertThat(usersTable.columns()).hasSize(3);
        assertThat(usersTable.columns())
                .extracting(JdbcColumnInfo::name)
                .map(String::toUpperCase)
                .containsExactlyInAnyOrder("ID", "NAME", "EMAIL");
        JdbcColumnInfo idColumn =
                usersTable.columns().stream()
                        .filter(c -> c.name().equalsIgnoreCase("id"))
                        .findFirst()
                        .orElseThrow();
        assertThat(idColumn.nullable()).isFalse();
        JdbcColumnInfo nameColumn =
                usersTable.columns().stream()
                        .filter(c -> c.name().equalsIgnoreCase("name"))
                        .findFirst()
                        .orElseThrow();
        assertThat(nameColumn.nullable()).isFalse();
        JdbcColumnInfo emailColumn =
                usersTable.columns().stream()
                        .filter(c -> c.name().equalsIgnoreCase("email"))
                        .findFirst()
                        .orElseThrow();
        assertThat(emailColumn.nullable()).isTrue();
        assertThat(emailColumn.defaultValue()).isNotNull();
    }

    @Test
    void getSchemaInfoReturnsPrimaryKey() {
        JdbcSchemaInfo schemaInfo = provider.getSchemaInfo(env);

        JdbcTableInfo usersTable =
                schemaInfo.schemas().get(0).tables().stream()
                        .filter(t -> t.name().equalsIgnoreCase("users"))
                        .findFirst()
                        .orElseThrow();
        JdbcPrimaryKeyInfo primaryKey = usersTable.primaryKey();
        assertThat(primaryKey).isNotNull();
        assertThat(primaryKey.columns()).map(String::toUpperCase).containsExactly("ID");
    }

    @Test
    void getSchemaInfoReturnsForeignKeys() {
        JdbcSchemaInfo schemaInfo = provider.getSchemaInfo(env);

        JdbcTableInfo ordersTable =
                schemaInfo.schemas().get(0).tables().stream()
                        .filter(t -> t.name().equalsIgnoreCase("orders"))
                        .findFirst()
                        .orElseThrow();
        assertThat(ordersTable.foreignKeys()).hasSize(1);
        JdbcForeignKeyInfo fk = ordersTable.foreignKeys().get(0);
        assertThat(fk.name()).isNotBlank();
        assertThat(fk.columns()).map(String::toUpperCase).containsExactly("USER_ID");
        assertThat(fk.referencedTable()).isNotBlank();
        assertThat(fk.referencedTable().toUpperCase(java.util.Locale.ROOT)).isEqualTo("USERS");
        assertThat(fk.referencedColumns()).map(String::toUpperCase).containsExactly("ID");
    }

    @Test
    void getSchemaInfoReturnsExportedKeys() {
        JdbcSchemaInfo schemaInfo = provider.getSchemaInfo(env);

        JdbcTableInfo usersTable =
                schemaInfo.schemas().get(0).tables().stream()
                        .filter(t -> t.name().equalsIgnoreCase("users"))
                        .findFirst()
                        .orElseThrow();
        assertThat(usersTable.exportedKeys()).hasSize(1);
        JdbcForeignKeyInfo exportedKey = usersTable.exportedKeys().get(0);
        assertThat(exportedKey.referencedTable().toUpperCase(java.util.Locale.ROOT))
                .isEqualTo("ORDERS");
        assertThat(exportedKey.columns()).map(String::toUpperCase).containsExactly("ID");
    }

    @Test
    void getSchemaInfoReturnsExportedKeysForChildTablesInDifferentSchemasWithSameConstraintName()
            throws Exception {
        try (Connection conn = env.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA IF EXISTS s1 CASCADE");
            stmt.execute("DROP SCHEMA IF EXISTS s2 CASCADE");
            stmt.execute("DROP TABLE IF EXISTS parent");
            stmt.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
            stmt.execute("CREATE SCHEMA s1");
            stmt.execute(
                    "CREATE TABLE s1.child_a ("
                            + "id INTEGER PRIMARY KEY, "
                            + "parent_id INTEGER, "
                            + "CONSTRAINT fk_shared FOREIGN KEY (parent_id) REFERENCES"
                            + " PUBLIC.parent(id))");
            stmt.execute("CREATE SCHEMA s2");
            stmt.execute(
                    "CREATE TABLE s2.child_b ("
                            + "id INTEGER PRIMARY KEY, "
                            + "parent_id INTEGER, "
                            + "CONSTRAINT fk_shared FOREIGN KEY (parent_id) REFERENCES"
                            + " PUBLIC.parent(id))");

            try {
                JdbcSchemaInfo schemaInfo = provider.getSchemaInfo(env);

                JdbcTableInfo parentTable = findTable(schemaInfo, "parent");
                assertThat(parentTable.exportedKeys()).hasSize(2);
                assertThat(parentTable.exportedKeys())
                        .extracting(JdbcForeignKeyInfo::referencedTable)
                        .map(String::toUpperCase)
                        .containsExactlyInAnyOrder("CHILD_A", "CHILD_B");
            } finally {
                stmt.execute("DROP SCHEMA IF EXISTS s1 CASCADE");
                stmt.execute("DROP SCHEMA IF EXISTS s2 CASCADE");
                stmt.execute("DROP TABLE IF EXISTS parent");
            }
        }
    }

    @Test
    void getSchemaInfoAggregatesMultiColumnForeignKeyIntoSingleEntry() throws Exception {
        try (Connection conn = env.createConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS child_composite");
            stmt.execute("DROP TABLE IF EXISTS parent_composite");
            stmt.execute(
                    "CREATE TABLE parent_composite (a INTEGER, b INTEGER, PRIMARY KEY (a, b))");
            stmt.execute(
                    "CREATE TABLE child_composite ("
                            + "id INTEGER PRIMARY KEY, "
                            + "pa INTEGER, "
                            + "pb INTEGER, "
                            + "CONSTRAINT fk_composite FOREIGN KEY (pa, pb) REFERENCES"
                            + " parent_composite(a, b))");

            try {
                JdbcSchemaInfo schemaInfo = provider.getSchemaInfo(env);

                JdbcTableInfo childTable = findTable(schemaInfo, "child_composite");
                assertThat(childTable.foreignKeys()).hasSize(1);
                JdbcForeignKeyInfo fk = childTable.foreignKeys().get(0);
                assertThat(fk.columns()).map(String::toUpperCase).containsExactly("PA", "PB");
                assertThat(fk.referencedTable().toUpperCase(java.util.Locale.ROOT))
                        .isEqualTo("PARENT_COMPOSITE");
                assertThat(fk.referencedColumns())
                        .map(String::toUpperCase)
                        .containsExactly("A", "B");

                JdbcTableInfo parentTable = findTable(schemaInfo, "parent_composite");
                assertThat(parentTable.exportedKeys()).hasSize(1);
                JdbcForeignKeyInfo exportedKey = parentTable.exportedKeys().get(0);
                assertThat(exportedKey.referencedTable().toUpperCase(java.util.Locale.ROOT))
                        .isEqualTo("CHILD_COMPOSITE");
                assertThat(exportedKey.referencedColumns()).hasSize(2);
            } finally {
                stmt.execute("DROP TABLE IF EXISTS child_composite");
                stmt.execute("DROP TABLE IF EXISTS parent_composite");
            }
        }
    }

    @Test
    void getSchemaInfoReturnsIndexes() {
        JdbcSchemaInfo schemaInfo = provider.getSchemaInfo(env);

        JdbcTableInfo ordersTable =
                schemaInfo.schemas().get(0).tables().stream()
                        .filter(t -> t.name().equalsIgnoreCase("orders"))
                        .findFirst()
                        .orElseThrow();
        assertThat(ordersTable.indexes())
                .extracting(JdbcIndexInfo::name)
                .map(String::toUpperCase)
                .contains("IDX_ORDERS_USER_ID");
        JdbcIndexInfo userIdIndex =
                ordersTable.indexes().stream()
                        .filter(i -> i.name().equalsIgnoreCase("idx_orders_user_id"))
                        .findFirst()
                        .orElseThrow();
        assertThat(userIdIndex.unique()).isFalse();
        assertThat(userIdIndex.columns())
                .extracting(JdbcIndexColumn::name)
                .map(String::toUpperCase)
                .containsExactly("USER_ID");
    }

    @Test
    void getSchemaInfoReturnsViews() {
        JdbcSchemaInfo schemaInfo = provider.getSchemaInfo(env);

        JdbcSchemaDetail schema = schemaInfo.schemas().get(0);
        assertThat(schema.views())
                .extracting(JdbcViewInfo::name)
                .map(String::toUpperCase)
                .contains("ACTIVE_USERS");
        JdbcViewInfo activeUsersView =
                schema.views().stream()
                        .filter(v -> v.name().equalsIgnoreCase("active_users"))
                        .findFirst()
                        .orElseThrow();
        assertThat(activeUsersView.columns()).hasSize(2);
        assertThat(activeUsersView.columns())
                .extracting(JdbcColumnInfo::name)
                .map(String::toUpperCase)
                .containsExactlyInAnyOrder("ID", "NAME");
    }

    private static JdbcTableInfo findTable(JdbcSchemaInfo schemaInfo, String tableName) {
        return schemaInfo.schemas().stream()
                .flatMap(schema -> schema.tables().stream())
                .filter(t -> t.name().equalsIgnoreCase(tableName))
                .findFirst()
                .orElseThrow();
    }
}
