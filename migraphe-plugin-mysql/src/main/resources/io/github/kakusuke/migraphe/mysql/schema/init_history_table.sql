-- History schema for the MySQL/MariaDB plugin.
--
-- Each --@apply block is one step, executed statement by statement. A step may be preceded by a
-- --@check detection query, in which case it is skipped once that query returns a row; steps that
-- create objects omit it and rely on IF NOT EXISTS instead. Detection queries are reserved for
-- steps with no portable conditional form: Oracle MySQL has no ALTER TABLE ... ADD COLUMN IF NOT
-- EXISTS, unlike MariaDB and PostgreSQL.
--
-- A detection query may carry positional parameters; every one of them is bound to the name of the
-- schema holding the history table, so a same-named table in another database on the same server
-- cannot satisfy the query. MySQL and MariaDB report no schema on the connection, so that name
-- arrives from getCatalog() — the database name, which is what table_schema holds here.
--
-- Index key lengths are kept within InnoDB's 767-byte limit so the table can also be created on
-- MariaDB 5.5-generation servers (innodb_file_format=Antelope / innodb_large_prefix=0), where a
-- utf8mb4 VARCHAR(255) column needs 1020 bytes and is therefore not indexable on its own.
-- The identifier columns stay utf8mb4 (node_id is derived from the task file path and may contain
-- non-ASCII characters); only the indexed prefixes are bounded. Both indexes are declared inside
-- CREATE TABLE, so the table needs no separate index steps.
--@apply history table
CREATE TABLE IF NOT EXISTS migraphe_history (
    id VARCHAR(64) PRIMARY KEY,
    node_id VARCHAR(255) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL,
    executed_at TIMESTAMP(6) NOT NULL,
    description TEXT,
    serialized_down_task LONGTEXT,
    duration_ms BIGINT,
    error_message TEXT,
    CHECK (status IN ('SUCCESS', 'FAILURE', 'SKIPPED')),
    CHECK (direction IN ('UP', 'DOWN')),
    INDEX idx_migraphe_history_node_env (node_id(100), target_id(60)),
    INDEX idx_migraphe_history_env (target_id(60))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Renames the column that older versions called environment_id. The name always held a target id
-- (targets/*.yaml), never a deployment environment. CHANGE COLUMN is used rather than RENAME
-- COLUMN because the latter needs MySQL 8.0 / MariaDB 10.5.2, and this resource still has to run
-- on 5.5-generation servers; restating the type is the price. The index prefixes follow the column
-- automatically, so the indexes need no separate step.
--@check rename environment_id to target_id
SELECT 1 FROM information_schema.columns
 WHERE table_schema = ?
   AND table_name = 'migraphe_history'
   AND column_name = 'target_id';
--@apply
ALTER TABLE migraphe_history CHANGE COLUMN environment_id target_id VARCHAR(255) NOT NULL;
