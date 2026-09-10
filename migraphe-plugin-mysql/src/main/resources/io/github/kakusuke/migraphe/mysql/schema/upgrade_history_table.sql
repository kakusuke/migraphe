-- History-table upgrades for the MySQL/MariaDB plugin, in order.
--
-- Every step here changes a table an older release created. None of it runs on its own: the
-- upgrade command applies these, and the other commands refuse while any is pending. A fresh
-- project never needs them, because init_history_table.sql already creates the current shape.
--
-- Each --@apply block is one step, executed statement by statement, preceded by a --@check
-- detection query that skips it once it is in place. Detection is the only option here: Oracle
-- MySQL has no ALTER TABLE ... ADD COLUMN IF NOT EXISTS, unlike MariaDB and PostgreSQL.
--
-- A detection query may carry positional parameters; every one of them is bound to the name of the
-- schema holding the history table, so a same-named table in another database on the same server
-- cannot satisfy the query. MySQL and MariaDB report no schema on the connection, so that name
-- arrives from getCatalog() — the database name, which is what table_schema holds here.
--
--@check rename environment_id to target_id
SELECT 1 FROM information_schema.columns
 WHERE table_schema = ?
   AND table_name = 'migraphe_history'
   AND column_name = 'target_id';
--@apply
ALTER TABLE migraphe_history CHANGE COLUMN environment_id target_id VARCHAR(255) NOT NULL;

-- Records the fingerprint of the UP content a node applied, so a later run can tell that the
-- definition was edited afterwards. Nullable, because rows written before this column existed carry
-- no fingerprint and null must read as "unknown" rather than "unchanged".
--
-- TEXT, like description and error_message, rather than a bounded width: MigrationNode
-- .fingerprint() leaves the token's derivation, and so its length, to the plugin. The column is in
-- no index, so the InnoDB key-length limit that bounds id and target_id does not apply here. A
-- bounded column would truncate silently on a non-strict server, and a truncated token never again
-- equals the freshly computed one, so an unchanged node would report as edited forever.
--@check add fingerprint column
SELECT 1 FROM information_schema.columns
 WHERE table_schema = ?
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
 WHERE table_schema = ?
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
-- LONGTEXT, not TEXT. The width now grows with the node's fan-in rather than with the project — the
-- column held the closure when this was chosen, and a chain of roughly 2,500 tasks with 25-character
-- ids overflowed TEXT's 65,535 bytes; edges make that unreachable, so LONGTEXT is no longer needed
-- for the reason it was picked. It stays because the failure mode has not changed and narrowing a
-- shipped column costs a migration: on a strict server (the default since 5.7) an oversized INSERT
-- fails after the migration's own DDL has committed, so the node is reported failed although it was
-- applied; on a non-strict server it is truncated to a last id that is a fragment, which nothing
-- downstream can detect. serialized_down_task is LONGTEXT for the same class of reason.
--@check add dependencies column
SELECT 1 FROM information_schema.columns
 WHERE table_schema = ?
   AND table_name = 'migraphe_history'
   AND column_name = 'dependencies';
--@apply
ALTER TABLE migraphe_history ADD COLUMN dependencies LONGTEXT;

-- Whether the row records something that ran or something that was claimed: EXECUTED or AMENDED.
-- The history is an audit log, and the maintenance command that makes it agree with the definitions
-- writes rows for migrations that never ran, so an operator asking "did migraphe ever run this DDL"
-- has to be able to answer it. Only the history's integrity check reads it: a second EXECUTED
-- apply of a migration that already stands is refused, an AMENDED one supersedes.
--
-- NULL is read as EXECUTED: every version that could write a row wrote it by running something.
-- VARCHAR(10) matches direction and status, which hold enum names the same way. No CHECK constraint,
-- unlike those two: theirs were declared when the table was created, and adding one to an existing
-- table is a second statement this step does not need.
--@check add origin column
SELECT 1 FROM information_schema.columns
 WHERE table_schema = ?
   AND table_name = 'migraphe_history'
   AND column_name = 'origin';
--@apply
ALTER TABLE migraphe_history ADD COLUMN origin VARCHAR(10);

-- The author's reason the migration is one-way — no_way_back: — or NULL when none was declared.
-- Without it, a row carrying no rollback payload answers two questions at once: the author declared
-- this one-way, or the row simply carries nothing. Those want opposite responses, and for a
-- migration whose task file is gone there is nowhere else left to look.
--
-- TEXT, like description and error_message: it is prose written by the task author, with no length
-- the configuration bounds.
--@check add no_way_back column
SELECT 1 FROM information_schema.columns
 WHERE table_schema = ?
   AND table_name = 'migraphe_history'
   AND column_name = 'no_way_back';
--@apply
ALTER TABLE migraphe_history ADD COLUMN no_way_back TEXT;
