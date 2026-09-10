-- History-table upgrades for the generic JDBC plugin, in order.
--
-- Every step here changes a table an older release created. None of it runs on its own: the
-- upgrade command applies these, and the other commands refuse while any is pending. A fresh
-- project never needs them, because init_history_table.sql already creates the current shape.
--
-- Each --@apply block is one step, executed statement by statement, preceded by a --@check
-- detection query that skips it once it is in place. That is what makes running the upgrade
-- repeatedly safe and why no schema-version table is needed. A detection query runs before the
-- column it looks for exists, so its failure is reported rather than read as "not applied":
-- mistaking a permission error for a missing column would turn the step into a blind DDL attempt.
--
-- A detection query may carry positional parameters; every one of them is bound to the name of the
-- schema holding the history table, so a same-named table elsewhere on the server cannot satisfy
-- the query. That name comes from the connection (Connection.getSchema(), falling back to
-- getCatalog()) because no expression yields it across H2, MySQL and PostgreSQL alike.
--
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

-- Records the fingerprint of the UP content a node applied, so a later run can tell that the
-- definition was edited afterwards. Nullable: rows written before this column existed carry no
-- fingerprint, and null must read as "unknown" rather than "unchanged".
--
-- TEXT, not a bounded width. MigrationNode.fingerprint() declares the token opaque and leaves its
-- derivation to the plugin, so it declares no length either, and this is the generic resource that
-- any plugin's token lands in: a SHA-512 hex digest is 128 characters, and a prefixed one longer
-- still. A too-narrow column truncates silently on a non-strict MySQL, and a truncated token never
-- again equals the freshly computed one, so an unchanged node would report as edited forever. The
-- column is in no index, which is what a bounded width would otherwise buy.
--
-- ALTER TABLE ... ADD COLUMN has no portable IF NOT EXISTS, so this step needs the detection query.
--@check add fingerprint column
SELECT 1 FROM information_schema.columns
 WHERE table_schema = ?
   AND UPPER(table_name) = 'MIGRAPHE_HISTORY'
   AND UPPER(column_name) = 'FINGERPRINT';
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
   AND UPPER(table_name) = 'MIGRAPHE_HISTORY'
   AND UPPER(column_name) = 'PLUGIN_METADATA';
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
 WHERE table_schema = ?
   AND UPPER(table_name) = 'MIGRAPHE_HISTORY'
   AND UPPER(column_name) = 'DEPENDENCIES';
--@apply
ALTER TABLE migraphe_history ADD COLUMN dependencies TEXT;

-- Whether the row records something that ran or something that was claimed: EXECUTED or AMENDED.
-- The history is an audit log, and the maintenance command that makes it agree with the definitions
-- writes rows for migrations that never ran, so an operator asking "did migraphe ever run this DDL"
-- has to be able to answer it. Only the history's integrity check reads it: a second EXECUTED
-- apply of a migration that already stands is refused, an AMENDED one supersedes.
--
-- NULL is read as EXECUTED: every version that could write a row wrote it by running something.
-- VARCHAR(10) matches direction and status, which hold enum names the same way.
--@check add origin column
SELECT 1 FROM information_schema.columns
 WHERE table_schema = ?
   AND UPPER(table_name) = 'MIGRAPHE_HISTORY'
   AND UPPER(column_name) = 'ORIGIN';
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
   AND UPPER(table_name) = 'MIGRAPHE_HISTORY'
   AND UPPER(column_name) = 'NO_WAY_BACK';
--@apply
ALTER TABLE migraphe_history ADD COLUMN no_way_back TEXT;
