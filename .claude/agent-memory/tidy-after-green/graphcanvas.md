---
name: graphcanvas
description: Tidy history, naming conventions, and architecture notes for migraphe-core's GraphCanvas / ExecutionGraphView rendering pipeline
metadata:
  type: project
---

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
