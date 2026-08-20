-- History schema for the MySQL/MariaDB plugin.
--
-- Each --@apply block is one step, executed statement by statement. A step may be preceded by a
-- --@check detection query, in which case it is skipped once that query returns a row; steps that
-- create objects omit it and rely on IF NOT EXISTS instead. Detection queries are reserved for
-- steps with no portable conditional form: Oracle MySQL has no ALTER TABLE ... ADD COLUMN IF NOT
-- EXISTS, unlike MariaDB and PostgreSQL.
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
    environment_id VARCHAR(255) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL,
    executed_at TIMESTAMP(6) NOT NULL,
    description TEXT,
    serialized_down_task LONGTEXT,
    duration_ms BIGINT,
    error_message TEXT,
    CHECK (status IN ('SUCCESS', 'FAILURE', 'SKIPPED')),
    CHECK (direction IN ('UP', 'DOWN')),
    INDEX idx_migraphe_history_node_env (node_id(100), environment_id(60)),
    INDEX idx_migraphe_history_env (environment_id(60))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
