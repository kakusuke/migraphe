# Tidy notes

Rules that apply anywhere in this repo. Read this file every cycle — it is short on purpose.
Per-class notes are gated by the table at the bottom.

## Repo conventions

- Records and immutable collections are idiomatic; sealed interfaces + pattern matching are used freely
- `@Nullable` (jspecify) + NullAway. Avoid `Optional` except in SmallRye `@ConfigMapping`
- Package structure is fixed — don't move classes between packages
- `migraphe-api` is public surface. So is anything a plugin module reaches via `super(...)`:
  the JDBC/PostgreSQL/MySQL generators are separately published, so their constructors are API too

## Match the file; don't improve it

Tidy makes code consistent with its surroundings, not with your preferences. Before calling something
a smell, check how its siblings do it — the "smell" is usually the file's established convention, and
"fixing" it produces a diff that isn't a tidy.

- Fully-qualified names vs. imports: whichever the file already uses
- Javadoc and comment density: match the neighbouring members. Don't document one constant when its
  siblings carry none
- A pre-existing inconsistency across sibling classes that this diff doesn't touch: leave it, and note
  concretely which siblings agree and which differs. Unify only when a cycle's diff lands on those lines

## When to extract, and when not to

- Extract at the **second real occurrence**, not the first — and "occurrence" means repeated *logic*.
  Identical one-liners serving different concerns (SQL-NULL normalization vs. config defaulting) are
  not a trigger. When a fix touches one of two structurally similar but not-yet-identical sites, fix
  the touched one only
- Introducing a new shared utility class is a design decision, not a tidy
- Never merge test doubles or helpers across Gradle modules — that needs a cross-module test-fixture
  source set. Evaluate duplication per module
- Renaming is safe for locals and parameters inside private methods. Record accessors and public
  method names need a caller check first, and are usually not worth it
- Public API shape changes — constructor arity, parameter-object extraction — are never bundled into a
  cycle that also adds behavior

## Traps that will bite again

- **`Comparator.comparingInt(T::f).reversed()`** inside `.thenComparing(...)` can lose its type
  parameter (JDK-8043371). Use the explicit witness: `Comparator.<T>comparingInt(T::f).reversed()`
- **`String.replaceAll(regex, …)` recompiles every call.** If it runs per item of a traversal,
  precompile to a `private static final Pattern` and use `PATTERN.matcher(s).replaceAll(…)`
- **`^…$` anchors are redundant on a `Pattern` used only via `.matches()`** — but grep the constant
  first: `.find()` / `.lookingAt()` callers do need them
