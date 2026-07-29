# JdbcMarkdownGenerator

Tidies that look safe and are not, in `migraphe-plugin-jdbc`'s markdown generator and its
PostgreSQL/MySQL subclasses. (`JdbcMarkdownDefinition` is shared, not subclassed — both plugins'
`definitionClass()` return it directly.)

Most entries here are refactors that pass the existing tests and still break output. Treat a green
test run as insufficient evidence for anything in this file — the ER assertions in
`JdbcMarkdownGeneratorTest` are substring `contains` checks on isolated blocks, not whole-output
comparisons, so they are blind to ordering and interleaving.

## Rendering invariants — reject these "simplifications"

- **`collectReachable` keeps a call-local `visited` per call.** `neighborhoodOf` calls it twice against
  the same `members` set (forward = ancestors, backward = descendants). Sharing one `visited` across
  both — to "avoid revisiting the root" or fuse the two BFS passes — drops nodes reachable in both
  directions, giving incomplete neighborhoods. No existing test catches it without a diamond-shaped FK
  fixture.
- **`collectReachable` adds `start` to `result` itself.** Contract is documented on `@param start`;
  don't hoist it to the caller.
- **`appendErDiagramSection` keeps its two-pass shape** — all entity lines, then all relationship
  lines. The fused per-table version is shorter and stays green, because a single-table caller can't
  tell the difference *and no test asserts whole-file line order anyway*. With N>1 it reorders
  `index.md` whenever an FK points at a later table. This one is defended by nothing but this note.
  General form: when unifying a single-element renderer with a list renderer, adopt the *list*
  version's pass structure.
- **Output order comes from filtering `fkGraph().orderedTables()`.** The O(N) scan per table is a
  deliberate accepted cost; an index/rank rewrite risks the byte-for-byte order guarantee.
- **`erIdKey(schemaName, tableName)` = `schemaName.length() + ":" + schemaName + tableName`** is
  injective and shared by the entity-ID cache key and the SHA-256 hash input. It must stay one helper —
  drift between the two breaks cache/hash parity silently. The hash feeds emitted entity IDs and
  `JdbcMarkdownGeneratorTest` reimplements the formula independently, so changing it changes every
  generated diagram.
- **`sanitizeMermaidLabel` handles six characters, in two different ways**: `"`, `[`, `]`, `\` are
  removed; `\n` and `\r` are replaced with a **space**. Entities render as `id["label"]`, so none of the
  six is optional. Don't collapse the two behaviors into one.
- **Constructor-built lookup fields** — `schemaByExactName` via `putIfAbsent` = first-match-wins,
  `schemasByLowerName` keyed with `Locale.ROOT`, `schemasByTableName`, `excludes` as
  `List<CompiledExclude>` — all preserve `schemas()` forward-iteration order and first-match/only-match
  semantics. Any rewrite must preserve both. (`erEntityIdCache` is *not* one of these: it's a lazily
  filled `HashMap`, order-irrelevant.)
- **Don't merge `SchemaTable` with `TableRef`.** `TableRef` identifies an entity known only by name — an
  FK's referenced table, constructed in `buildFkGraph` — where no `JdbcTableInfo` exists. Merging the
  two means fabricating one.

## Public API limits

- The `JdbcMarkdownGenerator` constructor is reached by `PostgreSQLMarkdownGenerator` /
  `MySQLMarkdownGenerator` via `super(...)` across three separately published modules. New config flags
  are added by **telescoping only**.
- Extracting an `ErDiagramOptions` parameter object is a pre-decided, separately scoped session — don't
  pre-empt it from a wiring cycle. If it lands, keep the old constructors `@Deprecated` and delegating.
- Extracting an `ErDiagramRenderer` collaborator (ER diagram + entity ID + Mermaid sanitization, >200
  lines) is out of scope for a Tidy phase — route to `general-purpose`.
- `DEFAULT_ER_DIAGRAM_PER_TABLE_MAX_ENTITIES` is `protected` so the subclasses' shorter constructors can
  delegate. `JdbcMarkdownDefinition`'s `@WithDefault("60")` needs a compile-time constant string and
  cannot reference it, so the two are kept in sync by a prose comment only.

## Config-field conventions

- Feature shape: `@WithDefault` field on `JdbcMarkdownDefinition` → telescoping generator constructor
  arg → guarded `protected append*` hook. Note that the two config-guarded hooks (`appendErDiagram`,
  `appendTableErDiagram`) are fully implemented in the base and overridden by neither subclass, while
  the hooks that *are* no-ops in the base carry no config guard. Don't assume a new flag needs a
  subclass override. A diff that already follows the shape end-to-end is a legitimate skip.
- Config fields routinely land on `*Definition` in one cycle and get wired into the generator and
  `*MarkdownPlugin.output()` in a later one. Never infer current constructor arity or wiring state from
  this file — read `JdbcMarkdownGenerator.java` and all three `*MarkdownPlugin.output()` methods.
- Telescoping defaults do **not** universally mirror the `@WithDefault`: `erDiagramLayout` is
  intentionally `"elk"` in config but `""` (no frontmatter) for direct library construction. Don't
  "fix" it.
- All three `*MarkdownPlugin.output()` methods are deliberately identical, on inline
  `definition.excludes().orElse(List.of())` with an imported `List`. Keep them that way.
- Javadoc verbosity differs across the ER-diagram fields because their semantics differ. Not drift.

## Test-side conventions

- Each module's plugin test owns its own private helpers, and the sets are **not** identical:
  `definition(...)` (telescoping overloads) and `resolverFor(...)` exist in all three;
  `usersMdPath(...)` only in `JdbcMarkdownPluginTest`; `schemaInfoWithUsersAndOrdersTable()` only in the
  PostgreSQL and MySQL ones. Check the file rather than assuming a helper is there. A new config field
  belongs inside the existing `definition(...)` helper, not as a new anonymous override.
- `@TempDir` is an **instance field** in `JdbcMarkdownPluginTest` but a **per-method parameter** in the
  PostgreSQL/MySQL plugin tests. Extracted helpers can reference it only in the first case.
- The literal `60` in test doubles is deliberately bare, not tied to the production default — a test
  double declares what that test uses.
