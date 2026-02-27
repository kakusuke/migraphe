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
  - Note: `ExecutionGraphView.java` was refactored away in Session 22; `GraphCanvas.java` is now
    the actual home of `assignLanesAndInsertMergeRows`

## Safe Rename Scope
- Only rename private-method-local variables and parameters
- Do NOT rename `RenderRow` record fields (e.g., `column()`, `isBranch()`) — they are
  referenced in many places including tests

## Key Architecture Notes (for tidy context)
- `ExecutionGraphView` renders a DAG as ASCII art using a dominator-tree-based algorithm
- `RenderRow` sealed interface: `NodeRow`, `ConnectorRow`, `MergeRow`, `BlankRow`
- `MergeRow.column()` stores the column where `├` is placed (the junction column)
- Branch nodes: the `├` junction is placed at `node.column() - 1` (one left of node column)
- Non-branch nodes: junction is at `node.column()` itself
- `assignLanesAndInsertMergeRows` is the core lane/merge-row logic; `doRender` does ASCII output
- After Session 22 refactor: `ExecutionGraphView` (54 lines) delegates to `DominatorTree` + `GraphCanvas`
- `GraphCanvas.java` contains `GroupInfo` sorting: use `Comparator.<GroupInfo>comparingInt(...)` with
  explicit type parameter when calling `.reversed()` to avoid type inference failure (JDK-8043371)
  where `Comparator.comparingInt(...).reversed()` inside `thenComparing()` may infer `Comparator<Object>`

## Java Type Inference Gotcha
- `Comparator.comparingInt(T::field).reversed()` inside `.thenComparing(...)` can lose type parameter
  — always use `Comparator.<T>comparingInt(T::field).reversed()` (explicit type witness) to be safe
