-- History schema for the generic JDBC plugin.
--
-- Each --@apply block is one step, executed statement by statement. A step may be preceded by a
-- --@check detection query, in which case it is skipped once that query returns a row; steps that
-- create objects omit it and rely on IF NOT EXISTS instead, which every supported database accepts
-- and which cannot mistake another schema's table for this one. Detection queries are reserved for
-- steps with no portable conditional form, such as ALTER TABLE ... ADD COLUMN.
--
-- id holds a UUID (36 characters) and is never used as a lookup key, so it is bounded at 64
-- characters: on MySQL/MariaDB a utf8mb4 VARCHAR(255) primary key needs 1020 bytes and exceeds
-- InnoDB's 767-byte index key limit on 5.5-generation servers.
--@apply history table
CREATE TABLE IF NOT EXISTS migraphe_history (
    id VARCHAR(64) PRIMARY KEY,
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
