-- History schema for the PostgreSQL plugin.
--
-- Each --@apply block is one step, executed statement by statement. A step may be preceded by a
-- --@check detection query, in which case it is skipped once that query returns a row; steps that
-- create objects omit it and rely on IF NOT EXISTS instead (CREATE TABLE since 9.1, CREATE INDEX
-- since 9.5). Detection queries are reserved for steps with no portable conditional form.
--
-- A detection query may carry positional parameters; every one of them is bound to the name of the
-- schema holding the history table, so a same-named table in another schema cannot satisfy the
-- query. The explicit cast is not required: the generic resource compares the same column against a
-- bare placeholder and initializes cleanly on PostgreSQL 16, which
-- PostgreSQLIntegrationTest.shouldInitializeHistorySchemaFromGenericResource pins. It is kept
-- because it costs nothing and states the comparison's type here rather than leaving it to how the
-- driver happens to bind the parameter.
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

-- Records the fingerprint of the UP content a node applied, so a later run can tell that the
-- definition was edited afterwards. Nullable, because rows written before this column existed carry
-- no fingerprint and null must read as "unknown" rather than "unchanged". TEXT because
-- MigrationNode.fingerprint() leaves the token's derivation, and so its length, to the plugin.
--
-- PostgreSQL does have ALTER TABLE ... ADD COLUMN IF NOT EXISTS, but the detection query is kept so
-- this step reads the same as its counterparts in the MySQL and generic resources, where Oracle
-- MySQL's lack of that clause makes detection the only option. The parameter is cast for the reason
-- given above.
--@check add fingerprint column
SELECT 1 FROM information_schema.columns
 WHERE table_schema = CAST(? AS text)
   AND table_name = 'migraphe_history'
   AND column_name = 'fingerprint';
--@apply
ALTER TABLE migraphe_history ADD COLUMN fingerprint TEXT;

-- Whatever the plugin that ran the node wants recorded alongside it. Opaque to core: stored and
-- handed back verbatim, and only the plugin that wrote it knows its encoding. The JDBC family uses
-- java.util.Properties, which keeps the value readable here. Nullable, and null means the plugin
-- recorded nothing — including every row written before this column existed.
--
-- TEXT for the same reason fingerprint is: core declares no format, so it can declare no length.
--@check add plugin_metadata column
SELECT 1 FROM information_schema.columns
 WHERE table_schema = CAST(? AS text)
   AND table_name = 'migraphe_history'
   AND column_name = 'plugin_metadata';
--@apply
ALTER TABLE migraphe_history ADD COLUMN plugin_metadata TEXT;

-- The node's declared direct dependencies as of when it was applied, so a rollback can be ordered
-- even through nodes the definitions no longer contain. The edges, not their transitive closure: a
-- closure cannot be inverted back into a DAG, so it cannot rebuild the graph this column exists to
-- rebuild. Newline-separated: node ids are derived from file paths and cannot contain a newline, so
-- the encoding is unambiguous and core needs no parser. An empty string means the node stood on
-- nothing; NULL means nobody recorded it, which is what every row written before this column
-- carries. The two are different answers.
--@check add dependencies column
SELECT 1 FROM information_schema.columns
 WHERE table_schema = CAST(? AS text)
   AND table_name = 'migraphe_history'
   AND column_name = 'dependencies';
--@apply
ALTER TABLE migraphe_history ADD COLUMN dependencies TEXT;

-- Whether the row records something that ran or something that was claimed: EXECUTED or AMENDED.
-- The history is an audit log, and the maintenance command that makes it agree with the definitions
-- writes rows for migrations that never ran, so an operator asking "did migraphe ever run this DDL"
-- has to be able to answer it. Only the history's integrity check reads it: a second EXECUTED
-- apply of a migration that already stands is refused, an AMENDED one supersedes.
--
-- NULL is read as EXECUTED: every version that could write a row wrote it by running something.
-- TEXT, matching direction and status in this resource, which hold enum names the same way. (The
-- generic and MySQL resources bound theirs; PostgreSQL does not pay for an unbounded text column.)
-- No CHECK constraint, unlike direction and status: those were constrained when the table was
-- created, and adding one to an existing table is a second statement this step does not need.
--@check add origin column
SELECT 1 FROM information_schema.columns
 WHERE table_schema = CAST(? AS text)
   AND table_name = 'migraphe_history'
   AND column_name = 'origin';
--@apply
ALTER TABLE migraphe_history ADD COLUMN origin TEXT;

-- The author's reason the migration is one-way — no_way_back: — or NULL when none was declared.
-- Without it, a row carrying no rollback payload answers two questions at once: the author declared
-- this one-way, or the row simply carries nothing. Those want opposite responses, and for a
-- migration whose task file is gone there is nowhere else left to look.
--
-- TEXT, like description and error_message: it is prose written by the task author, with no length
-- the configuration bounds.
--@check add no_way_back column
SELECT 1 FROM information_schema.columns
 WHERE table_schema = CAST(? AS text)
   AND table_name = 'migraphe_history'
   AND column_name = 'no_way_back';
--@apply
ALTER TABLE migraphe_history ADD COLUMN no_way_back TEXT;
