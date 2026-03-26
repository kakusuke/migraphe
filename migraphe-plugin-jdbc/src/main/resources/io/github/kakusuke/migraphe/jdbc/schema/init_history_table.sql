CREATE TABLE IF NOT EXISTS migraphe_history (
    id VARCHAR(255) PRIMARY KEY,
    node_id VARCHAR(255) NOT NULL,
    environment_id VARCHAR(255) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL,
    executed_at TIMESTAMP NOT NULL,
    description TEXT,
    serialized_down_task TEXT,
    duration_ms BIGINT,
    error_message TEXT
);
