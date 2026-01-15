CREATE TABLE IF NOT EXISTS migraphe_history (
    id TEXT PRIMARY KEY,
    node_id TEXT NOT NULL,
    environment_id TEXT NOT NULL,
    direction TEXT NOT NULL,
    status TEXT NOT NULL,
    executed_at TIMESTAMP NOT NULL,
    description TEXT,
    serialized_down_task TEXT,
    duration_ms BIGINT,
    error_message TEXT,

    CONSTRAINT check_status CHECK (status IN ('SUCCESS', 'FAILURE', 'SKIPPED')),
    CONSTRAINT check_direction CHECK (direction IN ('UP', 'DOWN'))
);

CREATE INDEX IF NOT EXISTS idx_migraphe_history_node_env
    ON migraphe_history(node_id, environment_id);

CREATE INDEX IF NOT EXISTS idx_migraphe_history_env
    ON migraphe_history(environment_id);
