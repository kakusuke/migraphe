-- Creates the history table for the MySQL/MariaDB plugin, with every column this version writes.
--
-- This resource only ever creates. It never alters a table an older release left behind: a history
-- can be shared with a deployment still running that release, and a column dropped out from under
-- it by whoever runs `migraphe status` first is not a thing any command should do on its own. The
-- steps that change an existing table live in upgrade_history_table.sql, behind `migraphe upgrade`,
-- which an operator schedules.
--
-- Each --@apply block is one step, executed statement by statement. Nothing here is guarded by a
-- detection query: creation leans on IF NOT EXISTS.
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
    fingerprint TEXT,
    plugin_metadata TEXT,
    dependencies LONGTEXT,
    origin VARCHAR(10),
    no_way_back TEXT,
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
