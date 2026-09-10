-- Creates the history table for the PostgreSQL plugin, with every column this version writes.
--
-- This resource only ever creates. It never alters a table an older release left behind: a history
-- can be shared with a deployment still running that release, and a column dropped out from under
-- it by whoever runs `migraphe status` first is not a thing any command should do on its own. The
-- steps that change an existing table live in upgrade_history_table.sql, behind `migraphe upgrade`,
-- which an operator schedules.
--
-- Each --@apply block is one step, executed statement by statement. Creation leans on IF NOT
-- EXISTS (CREATE TABLE since 9.1, CREATE INDEX since 9.5).
--
-- The index names still end in _env, which is what an older release created them as. Renaming the
-- column they cover does not rename the index, so keeping the names is what stops a re-creation
-- from producing a second index over the same columns.
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
    fingerprint TEXT,
    plugin_metadata TEXT,
    dependencies TEXT,
    origin TEXT,
    no_way_back TEXT,

    CONSTRAINT check_status CHECK (status IN ('SUCCESS', 'FAILURE', 'SKIPPED')),
    CONSTRAINT check_direction CHECK (direction IN ('UP', 'DOWN'))
);
-- The indexes are separate steps because PostgreSQL cannot declare them inside CREATE TABLE.
--
-- Both are guarded, and the guard is the one thing in this file that looks at an older shape: while
-- the table still carries environment_id these statements would name a column that does not exist
-- yet, and creation must not fail on a history it is not allowed to change. The upgrade resource
-- creates them after the rename. The guard is not a detection of "already done" — it is this
-- resource declining to touch a table that is not its own.
--@check node/target index
SELECT 1 FROM information_schema.columns
 WHERE table_schema = CAST(? AS text)
   AND table_name = 'migraphe_history'
   AND column_name = 'environment_id';
--@apply
CREATE INDEX IF NOT EXISTS idx_migraphe_history_node_env
    ON migraphe_history(node_id, target_id);

--@check target index
SELECT 1 FROM information_schema.columns
 WHERE table_schema = CAST(? AS text)
   AND table_name = 'migraphe_history'
   AND column_name = 'environment_id';
--@apply
CREATE INDEX IF NOT EXISTS idx_migraphe_history_env
    ON migraphe_history(target_id);
