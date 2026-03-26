CREATE TABLE IF NOT EXISTS migraphe_history (
    id VARCHAR(255) PRIMARY KEY,
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
    INDEX idx_migraphe_history_node_env (node_id, environment_id),
    INDEX idx_migraphe_history_env (environment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
