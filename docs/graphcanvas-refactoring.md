# GraphCanvas 2D Algorithm Refactoring Plan

## Context

`GraphCanvas` currently uses a two-stage conversion:
1D intermediate representation (`initialRows`) → 2D grid (`buildGrid()`).

Two tests remain `@Disabled` due to issues with this design:

| Test | Disabled Reason | Root Cause |
|------|-----------------|------------|
| `nestedDiamondRendering` | expected output not yet corrected | Complex non-dom-edge rendering with 2+ lanes |
| `reversedNonDomEdgeGroupProducesNoSpuriousMergeRow` | not yet implemented | Branch order classification logic bug |

**Strategy**: Build the canvas as a 2D array directly, organizing drawing order into three phases:
trunk → branches → non-dom edges.

---

## Key Design Insight: Virtual Trunk for Unification

### Virtual Trunk = column 0

```
grid col 0  : [virtual trunk column] ← not rendered
grid col 1  : [connector column]     ← not rendered  (connects col 0 and col 2)
grid col 2  : [actual trunk column]  ← rendered (rendering col 0)
grid col 3  : [connector column]
grid col 4  : [branch column]        ← rendered (rendering col 2)
...
```

- **row 0**: virtual root placed at grid col 0 → **not rendered**
- **last row**: virtual end placed at grid col 0 → **not rendered**
- **col 0 (+ col 1)**: virtual trunk columns → **not rendered**

### Why Unification Works

Both trunk and branches share the same structure:
- Branch = forkNode (row where the connecting node sits) + a series of node columns
- Trunk's forkNode = virtual root (row 0, col 0)
- Actual branch's forkNode = any node row in the trunk

### Rendering Exclusion Rules (in toString)

| Excluded | Reason |
|----------|--------|
| row 0 | virtual root (not displayed) |
| last row | virtual end (not displayed) |
| grid col 0, 1 | virtual trunk columns (not displayed) |

---

## Pre-trunk / Post-trunk Classification: Unified via Topo Order

### Problem with Current `classifyBranches`

Current logic: "Is there a non-dom edge from trunk subtree → branch subtree?"

Bug: When T depends on S and X, and S/X are post-trunk (due to non-dom edges from trunk),
T has no *direct* non-dom edge from trunk to T's subtree, so T is incorrectly classified as pre-trunk.

### Solution: Transitive Fixpoint

Iteratively expand the "extended trunk subtree" to include post-trunk branches' subtrees:

```
extendedTrunkSubtree = collectSubtreeNodes(trunk)
remaining = all branches

repeat until no changes:
  for each branch in remaining:
    if any non-dom edge goes from extendedTrunkSubtree to branch's subtree:
      → post-trunk
      add branch's subtree to extendedTrunkSubtree
      remove branch from remaining

pre-trunk = remaining
```

T's subtree receives non-dom edges from S and X (which are in the extended trunk after round 1),
so T naturally becomes post-trunk in round 2. No complex topological analysis needed.

---

## New Algorithm Overview

### GridBuilder New API

```java
// Constructor: draws virtual trunk from the start
GridBuilder()
  → 3 rows × 1 col:
     row 0: TaskCell(VIRTUAL_ROOT)    ← not rendered
     row 1: ConnectorCell(│)
     row 2: TaskCell(VIRTUAL_END)     ← not rendered

// Add actual trunk (= internally calls addBranch(VIRTUAL_ROOT, ids))
void addTrunk(List<NodeId> ids)

// Add branch
// 1. getCellPosition(forkNodeId) to get fork position (x, y)
// 2. insertColumn(x) to reserve branch columns (connector + node columns)
// 3. Process each node in ids with insertRow → draw, sequentially
void addBranch(NodeId forkNodeId, List<NodeId> ids)

// Draw non-dom edge (1 fork = 1 lane, no optimization)
// 1. getCellPosition(forkId), getCellPosition(mergeId) to locate positions
// 2. Add new merge lane column at right edge (insertColumn)
// 3. Draw lane vertical line (│) from forkId row → mergeId row
// 4. insertRow just before mergeId row to draw merge row (┘)
void drawNonDomEdge(NodeId forkId, NodeId mergeId)

// Get (x, y) coordinates of a given node's cell
record CellPosition(int x, int y) {}
CellPosition getCellPosition(NodeId id)

// Return rendering grid with virtual trunk rows/cols excluded
// (row 0 = VIRTUAL_ROOT, last row = VIRTUAL_END, col 0-1 = virtual trunk removed)
List<List<Cell>> toVisibleGrid()
```

