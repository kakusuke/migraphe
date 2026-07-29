---
name: jdbc_schema_info_provider
description: Tidy notes for JdbcSchemaInfoProvider (migraphe-plugin-jdbc) — foreign-key aggregation key design and test-lookup helper convention
metadata:
  type: project
---

## `buildKeyInfo` aggregation key (Session 2026-07-29)
- Foreign keys from `getImportedKeys`/`getExportedKeys` are aggregated into one
  `JdbcForeignKeyInfo` per constraint via a `Map<BuilderKey, ForeignKeyBuilder>` in
  `buildKeyInfo(ResultSet, boolean imported)`. The key was widened from a bare `String fkName`
  to `record BuilderKey(String fkTableSchem, String fkTableName, String fkName)` because
  `FK_NAME` is only unique **within** a child table — two child tables in different schemas can
  legitimately share the same constraint name (e.g. both named `fk_shared`), and H2 can also
  return a `null` `FK_NAME` for anonymous constraints. Composite-key columns (multi-column FKs)
  correctly aggregate into a single entry because JDBC emits one row per FK *column*, all sharing
  the same `(schema, table, fkName)` triple.
- Tidy pattern found here: when a `String fkName = rs.getString(...); if (fkName == null) fkName
  = "";` null-normalization block exists right next to an established `nullToEmpty(@Nullable
  String)` helper, always collapse it to `String fkName = nullToEmpty(rs.getString(...));` — same
  behavior, removes the need for an effectively-final workaround variable (`String finalFkName =
  fkName;`) that existed only because the reassignment made `fkName` non-effectively-final for the
  lambda passed to `computeIfAbsent`. Once collapsed, the lambda can read the constructed key's
  own field directly (`k -> new ForeignKeyBuilder(k.fkName())`) instead of capturing a shadow
  variable.
- `java.util.Locale.ROOT` fully-qualified (no import) in
  `JdbcSchemaInfoProviderTest` is **pre-existing file convention**, not an oversight — don't add
  a `Locale` import "to fix" it; that would be an unnecessary, non-tidy diff. Verify existing
  usages in the same file before treating a fully-qualified name as a smell.
- Test-only DRY: this test class already had a per-schema single-table lookup idiom
  (`schemaInfo.schemas().get(0).tables().stream().filter(...).findFirst().orElseThrow()`) for the
  common single-schema case, but new multi-schema tests needed a `flatMap` across
  `schemas()` first. When 2+ call sites repeat that flatMap-filter-findFirst shape, extracting a
  private static test helper (`findTable(JdbcSchemaInfo, String tableName)`) is a safe, readability
  win — pure lookup, no behavior change, no assertion touched.
