-- Index key lengths are kept within InnoDB's 767-byte limit so the table can also be created on
-- MariaDB 5.5-generation servers (innodb_file_format=Antelope / innodb_large_prefix=0), where a
-- utf8mb4 VARCHAR(255) column needs 1020 bytes and is therefore not indexable on its own.
-- The identifier columns stay utf8mb4 (node_id is derived from the task file path and may contain
-- non-ASCII characters); only the indexed prefixes are bounded.
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
