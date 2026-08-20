-- History schema for the PostgreSQL plugin.
--
-- Each --@apply block is one step, executed statement by statement. A step may be preceded by a
-- --@check detection query, in which case it is skipped once that query returns a row; steps that
-- create objects omit it and rely on IF NOT EXISTS instead (CREATE TABLE since 9.1, CREATE INDEX
-- since 9.5). Detection queries are reserved for steps with no portable conditional form.
--
-- A detection query may carry positional parameters; every one of them is bound to the name of the
-- schema holding the history table, so a same-named table in another schema cannot satisfy the
-- query. The parameter is cast explicitly because PostgreSQL cannot infer the type of a bare
-- placeholder compared against information_schema's sql_identifier domain.
--
-- The table and each index are separate steps: every statement is executed on its own, and a
-- manually dropped index is recreated on the next run because each step always runs.
--@apply history table
CREATE TABLE IF NOT EXISTS migraphe_history (
    id TEXT PRIMARY KEY,
    node_id TEXT NOT NULL,
    target_id TEXT NOT NULL,
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

-- Renames the column that older versions called environment_id. The name always held a target id
-- (targets/*.yaml), never a deployment environment. This step precedes the index steps so that a
-- legacy table is renamed before the indexes below are evaluated against the new column name.
--@check rename environment_id to target_id
SELECT 1 FROM information_schema.columns
 WHERE table_schema = CAST(? AS text)
   AND table_name = 'migraphe_history'
   AND column_name = 'target_id';
--@apply
ALTER TABLE migraphe_history RENAME COLUMN environment_id TO target_id;

--@apply node/target index
CREATE INDEX IF NOT EXISTS idx_migraphe_history_node_env
    ON migraphe_history(node_id, target_id);

--@apply target index
CREATE INDEX IF NOT EXISTS idx_migraphe_history_env
    ON migraphe_history(target_id);
