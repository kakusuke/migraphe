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
