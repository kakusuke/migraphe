# JdbcSchemaInfoProvider

One invariant that looks like redundancy and is not.

## `buildKeyInfo` aggregation key — do not simplify

`buildKeyInfo(ResultSet, boolean imported)` aggregates FK rows into one `JdbcForeignKeyInfo` per
constraint using `Map<BuilderKey, ForeignKeyBuilder>`, where
`record BuilderKey(String fkTableSchem, String fkTableName, String fkName)`.

The three-field key reads like over-engineering on the `getImportedKeys` path, where schema and table
are constant for the whole result set. It is not. `FK_NAME` is unique only *within* a child table, so
on the `getExportedKeys` path two child tables can legitimately carry the same constraint name and
collapsing the key to a bare `fkName` silently merges them — duplicated columns, overwritten
`referencedTable`, one child dropped from the output. `FK_NAME` can also be null for anonymous
constraints.

Multi-column FKs still aggregate correctly: JDBC emits one row per FK *column*, and all of them share
the same `(schema, table, fkName)` triple.

## `MySQLSchemaInfoProvider.buildKeyInfo` keys on the catalog, not the schema

It carries the same three-field `BuilderKey`, but discriminates on **`FKTABLE_CAT`** where the JDBC
version uses `FKTABLE_SCHEM`. That is not an oversight to "unify": MySQL reports the database in the
catalog column and leaves `FKTABLE_SCHEM` null, so porting the JDBC field verbatim would give a key
whose first component is always `""`.

Reachable collision here: two child tables **in different databases** sharing a constraint name — legal,
because MySQL scopes FK-name uniqueness per database, and cross-database FKs are allowed. Two children
in the *same* database cannot collide.

The anonymous-constraint `""` bucket that remains open on the JDBC path is **not** reachable in MySQL —
InnoDB always auto-names foreign keys (`<table>_ibfk_N`), so `FK_NAME` is never null.

Testing this needs a **root** connection: the Testcontainers `test` user cannot create a second database,
and `information_schema` filters rows by privilege, so a non-root provider run would not see the
cross-database child even if it existed.