### GraphCanvas Role (Coordinator)

```
layout(dt):
  grid = new GridBuilder()  ← constructor draws virtual trunk

  // Phase 1 & 2: Draw actual trunk and branches recursively
  trunkPath = [A, B, C, ...]
  grid.addTrunk(trunkPath)   ← internally calls addBranch(VIRTUAL_ROOT, trunkPath)

  drawSubtree(nodeId):
    branches = domChildren(nodeId) \ trunkChild(nodeId)
    classify branches using transitive fixpoint (extendedTrunk approach)
    pre  = pre-trunk branches
    post = post-trunk branches

    for B in pre:
      grid.addBranch(nodeId, subtreePath(B))
      drawSubtree(B) (recurse)
    recurse trunk child
    for B in post:
      grid.addBranch(nodeId, subtreePath(B))
      drawSubtree(B) (recurse)

  // Phase 3: Non-dom edges (1 fork = 1 lane, no optimization)
  for each nonDomEdge (source → target):
    grid.drawNonDomEdge(source, target)

renderLines():
  rows = grid.toVisibleGrid()  ← GridBuilder handles virtual trunk exclusion
  return rows.map(cells -> toSymbol())
```

---

## TDD Cycles

### Cycle 1 — Add new GridBuilder APIs

Files: `GridBuilder.java`, `GridBuilderTest.java`

1. **Red**: Add tests for new no-arg constructor (virtual trunk), `addBranch`, `getCellPosition`, `toVisibleGrid`
2. **Green**: Implement the methods in `GridBuilder.java`
3. **Refactor**: Clean up GridBuilder

### Cycle 2 — Add GridBuilder.drawNonDomEdge

Files: `GridBuilder.java`, `GridBuilderTest.java`

1. **Red**: Add tests for `drawNonDomEdge` (1 fork = 1 lane)
2. **Green**: Implement
3. **Refactor**: Clean up

### Cycle 3 — Rewrite GraphCanvas with new algorithm

Files: `GraphCanvas.java`, `ExecutionGraphViewTest.java`

1. **Red**: Remove `@Disabled` from `reversedNonDomEdgeGroupProducesNoSpuriousMergeRow`
2. **Green**: Rewrite `GraphCanvas.layout()` using 3-phase algorithm
   - Replace `emitSubtree` + `initialRows` with `new GridBuilder()` → `addTrunk` → `addBranch` → `drawNonDomEdge`
   - Fix branch classification to use transitive fixpoint
3. **Refactor**: Remove unused fields/methods from GraphCanvas

### Cycle 4 — Enable nestedDiamondRendering

Files: `ExecutionGraphViewTest.java`

1. **Red**: Remove `@Disabled` from `nestedDiamondRendering`
   - Verify actual output and confirm expected output is correct
2. **Green**: Fix algorithm if needed
3. **Refactor**: Clean up

---

## Files Changed

| File | Change |
|------|--------|
| `migraphe-core/.../graph/GraphCanvas.java` | Main refactoring target |
| `migraphe-core/.../graph/GridBuilder.java` | New API additions |
| `migraphe-core/.../graph/ExecutionGraphViewTest.java` | Remove 2 `@Disabled` annotations |
| `CLAUDE.md` | Changelog update (end of session) |

**No changes (reference only)**:
- `Cell.java`, `DominatorTree.java`, `GroupInfo.java`, `NonDomEdge.java`, `BranchClassification.java`

---

## Verification

```bash
./gradlew test          # 308 tests (306 + 2 re-enabled) all pass
./gradlew spotlessApply # Format check
```
