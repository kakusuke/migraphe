---
name: jdbc_markdown_generator
description: Tidy history and recurring patterns for JdbcMarkdownGenerator / JdbcMarkdownDefinition (migraphe-plugin-jdbc), incl. ER diagram feature
metadata:
  type: project
---

## Template Method Pattern
- Template Method base class: `protected void append*(...)` hooks are no-ops in the base, overridden
  by PostgreSQL/MySQL subclasses. New optional features (e.g. `erDiagram` toggle, Session ~2026-07-22)
  fit this pattern cleanly: config field on `JdbcMarkdownDefinition` (`@WithDefault`) + boolean field
  on the generator + telescoping constructor (old N-arg delegates to new N+1-arg with the default) +
  a guarded `append*` hook. When a cycle's diff already follows this shape with full Javadoc, there is
  usually nothing to tidy — confirmed skip in that case is correct, don't force a change.
- Known gap as of 2026-07-22: `JdbcMarkdownPlugin.output()` still calls the 3-arg
  `JdbcMarkdownGenerator` constructor and never reads `definition.erDiagram()` — the config flag is
  not actually wired to production behavior yet (tests pass because the default is `true` and test
  doubles hardcode `true`). This is a Green-phase feature gap, not a tidy concern — do not "fix" it
  during tidy (that would add new behavior, not preserve it). Flag it if asked for a broader review.
- Spotless commonly rewraps Javadoc `<p>` lines that cross the column limit purely from documentation
  reordering/insertion (e.g. `{@link #appendIndexHeader(StringBuilder)}` wrapping) — this is expected,
  behavior-preserving noise, not a sign something is wrong.

## Tidy History
- 2026-07-22: `appendErDiagram()`'s manual `for` loop building `tableNames` (a
  `HashSet<String>` populated one `.add()` per table) was replaced with
  `tables.stream().map(JdbcTableInfo::name).collect(Collectors.toSet())`, removing the now-unused
  `HashSet` import. `Collectors` was already imported/used elsewhere in the file (`Collectors.joining`
  for index columns), so the stream form matches existing file style. General pattern: a loop that
  only populates a `Set`/`List` from one field of each element is a safe stream-collect tidy target
  as long as the collector import is already present or trivially added.
- 2026-07-22 (later same session): Foreign Keys section in `generateTableFile()` had
  the referenced-schema fallback ternary (`fk.referencedSchema().isEmpty() ? schemaName :
  fk.referencedSchema()`) inline inside a long `sb.append(...)` chain. Extracted to a private static
  helper `resolveReferencedSchema(String schemaName, JdbcForeignKeyInfo fk)`. Deliberately did NOT
  touch the Exported Keys section (`ek` loop) even though it needed an analogous fix later — the two
  loops used different local variable names (`fk` vs `ek`) so there was no duplication to unify yet.
  General lesson: when a bugfix touches one of two structurally similar but not-yet-identical loops,
  extract a helper for the fixed loop only; don't preemptively share with the unfixed loop.
- 2026-07-22 (follow-up cycle after Exported Keys got the same fallback fix): once both the Foreign
  Keys (`fk`) and Exported Keys (`ek`) loops built the identical Markdown link fragment
  (`"[" + refTable + "](../../" + resolveReferencedSchema(...) + "/tables/" + refTable + ".md)"`), the
  duplication became real and was extracted into `referencedTableLink(String schemaName,
  JdbcForeignKeyInfo fk)`. `JdbcForeignKeyInfo` is the shared type for both FK and exported-key rows,
  so one helper serves both loops without generics/overloads. Confirms: extract shared logic once the
  second occurrence actually exists, not before.
