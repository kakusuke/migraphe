# Tidy-After-Green Agent Memory

## Project Conventions (confirmed)
- Java 21, Gradle 8.5 Kotlin DSL
- Records and immutable collections are idiomatic
- Sealed interfaces + pattern matching used in `RenderRow` hierarchy
- `@Nullable` (jspecify) + NullAway; avoid `Optional` except in SmallRye `@ConfigMapping`
- Package structure is fixed — do not move classes
- Public API lives in `migraphe-api`; be extra conservative there

## Recurring Naming Patterns to Watch
- Compressed `StringBuilder` abbreviations (e.g., `mlc`, `sb`) appear in `ExecutionGraphView`
  — prefer full descriptive names when the builder has a specific semantic role
- Local variables named after their type prefix + "Col" (e.g., `mergeCol`) can be improved
  to express *purpose* (e.g., `junctionColumn`) especially when the test display name gives a hint

## Files Tidied
- `migraphe-core/src/main/java/.../core/graph/ExecutionGraphView.java`
  - `assignLanesAndInsertMergeRows()` method (lines ~600-630): renamed `mergeCol` -> `junctionColumn`,
    `mlc` -> `mergeLaneChars` (2026-02-20, Session 22)
  - Same block: extracted `int rowAboveMerge = i - 1` with explanatory comment to make the
    "one row above the inserted merge row" intent self-evident (2026-02-20, Session 23)
  - Overall file is large (~800 lines); other compressed names (`sb`) exist but are conventional
    enough to leave as-is

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
