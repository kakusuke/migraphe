-- History schema for the generic JDBC plugin.
--
-- Each --@apply block is one step, executed statement by statement. A step may be preceded by a
-- --@check detection query, in which case it is skipped once that query returns a row; steps that
-- create objects omit it and rely on IF NOT EXISTS instead, which every supported database accepts
-- and which cannot mistake another schema's table for this one. Detection queries are reserved for
-- steps with no portable conditional form, such as ALTER TABLE ... ADD COLUMN.
--
-- A detection query may carry positional parameters; every one of them is bound to the name of the
-- schema holding the history table, so a same-named table elsewhere on the server cannot satisfy
-- the query. That name comes from the connection (Connection.getSchema(), falling back to
-- getCatalog()) because no expression yields it across H2, MySQL and PostgreSQL alike.
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
    error_message TEXT
);

-- Renames the column that older versions called environment_id. The name always held a target id
-- (targets/*.yaml), never a deployment environment. Neither RENAME COLUMN nor CHANGE COLUMN is
-- portable across everything this resource may run against, so the rename is spelled as a copy:
-- add, backfill, drop. Each statement is plain SQL-92 and the step as a whole is guarded by the
-- detection query, so a table already carrying target_id is left alone.
--@check rename environment_id to target_id
SELECT 1 FROM information_schema.columns
 WHERE table_schema = ?
   AND UPPER(table_name) = 'MIGRAPHE_HISTORY'
   AND UPPER(column_name) = 'TARGET_ID';
--@apply
ALTER TABLE migraphe_history ADD COLUMN target_id VARCHAR(255);
UPDATE migraphe_history SET target_id = environment_id;
ALTER TABLE migraphe_history DROP COLUMN environment_id;