- 2026-07-23: in `appendErDiagram()`'s relationship loop, `resolveReferencedSchema(st.schemaName(),
  fk)` was already computed once (as `refSchema`) to check `entityIds.contains(...)` for the
  membership guard, but `appendErRelationship(sb, schemaName, table, fk)` recomputed the identical
  value internally via its own `resolveReferencedSchema` call. Passed the already-computed
  `refSchema` through as an extra parameter instead (private method, single call site, safe signature
  change). General lesson: when a loop pre-computes a value for a guard/filter condition, check
  whether the subsequently-called private helper recomputes the same value from the same inputs —
  low-risk "pass it through" tidy target, distinct from the "don't preemptively unify across two
  not-yet-identical call sites" caution above (this case has one call site already computing the
  value redundantly, not two separate sites being unified).
- 2026-07-23 (entity ID collision resolution #1b): `appendErDiagram()` had grown to 3 loops (ID
  assignment with `_2`/`_3` suffixing on collision, entity rendering, relationship rendering) plus a
  `SchemaTableKey(schemaName, tableName)` record used as the `Map` key, with `new
  SchemaTableKey(st.schemaName(), st.table().name())` repeated 4x. Extracted the ID-assignment loop
  into `private Map<SchemaTableKey, String> assignEntityIds(List<SchemaTable>)` and added `private
  static SchemaTableKey keyOf(SchemaTable st)` to replace 3 of the 4 `new SchemaTableKey(...)` call
  sites (the 4th, looking up a *referenced* table by name only, has no `SchemaTable` object available
  and must stay as `new SchemaTableKey(refSchema, fk.referencedTable())`). Confirmed
  `SchemaTable`/`SchemaTableKey` should NOT be merged: `SchemaTable` wraps a full `JdbcTableInfo`
  (only available for tables actually being rendered), while `SchemaTableKey` is used to look up
  entities you only know by name (e.g. an FK's referenced table) — merging would require constructing
  a fake `SchemaTable` with no real `JdbcTableInfo`, a worse design, not real duplication. General
  lesson: a `Map` key record and a value-holder record with overlapping fields are not always
  unifiable — check whether every call site actually possesses the full value object first.
- 2026-07-23 (code-review findings #6/#7): two findings in the same file, both about redundant regex
  compilation. #6: `sanitizeMermaid(String)` called `token.replaceAll(regex, "_")` (recompiles the
  pattern every call — hot path, called per column/type/entity/relationship). Fixed by precompiling
  `private static final Pattern MERMAID_SANITIZE_PATTERN = Pattern.compile("[^A-Za-z0-9_]")` next to
  the existing `DEFAULT_SCHEMA_EXCLUDE` precompiled pattern, then
  `MERMAID_SANITIZE_PATTERN.matcher(token).replaceAll("_")` — `String.replaceAll` is documented to be
  exactly `Pattern.compile(regex).matcher(str).replaceAll(repl)` internally, so this is a
  behavior-identical hot-path optimization, not just style. #7: `isSchemaExcluded`/`isTableExcluded`
  were doing `Pattern.compile(exclude.schema().get(), CASE_INSENSITIVE)` inside a loop over the
  `excludes` list, called once per schema/table in `generate()` AND again per schema/table inside
  `nonExcludedTables()` (used by `appendErDiagram`) — the same user-supplied exclude patterns were
  recompiled on every check, doubled across two full traversals. Fixed by introducing a private
  record `CompiledExclude(@Nullable Pattern schemaPattern, @Nullable Pattern tablePattern)` and
  compiling the `List<JdbcMarkdownDefinition.ExcludePattern>` constructor argument into
  `List<CompiledExclude>` exactly once in the constructor, then rewriting both methods to iterate
  `CompiledExclude` with null-checks instead of `Optional.isPresent()/isEmpty()/get()` (semantically
  identical). The `excludes` field type changed from `List<ExcludePattern>` to `List<CompiledExclude>`
  — private-field-only type change, safe. General lesson for this file (and similar Template-Method
  generators with user-supplied regex exclude lists): if a raw exclude-pattern list is only ever
  consumed by re-deriving compiled `Pattern`s inside per-item-of-a-traversal methods, and the
  traversal runs more than once, precompiling into a private record wrapping `@Nullable Pattern`
  fields at construction time is a safe, minimal-diff fix — no need to unify the two traversals
  themselves to eliminate the compilation cost.
- 2026-07-23 (ER entity ID #1b改): `erIdHash(schemaName, tableName)` computed a SHA-256 digest then
  converted it to hex via `for (byte b : digest) sb.append(String.format("%02x", b))`, then
  `substring(0, 8)`. Replaced with `HexFormat.of().formatHex(digest).substring(0, 8)` (added
  `java.util.HexFormat` import). Verified equivalence before changing: `String.format("%02x", b)`
  where `b` is a `byte` autoboxes to `Byte`, and `java.util.Formatter`'s `%x`/`%X`/`%o` conversions
  special-case `Byte`/`Short` arguments — they mask to the argument's own bit width (unsigned) rather
  than sign-extending to `int` the way `%d` would. So each byte always yields exactly 2 lowercase hex
  chars, identical to what `HexFormat` (also lowercase, no delimiter by default) produces — this is
  NOT the classic "%x on a widened negative byte prints 8 f's" bug, because the argument here is a
  boxed `Byte`, not a raw `int`. Confirmed the test helper `expectedErId`/`shortHash` in
  `JdbcMarkdownGeneratorTest` independently reimplements the exact same loop-based approach, so both
  production and test computed identically before AND after (all 19 tests stayed green). General
  lesson: before "simplifying" byte-array-to-hex loops using `String.format("%02x", b)`, check
  whether `b` is a raw `int`/widened primitive (risk of the 8-f bug, do NOT touch) vs. an autoboxed
  `Byte`/`Short` (safe, masks correctly, `HexFormat` is a behavior-identical simplification).
