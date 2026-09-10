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
- **Every plugin-supplied accessor `recordSuccess` calls is a new fault path, and they do not all
  degrade alike.** `fingerprintOf` catches and returns `null`, so a broken accessor costs the token
  but keeps the success record — the DDL is already committed and losing the record re-applies it.
  `node.name()`, `node.target().id()` and `node.noWayBack()` are unguarded: they are inside
  `executeNode`'s try, so the latch accounting is safe, but the catch never calls `history.record` —
  so a throw leaves **no history row at all** for a migration whose DDL already committed, and the
  next run re-applies it. Reported failure, no record; that is worse than the recorded failure it
  looks like. Before adding another `node.*` call there, decide which of the two policies it gets,
  and say so.
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
- **`StatusLineFormatter.markerFor` and `AmendService.isDrifted` switch over `UpContentState` with
  no `default` arm on purpose.** Adding a constant to the enum then fails to compile until each
  decides what it means (`AmendService.planIncomplete` is deliberately outside this — see below). Never add `default ->` to quiet that: a new state would silently render as
  `[✓]`, i.e. "no change detected", and would silently fall outside what `amend` resolves — the two
  answers a new state is least likely to mean
- **`StatusServiceTest`'s state table has two rows expecting `NOT_APPLICABLE` and neither is
  redundant.** `pending` covers "never applied", and `pending-throwing` covers "never applied *and*
  the accessor throws" — the second is the only thing holding `upContentState`'s `appliedRecord ==
  null` check above the `fingerprint()` read. Deleting it as a duplicate re-opens a hole where a
  broken plugin's pending node reads `UNREADABLE`. (A third row, `opt-out`, once covered "plugin
  returns null" as `NOT_APPLICABLE`; that reading is retired — a declared node answering null is out
  of contract, so the row is now `no-token` and expects `UNREADABLE`.)
  `AmendService.entryFor` depends on that ordering for the one value it still asserts: its guard,
  `isClaimable`, treats a readable non-null fingerprint as established, and says so in a
  `requireNonNull` message. It does **not** assume a latest record — a migration never recorded or
  only rolled back is claimable and has none. Reorder `upContentState` and that becomes a reachable
  failure rather than an assertion
- **A `jdbc:h2:mem:<name>;DB_CLOSE_DELAY=-1` constant is shared by every test in the class.** The
  delay keeps the database alive past the last connection, so a second test that reuses the constant
  inherits the first one's `migraphe_history` rows and its schema — a migration the new test never
  applied reads as applied, and a node it expected to be drifted already agrees. `@TempDir` isolates
  the *files*, not the database. Give each test its own database name, or make the fixture take the
  URL. The symptom is a fixture that behaves as though a step were skipped
- **A guard that only ever fires on inputs another condition already excluded is not pinned.**
  `AmendService`'s `recordsASuccessfulApply` looked covered by a test named for it, but that test's
  fixture had only a DOWN row, so `upContentState()` returned `NOT_APPLICABLE` and the `== UNKNOWN`
  check excluded it first — deleting the guard left the whole suite green. The fixture has to reach
  the guard: an `upSuccess` with no fingerprint *followed by* a `downSuccess` makes `appliedRecord`
  the UP row (so the state is `UNKNOWN`) while `latestRecord` is the DOWN row, and only the guard
  stops `entryFor` writing a fingerprint onto a rollback's record id. When a test is named for a
  predicate, check that removing the predicate turns it red — **in every form that reads it**.
  Pinning it in one of two forms is the failure that recurred here: the guard and `isDrifted`'s
  `NOT_APPLICABLE` arm were each covered on `planIncomplete()` and left free on `plan(NodeId)`, which
  is the form a user types and the one that accepts `CHANGED`. `NOT_APPLICABLE -> true` there turned
  `amend <node>` on a node reporting no token into an NPE from `entryFor` and a stack trace. The
  reachable population has narrowed since — a plugin now has to supply a fingerprint — but a switch
  arm covered on one planning form and free on the other is the shape to watch for
- **A blocker type and its formatter are two cycles, not one.** The `UpBlocker`/`UpPlanFormatter`
  pairing makes creating them together feel like one step, but the formatter's subject is *wording* —
  a separately observable behavior with its own test class. Creating both inside a cycle whose red was
  about the blocker means the formatter's test goes green on its first run, with no red anywhere: a
  gate violation that cannot be repaired afterwards. Write the refusal text in its own cycle
- **"Cannot act on this" and "nothing to do" are different answers, and collapsing them reports a
  typo as a success.** `amend`, `up` and `down` all reach a state where an empty plan is honest —
  the migration is defined and already agrees — so emptiness cannot carry refusal. The repo's shape
  for this is a sealed blocker on the plan record (`UpBlocker`/`UpPlan.blocker()`,
  `DownBlocker`, now `AmendBlocker`) with the wording in a core formatter, so both front ends refuse
  identically. Two things a tidier could quietly break: an orphan must not be reported as "no such
  migration" — the history holds it, so that is a false statement — and a defined-but-agreeing node
  must not acquire a blocker, which would turn `Nothing to amend.` into an error
- **`AmendService`'s two planning forms are asymmetric on purpose, and the asymmetry is the safety
  argument.** `planIncomplete()` takes only `UpContentState.UNKNOWN` — the plugin has a token, the
  record has none — so it can only ever fill a gap, which is what makes a bulk form defensible.
  `plan(NodeId)` uses `isDrifted`, which also accepts `CHANGED`, where a token exists and differs and
  writing over it destroys what was recorded irrecoverably; that is why it demands a named node.
  Do not unify them on `isDrifted`, and do not "simplify" `planIncomplete` by reusing it: a bulk form
  that overwrites differing tokens is the exact operation the design forbids.
  `planIncomplete` compares against one constant rather than switching, so unlike `isDrifted` and
  `markerFor` it does **not** stop compiling when a state is added — deliberately. For a bulk form,
  silently excluding an unrecognised state is the fail-safe direction; for a marker that would
  silently render `[✓]`, and for `isDrifted` it would silently under-resolve, which is why those two
  keep the compile-stop
- **`HistoryRepository` is append-only, and adding a mutation method to it is not a simplification.**
  `SynchronizedHistoryRepository` overrides every method explicitly, so an added `default` is silently
  inherited as a throwing stub, and every third-party repository is handed something it cannot honour.
  Anything that revises what the history says is written as an appended row, not an edit — see
  [docs/ARCHITECTURE.md](../../../../docs/ARCHITECTURE.md) — "Drift and its repair" — for why that works and what a row carries
- **A capability detected by `instanceof` is lost through a wrapper.** `SynchronizedHistoryRepository`
  is not the thing that implements it, so callers must use what
  `ExecutionContext.createHistoryRepository()` returns (the raw implementation) rather than anything
  `DagExecutor` has wrapped
- **Gradle functional tests can reach a real history repository two ways, and the cheap one is easy to
  miss.** `type: noop` gives every task a fresh `InMemoryHistoryRepository` with no driver and no
  database, which is enough to exercise `withExecutionContext` → `createHistoryRepository` →
  `initialize()` → any read that an empty history answers. Only tests that need *applied* rows need
  the other way: a resolvable configuration (`jdbcPluginJar`) handed to the test JVM as a system
  property and declared in the generated build script as `migraphePlugin(files(...))`, following the
  `generatorJsonJar` precedent. In that second form the database must be **file-backed** — TestKit
  runs the build in its own daemon JVM, so an in-memory H2 is invisible to the assertions
- **A scoping test needs an assertion in both directions, and a shared fixture can destroy one of
  them.** Three cycles in a row shipped a test that pinned "X is in scope" and omitted "Y is not",
  and each time the omitted half was the destructive one. Worse, running two forms in sequence inside
  one test can make the second assertion vacuous: once the first run has resolved a node, the drift
  set has shrunk, and a whole-set implementation returns the same answer as a scoped one. If a test
  exercises two scopes, give each its own arrangement — or prove the second still discriminates by
  mutating the call it is supposed to pin
- **Splitting a guard from the dispatch it guards is only safe while the new input is inert.** Adding
  `--migration` to `MigrapheAmendTask` one cycle before the code that interprets it left a live path:
  the flag passed the guard and fell through to the old whole-drift-set call, so naming one migration
  amended every drifted node, unprompted, overwriting fingerprints that cannot be recovered. Reaching
  for that split because a previous audit said `scope: too-large` is the mistake — the earlier finding
  was about an *unverified* behavior, not about arity. Before deferring the dispatch, ask what the
  declared-but-unwired input reaches; if it reaches the destructive branch, the two are one step
- **The CLI takes a migration positionally; the Gradle task cannot, so it needs an option — and the
  option is `--migration`, not `--target`.** In migraphe a *target* is a connection (`targets/*.yaml`),
  which is what `history.target` and a task's `target:` both name. `MigrapheDownTask`'s `--target` is a
  node id despite that and predates the distinction; do not copy it into new tasks. `migrapheAmend`
  uses `--migration`, matching what the CLI help calls the positional argument
- **Adding a Gradle task means editing `MigrapheGradlePlugin`'s class javadoc too.** It enumerates the
  task names and its `apply` javadoc counts them ("the six Migraphe tasks"); both go stale silently
- **`ExecutionContext.createHistoryRepository()` must never return a repository that discards writes.**
  It used to fall back to `InMemoryHistoryRepository` when `history.target` named nothing configured, so a
  typo made every run apply its migrations to the real database and then throw the record away — silently,
  with `validate` still exiting 0. It now throws. Any future "be lenient here" fallback re-opens that hole;
  the only safe leniency is refusing to run
- **The applied state is decided by the most recent *successful* record, and `wasExecuted` /
  `executedNodes` must agree.** Non-SUCCESS records never change it: a failed rollback leaves the node
  applied, because nothing was undone. Both used to read the latest record of any status, which made a
  DOWN FAILURE row report the node as never applied while its objects still stood. The two are separate
  queries in each implementation, so a change to one silently desynchronises them — the interface javadoc
  now states the rule, and every implementation is bound by it
- **`NodeStatus` carries two records because they answer different questions.** `latestRecord` is what
  last happened; `appliedRecord` is the most recent successful UP, which is what actually put the node in
  its current state. Everything about the applied state — the fingerprint comparison above all — reads
  `appliedRecord`; only the display annotation reads `latestRecord`. Collapsing them re-creates the bug
  where a node whose rollback failed reported `[?]` (the failed row carries no fingerprint) and rendered
  its apply as having taken 0ms. `StatusService` imposes the order on `allRecords` itself rather than
  trusting it: the interface declares none, and the two shipped implementations differ
- **A node that cannot be rolled back freezes what it stands on, never what stands on it.** Removing a
  node's dependency breaks it; removing something that depends on it does not. So the frozen set is
  `{no down task}` plus their transitive *dependencies*, and the remainder stays closed under
  dependents — which is exactly what makes rolling the remainder back in reverse order safe. Reversing
  that direction, or dropping the exclusion so a partial set runs, re-creates the corruption where
  `down --all` removed a node's dependencies and left the node itself recorded as applied
- **`undeclaredIrreversibleNodes` is deliberately outside `MigrationGraph.validate()`.** `validate()`
  runs on every `ExecutionContext.load`, and a project in this state must still be able to report its
  status — the same reason a dangling dependency taking `status` down was a defect. What stops is a run
  that would *apply* something. Moving the check into `validate()` reintroduces that
- **After `up` refuses an undeclared irreversible task, "the author forgot the rollback" is a
  legacy-only state.** A new apply is now always either reversible or declared one-way, so a node that
  is applied, has no rollback and has no declaration can only come from history written before the
  rule. Tests for that path cannot reach it through `up` and have to write the history row directly
- **In `status`, orphans get a footer block, never a marker.** `status` is built from the
  definitions, so an applied migration the task files have lost is not in the graph it walks and
  `ExecutionGraphView.renderLines` has no line to hang a marker on. Each one is placed in the target
  *its own row* names, so deleting the last task pointing at a target does not hide that target's
  rows. This is `status` only: `down` and `rebuild` plan over the run graph, which carries a node for
  every applied row, and there they are ordinary nodes
- **What can come down is asked of the row and the target, never of the definition.**
  `DownService.canComeDown(node, appliedRow)` is true only when the row kept a payload *and* the
  node's target is a `DownTaskRestorer` — the two conditions the executor itself applies. A task
  file declaring a `down:` says nothing about it: the rollback runs the payload the row kept. While
  this read `node.downTask()`, a plan listed migrations the run then refused one at a time,
  mid-run. It is computed over the graph the rollback will execute against, which is why
  `rollbackBlockers` takes one. Orphans are nodes of that graph like anything else — the run graph
  carries a node for every applied row the definitions have lost — so the closure sees them and no
  separate path plans them.
  **Consequence for fixtures**: a row's payload has to match what its node declares, because that is
  what `up` writes — a node with a `down:` records one, a node with `no_way_back:` records none.
  Eight tests across three classes had it the other way round and only failed once the rule moved
- **A cycle is fatal at load; an unresolved dependency is not.** No order satisfies a cycle, so nothing
  can be built from that graph. An unresolved dependency is incompleteness — the description points
  outside itself, which is exactly what deleting a task file leaves, and the node it names is often
  sitting in the history as applied. Throwing on it took `status` and `down` down with it, so the
  project could not even be diagnosed. Keep the split: `load` throws only on cycles, `validate()`
  still reports both, and the commands that would *apply* something refuse
- **`createNode` exists in three near-identical copies** — `JdbcMigrationNodeProvider`,
  `PostgreSQLMigrationNodeProvider`, `MySQLMigrationNodeProvider` — differing only in the environment
  type check and its message. Anything added to one and not the others is dropped silently for two of
  the three shipped plugins, which is how `no_way_back` reached `validate` but not `up`. When adding a
  field, change all three, and put the test in `MySQLPluginTest`/`PostgreSQLPluginTest` too: a test
  written only against the provider you just edited proves nothing about the other two. Merging them
  is not a tidy — the wrong-environment exception type and message are pinned by tests
- **Never undo a mutation check with `git checkout --` while the work is uncommitted.** The mutation
  and the change being verified live in the same file, so restoring from HEAD throws away both. Copy
  the file to the scratchpad first and restore from the copy, or commit before mutating. This has
  already cost one file of finished work
- **`--preview` reports what running would report, minus the execution — exit code included.**
  Everything that refuses a run is decided before anything runs, so a preview that exits zero on a plan
  the real run would fail is not a rehearsal, and the CI use of `--preview` silently stops working. The
  only outcome a preview cannot predict is a failure during execution
- **`MigrationGraph` walks dependencies two ways on purpose.** `getAllDependencies`/`collectDependencies`
  follow the **adjacency list**, which `fromNodesUp`/`fromNodesDown` narrow to the supplied subset — so
  every id they yield is a node that exists in the graph. `canonicalTransitiveDependencies`/
  `collectDeclaredDependencies` follow `MigrationNode.dependencies()` instead, and therefore include a
  declared id whose node is absent. Neither can be expressed as the other. Folding them together breaks
  one of two things: `DagExecutor.propagateFailure` builds its cone from the adjacency form and would
  start marking ids that are not nodes, or a fingerprint over the declared form would shrink whenever a
  task file is deleted, reporting an untouched dependent as edited.
  The declared form still shrinks in one case: a deleted node's own declarations are gone, so a
  dependent's closure loses whatever stood behind it *and is not also reachable along another declared
  path* — a diamond survives the deletion untouched. Nothing in the graph recovers it, but the history
  could: a `dependencies` column would let an absent node's declarations be read from its recorded row.
  Weigh that against making the fingerprint depend on the history rather than the definitions alone.
  `canonicalTransitiveDependencies_losesWhatStoodBehindADeletedNode` and
  `canonicalTransitiveDependencies_keepsWhatASecondDeclaredPathStillReaches` pin both halves
- **An ordering assertion over a `HashSet`-derived list can be vacuous.**
  `MigrationGraph.getAllDependencies` returns a `HashSet`, and short ASCII identifiers below 2^16 hash
  to ascending buckets — so `"a"`, `"b"`, `"z"` iterate in sorted order already and
  `containsExactly(a, b, z)` passes against an implementation with no sort at all. Insertion order does
  not help; the bucket index decides. Use identifiers whose bucket order differs (the project's real
  `db1/001_a` form does), and prove it by deleting the `.sorted(...)` and watching the test go red — a
  red that was only a *compile* failure never executed the assertion, so it demonstrates nothing about
  its discriminating power
- **A test double that ignores its argument hides a wiring bug in the caller.** `FingerprintedNode`
  returns a fixed token and `ThrowingFingerprintNode` always throws, which is right for what each one
  models — but it means every `StatusServiceTest` / `AmendServiceTest` case passes whatever list
  `StatusService` hands to `fingerprint`, including the wrong one. `DependencyEchoingNode` returns the
  argument. Two tests use it, one per direction — `upContentStateComparesAgainstTheTokenTheClosureProduces`
  (read path, `StatusService`) and `shouldPassTheTransitiveClosureToTheFingerprint` (write path,
  `DagExecutor`) — and each is the only test in the repo that fails when its own call site is fed
  `List.of()` or `node.dependencies()`. Both fixtures put a node two hops away so the transitive form is
  required, not merely the direct one. Keep all three doubles: replacing the fixed-token one with the
  echoing one everywhere would make every expected value in `upContentStateDistinguishesEveryCase`,
  `StatusLineFormatterTest` and `AmendServiceTest` a function of fixture graph shape, and those tests
  would stop being about the states they are named for.
  Three call sites now each have one such test — `StatusService`, `DagExecutor`, `AmendService` — and a
  fingerprint fixture must put a node **two hops** away or the direct-dependency mistake passes.
  Do not try to pin the canonical *order* from one of these call-site fixtures. `collected` is a
  `HashSet`, so dropping the sort yields bucket order, not traversal order — whether a two-element
  fixture then disagrees with ascending order is luck, and a fixture that happens to agree proves
  nothing while looking like it proved something. The ordering rule has a home test that chooses its
  ids for the purpose: `canonicalTransitiveDependencies_returnsTheClosureSortedByNodeIdValue`
- **A signature change on `migraphe-api` is not covered by running the modules that use it.**
  `migraphe-api` has its own tests, and they characterize the SPI's defaults — `MigrationNodeTest`
  pins that `fingerprint`'s `default` returns null. Every other module's run only needs
  `:migraphe-api:compileJava` and `:migraphe-api:jar`, so `:migraphe-api:compileTestJava` never
  executes and a missed call site there stays invisible through a green core/jdbc/cli/gradle sweep.
  After changing anything in `migraphe-api`, run `migraphe-api` itself, and grep for the old signature
  across **every** module's `src/test`, not just the plugin modules
- **A destructive command asks every blocker before its first destructive step, and `--preview` must
  return what the real run would.** `DownCommand` states the rule — "a preview that exits zero on a
  plan the real run would fail is not a rehearsal" — and `RebuildCommand` broke it: it ran the DOWN
  executor and only then consulted `UpService.plan(null).blocker()`, so a task with neither `down:`
  nor `no_way_back:` left the database torn down with no way forward, and the preview said nothing.
  Hoisting the check is safe precisely because both `UpBlocker` variants read
  `graph.unresolvedDependencies()` / `graph.undeclaredIrreversibleNodes()` — the graph alone, not the
  history — so the answer cannot change across the rollback. Check that property before hoisting any
  other blocker.

- **`PayloadProvidingTask` (a test double for an UP task) returns `downSql` as its signature**, which
  is exactly what the `Task.signature()` contract forbids. Harmless while nothing calls `signature()`
  in production; it becomes a fixture proving the opposite of the rule the moment a node composes
  signatures. Fix it in the cycle that wires that up, and do not copy the pattern — the same shape
  (a label standing in for content) has compiled and passed at five hand-off sites in this project.

- **A task signs its own direction only.** `JdbcUpTask` holds `downSql` and `autocommitDown` so it
  can record them, but signs neither: whoever composes the fingerprint asks the DOWN task for that
  half, and including it in both would count the rollback twice. The test that catches it varies
  `downSql` and `autocommitDown` and asserts the UP signature is *unchanged*.

- **Moving a serializer between classes is the moment to pin what its old home never pinned.** The
  type-directed renderer arrived in `SimpleMigrationNode` with its shape brackets (`[` list, `{` set,
  `<` map) and its `strip()` both unexercised — deleting either left the suite green. Porting it into
  `SimpleTask` was free cover: one assertion that a list and a set of the same elements differ kills
  all three bracket pairs at once, and one that `"  X  "` and `"X"` agree kills the `strip()`.

- **A regex that adds a required builder call will also "fix" the test that exists because the call is
  missing.** Sweeping `.upTask(SimpleTask.of(X))` → `+ .upContent(X)` across core's tests silently
  repaired `refusesToBuildWithoutTheContentItsFingerprintIsOver`, whose whole point is the omission.
  After any bulk edit over test sources, re-read the tests that assert a *refusal*.

- **Serializing content for a fingerprint: `List` keeps its order, `Set` and `Map` must be sorted.**
  A `HashSet`'s iteration order is not something a token recorded on one machine may rely on when it
  is compared on another, and the fingerprint contract demands stability across platforms. Sort on the
  *serialized* element (and on serialized key+value for a map), not on the object, so nested lists and
  maps sort by what they say. `LinkedHashSet`/`LinkedHashMap` fixtures in two different orders are what
  catch a dropped sort; a `Set.of(...)` fixture will not, because its order is already unspecified.

- **Removing a `default` is not the same as making a method non-null, and NullAway is what tells you
  apart.** `MigrationNode.fingerprint` lost its `default null` so no plugin inherits an opt-out; it
  kept `@Nullable`, because `OrphanNode` presents a history row and a row can carry no token. An
  earlier attempt tightened both at once and failed to compile — which is the cheap way to learn that
  the design note said "loses its default", not "becomes non-null". Read the decision's words before
  widening what it licenses.

- **`LayoutTree.VirtualNode` implements `MigrationNode` and is not a migration.** Any new required
  member has to be answered there too, and the class's established answer is to throw with a message
  saying why — `upTask()` already does. It is a synthetic rendering root; do not give it a plausible
  value.

- **A second fingerprint implementation is bound by `migration_node_fingerprint.md`, even when the
  diff touches neither gated path.** That file's rule — state only what a test pins, and phrase every
  reason as a trade naming its own cost — is about fingerprint javadoc, not about one class. Writing
  `SimpleMigrationNode`'s produced the same two failures it records: a claim that an absent rollback
  differs from a blanked one, and that the closure's order is the caller's, neither of which any test
  pinned until the file was read.

- **Giving a node a fingerprint without giving its provider the content is a silent wrong answer.**
  `SimpleMigrationNode` returning a token while `NoopMigrationNodeProvider` passed no content made
  every `noop` node hash identically, so editing a task's `up` read as `UNCHANGED` — worse than the
  `null` it replaced, which at least meant "not comparable". Whenever a node starts producing a token,
  check that every provider constructing it supplies the content that token is over.

- **`LayoutTree`'s `NonTreeEdge` is a spanning tree, not a transitive reduction.** It attaches each
  node to exactly one parent and calls every other parent edge non-tree, so it drops the second side
  of a diamond — an edge a reduction keeps. It exists to route ASCII connectors, and it is the wrong
  thing to reach for when comparing two DAGs. `MigrationGraph.transitiveReduction()` is the right one;
  its diamond test is what tells the two apart, so do not delete it as redundant with the a→b→c case.

- **A compile-error red proves the method is absent, not that the assertions measure anything.** Both
  reduction tests were red only because `transitiveReduction()` did not exist. Each assertion needed
  its own mutation — "reduce nothing" kills the a→b→c case, "keep one parent" kills the diamond — and
  only running both shows the two tests are not the same test twice.

- **A shared `@Container` plus identical node ids across methods is a hidden ordering dependency.**
  `@TempDir` is per-method, so the project files are fresh, but the database is not: in
  `RebuildCommandTest` both methods build `users` and the same `db/00N_*` ids, so whichever runs first
  decides what the second one measures. The failure mode is silent — the second method's
  "the object is still there" assertion passes because the *other* method left it there, and the
  mutation that should kill it survives. JUnit's default order is deterministic, so this does not
  flake; it goes wrong the day a method is renamed or added. Drop the objects in `@BeforeEach`.

- **Capture stderr per invocation when a test runs the same command twice.** One shared
  `ByteArrayOutputStream` accumulates both, so `contains(...)` cannot say which invocation produced
  the message — and "the preview reported it" is usually the half that matters.

- **Asserting "the database was not touched" needs the failure to happen after the destructive step,
  not before it.** A test that asserts an exit code *and* a surviving database object proves only the
  exit code if the command bails at the first assertion's condition. Reconstruct the exact pre-fix
  ordering — here: preview reports, the real run still tears down first — and check the *object*
  assertion is the one that fails.

- **A plan's blocker and its empty target set are different answers, and a front end that drops the
  blocker fails nothing.** Every blocked plan is also empty, so a front end that reads only
  `plan.toRecord().isEmpty()` / `plan.targetNodes().isEmpty()` reports the *honest-success* line —
  `Nothing to amend.`, `No pending migrations` — for an id it cannot act on at all, and every existing
  test still passes. There are six sites of this shape (`UpBlocker`, `DownBlocker`, `AmendBlocker`
  x CLI command + Gradle task); each reads its blocker immediately after building the plan and
  *before* its own empty branch. A tidier reordering those two `if`s reintroduces the bug silently in
  the CLI (wrong exit code, no stderr) and in Gradle (build succeeds). The Gradle three also agree on
  `String.join(System.lineSeparator(), XPlanFormatter.format(blocker))` inside a `GradleException` —
  match that, not the CLI's `forEach(System.err::println)`. Extracting the six into one helper is a
  design decision, not a tidy: they differ in how they surface the lines and in the exit contract.

- **Three `MigrationNodeProvider`s duplicate the whole `createNode` body, and each module tests only
  itself.** `JdbcMigrationNodeProvider`, `PostgreSQLMigrationNodeProvider` and
  `MySQLMigrationNodeProvider` each read the same `SqlTaskDefinition` and build the same
  `JdbcMigrationNode.builder()` chain; only the environment cast and the exception type differ. A
  field added to `SqlTaskDefinition` and wired into one of them **compiles and goes green while doing
  nothing on the other two** — the two DB-specific plugins are the ones real projects use, and
  `withValidateUnknown(false)` at `ConfigLoader.java:430` means an unread key is dropped rather than
  reported, so it fails at execution and stays silent in `validate`. After touching any of the three,
  grep the other two. Collapsing them into one is a design decision, not a tidy: they differ in the
  environment type they demand and in which `JdbcException` subclass they throw.

- **These notes are an agent-process file; never cite them from published javadoc.** A javadoc line
  ending "see the note in the tidy references" reaches a plugin developer reading the jar, for whom
  `.claude/skills/**` does not exist. When a rule here explains why code is shaped a way, restate the
  reason in the javadoc or leave the javadoc silent — do not link out of the product into the harness.

- **Two repositories rebuild `ExecutionRecord` component-by-component, so a new component silently
  becomes `null` in them.** `JdbcHistoryRepository`'s row mapper calls the canonical constructor
  with a field list rather than copying the record, so adding a component compiles everywhere only after both are touched —
  and because every added component so far is `@Nullable`, forgetting one yields a silently empty
  value rather than a failure. Adding a component also breaks every direct `new ExecutionRecord(...)`
  in the test tree (currently jdbc, mysql and core); the factories are what most call sites use, so
  grep for the constructor, not for the factory names.

- **`JdbcUpTask.create`'s four-argument form defaults the rollback's mode to the apply's.** That is
  deliberate for a caller that genuinely has one flag, but it means a caller which *does* know both
  and reaches for the short form loses the distinction silently — the task still runs, still records
  metadata, and records the wrong mode. Only `JdbcMigrationNode.upTask()` calls it in production, and
  it must use the five-argument form. If the four-argument form ever has no production caller left,
  delete it rather than leaving a footgun that compiles.

- **A capability interface must not impose a rule its only implementation breaks.** `DownTaskRestorer`
  first told implementations not to guess when the recorded metadata is absent, while
  `JdbcTarget.restoreDownTask` falls back to a transaction — exactly the javadoc-versus-code
  mismatch `migration_node_fingerprint.md` exists to stop, one package over. When an interface has one
  implementation, every "implementations must" sentence is really a claim about that implementation:
  either it holds there or the sentence is wrong.

- **A history column whose size can grow unboundedly needs `LONGTEXT` on MySQL, not `TEXT`.**
  `TEXT` is 65,535 bytes. `fingerprint` and `plugin_metadata` are bounded by their content and are
  fine. `dependencies` was the motivating case while it held a transitive closure — a chain of ~2,500
  tasks with 25-character ids overflowed it — and it now holds declared direct edges, so its width
  follows a node's fan-in and that particular overflow is out of reach. The column stays `LONGTEXT`;
  the *reason* it was chosen is retired. The failure mode is what makes the type worth keeping: the
  INSERT fails *after* the migration's own DDL committed, so `DagExecutor`'s guarded catch reports the
  node failed although it was applied — and on a non-strict server it truncates silently instead.
  `serialized_down_task` is already `LONGTEXT` for the same reason. PostgreSQL and H2 `TEXT` are unbounded, so this is a MySQL/MariaDB-only
  decision and the generic resource keeps `TEXT`. Before adding a column, ask whether its width is a
  function of the project's size.

- **The recorded plugin metadata has a hand-rolled writer and a `Properties.load` reader, in
  different classes.** `JdbcUpTask` builds `autocommit.down=<bool>\n` by concatenation —
  `Properties.store` is unusable because it emits a non-deterministic `#<date>` header — while
  `JdbcTarget.restoreDownTask` parses it with `Properties.load`. Nothing makes the two agree
  except `JdbcTargetTest.restoresTheModeTheUpTaskActuallyWroteDown`, which runs the real up task
  and feeds its output to the real restorer. Renaming the key on either side, or adding a key that
  needs escaping the writer does not do, has to fail that test — if you change either half, check it
  still exists.

- **A `Task` capability, not a core helper, is where "what would this record?" belongs.** Decision 8b
  makes `serialized_down_task` and `plugin_metadata` the plugin's, with core storing them unopened, so
  core cannot assemble a payload for a command that writes the history without executing — it has to
  ask. `RollbackPayloadProvider` on the up task is that ask, and `JdbcUpTask` can answer it because
  both values are derived from the definition alone. A task whose payload depends on what the run
  observes must decline the capability rather than return a guess.

- **Inserting a method before an existing one orphans that one's javadoc onto the newcomer.** This
  has now happened twice — `SimpleMigrationNode.build()` and `MigrationGraph.transitiveReduction()` —
  because a scripted insert anchored on `public ... methodName(` lands *after* the javadoc block, not
  before it. Neither `run_test` nor `run_spotless` notices; only `run_errorprone_check` does, via
  javac's `コメントなし` on the now-undocumented method, and that is a 12-minute clean build at
  session end. Anchor the insert on the javadoc's opening `/**`, and re-read the two methods either
  side of any scripted insertion.

- **`run_test` never runs the `javadoc` task, so a stale `{@link}` survives every green run.**
  Changing a method's signature invalidates every `{@link Type#method(ParamType)}` that names it —
  seven of them for one parameter change here, across three modules — and the build only fails at
  `run_errorprone_check`. After any signature change, grep for the old parameter list in `{@link}`
  form before believing a green suite.

- **`JdbcUpTask`'s class javadoc enumerates the capabilities it implements.** Adding one and leaving
  the javadoc listing only `SqlContentProvider` goes stale silently — the same shape as
  `MigrapheGradlePlugin` counting its tasks. Check the class comment whenever the `implements` clause
  changes.

- **Attributes derived from one definition are written together or not at all.** Offering them as
  separate calls lets a row end up with a fingerprint folded over one state and other columns holding
  another, and internal consistency of a row is the one thing its readers must be able to rely on. Do
  not split such a write "for flexibility". Corollary: when a write widens, check whether every caller
  has the new value to hand; supplying a placeholder to make it compile is *worse* than not writing —
  it asserts something false. A caller without the value is the design question, not the signature.

- **Changing a contract means hunting assertions that encode the old one, not just call sites that
  fail to compile.** Widening a write to cover one more attribute broke three tests that compiled
  fine and asserted the opposite contract — `isEqualTo("DOWN SQL")`, i.e. "nothing else changes". The
  compiler finds the arity change; only reading each assertion finds the ones now asserting the wrong
  thing. Grep the fields the new contract touches, not only the method name.

- **`amend` has one form, and it writes every attribute the definition determines.** There was a
  bulk form for rows carrying no fingerprint; it is gone, and filling those rows is `upgrade`'s job —
  which touches only columns that are absent, the safety property the bulk form never actually had.
  What states amend's meaning is `AmendNotice`: an unconditional statement of what gets replaced,
  rendered identically by both front ends. Do not reintroduce a per-row warning: a row without one
  reads as untouched, which is false for every row in an amend plan.

- **Every place that hands a transitive closure onwards needs its own `List.of()` mutation check —
  and the fix was to stop handing it over.** This was the same defect five times: `DagExecutor`,
  `StatusService`, `AmendService.entryFor` and `AmendService.apply` each passed the closure to
  something, and in each case replacing it with `List.of()` compiled and failed nothing until a test
  was written for that specific hand-off. `MigrationGraph.fingerprinterFor` removes the hand-off:
  the closure is captured where it is computed and no caller can supply one. When a value has been
  mis-passed this often, look for the parameter to delete rather than the test to add. The
  fixture must put a node **two hops** away, or `node.dependencies()` passes too, and for
  `AmendService` the target node must be wrapped in `DependencyEchoingNode` — a plain test node
  reports no fingerprint, so `planIncomplete` never selects it and the plan is empty for a reason that
  looks like the assertion failing.

- **A dependents-cascade fixture has to hang the extra node off the node that changed.** In
  `RebuildServiceTest` the first attempt put the unapplied node `d` under `a` while the *changed* node
  was `b`, so `getAllDependents(b)` never saw it and the "unapplied dependents are excluded"
  assertion was vacuous — the mutation that drops the `applied` filter survived. ErrorProne says so
  too: removing the filter makes the `applied` set `ModifiedButNotUsed`, which is a second, free
  signal that nothing reads it. When a test is about which dependents are swept in, every fixture
  node has to be a dependent *of the node under test*, not merely somewhere in the graph.

- **A fixture where every edge target happens to be present cannot tell `MigrationGraph.create()`
  from `fromNodesUp()`.** `fromNodesUp` silently drops dependencies pointing outside the supplied
  list; `create()` + `addNode` keeps them. Any test about what edges a graph holds needs at least one
  edge whose target is *absent*, or both implementations pass and the choice between them is
  unpinned. This is the same shape as the closure hand-off defect: the discriminating input is the
  one the happy-path fixture never contains.

- **A node's fingerprint has to be folded through its own graph's `Fingerprinter`, and swapping the
  two sides is invisible on the history side.** `MigrationGraph.fingerprinterFor` captures that
  graph's dependency closure, so `declaredNode.fingerprint(history.fingerprinterFor(id))` folds the
  recorded edges into the declared token. It compiles, and it cannot be caught by watching the
  history side: `RecordedNode` ignores the argument entirely and returns its recorded token either
  way. Only the definitions side shows the damage, so **every fixture comparing the two sides must
  give at least one node a declared dependency the recorded closure does not have** — with both
  closures empty, the swap is green and the pairing is unpinned. The history half of the contract is
  pinned separately, by `RecordedNodeTest > fingerprint は記録された値そのもの — 計算し直さない`.

- **A node id does not carry its target, so an id match is not an agreement.** The id comes from the
  file path and stays put when `target:` is edited. Whatever mechanism catches that — today the token
  covers `target:` — must not be removed as a redundant key check: without it the two sides read as
  agreeing while the old target still holds the objects.

- **A refusal that names what happened is not redundant with a lower-level throw that does not.**
  `MigrationGraph.addNode` already rejects a repeated `NodeId`, so a check above it looks removable —
  but its `IllegalArgumentException` names neither the targets involved nor what to do. Deleting the
  check would replace a refusal that says what to do with one that says "Node already exists".

- **Two `MigrationGraph` predicates answer wrongly on a graph built from history rows.**
  `undeclaredIrreversibleNodes()` reads `downTask()`, which a `RecordedNode` returns as `null` by
  design — the rollback is rebuilt from the row's payload by `DownTaskRestorer`, not held as a task —
  so it flags every recorded row that carries a perfectly good payload and no `no_way_back`. Reporting
  the row's reason narrowed that to most rows rather than all of them; the predicate still cannot be
  pointed at the recorded side. And `validate()` / `unresolvedDependencies()` report every
  deleted-but-recorded dependency as dangling — which on the history side is the normal, expected
  state, not a fault. Neither is a bug in those methods: they were written for the graph the
  definitions describe. Never run a graph-level check on the recorded side without asking first
  whether its answer means anything there.

- **Spotless rewrapping is expected noise**, including on pre-existing violations on lines you touched.
  Never hand-pre-format; run `run_spotless` after the edits and re-verify green

- **An SPI signature change is not finished in the source.** `docs/PLUGIN_DEVELOPMENT.md` and its
  `.ja.md` twin carry a literal copy of `HistoryRepository`'s method list, and nothing compiles it, so
  a half-updated copy sits there reading as current. Grep both for the *retired* signature rather than
  for the method name you changed

- **Anything derived from `latestApplies()` inherits `HashMap` ordering.** The default folds the rows
  into a map and returns `List.copyOf(values())`, so a list built by walking it is in no order at all.
  That is invisible in a test with one element and shows up as output that reorders between runs;
  whatever is displayed or compared needs an order imposed where it is produced

- **`MigrationGraph` has two edge sets.** `hasCycle()`, `getDependencies` and the layout walk the
  **adjacency list**; `canonicalTransitiveDependencies`, `transitiveReduction`, `nodesOnCycles` and
  the fingerprint closure read what the nodes **declare**. On cycle *existence* they agree
  everywhere — `addNode` copies one into the other, `fromNodesUp` drops only the edges the declared
  walk stops at anyway, and `fromNodesDown` reverses, which no cycle survives or appears under. What
  keeps `hasCycle()` from delegating to `nodesOnCycles()` is cost, not correctness: one O(V+E) DFS
  against V closure walks that each allocate and sort, on a path `validate()`, `ExecutionContext`
  and `TopologicalSort` run every command

- **A message assembled from a template pins less than it looks.** `hasMessageContaining` twice is
  order-insensitive and blind to whatever sits between the two matches, so the separator, the line
  order and any prose between them are unasserted. Assert the whole block — leading separator
  included — when the layout is part of what the message is for.
- **An assertion that claims to pin an *order* usually does not.** A small `HashMap`/`HashSet` often
  iterates in sorted order by accident — five short keys is enough — so `containsExactly` passes with
  the sort deleted. Prove it: remove the `sorted(...)` from production, watch the test fail on order
  with identical membership, put it back and confirm `git diff` is empty. Identifiers picked for
  readability are the ones that coincide; the path-like ones already in `MigrationGraphTest` do not

- **A node standing for a history row reports no down task of its own.** `RecordedNode.downTask()` is
  `null` on purpose: the rollback is the payload its row carries, restored by the target. So anything
  that reads `downTask() != null` as "this can be rolled back" — `DownService.rollbackBlockers()` is
  the one that does — must read the **declarations**, never the graph a plan carries. Pointing it at
  the plan's graph freezes every migration the history placed somewhere the definitions no longer do,
  which is exactly the set that most needs to come down

- **Every command builds its own `DagExecutor`, twice over.** The CLI command and the Gradle task
  each construct one from a graph they choose themselves, so a rule about *which graph a phase runs
  over* lands in four places and a change that reaches one leaves the other three running the old
  one — silently, because both compile and both are green. `down` took its graph from the plan while
  `rebuild` still took the declarations for months. When a service starts deciding something about
  execution, put it on the plan record and grep for `new DagExecutor(` to find everyone who has to
  read it

- **A rollback runs the row's payload or nothing.** `DagExecutor.taskFor` has no fallback to
  `node.downTask()` in the DOWN direction: substituting the definition would run D where H was
  asked. That makes `DownTaskRestorer` load-bearing rather than optional — a target that does not
  implement it cannot roll anything back, and the executor refuses instead of quietly running the
  task file. `SimpleTarget` implements it (returning `SimpleTask.of(payload)`), which is what keeps
  every core DOWN test exercising the recorded path; a fixture that wants a *failing* rollback must
  supply it through a restoring target, because a failing `downTask()` on the node is now never
  reached

- **A `down` fixture's rows need fingerprints.** The fingerprint is the one marker of a complete row:
  a row carrying none predates the columns the definitions determine, so nothing in it can be read at
  face value — including the edges a rollback orders itself by — and `down` stops rather than
  guessing. Only `up` (which asks `wasExecuted` alone) and `status` (which must be able to report a
  project in that state) are exempt. So the short `ExecutionRecord.upSuccess(id, target, name,
  payload, ms)` overload builds an *incomplete* row: use it only when the test is about that, and
  pass a token otherwise. Two dozen fixture rows across `DownServiceTest` and
  `DagExecutorRollbackTest` used the short form and froze the moment the rule landed

## A node has more than one history row

`amend` appends rather than rewriting, so "the one history row" stopped being a thing. Anything that
reads the history — production or test — must order the way everything else does: the newest
`direction = 'UP' AND status = 'SUCCESS'`, `ORDER BY executed_at DESC, id DESC` (the id breaks a tied
timestamp; see decision 24). A bare `SELECT … FROM migraphe_history` returns the *oldest* row on H2,
because ids are UUIDv7.

Four functional tests and one `MainTest` case read the first row and went green for the wrong reason
until amend appended; a module-scoped run does not catch them, because they live in
`migraphe-gradle-plugin` and `migraphe-cli`. **After changing what `amend` writes, run `run_build`,
not the module.**

The same rule is a live production bug where it is not yet followed: anything that folds *every* row
rather than the latest applied one keeps reading a superseded claim forever.

**The "latest applied" rule has exactly one implementation**: `HistoryRepository.latestApplies()`, a
default method over `allRecords()`. `DownService`, `StatusService` and `RecordedGraph` all read it;
the folds they used to carry are gone. **Do not write another.** It returns **one row per identifier**: it walks
each identifier's successful rows in order and keeps the placement standing at the end. Two shapes
break it, both tried: filtering to `UP` before folding rather than walking resurrects a placement a
later `down` removed; and keying by `(node, target)` reports a migration rolled back and re-applied
elsewhere as standing in two places, because the rollback supersedes nothing outside its own pair. The
walk is also the integrity check — a second `EXECUTED` apply landing while the migration already stands
is refused, judged by `origin` and never by the target — so **it throws**, and a caller that folds its
own copy loses that. What is *not* that rule, and stays
local, is the newest row of *any* kind (`StatusService`'s `latestRecord`): no repository read answers
it, and it is a different question — a failed rollback is the newest thing a node has and applied
nothing. **Reading it where the applied row was meant is a permanent hide, not a transient one**:
the bulk amend that used to fill absent fingerprints skipped every node whose apply had since been
followed by a failed rollback or a failed re-apply, and nothing later makes an old row newer, so
those nodes were out of every run forever. When a predicate asks "was this applied", `appliedRecord()` is the field; `latestRecord()`
answers "what happened last". Wherever recency is judged, sort by `executedAt` **then by id** — identifiers are time-ordered (UUIDv7), and the tie-break is what saved MariaDB, whose driver
drops sub-second precision. A bare `max(comparing(executedAt))`, or a strict `isAfter` in a merge,
keeps the *older* row on a tie, which means the superseded one. `InMemoryHistoryRepository.BY_RECENCY`
and `JdbcHistoryRepository`'s SQL apply the same ordering to their own reads.

## `amend` re-reads the history between planning and applying

`AmendService.apply` calls `statusService.getStatus()` again for each planned withdrawal, so a plan can
be carried out against a history that no longer matches it and legitimately write fewer rows than it
listed. Never report an amend's result from the plan's sizes; report what `apply` returned.

The window exists in **both** front ends — the trigger is another process touching the same history
DB, which needs no seam in the command itself. What differs is testability: the CLI's confirmation
prompt sits between the two reads, so a test drives the change through the `InputStream` the prompt
reads from (an override that mutates the database and then answers), while the Gradle task never
prompts and the state is unreachable from a functional test. Hence the deliberate divergence: the CLI
says `Nothing was written.`, and `MigrapheAmendTask` prints the plan and then nothing. Do not "fix"
the Gradle side by adding an arm no test can reach — change it only together with a seam that pins it.

## A direct-edge column has to be walked, not looked up

`ExecutionRecord.dependencies` holds a node's **declared direct dependencies**. Any question of the
form "does anything still stand on X" is therefore a reachability query over the recorded rows, not a
per-node `contains(X)`. A single lookup gives the right answer only while every node between the
asker and X is itself applied and gets asked — and `amend` on an intermediate, or a rollback after a
dependent stopped declaring it, breaks exactly that. `DownService.reachFrom` is the shape: walk,
treat both "no recorded apply" and "recorded no dependencies" as *cannot say* → refuse, and resolve
ids against every declared target, since nothing puts a dependency in its dependent's target.

## A printed remedy is a claim, and it is usually false at first

Three consecutive cycles on `DownService` shipped an error message naming a command the same command
refuses, or describing a state that was not the one being reported. Two ways it happens:

- **Asserting how the state arose.** "No run writes that, so the history has been edited or damaged"
  was false the moment a released version's ordinary commands could produce it. A refusal may say what
  it is looking at; it must not narrate a past it cannot see. Name the rows and stop.
- **Porting the text.** Wording copied from another refusal carries that refusal's preconditions.
  `RecordedGraph.appliedInTwoTargets` says "run `migraphe down X`" and nothing contradicts it there —
  it has no production caller at all, so no command prints it and no state constrains it. On
  `DownService`'s (now deleted) orphan path the same sentence named a command that path's own guard
  refused. Check
  the preconditions where the text is going, not where it came from; a source with none proves
  nothing.
- **One message, several states.** A blocker widened to cover a second state keeps the first state's
  sentence. Enumerate the states that construct each message and check the sentence against each.
- **"Why this cannot come down" is answered twice, and the two must agree.**
  `DagExecutor.rollbackRefusalFor` says it as whole sentences prefixed with the id and stops a run;
  `DownPlanFormatter.why` says it as a clause after an em dash and refuses before anything starts.
  The rule is one — the author's reason, then the fingerprint (a row that carries none cannot speak
  for its contents *at all*, so it is asked before the payload), then a complete row that kept no
  payload, then a payload the target cannot rebuild. They have drifted twice: once when only the
  plan learned to refuse a payload-less row, once when only the plan learned that the fingerprint
  comes first, and in both cases the run silently permitted what the plan had refused. Change one,
  read the other; unifying them means agreeing on a sentence shape first, which is a design step,
  not a tidy.
- **Reading the subject off the wrong graph.** A refusal that quotes the author — `why(node)` reads
  `node.noWayBack()` — has to resolve its node from the graph that will execute. Resolved off the
  declarations, a node frozen because its *row* carries no rollback is reported as "it has no down
  migration, and none was declared" while the task file declares one, sending the operator to edit
  something already correct. Whenever a blocker carries a `MigrationNode` rather than a `NodeId`,
  check which graph produced it.

Assert the remedy line, not just the first line — and where the remedy is a command, assert that the
command is accepted after the step the message describes (build the post-remedy graph and plan it).
A test that only checks the wording pins the wording, which is what let all three ship.

## Inserting a method: anchor on the sibling's body, never on its doc comment

Five times in one session a scripted edit put a new method's javadoc *between* an existing method's
doc comment and its declaration, silently reassigning the doc and leaving the sibling undocumented.
Counting `/**` against `*/` does not catch it — the delimiters still balance. What catches it is
`javadoc`'s "コメントなし" warning, which only the full `run_build` runs, and reading the anchor:

- **Insert after a method's closing `}` and blank line**, not before a `/**`. Anchoring on the
  *declaration line* is the same mistake wearing a different hat — the doc above it is left orphaned.
- **`javadoc` does not check private members**, so the full `run_build` stays green when the
  displaced pair is private. Read the region back; the build will not tell you.
- After any scripted insertion or deletion near a doc comment, look at the lines above *both*
  neighbouring declarations before running anything.
- **The anchor that keeps catching this is a `private` helper at the end of a test class.** Inserting
  a new `@Test` "before `writeReversiblePair`" puts it between that helper's javadoc and the helper.
  The build stays green, `javadoc` never sees test sources, and the review that finds it is an
  audit. Anchor on the *blank line after the previous method's closing brace* instead, or paste the
  helper's javadoc back in the same edit. This has now happened three times in one session, in three
  different test classes — treat "insert a @Test before an existing member" as the trap itself, not
  as a thing to be careful about. It is **not** test-only: anchoring a new *production* method on
  `private static String why(` in `DownPlanFormatter` put it between `why`'s javadoc and `why`. Any
  insertion anchored on a declaration line has this shape; anchor on the declaration's **javadoc
  opening** (or the blank line above it) instead.
- **Orphaned pairs are already sitting in the tree.** `rollbackBlockers()` carried two stacked doc
  comments for months — javac keeps the last, so the first was dead text that still read as current
  and had drifted out of date. A `*/` line immediately followed by a `/**` line finds them; it costs
  one grep and there is no other signal.

The same slip in reverse — deleting a method and leaving its doc behind — has happened too; the
orphan then attaches itself to whatever follows.

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
