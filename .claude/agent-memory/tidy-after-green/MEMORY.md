# Tidy-After-Green Agent Memory

## Project Conventions (confirmed)
- Java 21, Gradle 8.5 Kotlin DSL
- Records and immutable collections are idiomatic
- Sealed interfaces + pattern matching used in `RenderRow` hierarchy
- `@Nullable` (jspecify) + NullAway; avoid `Optional` except in SmallRye `@ConfigMapping`
- Package structure is fixed — do not move classes
- Public API lives in `migraphe-api`; be extra conservative there

## Recurring Naming Patterns to Watch
- Compressed `StringBuilder` abbreviations (e.g., `sb`) appear in `GraphCanvas`
  — prefer full descriptive names when the builder has a specific semantic role
  - `mlc` was renamed to `mergeLaneChars` in Session 24; `sb` remains conventional
- Local variables named after their type prefix + "Col" (e.g., `mergeCol`) can be improved
  to express *purpose* (e.g., `junctionColumn`) especially when the test display name gives a hint

## Files Tidied
- `migraphe-core/src/main/java/.../core/graph/GraphCanvas.java`
  - `assignLanesAndInsertMergeRows()`: renamed `mlc` -> `mergeLaneChars` (2026-02-27, Session 24)
  - Same block: added comment explaining why `i - 1` is used for `laneActive` lookup in merge rows
    (merge row is inserted *before* row i, so the state of row i-1 is the correct reference)
  - `mergeCol` local var kept as-is (it is `mr.column()` at call site, not ambiguous here)
  - `buildGrid()` non-branch NodeRow: removed double-set workaround by adding `excludeColumn` param
    to `applyActiveColumnBars()`; ConnectorRow passes `-1` (no exclusion) (2026-03-02)
  - Dead code removal (2026-03-02, Session 26): removed 14 private dead methods and 1 dead record:
    `LaneResult`, `assignLanesAndInsertMergeRows()`, `computeNodeLaneChar()`, `isLaneActiveAtRow()`,
    `doRender()`, `buildConnectorLine()`, `buildNodeLine()`, `buildMergeLine()`,
    `buildLaneAreaForNode()`, `buildLaneAreaForConnector()`, `buildLaneAreaForMerge()`,
    `hasLaneConnection()`, `fillSpacesAfterNode()`, `padToWidth()`.
    These formed a closed call graph (old string-based rendering path) superseded by
    the `buildGrid()` / `Cell`-based rendering path. No external callers existed.
    File reduced from 947 to 565 lines.

## Safe Rename Scope
- Only rename private-method-local variables and parameters
- Do NOT rename `Row` record fields (e.g., `column()`, `isBranch()`) — referenced widely

## Key Architecture Notes (for tidy context)
- `ExecutionGraphView` renders a DAG as ASCII art using a dominator-tree-based algorithm
- `Row` sealed interface (private to `GraphCanvas`): `NodeRow`, `ConnectorRow`, `MergeRow`, `BlankRow`
- `Cell` sealed interface (package-private): used by `buildGrid()` to produce a typed cell grid
- `MergeRow.column()` stores the column where `├` is placed (the junction column)
- Branch nodes: the `├` junction is placed at `node.column() - 1` (one left of node column)
- Non-branch nodes: junction is at `node.column()` itself
- `buildGrid()` is the core grid-construction logic; `renderGrid()` converts it to strings
- After Session 22 refactor: `ExecutionGraphView` (54 lines) delegates to `DominatorTree` + `GraphCanvas`
- After Session 26 dead-code removal: only the Cell-based rendering path remains in `GraphCanvas`
- `GraphCanvas.java` contains `GroupInfo` sorting: use `Comparator.<GroupInfo>comparingInt(...)` with
  explicit type parameter when calling `.reversed()` to avoid type inference failure (JDK-8043371)

## Java Type Inference Gotcha
- `Comparator.comparingInt(T::field).reversed()` inside `.thenComparing(...)` can lose type parameter
  — always use `Comparator.<T>comparingInt(T::field).reversed()` (explicit type witness) to be safe

## JdbcMarkdownGenerator / JdbcMarkdownDefinition (migraphe-plugin-jdbc)
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
- Tidy applied (2026-07-22): `appendErDiagram()`'s manual `for` loop building `tableNames` (a
  `HashSet<String>` populated one `.add()` per table) was replaced with
  `tables.stream().map(JdbcTableInfo::name).collect(Collectors.toSet())`, removing the now-unused
  `HashSet` import. `Collectors` was already imported/used elsewhere in the file (`Collectors.joining`
  for index columns), so the stream form matches existing file style. Good general pattern in this
  file: a loop that only populates a `Set`/`List` from one field of each element is a safe
  stream-collect tidy target as long as the collector import is already present or trivially added.
- Tidy applied (2026-07-22, later same session): Foreign Keys section in `generateTableFile()` had
  the referenced-schema fallback ternary (`fk.referencedSchema().isEmpty() ? schemaName :
  fk.referencedSchema()`) inline inside a long `sb.append(...)` chain. Extracted to a private static
  helper `resolveReferencedSchema(String schemaName, JdbcForeignKeyInfo fk)` (placed just above
  `formatType`). Deliberately did NOT touch the Exported Keys section (`ek` loop, ~line 299) even
  though it will need an analogous fix in a future cycle — the two loops use different local variable
  names (`fk` vs `ek`) so there was no duplication to unify yet, and the task explicitly scoped this
  tidy to Foreign Keys only. General lesson: when a bugfix touches one of two structurally similar
  but not-yet-identical loops, extracting a helper for the fixed loop only (rather than trying to
  share it preemptively with the unfixed loop) keeps the tidy diff minimal and avoids coupling to a
  not-yet-written future change.
- Tidy applied (2026-07-22, follow-up cycle after Exported Keys got the same fallback fix): once both
  the Foreign Keys (`fk`) and Exported Keys (`ek`) loops built the identical Markdown link fragment
  (`"[" + refTable + "](../../" + resolveReferencedSchema(...) + "/tables/" + refTable + ".md)"`), the
  duplication became real and was extracted into `referencedTableLink(String schemaName,
  JdbcForeignKeyInfo fk)`, placed directly below `resolveReferencedSchema`. `JdbcForeignKeyInfo` is
  the shared type for both FK and exported-key rows, so one helper serves both loops without any
  generics or overloads. This confirms the earlier "don't preemptively unify" note: the right time to
  extract shared logic is once the second occurrence actually exists, not before.