- **`String.format("%02x", b)`** is correct for an autoboxed `Byte`/`Short` (Formatter masks to the
  argument's own width) and is the classic eight-`f` bug for a widened `int`. Check the argument type
  before touching hex formatting
- **Replacing `equalsIgnoreCase` with a lowercase-keyed map** needs `toLowerCase(Locale.ROOT)`; bare
  `toLowerCase()` reintroduces the Turkish-`i` mismatch that `equalsIgnoreCase` didn't have
- **Deleting a map's redundant "pre-populate all keys" loop** is safe only if every reader uses
  `get`/`getOrDefault`. Any `containsKey`, `keySet`, or `entrySet` reader makes absent-vs-empty
  observable
- **Telescoping a subclass constructor**: the existing N-arg form must delegate with `this(…, default)`,
  not keep calling `super(…)`. Otherwise the subclass quietly gains a second terminal constructor and a
  duplicated field assignment. The invariant is "exactly one terminal constructor"
- **Null-normalization next to an existing helper**: `String s = rs.getString(…); if (s == null) s = "";`
  collapses to `nullToEmpty(rs.getString(…))`. This usually also removes an effectively-final shadow
  variable that only existed so a lambda could capture it
- **A throw on `DagExecutor.executeNode`'s success path costs more than one record.** The virtual
  thread's `finally` counts the latch down, but `processCompletion` is skipped, so dependents never
  reach the ready queue. The run then hangs iff the throwing node has a dependent (for UP) inside the
  target set that was not already ready — the coordinator polls an empty queue while those dependents
  still hold latch counts. Otherwise it *returns success* with the node's record missing and
  `executedCount` short, so a completed run is no evidence that nothing threw. Never move a call that
  can throw between `task.execute()` and `processCompletion`. The `catch` guards its `failureCount`
  bump with `failedNodes.add(...)` while the `else` branch does not; that asymmetry is load-bearing
  for two independent reasons — the `else` branch's own `propagateFailure` can throw and re-enter the
  `catch` after its `add` already succeeded, *and* another node's `propagateFailure` can add this one
  concurrently
- **The coordinator loop checks `failedNodes` twice, and both checks are needed.** The one before
  `semaphore.acquire()` avoids taking a permit for a node already known to be skipped; the one after it
  closes the window in which another node's `propagateFailure` marks this one while it waits for the
  permit — without it a node reported skipped still gets dispatched and its migration runs. Neither
  path counts the latch down, because `propagateFailure` already did for every node it marks
- **`propagateFailure`'s cone can contain a node that is currently running.** Its cone is
  `getAllDependents`/`getAllDependencies` over the whole graph, while `ReadyNodeTracker` counts
  in-degree only over dependencies inside `targetNodes` — so with `a→b→c` and `b` already applied,
  `targetNodes` is `{a, c}`, `c` starts ready, and under parallelism `a` can mark a running `c`
  skipped (counting its latch down a second time). Never write a tidy that assumes cone membership
  implies not-yet-started
- **The `status` summary counts one per *graph* node, while the rendered lines come from
  `ExecutionGraphView.renderLines`.** Both the CLI command and the Gradle task now take their counts
  from `StatusService` and their lines from the canvas, so the two agree only while every graph node is
  laid out exactly once (`renderLines` applies the label function per non-`VirtualNode`). A layout
  change that collapses or omits a node would make the summary disagree with what is printed, and no
  test would say so — the existing ones use 2-3 node graphs where the sets coincide
- **The write and read paths handle a throwing `MigrationNode.fingerprint()` differently on
  purpose.** `DagExecutor.fingerprintOf` degrades it to `null`, because the node's DDL has already
  committed and the record must still be written; `StatusService.NodeStatus.upContentState` reports
  `UNREADABLE`, because a report can say "your plugin is broken" where a stored token cannot. They
  look like the same try/catch — do not unify them, and do not "simplify" the status side back to
  `null`, which would make a plugin fault indistinguishable from a plugin that opts out
- **`StatusLineFormatter.markerFor` switches over `UpContentState` with no `default` arm on
  purpose.** Adding a constant to the enum then fails to compile until the renderer decides how it
  looks. Never add `default ->` to quiet that: a new state would silently render as `[✓]`, i.e. as
  "no change detected", which is the one answer a new state is least likely to mean
- **`StatusServiceTest`'s state table has three rows expecting `NOT_APPLICABLE` and none is
  redundant.** `pending` covers "never applied", `opt-out` covers "plugin returns null", and
  `pending-throwing` covers "never applied *and* the accessor throws" — the last one is the only
  thing holding `upContentState`'s `latestRecord == null` check above the `fingerprint()` read.
  Deleting it as a duplicate re-opens a hole where a broken plugin's pending node reads `UNREADABLE`
- **`HistoryRepository` is append-only, and `HistoryFingerprintUpdater` is deliberately not part of
  it.** Do not "simplify" by folding the capability in: `SynchronizedHistoryRepository` overrides every
  method explicitly, so an added `default` would be silently inherited as a throwing stub, and every
  third-party repository would be handed a mutation method it cannot honour. Nor try to implement
  fingerprint revision by *appending* a row — `wasExecuted` is "the latest row is UP and SUCCESS" in
  every implementation, so any appended row that is not a full fake apply makes the node read as never
  applied and `up` re-runs it. Revision has to be an in-place update of the existing row
- **A wrapped history repository loses the capability.** `instanceof HistoryFingerprintUpdater` is
  false for `SynchronizedHistoryRepository`, so callers must use what
  `ExecutionContext.createHistoryRepository()` returns (the raw implementation) rather than anything
  `DagExecutor` has wrapped
- **Spotless rewrapping is expected noise**, including on pre-existing violations on lines you touched.
  Never hand-pre-format; run `run_spotless` after the edits and re-verify green

## Small wins worth remembering

- When a loop pre-computes a value for a guard and then calls a helper that recomputes it, pass it
  through — then look again for values *derived* from it that the callee also recomputes
- When a test builds an object from a literal and later retypes that literal to construct an assertion
  path, read it back off the object (`definition.name()`) instead of extracting a constant

## Keeping these notes honest

Record only what changes a future Tidy: a trap that recurs, an invariant a tidier could break without
noticing. Never record what you did or when — that is git's job, and accumulated narrative is what made
these files unreadable.

Re-verify any claim here against current source before acting on it. Notes go stale silently: a whole
topic file in this directory once described a class that had since been renamed out of existence.

## Topic files — gated

Open one **only** when the cycle's diff touches its paths. If none match, you're done here.

| File | Open only when the diff touches |
|---|---|
| [jdbc_markdown_generator.md](jdbc_markdown_generator.md) | `jdbc/markdown/**`, `postgresql/markdown/**`, `mysql/markdown/**` |
| [jdbc_schema_info_provider.md](jdbc_schema_info_provider.md) | `jdbc/schema/JdbcSchemaInfoProvider*`, `mysql/schema/MySQLSchemaInfoProvider*` |
| [cli_main.md](cli_main.md) | `cli/Main.java` |
| [migration_node_fingerprint.md](migration_node_fingerprint.md) | `api/graph/MigrationNode.java`, `jdbc/JdbcMigrationNode*` |
