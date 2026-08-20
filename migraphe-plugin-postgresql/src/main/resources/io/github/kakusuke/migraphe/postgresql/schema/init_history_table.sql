-- History schema for the PostgreSQL plugin.
--
-- Each --@apply block is one step, executed statement by statement. A step may be preceded by a
-- --@check detection query, in which case it is skipped once that query returns a row; steps that
-- create objects omit it and rely on IF NOT EXISTS instead (CREATE TABLE since 9.1, CREATE INDEX
-- since 9.5). Detection queries are reserved for steps with no portable conditional form.
--
-- The table and each index are separate steps: every statement is executed on its own, and a
-- manually dropped index is recreated on the next run because each step always runs.
--@apply history table
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

--@apply node/environment index
CREATE INDEX IF NOT EXISTS idx_migraphe_history_node_env
    ON migraphe_history(node_id, environment_id);

--@apply environment index
CREATE INDEX IF NOT EXISTS idx_migraphe_history_env
    ON migraphe_history(environment_id);
