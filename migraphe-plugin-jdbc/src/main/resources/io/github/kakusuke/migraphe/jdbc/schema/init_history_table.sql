-- Creates the history table for the generic JDBC plugin, with every column this version writes.
--
-- This resource only ever creates. It never alters a table an older release left behind: a history
-- can be shared with a deployment still running that release, and a column dropped out from under
-- it by whoever runs `migraphe status` first is not a thing any command should do on its own. The
-- steps that change an existing table live in upgrade_history_table.sql, behind `migraphe upgrade`,
-- which an operator schedules.
--
-- Each --@apply block is one step, executed statement by statement. Nothing here is guarded by a
-- detection query: creation leans on IF NOT EXISTS, which every supported database accepts and
-- which cannot mistake another schema's table for this one.
--
-- id holds a UUIDv7 (36 characters) and is never used as a lookup key, so it is bounded at 64
-- characters: on MySQL/MariaDB a utf8mb4 VARCHAR(255) primary key needs 1020 bytes and exceeds
-- InnoDB's 767-byte index key limit on 5.5-generation servers.
--@apply history table
CREATE TABLE IF NOT EXISTS migraphe_history (
    id VARCHAR(64) PRIMARY KEY,
    node_id VARCHAR(255) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL,
    executed_at TIMESTAMP NOT NULL,
    description TEXT,
    serialized_down_task TEXT,
    duration_ms BIGINT,
    error_message TEXT,
    fingerprint TEXT,
    plugin_metadata TEXT,
    dependencies TEXT,
    origin VARCHAR(10),
    no_way_back TEXT
);

-- Renames the column that older versions called environment_id. The name always held a target id
-- (targets/*.yaml), never a deployment environment. Neither RENAME COLUMN nor CHANGE COLUMN is
-- portable across everything this resource may run against, so the rename is spelled as a copy:
-- add, backfill, drop. Each statement is plain SQL-92 and the step as a whole is guarded by the
-- detection query, so a table already carrying target_id is left alone.
