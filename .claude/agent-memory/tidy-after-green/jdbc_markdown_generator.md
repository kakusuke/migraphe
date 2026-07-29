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
- ~~Known gap as of 2026-07-22: `JdbcMarkdownPlugin.output()` still calls the 3-arg
  `JdbcMarkdownGenerator` constructor and never reads `definition.erDiagram()`~~ — **CORRECTED
  2026-07-28**: this note was stale. By the time `erDiagramLayout()` was added (Session
  2026-07-27/28), `JdbcMarkdownPlugin.output()` was already calling a constructor that wired
  `definition.erDiagram()` AND `definition.erDiagramKeysOnly()` through to `JdbcMarkdownGenerator`
  (the exact commit/session where `erDiagram`/`erDiagramKeysOnly` wiring landed wasn't captured in
  this file at the time — a gap in this memory's own history, not in the code). The
  2026-07-27/28 cycle added the 7th constructor arg, `definition.erDiagramLayout()`, completing
  full config-to-generator wiring for all three ER-diagram-related flags. Lesson: when a memory
  entry claims a specific method "never reads" a field or "isn't wired yet," re-verify against the
  current source before trusting it in a later session — these gap notes can go stale silently once
  a later Green phase closes the gap without an explicit corresponding memory update.
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
- 2026-07-23 (code-review findings #6/#7/#8, second review pass): three more findings in the same
  file. #8: `erEntityId(String, String)` was `private static`, recomputing `MessageDigest.getInstance`
  + hex formatting every call even though it's called multiple times per (schema, table) across the
  entity-rendering and relationship-rendering loops. Dropped `static` (it's only ever called from
  instance methods in this class, so removing `static` is a safe private-method-scope change) and
  added an instance field `private final Map<String, String> erEntityIdCache = new HashMap<>()`,
  memoizing on the exact same cache key formula already used as the hash input
  (`schemaName.length() + ":" + schemaName + tableName` — already proven injective, see the erIdHash
  javadoc from an earlier session), via `computeIfAbsent`. #7: `resolveReferencedSchema` and its
  helpers (`schemaContainsTable(String,String)`, `schemasContainingTable`) repeated full
  `schemaInfo.schemas()` traversals per FK (exact-name loop, case-insensitive-collect loop, per-schema
  table scan) — added three instance fields built **once**, in the constructor, by a single pass over
  `schemaInfo.schemas()`: `Map<String, JdbcSchemaDetail> schemaByExactName` (first-match-wins via
  `putIfAbsent`, preserving original first-occurrence semantics), `Map<String, List<JdbcSchemaDetail>>
  schemasByLowerName` (keyed by `schema.name().toLowerCase(Locale.ROOT)` — used `Locale.ROOT`
  explicitly since the original used locale-independent `String.equalsIgnoreCase`, and an unqualified
  `.toLowerCase()` risks Turkish-locale `i` mismatches that `equalsIgnoreCase` wouldn't have), and
  `Map<String, List<JdbcSchemaDetail>> schemasByTableName` (built via a nested loop over each schema's
  `tables()`). Every list preserves `schemas()` iteration order because it's built by appending during
  a single forward pass, matching the original loops' first-match/only-match semantics exactly. #6:
  `appendErDiagram`'s relationship loop already computed `refSchema` (previous session) but
  `appendErRelationship` still recomputed both `refEntityId` and `entityId` internally from raw
  schemaName/table/fk arguments. Changed `appendErRelationship`'s signature from `(StringBuilder,
  String schemaName, JdbcTableInfo table, JdbcForeignKeyInfo fk)` to `(StringBuilder, String
  refEntityId, String entityId, JdbcForeignKeyInfo fk)` (private method, single call site — safe),
  and hoisted `entityId = erEntityId(st.schemaName(), st.table().name())` computation above the inner
  `for (fk : foreignKeys())` loop in `appendErDiagram` since it doesn't depend on `fk`. General lesson
  reinforcing the 2026-07-23 (#1) entry above: once a "pass the already-computed value through"
  opportunity is found, check whether *further* values computed downstream from it (here: the two
  entity IDs derived from the schema) are *also* recomputed by the callee — worth doing a second pass
  once the first fix's pattern is understood. All three fixes kept together in one cycle since they
  compound (fields from #7 make #6/#8's hot-path calls cheap even before memoization, and the
  memoization in #8 makes the remaining redundant calls in #6's call site near-free either way) — 26
  tests (`JdbcMarkdownGeneratorTest` + `JdbcMarkdownPluginTest`) stayed green, output byte-identical.
- 2026-07-23 (findings #859/#745, third review pass): #859 — `sanitizeMermaidLabel`'s Javadoc said
  "removing any double quotes" / "ordinary table names are returned unchanged," but the method body
  actually strips `"`, `\n`, `\r`, `[`, `]`, and `\` (needed because entities are written as
  `id["label"]` — backslashes/brackets/newlines would break the Mermaid alias syntax, not just the
  quotes). Javadoc-only fix, no code change: rewrote the description to list all five stripped
  character classes and why (`["..."]` alias breakage), and reworded `@return` to match. #745 — the
  same injective key expression `schemaName.length() + ":" + schemaName + tableName` was duplicated
  verbatim in `erEntityId`'s `cacheKey` local and `erIdHash`'s `hashInput` local (previously connected
  only by a comment: "Same injective combination as erIdHash's hash input"). Extracted a `private
  static String erIdKey(String schemaName, String tableName)` returning that expression; both call
  sites now call it, and the field Javadoc on `erEntityIdCache` was updated from "keyed by the {@link
  #erIdHash} input" to "keyed by {@link #erIdKey(String, String)}" for accuracy. General lesson: when
  two methods share an injective/derived key formula connected only by a prose comment ("same as X"),
  that's a real duplication-of-truth risk (one could drift without the other) even though the
  expression itself is trivial (one line) — extracting a tiny private static helper is still worth it
  specifically because a future edit to either copy could silently break cache-key/hash-input parity.
  All 19+7 tests (`JdbcMarkdownGeneratorTest`+`JdbcMarkdownPluginTest`) stayed green; spotless only
  rewrapped the `md.digest(erIdKey(...).getBytes(...))` line, no semantic diff.
- 2026-07-27/28 (test-side tidy after `erDiagramLayout()` config field added): the Green-phase diff
  added `@Override public String erDiagramLayout() { return "elk"; }` to 5 pre-existing anonymous
  `JdbcMarkdownDefinition` implementations — 3 in `JdbcMarkdownPluginTest`
  (migraphe-plugin-jdbc), 2 in `PostgreSQLMarkdownPluginTest` (migraphe-plugin-postgresql). This is
  test-only duplication (not the production `JdbcMarkdownGenerator`/`Definition` code this file's
  history otherwise tracks), but the anonymous-impl pattern had already crossed the "2nd occurrence"
  threshold in earlier sessions without being tidied — worth recording since it's a different kind of
  target than the production-code entries above. Evaluated per-file (never across the two test files:
  they live in different Gradle modules — migraphe-plugin-jdbc vs migraphe-plugin-postgresql — so a
  shared test-fixture helper would require a cross-module test-fixture source set, which is out of
  scope for a minimal-diff tidy). Within each file, extracted two private helper methods: `private
  JdbcMarkdownDefinition definition(String name, boolean erDiagram)` (JdbcMarkdownPluginTest;
  `type`/`outputDir`/`erDiagramKeysOnly`/`erDiagramLayout`/`excludes` were constant across all 3 call
  sites, only `name`/`erDiagram` varied) and `private JdbcMarkdownDefinition definition(boolean
  erDiagram)` (PostgreSQLMarkdownPluginTest; only `erDiagram` varied across its 2 call sites) — plus a
  `private DefinitionResolver resolverFor(GeneratorDefinition definition)` helper in each file
  (identical `klass.cast(definition)` boilerplate was duplicated the same number of times). This
  collapsed each ~35-line anonymous impl + ~7-line resolver block down to a 1-line call per test.
  Confirmed `tempDir` is an instance `@TempDir` field in `JdbcMarkdownPluginTest` (safe to reference
  from the extracted instance method) but a per-test-method `@TempDir Path tempDir` parameter in
  `PostgreSQLMarkdownPluginTest` (not a field) — that file's `outputDir()` was already the fixed
  string `"docs/schema"` in both call sites, so no parameter needed to be threaded through. Left the
  `JdbcTableInfo`/`JdbcSchemaDetail`/`PostgreSQLSchemaInfo` construction duplication in
  `PostgreSQLMarkdownPluginTest`'s two remaining tests untouched — pre-existing, not touched by this
  cycle's diff, out of scope. All tests green before/after in both modules; spotless made no further
  changes (confirmed via up-to-date task cache on re-run). General lesson: when a Green-phase diff
  adds one more override line to N pre-existing near-identical anonymous test doubles, check whether
  extracting a private factory-method helper is now justified — evaluate per-module/per-file only
  (never merge test doubles across Gradle modules without being asked), and verify which fields are
  truly constant vs varying across the existing call sites before choosing the helper's parameter
  list.
- 2026-07-27/28 (erDiagramLayout Green diff, tidy pass — skipped): the 6-arg constructor added for
  `erDiagramLayout` (String, frontmatter block in the ER diagram fence) was evaluated against three
  specific candidates and all three were confirmed clean, no change made. (1) The 3-line frontmatter
  string-building block inside `appendErDiagram()` (`if (!erDiagramLayout.isEmpty()) { sb.append("---\n
  config:\n  layout: ").append(erDiagramLayout).append("\n---\n"); }`) has exactly one call site and
  low complexity — per the established "extract once a 2nd occurrence is real, not before" convention
  in this file's history (see the Foreign/Exported Keys entries above), a private helper here would
  only add indirection with zero duplication removed. (2) The 6-arg constructor's Javadoc matches the
  telescoping pattern's style/grammar exactly (each `@param` line carried forward unchanged, one new
  `@param` appended) — no drift from the 3/4/5-arg constructors' conventions. (3) The new test
  `indexMdErDiagramFenceContainsLayoutFrontmatterWhenConfigured` mirrors sibling tests
  (`erDiagramKeysOnlyShowsOnlyPkAndFkColumns`, `erDiagramShowsAllColumnsByDefault`) in naming
  convention, `@TempDir Path outputDir` signature, and reuse of the shared `buildSchemaInfo()` helper —
  no restructuring needed. Note: `run_spotless` still executed `spotlessJavaApply` on
  `migraphe-plugin-jdbc` (not up-to-date) and reformatted the new test's constructor-call line
  wrapping — this is normal formatter noise on freshly-added, not-yet-formatted code, not a tidy
  action; re-ran tests after and confirmed still green. General reinforcement: a Green-phase diff that
  already follows this file's established telescoping-constructor + Template-Method-hook conventions
  end-to-end is a legitimate `tidy_status: skipped`, not a sign the agent should force a change to
  justify the cycle.
- 2026-07-28 (erDiagramLayout validation regex tidy, applied): the Green-phase diff for the
  invalid-layout-name guard added `private static final Pattern LAYOUT_FRONTMATTER_PATTERN =
  Pattern.compile("^[A-Za-z0-9_-]+$")` used only via `.matcher(erDiagramLayout).matches()`. Two
  changes applied: (1) dropped the `^`/`$` anchors — pattern text simplified to
  `"[A-Za-z0-9_-]+"`. Verified equivalence by reasoning (and cross-checked against the parent
  orchestrator's empirical probe of `elk`/`elk\n`/`elk\r\n`/`""`): `Matcher.matches()` always
  requires the match region to span the *entire* input regardless of explicit `^`/`$` anchors —
  it's defined as "attempt to match the entire region." The subtlety that trips people up is that a
  *trailing* `$` (non-MULTILINE) is a zero-width assertion satisfied *before* a final line
  terminator, so `^charclass+$` can look like it "matches up to but excluding a trailing newline" —
  but `matches()` still fails in that case because the consumed match region (up to the char class)
  doesn't extend to the true end of input (the newline is still unconsumed), so the anchors add
  zero effective behavior on top of `matches()`'s own full-string requirement. **General rule for
  this codebase: `^...$`-wrapped patterns consumed exclusively via `.matches()` (never `.find()` or
  `.lookingAt()`) are anchor-redundant — safe to simplify to the bare character class, but always
  verify no other call site or copy-paste of the same Pattern constant is used with `.find()`
  elsewhere (grep first) before dropping anchors, since `.find()` requires them for narrowing.**
  (2) renamed the constant `LAYOUT_FRONTMATTER_PATTERN` → `VALID_LAYOUT_NAME_PATTERN` — the old name
  described the *feature* it gates (the frontmatter block) rather than *what the regex validates*
  (the character set of a legal layout-engine name); grepped first to confirm both the declaration
  and its single call site were the only two usages in the whole repo before renaming (private
  field, single file). Evaluated and skipped a third candidate: adding a comment/Javadoc explaining
  the `.matches()`-implies-full-match reasoning — none of this file's other `private static final
  Pattern` constants (`DEFAULT_SCHEMA_EXCLUDE`, `MERMAID_SANITIZE_PATTERN`) carry per-field
  Javadoc, so adding one only for this constant would be inconsistent with established local
  density; skipped per the "match existing comment density, don't manufacture documentation" rule.
  All tests green before/after (module + full suite), spotless made no further changes beyond what
  the edits already matched.
- 2026-07-28 (erDiagramLayout null-normalization ternary, evaluated — skipped): the 6-arg
  constructor's `this.erDiagramLayout = erDiagramLayout != null ? erDiagramLayout : "";` looks
  identical in shape to `nullToEmpty(@Nullable String)` (`return value != null ? value : "";`),
  which already exists as a private static helper in *two* other places: `JdbcSchemaInfoProvider`
  (same module, `io.github.kakusuke.migraphe.jdbc.schema` package — migraphe-plugin-jdbc) and
  `MySQLSchemaInfoProvider` (`io.github.kakusuke.migraphe.mysql.schema` — migraphe-plugin-mysql,
  which already `api`-depends on migraphe-plugin-jdbc, so no *new* module dependency would be
  needed to share a helper from migraphe-plugin-jdbc). Despite crossing the "2nd occurrence is
  real, unify" threshold in raw code-shape terms (this makes 3), deliberately did NOT extract a
  shared helper. Two reasons, in order of weight: (1) **semantically different concerns wearing
  the same one-line disguise** — the two existing `nullToEmpty` calls convert a JDBC
  `ResultSet.getString(...)` SQL-NULL column value into `""` for storage in a schema-metadata
  record (a data-normalization concern tied to `DatabaseMetaData` semantics), whereas
  `erDiagramLayout`'s ternary defaults an *optional constructor/config parameter* to an empty
  sentinel that later gates whether a Mermaid frontmatter block is emitted (a
  feature-toggle/rendering concern). Unifying under one shared name (e.g. a generic
  `Strings.nullToEmpty`) would flatten this distinction and invite future misuse across unrelated
  domains for the sake of saving one line. (2) **scope discipline** — even though the module
  dependency direction already exists, introducing a *new shared public utility class* (deciding
  its package, name, visibility, and Javadoc) is a small design decision, not a pure "extract
  what's already there" tidy; it exceeds the minimal-diff bar this file's history otherwise
  applies (see the Foreign/Exported Keys and erIdKey entries above — those unified *identical*
  logic already serving the *same* concern within one file/class, not superficially-similar
  one-liners serving different concerns across module boundaries). General lesson for this
  codebase: when evaluating a "3rd occurrence of the same shape" duplication candidate that spans
  module boundaries, check whether the occurrences share the *same semantic concern* (safe to
  unify) or merely the *same syntax* applied to different concerns (leave alone) — the "2nd
  occurrence" convention in this project's memory is about repeated *logic*, not repeated
  *syntax*. If a genuine 4th occurrence of the *ResultSet-nullable-column* variant appears in a
  new schema-info provider, that would be the real trigger to extract a shared helper (scoped to
  the `*SchemaInfoProvider` family specifically, not merged with unrelated config-defaulting code
  like `JdbcMarkdownGenerator`'s). Verified with `run_test` (module=migraphe-plugin-jdbc, then
  full suite, both green) and `run_spotless` (re-ran spotlessJavaApply on migraphe-plugin-jdbc due
  to cache invalidation from the prior cycle's uncommitted edits, but produced no content change —
  confirmed via re-read) since no production/test code was changed this cycle.
- 2026-07-28 (JdbcMarkdownPluginTest, `outputPassesErDiagramLayoutToGenerator` duplication,
  applied): the new test added alongside `erDiagramLayout()` wiring in `JdbcMarkdownPlugin.output()`
  duplicated the H2 users/orders schema setup (create env, drop/create `users`+`orders` tables with
  an FK, call `new JdbcSchemaInfoProvider().getSchemaInfo(env)`) verbatim from the pre-existing
  `outputExportedKeyLinksToReferencingTable` test — differing only in the H2 in-memory DB name
  string. This is the second occurrence of that exact setup block (the file's established
  "extract once a 2nd occurrence is real" convention, same as the production-code entries above),
  so extracted `private JdbcSchemaInfo schemaInfoWithUsersAndOrders(String dbName) throws
  Exception` (parameterized only on `dbName`, reused for both the JDBC URL and the
  `JdbcEnvironment.create` name arg since both tests used the same string for both). Verified `env`
  wasn't referenced by either test after the `getSchemaInfo` call, so hiding it inside the helper
  is safe. Both call sites collapsed from ~20 lines to 1. All tests green before/after (module +
  full suite); `run_spotless` re-ran `spotlessJavaApply` on migraphe-plugin-jdbc (cache
  invalidated by the edit) but produced no further content change on re-read. General lesson:
  this "2nd occurrence in a freshly-added sibling test" pattern recurs in this test file's
  history (see the 2026-07-27/28 `definition(...)`/`resolverFor(...)` entry above) — when a new
  test is added next to an existing test that already does near-identical multi-line JDBC/H2
  setup, check for extraction opportunities as part of the same tidy cycle rather than waiting for
  a 3rd occurrence, since the threshold was already met by the 2nd.
- 2026-07-28 (PostgreSQLMarkdownPluginTest, `schemaInfoWithUsersTable()` extraction, applied): the
  Green-phase diff added a 6-arg `PostgreSQLMarkdownGenerator` constructor (telescoping to a
  7th arg equivalent via the shared `JdbcMarkdownGenerator` super-constructor —
  `erDiagramLayout`), wired through `PostgreSQLMarkdownPlugin.output()`, plus a new test
  `outputPassesErDiagramLayoutToGenerator` in `PostgreSQLMarkdownPluginTest`. That new test
  built the identical single-table `PostgreSQLSchemaInfo` (one `users` table with an `id`
  column, `pk_users` PK, `public` schema, `plpgsql` extension) verbatim from the two
  *pre-existing* tests `outputGeneratesMarkdownFiles` and
  `outputOmitsErDiagramWhenDefinitionDisablesIt` — this made 3 identical occurrences in one
  file, clearing the "2nd occurrence is real" bar with room to spare. Extracted `private
  PostgreSQLSchemaInfo schemaInfoWithUsersTable()` (no parameters — unlike
  `JdbcMarkdownPluginTest`'s analogous `schemaInfoWithUsersAndOrders(String dbName)`, none of
  the three call sites varied any field, so no parameter was needed) and replaced each ~30-line
  block with `var schemaInfo = schemaInfoWithUsersTable();`. Verified none of the three tests
  referenced the intermediate locals (`idColumn`, `usersPk`, `usersTable`, `schemaDetail`) after
  schema-info construction — only `schemaInfo` itself was used downstream in each test — so
  hiding them inside the helper was safe. Deliberately did NOT touch
  `PostgreSQLMarkdownGeneratorTest`'s own near-identical schema-building block (different test
  class in the same file/module) — out of scope for this cycle's diff, and would need its own
  duplication count check before extracting. Also evaluated (both skipped, no change): (a) the
  6-arg constructor's Javadoc already follows the file's telescoping-constructor style exactly
  (each prior `@param` line carried forward, new one appended) — matches the
  `JdbcMarkdownGenerator` history entry's established convention; (b) `PostgreSQLMarkdownPlugin`
  wiring order (`erDiagram`, `erDiagramKeysOnly`, `erDiagramLayout`) matches
  `JdbcMarkdownPlugin.output()`'s wiring exactly — the only structural difference between the
  two `output()` methods (`PostgreSQLMarkdownPlugin` inlines `definition.excludes().orElse(java.util.List.of())`
  instead of `JdbcMarkdownPlugin`'s private `resolveExcludes(definition)` helper, and uses a
  fully-qualified `java.util.List.of()` rather than an imported `List`) predates this cycle's
  diff (not touched by the erDiagramLayout change) and was left alone as out-of-scope
  pre-existing inconsistency, not part of the reviewed diff. All tests green before/after
  (module + full suite); `run_spotless` executed `spotlessJavaApply` on migraphe-plugin-postgresql
  (cache invalidated by the edit) but produced no further content change on re-read. General
  lesson reinforcing the `JdbcMarkdownPluginTest` 2026-07-28 entry above: this "N-arg config
  field added → new sibling test duplicates existing schema-construction boilerplate" pattern
  now has two independent confirmed occurrences across two different test files/modules
  (`JdbcMarkdownPluginTest` and `PostgreSQLMarkdownPluginTest`) for the *same* underlying
  `erDiagramLayout` Green-phase change — when the config-wiring diff is this "telescoping
  constructor + Template Method" shape, always check the newly-added test for schema-info
  duplication against its sibling tests in the *same file* as a matter of course, but never
  merge the resulting helper across files/modules (postgresql and jdbc test doubles must stay
  independent — no new cross-module test dependency).
- 2026-07-28 ((A) erDiagramLayout feature now complete across all 3 markdown plugins — status
  note, no code change): the 6-arg `MySQLMarkdownGenerator` constructor (`@Nullable String
  erDiagramLayout`, telescoping from 5-arg via `this(..., "")`, forwarding to
  `super(..., erDiagramLayout)` + `this.mysqlInfo = schemaInfo`) plus the matching
  `MySQLMarkdownPlugin.output()` wiring (`definition.erDiagramLayout()`) landed this cycle,
  mirroring the same shape already present in `JdbcMarkdownGenerator`/`JdbcMarkdownPlugin` and
  `PostgreSQLMarkdownGenerator`/`PostgreSQLMarkdownPlugin` from the prior two cycles (see the
  2026-07-28 entries above). This closes out the `er-diagram-layout` config feature end-to-end
  for `jdbc-markdown`, `postgresql-markdown`, and `mysql-markdown`. Reviewed three specific tidy
  candidates on the MySQL diff and applied **no changes** (all skip): (1) the 3 test helpers
  ported into `MySQLMarkdownPluginTest` (`definition(boolean)`, `resolverFor(...)`,
  `schemaInfoWithUsersTable()`) were checked against the sibling `MySQLMarkdownGeneratorTest` in
  the same module for convention drift — the `id`/`INT`/`Types.INTEGER`/autoIncrement=true column
  shape matches that sibling file's own `JdbcColumnInfo` construction verbatim, the
  `MySQLSchemaInfo`/`JdbcSchemaDetail` constructor arg counts were verified against the current
  record definitions (8 and 7 fields respectively) and match exactly, and no unused/leftover
  PostgreSQL-only import (e.g. `PostgreSQLExtensionInfo`) was present — confirms this is a clean,
  natural MySQL-flavored port, not a copy-paste residue. (2) the 6-arg constructor Javadoc in
  `MySQLMarkdownGenerator` was diffed line-by-line against `PostgreSQLMarkdownGenerator`'s 6-arg
  Javadoc and the file's own 3/4/5-arg constructors — identical grammar/param-carry-forward
  pattern, no drift. (3) compared `output()` in all three plugin classes as instructed —
  confirmed (out-of-scope, reported not touched) a **pre-existing** inconsistency predating this
  cycle: `JdbcMarkdownPlugin.output()` resolves excludes via a private static
  `resolveExcludes(definition)` helper, while both `MySQLMarkdownPlugin.output()` and
  `PostgreSQLMarkdownPlugin.output()` inline `definition.excludes().orElse(List.of())` (Postgres
  further differs by using a fully-qualified `java.util.List.of()` instead of an imported
  `List`). This three-way inconsistency was already noted as out-of-scope in the
  `PostgreSQLMarkdownPluginTest` 2026-07-28 entry above; now confirmed MySQL's `output()` matches
  Postgres's inline style, not Jdbc's helper style — still leaving it untouched since no cycle's
  diff has actually touched this particular line. General lesson: when a task explicitly asks to
  "observe but not touch" a known pre-existing inconsistency across 3 sibling plugin classes,
  confirming which two agree and which one differs (rather than re-describing the difference in
  the abstract) is the useful signal to leave in memory — if a future cycle ever *does* touch one
  of these three `output()` methods, unifying to the `resolveExcludes` helper (or removing it in
  favor of universal inlining) would be a legitimate, low-risk 3-way tidy at that point, scoped to
  all three files together since the duplication is now a confirmed real 3-occurrence case, not
  merely 2.
- 2026-07-28 (`erDiagramPerTable` config field, `JdbcMarkdownDefinitionTest` setup-duplication
  tidy, applied): the Green-phase diff added `boolean erDiagramPerTable()` (4th ER-diagram-related
  `@WithDefault` field on `JdbcMarkdownDefinition`) plus `erDiagramPerTableDefaultValue()` in
  `JdbcMarkdownDefinitionTest`. That test file had **6** near-identical test methods
  (`typeIsRead`, `nameIsRead`, `outputDirDefaultValue`, `erDiagramLayoutDefaultValue`,
  `erDiagramPerTableDefaultValue`, `canBeBuiltWithoutTarget`) each rebuilding the exact same
  `SmallRyeConfigBuilder().withMapping(...).withDefaultValue("type", "jdbc-markdown")
  .withDefaultValue("name", "mydb").build()` boilerplate, differing only in the final assertion.
  Extracted `private static JdbcMarkdownDefinition defaultDefinition()` and collapsed all 6 down
  to one-line bodies. Left `outputDirCustomValue()` and `excludesWithSchemaAndTable()` untouched —
  both add extra `withDefaultValue(...)` calls beyond type/name, so they don't fit the
  zero-parameter helper and weren't worth parameterizing for just 2 call sites each. Also evaluated
  and explicitly skipped: (a) Javadoc consistency across the 4 ER-diagram fields
  (`erDiagram`/`erDiagramKeysOnly`/`erDiagramLayout`/`erDiagramPerTable`) on
  `JdbcMarkdownDefinition` — the differing verbosity (e.g. `erDiagramKeysOnly` has an extra `<p>`
  paragraph) reflects genuinely differing semantic complexity, not drift; no change needed. (b) the
  3 per-module anonymous-`JdbcMarkdownDefinition` test doubles in `JdbcMarkdownPluginTest`
  (migraphe-plugin-jdbc), `PostgreSQLMarkdownPluginTest`, `MySQLMarkdownPluginTest` — checked and
  confirmed the Green-phase diff already added `@Override public boolean erDiagramPerTable()`
  *inside* each module's pre-existing shared `definition(...)` helper (established in the
  2026-07-27/28 `definition(...)`/`resolverFor(...)` entry above), so no new duplication was
  introduced this cycle; correctly a no-op per-module. General lesson: when a new
  `@WithDefault`-only config field lands as the *n*-th field on an already-well-covered
  `*Definition` interface, the highest-value tidy target is usually the flat *default-value test
  method* duplication in the interface's own `*DefinitionTest` (setup boilerplate scales with test
  count, independent of how many fields exist) rather than the interface's Javadoc or the
  per-plugin-module test doubles (which typically already route new fields through an existing
  shared helper once that helper exists).

- 2026-07-28 (`appendErDiagram`/`appendTableErDiagram` section-rendering duplication, applied): both
  methods built an identical Mermaid ER-diagram section (heading, code fence, optional layout
  frontmatter, entity loop, relationship loop, closing fence) but had drifted into structurally
  different shapes — `appendErDiagram` (index.md, N tables) used a genuine **two-pass** structure
  (all entities first, then all relationships), while `appendTableErDiagram` (single-table page,
  added a cycle earlier) used a **fused one-pass** structure (render the one entity, then loop its
  FKs for relationships) since with exactly one table there was no observable difference between
  "render entity then relationships" and "render entity, then for that same entity render its
  relationships." Extracted `private void appendErDiagramSection(StringBuilder sb,
  List<SchemaTable> tables)` — **critically, using the two-pass shape**, not the fused one, even
  though the fused shape was shorter/simpler and passed all existing tests when substituted for
  both callers. **Why two-pass is mandatory, not stylistic**: with N>1 tables, a fused per-table
  loop (entity_A, relationships_A, entity_B, relationships_B, ...) interleaves entity and
  relationship Mermaid lines in a different order than two full passes (entity_A, entity_B, ...,
  relationships_A, relationships_B, ...) whenever any table's FK references a table appearing later
  in iteration order — the relationship line for table A's FK-to-B would appear *before* B's entity
  line in the fused version but *after all* entities in the two-pass version. Mermaid itself doesn't
  care (it resolves entity references regardless of appearance order in `erDiagram` fences), but the
  **existing test suite's exact-string/line-order assertions on `index.md`'s ER diagram fence would
  silently pass or fail depending on FK direction and table iteration order** — a change that is
  invisible in a single-table test (which is exactly what `appendTableErDiagram`'s own tests are, by
  definition) but real once verified against a multi-table users/orders-style fixture. Confirmed by
  literally trying the fused shape as the extraction target first, running the full suite, and
  observing it would have looked green for the specific fixtures in this repo (small N, FK order
  happens to already match iteration order) — the risk is latent, not something the current test
  suite is guaranteed to catch, hence the explicit two-pass mandate from the orchestrator rather than
  something safe to infer purely from running tests. **General lesson: when unifying two
  near-duplicate rendering methods where one iterates a single element and the other iterates a
  list, always adopt the *list* version's pass structure as the shared implementation — a
  single-element caller cannot distinguish fused-pass from two-pass by its own output, so use the
  single-element case only to verify the *extraction preserves both callers' behavior*, never as
  the source of the shared structure itself.** Also: `entityIds` in the single-table version was
  `Set.of(entityId)` (one element) vs. the multi-table version's `tables.stream().map(...).collect(
  Collectors.toSet())` — these are only equivalent at exactly one element; the shared method uses
  the stream-collect form (correct for any list size) rather than special-casing size 1. All tests
  green before/after (module `migraphe-plugin-jdbc` + full ~968-test suite); `run_spotless`
  reformatted the touched file (`spotlessJavaApply` not up-to-date) but produced no further content
  change on re-read of the extracted section.

- 2026-07-28 (`buildFkGraph`/`appendTableErDiagram` tidy after ancestor-BFS cycle, applied +
  1 skip): three candidates reviewed on the same-session diff that added `TableRef`/`FkGraph`,
  lazy `fkGraph()`, `buildFkGraph()`, and the ancestor-BFS `appendTableErDiagram`. (1) **Applied**:
  `buildFkGraph()` had a redundant pre-population loop (`for (SchemaTable st : ordered) {
  forward.put(src, new LinkedHashSet<>()); backward.put(src, new LinkedHashSet<>()); }`) before the
  real edge-building loop, which already used `computeIfAbsent` for both maps. Verified safety by
  grepping the whole file for `.forward()`/`.backward()` call sites first: `forward()` has exactly
  one reader (`appendTableErDiagram`'s `forward.getOrDefault(current, Set.of())`), and `backward()`
  has **zero** readers yet (intentionally unused — reserved for a future descendant-BFS cycle, per
  the orchestrator's explicit "do not implement descendant BFS yet" instruction). Since the only
  reader uses `getOrDefault` (never bare `.get()` or `.keySet()`/`.entrySet()` iteration order),
  removing the pre-population loop cannot change observable behavior — it only means keys with zero
  edges are simply absent from the map instead of present-with-an-empty-set, and both `getOrDefault`
  and (future) `computeIfAbsent`-based writers handle an absent key identically to a
  present-empty-set key. General rule for this file: before touching any map built by both a
  "pre-populate all keys" loop and a "computeIfAbsent" loop, grep for every reader of that map and
  confirm none of them relies on key presence (only `.get()`/`.getOrDefault()` value contents, or
  count via `.size()` of a *value* set, not the outer map) — `.keySet()` iteration or `.containsKey()`
  checks would make this unsafe. (2) **Applied**: replaced fully-qualified `java.util.LinkedHashSet`
  / `java.util.ArrayDeque` (4 occurrences total, in `appendTableErDiagram` and `buildFkGraph`) with
  imported short names, matching this file's existing convention of importing every `java.util.*`
  type it uses (`ArrayList`, `HashMap`, `HexFormat`, `List`, `Locale`, `Map`, `Set` were already
  imported this way) — added `import java.util.ArrayDeque;` and `import java.util.LinkedHashSet;` in
  alphabetical position. (3) **Skipped**: the `appendTableErDiagram`
  `fkGraph().orderedTables().stream().filter(st -> members.contains(new TableRef(...))).toList()`
  runs an O(N) scan per table (O(N²) across all tables in a schema) to preserve canonical output
  order while filtering to BFS-reachable ancestors. Evaluated building an index (`Map<TableRef,
  SchemaTable>` + a `Map<TableRef, Integer>` rank) inside `FkGraph` to instead iterate just the
  (typically much smaller) `members` set and re-sort by rank — rejected: it requires widening the
  private `FkGraph` record's shape (2 new fields) and getting the re-sort exactly right without
  breaking the canonical-order guarantee that's explicitly flagged as byte-for-byte critical in this
  file's history (see the `appendErDiagramSection` two-pass entry above) — a correctness-risk/benefit
  tradeoff that doesn't pay off at the stated ~200-table scale. Per the orchestrator's explicit
  "skip if complex" instruction, left as-is with this reasoning recorded rather than attempting a
  more elaborate index-based rewrite. Verified via `run_test` (module=migraphe-plugin-jdbc, then
  full ~968-test suite, both green before AND after spotless) and `run_spotless` — note: spotless
  ALSO fixed a **pre-existing** violation on this exact filter line
  (`.filter(st -> members.contains(new TableRef(...)))` needed multi-line rewrapping per Google Java
  Format/AOSP column-width rules) that had nothing to do with this cycle's own edits; re-ran tests
  after spotless and confirmed still green. General lesson: when a parent-supplied "known spotless
  violation" note points at a line inside the same method being tidied, don't try to
  manually pre-format it — just let `run_spotless` fix it after the tidy edits land, then re-verify
  green, since spotless's rewrap of an unrelated line is independent of (and should not gate) the
  behavior-preserving tidy itself.

- 2026-07-28 (`collectReachable` implicit contract + `TableRef` construction duplication, applied):
  reviewed 3 candidates flagged by the orchestrator on the diff that replaced
  `appendTableErDiagram`'s inline BFS with two `collectReachable(root, forward/backward, members)`
  calls. (1) **Applied** — `collectReachable` did NOT add `start` to `result` itself; correctness
  depended entirely on the caller doing `members.add(root)` before the two calls, an invisible
  contract that a future 3rd call site could violate without a compile error. Fixed by moving
  `result.add(start)` into the method (right after `visited.add(start)`) and deleting the caller's
  `members.add(root)` line — behavior-identical because `result` is a `Set`, so the two calls now
  each (redundantly, harmlessly) add `root` on top of what the caller no longer adds. Added a
  Javadoc making the contract explicit: `@param start ... always included in {@code result}`. (2)
  **Applied** — `new TableRef(st.schemaName(), st.table().name())` was duplicated in 3 places
  (`appendTableErDiagram`'s canonical-order filter, `buildFkGraph`'s `known` stream, and
  `buildFkGraph`'s `src` local) — a real 3-occurrence duplication matching this file's own
  established "2nd occurrence is real, extract" convention (see the `SchemaTableKey`/`keyOf`
  precedent from 2026-07-23 above, same shape: a private static `TableRef refOf(SchemaTable st)`
  helper). Extracted `private static TableRef refOf(SchemaTable st)` placed directly after the
  `TableRef` record declaration; replaced all 3 call sites (`ordered.stream().map(st -> new
  TableRef(...))` → `.map(JdbcMarkdownGenerator::refOf)`, and the two direct `new TableRef(...)`
  constructions → `refOf(st)`). Did NOT touch the two-line filter's readability shape further —
  spotless auto-wrapped `fkGraph().orderedTables().stream().filter(st ->
  members.contains(refOf(st))).toList()` into 3 lines on its own; no manual formatting was
  attempted. (3) **Evaluated, no change** — `appendTableErDiagram`'s 3-step shape ((a) neighborhood
  BFS via `collectReachable` x2, (b) canonical-order filter, (c) delegate to
  `appendErDiagramSection`) was considered for a `neighborhoodOf(TableRef root)` extraction (the
  orchestrator flagged this as a "maybe, for future guard/body separation" candidate ahead of
  cycles 5/6 that will add `!erDiagram`/`!erDiagramPerTable` guards). Skipped: at 13 lines with no
  duplication and no deep nesting, extracting now would be preemptive refactoring for a guard that
  doesn't exist yet in this cycle's diff — YAGNI; the orchestrator's own instructions explicitly
  forbid adding those guards this cycle, so there's nothing concrete yet to "separate from." Revisit
  when cycle 5/6 actually lands a guard clause in this method — at that point a guard-then-delegate
  shape may make a `neighborhoodOf` extraction genuinely justified (matching `appendErDiagram`'s
  existing guard-then-delegate shape). Also confirmed via the Javadoc-density check (candidate 3):
  `TableRef`/`FkGraph`/`SchemaTable` are all bare one-line private records with **no** Javadoc in
  this file (matches existing convention — only `CompiledExclude` among the private records has
  Javadoc, because it exists specifically to explain *why* it exists, i.e. precompilation
  motivation) and `buildFkGraph`/`fkGraph()` match the sparse-Javadoc convention of other simple
  private builder/accessor methods (`nonExcludedTables()`, `schemasContainingTable`) — no Javadoc
  added to these, consistent with the "don't manufacture documentation" rule from the 2026-07-28
  `VALID_LAYOUT_NAME_PATTERN` entry above.
  **CRITICAL DESIGN CONSTRAINT — recorded per orchestrator's explicit request**: the two
  `collectReachable(root, fkGraph().forward(), members)` / `collectReachable(root,
  fkGraph().backward(), members)` calls in `appendTableErDiagram` MUST each use their own
  call-local `visited` set (never a shared/passed-in `visited`). If a future edit refactors
  `collectReachable` to accept an external `visited` set shared across both the forward and
  backward call (e.g. to "avoid re-visiting the root twice" or to "unify the two BFS passes into
  one"), it will silently corrupt the ancestor/descendant semantics: a node reached via the forward
  (descendant... actually ancestor via FK-forward) edge would then block the backward pass from
  revisiting it even via a genuinely different edge direction, causing some genuinely-reachable
  backward-direction nodes to be skipped once they'd already been marked visited by the forward
  pass (or vice versa). This produces incomplete `members` sets — a real correctness bug, not
  merely a style issue — and would NOT necessarily be caught by the existing test suite except by
  a fixture specifically shaped to have a node reachable in *both* directions from the root (a
  diamond-shaped or cyclic-looking FK graph). The regression-guard phase hand-verified this
  BFS-independence property is required; any future tidy that "simplifies" `collectReachable`'s
  signature to share `visited` across both calls must be rejected/reverted regardless of whether
  tests still pass.
  Verified via `run_test` (module=migraphe-plugin-jdbc, then full ~968-test suite, both green
  before AND after `run_spotless`); `run_spotless` reformatted only the filter-chain line
  (multi-line wrap), no other content change.

- 2026-07-28 (`erDiagramPerTable` guard cycle, `neighborhoodOf` extraction, applied — reverses the
  prior "too early" skip): the Green-phase diff added `boolean erDiagramPerTable` + a
  `if (!erDiagramPerTable) { return; }` guard as the first line of `appendTableErDiagram`. The
  orchestrator explicitly flagged that the *next* cycle (6) will add a second guard
  (`!erDiagram`, the master switch also suppressing per-table diagrams) to the same method, and
  asked whether the now-4-responsibility method ((a) guard(s), (b) neighborhood BFS via 2x
  `collectReachable` + canonical-order filter, (c) empty check, (d) delegate to
  `appendErDiagramSection`) had crossed the threshold for extracting the BFS/filter block into its
  own method. The prior cycle (2026-07-28, `collectReachable` implicit contract entry above) had
  evaluated and **skipped** this same extraction as premature ("no guard yet, YAGNI"). This cycle
  reversed that call: extracted `private List<SchemaTable> neighborhoodOf(TableRef root)`
  (root→members-BFS→canonical-order-filter→return), leaving `appendTableErDiagram` as a clean
  guard→fetch→empty-check→delegate shape that now mirrors `appendErDiagram`'s existing shape
  exactly (`if (!erDiagram) return; List<SchemaTable> tables = nonExcludedTables(); if
  (tables.isEmpty()) return; appendErDiagramSection(sb, tables);` — same 4-step narrative, same
  "fetch method name is a plain noun/noun-phrase" convention: `nonExcludedTables()` /
  `neighborhoodOf(root)`). Deciding factor over the prior skip: the guard-count actually grew from
  0→1 this cycle (not merely "about to grow" as it was last time), and a *second* guard landing
  next cycle was stated as a near-certainty by the orchestrator, not speculative — at that point a
  2-guard-then-BFS-then-filter method would clearly be doing too much in one place, and doing the
  extraction now (once, cleanly, before the 2nd guard lands) is cheaper than doing it under time
  pressure in the very next cycle. General lesson reinforcing (not contradicting) the prior skip:
  "no guard yet → don't extract for a guard that doesn't exist" is right when the guard is
  *hypothetical*; once a *first* guard has actually landed and a *second* is *explicitly
  committed* for the next cycle by the task description itself, that's the trigger point, not
  "wait until both guards exist." Bundled a second, zero-risk fix in the same edit: the
  pre-existing `appendTableErDiagram` Javadoc said "showing the table itself plus every table
  transitively reachable by following its foreign keys (its ancestors)" — stale wording that only
  described the forward/ancestor half of the traversal even though the method (since an earlier
  cycle, see the `collectReachable`/descendant-BFS entries above) has always also traversed
  backward/descendants; the existing test `tablePageErDiagramIncludesTransitiveDescendantsButNotTheirAncestors`
  proves descendants were already covered. Reworded to "following foreign keys in either direction
  (its ancestors and descendants)" — this predates the current diff and wasn't one of the 3
  assigned candidates, but was folded in since it's a trivial, zero-risk, same-method Javadoc fix
  made while already editing that exact method; documented separately here in case a future
  session wonders why an "unassigned" doc fix appeared in this cycle's diff. Verified via
  `run_test` (module=migraphe-plugin-jdbc, then full ~968-test suite, both green) and
  `run_spotless` (reformatted migraphe-plugin-jdbc, cache not up-to-date from the edit, but
  produced no further content change on re-read — confirmed by a follow-up `run_test` reporting
  `UP-TO-DATE` for the module's test task, proving spotless didn't touch compiled sources again).

## Constructor Arity / API-Shape Tracking (ErDiagramOptions extraction trigger)
- `JdbcMarkdownGenerator`'s public constructor has grown telescopically with each new ER-diagram
  config flag: 3-arg (base) → 5-arg (`erDiagram`, `erDiagramKeysOnly`) → 6-arg (`erDiagramLayout`,
  Session 2026-07-27/28) → **7-arg expected once `erDiagramPerTable` is wired through** (this
  cycle added the `JdbcMarkdownDefinition` config field + Template Method test coverage, but did
  NOT yet touch `JdbcMarkdownGenerator`'s constructor or `JdbcMarkdownPlugin.output()` wiring —
  that wiring is presumably a separate, not-yet-landed cycle).
- **Decision, recorded for future sessions**: do NOT extract an `ErDiagramOptions` record (bundling
  `erDiagram`/`erDiagramKeysOnly`/`erDiagramLayout`/`erDiagramPerTable`, and any further ER flags)
  as part of *this* feature's completion. `JdbcMarkdownGenerator`'s constructor is public API
  (`migraphe-plugin-jdbc`, consumed by `PostgreSQLMarkdownGenerator`/`MySQLMarkdownGenerator`
  subclass constructors calling `super(...)`), so changing its shape is an API-shape refactor, not
  a tidy — per this project's TDD discipline, feature-adding Green-phase work and API-shape
  Refactor-phase work should not be mixed in the same cycle.
- **UPDATE 2026-07-28 (trigger reached, extraction explicitly deferred by the orchestrator)**: a
  6th ER-diagram-related field, `erDiagramPerTableMaxEntities` (`@WithDefault("60") int`), was added
  to `JdbcMarkdownDefinition` this cycle. As of this cycle it exists ONLY on the `*Definition`
  interface + the 3 modules' test-double `definition(...)` helpers + `JdbcMarkdownDefinitionTest` —
  **`JdbcMarkdownGenerator`'s constructor was NOT touched and remains at 7 args** (verified by
  reading `JdbcMarkdownGenerator.java` directly: telescoping 3→5→6→7-arg constructors, topping out
  at the `erDiagramPerTable` overload; `JdbcMarkdownPlugin.output()` still calls the 7-arg form and
  does not read `definition.erDiagramPerTableMaxEntities()` anywhere yet). This is the same
  "`*Definition` field lands first, `*Generator`/`*Plugin` wiring lands in a later cycle" two-step
  pattern already established for `erDiagramPerTable` itself. **The 7→8-arg trigger will fire the
  *next* time a cycle wires `erDiagramPerTableMaxEntities` into the `JdbcMarkdownGenerator`
  constructor** (mirroring the `erDiagramPerTable`-wiring cycle) — at that point the constructor
  would grow from 7 to 8 args. **The parent orchestrator has explicitly pre-decided, ahead of that
  wiring cycle landing, that when it does land, the 8th arg should still be added via telescoping
  (NOT via an `ErDiagramOptions` extraction in the same cycle as the wiring)** — extraction of
  `ErDiagramOptions` is to be treated as its own independent, separately-scoped Refactor session,
  not bundled with the wiring/feature cycle. **Reason recorded by the orchestrator**: changing the
  shape of a public API consumed across 3 JitPack-distributed modules
  (`migraphe-plugin-jdbc`/`-postgresql`/`-mysql`) is a breaking change and must not be mixed into the
  same cycle as a feature addition — API-shape changes need their own dedicated review/session.
  **Known trade-off, recorded explicitly**: telescoping to 8 args now (instead of extracting
  `ErDiagramOptions` in the wiring cycle) means the public API will change shape *twice* — once
  7→8 (this upcoming telescoping step), and again 8→`ErDiagramOptions` (the eventual extraction) —
  rather than once, if the extraction had been done together with the 8th-arg wiring. This
  double-churn was a deliberate, informed choice by the orchestrator (scope separation prioritized
  over minimizing total API churn), not an oversight — do not "helpfully" pre-empt it by extracting
  `ErDiagramOptions` early in a future wiring cycle without being explicitly re-instructed to do so.
  **Suggested compatibility approach for the eventual extraction** (proposal only, not yet
  authorized): keep the existing (up to 8-arg) constructor(s) intact but `@Deprecated`, and add a new
  constructor overload accepting an `ErDiagramOptions` record, with the deprecated constructors
  delegating to it internally — this preserves source/binary compatibility for any external
  JitPack consumer already calling the old constructor shape directly.
- **General lesson for this "Constructor Arity" section going forward**: when a new ER-diagram
  `@WithDefault` field lands on `JdbcMarkdownDefinition`, always check (by reading
  `JdbcMarkdownGenerator.java` and `*MarkdownPlugin.output()` directly, not by trusting this memory
  file's arg-count claims) whether the *same* cycle also wired it into the generator constructor —
  the `*Definition`-field-first / `*Generator`-wiring-later two-step split recurs often enough in
  this feature's history (see `erDiagramPerTable` above, now `erDiagramPerTableMaxEntities`) that
  assuming "field added ⇒ constructor arg count already grew" would be a stale-memory mistake, the
  same category of error already documented at the top of this file's Template Method Pattern
  section for the `erDiagram`/`erDiagramKeysOnly` wiring note.
- `JdbcMarkdownGenerator` itself is approaching ~1100 lines with this feature's completion. Given
  this file's long history of small in-place tidies (see entries above — regex precompilation,
  entity-ID memoization, schema-lookup maps, hex formatting), a future dedicated session should
  evaluate extracting a standalone `ErDiagramRenderer` (or similar) collaborator to own the
  `appendErDiagram()`/`appendErRelationship()`/entity-ID/hex/Mermaid-sanitization logic. That
  extraction would move >200 lines and is explicitly **out of scope for `tidy-after-green`** (per
  the `/tdd-cycle` routing rule: net-new/large-move refactors route to `general-purpose`, not this
  agent) — left here as a flagged future-session task, not something to attempt piecemeal during
  routine Refactor phases.

- 2026-07-28 (`appendTableErDiagram` 2nd guard `!erDiagram` merged into 1st guard `!erDiagramPerTable`,
  applied): the Green-phase diff for this cycle added `if (!erDiagram) { return; }` as a brand-new
  standalone guard placed *before* the pre-existing `if (!erDiagramPerTable) { return; }` (rather than
  merging into it), plus the Javadoc line "Does nothing if {@code erDiagram} (the master switch) or
  {@code erDiagramPerTable} was disabled" — that Javadoc wording already reads as a natural `||`
  condition. Grepped the whole file for any other adjacent-single-condition-guard pair or any existing
  compound `||`/`&&` guard condition: found zero precedent either way (every other guard in this file
  is a single independent condition), so there was no "established two-guard style" to preserve.
  Sibling method `appendErDiagram()` (the index.md counterpart) uses exactly one guard
  (`if (!erDiagram) return;` only — it has no per-table-equivalent flag). Merged the two conditions
  into `if (!erDiagram || !erDiagramPerTable) { return; }`, making `appendTableErDiagram`'s guard
  shape a single line matching `appendErDiagram`'s single-guard convention structurally (even though
  the condition itself is now compound) — left the Javadoc untouched since its existing "X or Y"
  wording already matched the merged condition without needing a rewrite. All tests green
  before/after (module `migraphe-plugin-jdbc` + full ~968-test suite); `run_spotless` ran
  `spotlessJavaApply` on migraphe-plugin-jdbc (cache invalidated by the edit) but produced no further
  content change on re-read. **General rule reinforcing the `neighborhoodOf` extraction entry
  above**: when a *new* guard is about to be added right next to an existing single-condition guard
  in the same method, and the file has no established precedent for keeping guards as separate
  sequential `if` blocks vs. merging with `||`, prefer merging into a single compound-condition guard
  clause when (a) both conditions gate the exact same early-return with no distinct side effects per
  branch, and (b) a sibling method in the same file already uses the single-guard shape for an
  analogous "should this section render at all" check — matching the established single-guard
  narrative outweighs the minor loss of "one condition per line" granularity. This is the trigger
  point for merging; do NOT wait for a 3rd guard to "prove" the pattern, since two guards guarding
  the identical unconditional early return are already redundant as separate blocks by definition.

- 2026-07-28 (`erDiagramPerTable` wiring into `JdbcMarkdownPlugin.output()`, applied by a prior
  Green phase this same session; tidy pass on the test-side diff): `output()` now passes
  `definition.erDiagramPerTable()` as the 7th constructor arg to `JdbcMarkdownGenerator`
  (`type: jdbc-markdown` is therefore **fully wired end-to-end**: config field →
  `JdbcMarkdownDefinition.erDiagramPerTable()` → `JdbcMarkdownPlugin.output()` →
  `JdbcMarkdownGenerator` constructor → guarded `appendTableErDiagram`). The accompanying
  `JdbcMarkdownPluginTest` diff added a 3-arg `definition(String name, boolean erDiagram, boolean
  erDiagramPerTable)` overload with the existing 2-arg `definition(String name, boolean
  erDiagram)` telescoping to it (`erDiagramPerTable` defaults to `true`) — matches this file's
  established telescoping-test-helper convention exactly (see the 2026-07-27/28
  `definition(...)`/`resolverFor(...)` entry above), confirmed not breaking any of the existing
  4 call sites. Also found and fixed a small **intra-test** duplication while reviewing the new
  test `outputOmitsErDiagramOnTablePageWhenPerTableDisabled` against its closest sibling
  `outputExportedKeyLinksToReferencingTable`: both tests independently repeated the
  definition-name string literal (e.g. `"exported-key-test"`) twice within the *same* test
  method — once as the `definition(name, ...)` constructor arg, again as the first
  `tempDir.resolve(...)` path segment when building `usersMd` — a real typo-risk duplication
  (if the two literals ever drifted, the test would break confusingly). Replaced the second
  occurrence with `definition.name()` in both tests (behavior-identical: `definition.name()`
  always returns the exact string originally passed in). This is a different duplication shape
  than the cross-test "2nd occurrence of setup boilerplate" pattern this file's history usually
  tracks (see `schemaInfoWithUsersAndOrders`/`schemaInfoWithUsersTable` entries above) — it's a
  same-test-method literal repeated for two different purposes (config value vs. path
  assertion), fixable by referencing the object's own accessor instead of extracting a new
  helper. General lesson: when a test constructs an object from a literal and later reconstructs
  an assertion path from the *same* literal typed a second time, prefer reading the value back
  off the object (`definition.name()`) over introducing a shared constant or helper — smallest
  possible diff, and removes the literal-drift risk entirely. All tests green before/after
  (module + full ~968-test suite); `run_spotless` ran `spotlessJavaApply` on migraphe-plugin-jdbc
  (cache invalidated by the edit) but produced no further content change on re-read.
  **IMPORTANT — PostgreSQL/MySQL gap**: `PostgreSQLMarkdownPlugin.output()` and
  `MySQLMarkdownPlugin.output()` have **not** been updated to pass `erDiagramPerTable` through
  yet (as of this session) — setting `er-diagram-per-table` in a `postgresql-markdown` or
  `mysql-markdown` generator config is currently silently ignored (the generator always uses
  whatever default the N-arg constructor without that parameter supplies). This wiring is
  explicitly deferred to the *next* cycle, which is also expected to unify the three plugins'
  `excludes()` resolution (see the "Constructor Arity" section below and the 2026-07-28
  `resolveExcludes` 3-way-inconsistency entry above) — both changes should land together since
  touching `PostgreSQLMarkdownPlugin.output()`/`MySQLMarkdownPlugin.output()` for the
  `erDiagramPerTable` wiring is the same edit site as the excludes unification, so bundling them
  keeps the diff to one place instead of two separate touches to the same method.

- **Status note (2026-07-28): the generator-side per-table ER-diagram neighborhood feature is now
  functionally complete** across guard logic (`erDiagram` master switch + `erDiagramPerTable`
  sub-switch, single merged guard), BFS/canonical-order extraction (`neighborhoodOf`,
  `collectReachable` with the documented independent-`visited`-per-call constraint), and rendering
  reuse (shared `appendErDiagramSection` two-pass structure with `appendErDiagram`). Test coverage
  (5 table-page-focused tests: `tablePageContainsErDiagramSection`,
  `tablePageErDiagramIncludesTransitiveAncestors`,
  `tablePageErDiagramIncludesTransitiveDescendantsButNotTheirAncestors`,
  `tablePageOmitsErDiagramWhenPerTableDisabled`, `tablePageOmitsErDiagramWhenErDiagramDisabled`) was
  reviewed for extraction-worthiness this cycle and found NOT to need consolidation — each test uses
  a genuinely different `JdbcMarkdownGenerator` constructor-arg combination (4-arg default-true,
  5-arg `false` for erDiagram, 7-arg `false` for erDiagramPerTable), so there is no verbatim setup
  duplication to extract, only superficially similar test bodies. **The remaining gap for this whole
  feature (per-table ER diagrams) is *plugin wiring only*** — i.e., whether/how `erDiagramPerTable`
  is threaded through `JdbcMarkdownPlugin.output()` / `PostgreSQLMarkdownPlugin.output()` /
  `MySQLMarkdownPlugin.output()` (mirroring the `erDiagram`/`erDiagramKeysOnly`/`erDiagramLayout`
  wiring precedent from the constructor-arity entries above) and the corresponding
  `JdbcMarkdownDefinition`/subclass `@WithDefault` config field, if not already landed — check
  current source before assuming this gap is still open, since it may close in a subsequent cycle
  without an explicit memory update (see the "erDiagram wiring" stale-note correction at the top of
  this file's Template Method Pattern section for a precedent of exactly this kind of drift).

- **Status note (2026-07-28, updated): both (A) `er-diagram-layout` and (B) `er-diagram-per-table`
  are now end-to-end complete across all 3 markdown plugins** — `jdbc-markdown`,
  `postgresql-markdown`, `mysql-markdown` all wire `definition.erDiagramLayout()` and
  `definition.erDiagramPerTable()` through their respective `*MarkdownPlugin.output()` into the
  7-arg `*MarkdownGenerator` constructor. The "plugin wiring only" gap noted in the status entry
  above is now closed for all three plugins (confirmed by reading current source, not just prior
  memory).
- 2026-07-28 (3-way `excludes()` resolution unification, applied): per the previously-recorded
  3-way inconsistency (see the `MySQLMarkdownGenerator` 2026-07-28 status entry above), unified
  `JdbcMarkdownPlugin`, `PostgreSQLMarkdownPlugin`, `MySQLMarkdownPlugin`'s `output()` methods to
  all resolve excludes the same way: `var excludes = definition.excludes().orElse(List.of());`
  inlined directly in `output()`, with `List` imported (no fully-qualified `java.util.List.of()`
  anywhere). Concretely: (1) removed `JdbcMarkdownPlugin`'s private static `resolveExcludes(...)`
  helper (single call site, one-line body — extracting it violated this file's own "don't extract
  for a single occurrence" convention) and inlined the expression at its one call site; (2) fixed
  `PostgreSQLMarkdownPlugin` to `import java.util.List;` and call `List.of()` instead of
  `java.util.List.of()` (it already inlined the expression, only the import was inconsistent);
  (3) `MySQLMarkdownPlugin` needed no change — it already inlined with an imported `List`, which
  turned out to be the "target" style all three converged on. No behavior change: all three
  expressions were byte-for-byte identical (`definition.excludes().orElse(List.of())`) before and
  after: this was pure style unification (deleting an indirection layer + fixing an import), not a
  logic change. Verified via full-suite `run_test` (green before and after) and `run_spotless`
  (reformatted `migraphe-plugin-jdbc`/`migraphe-plugin-postgresql`/`migraphe-plugin-mysql` due to
  cache invalidation from the edits, but produced no further content change on re-read).
  **Correction to prior memory**: an earlier task description referenced a belief that
  `resolveExcludes`/one of the three `excludes()` call sites performed a defensive copy (e.g.
  `List.copyOf(...)`) that the other two lacked. This was verified **false** by reading current
  source before this cycle's edit — all three were the exact same one-line expression
  (`definition.excludes().orElse(List.of())`, no `List.copyOf` anywhere in any of the three
  `output()` methods). The only real differences were (a) helper-method indirection (Jdbc only)
  and (b) fully-qualified vs. imported `List` (Postgres only) — both now unified. General lesson:
  when a task description asserts a specific behavioral difference (e.g. "X does a defensive copy
  and Y doesn't") between near-duplicate call sites, always re-read all the actual call sites
  before touching them — don't trust the claim at face value even when it comes from an
  orchestrator/regression-guard summary, since such claims can themselves be stale or mistaken (this
  is the same "verify before trusting a memory/summary claim" discipline already established
  elsewhere in this file for the `erDiagram`-wiring stale-note correction at the top of the
  Template Method Pattern section).
- 2026-07-28 (other tidy candidates reviewed this cycle, all skipped): (1) the 7-arg constructor
  Javadoc `@param erDiagramPerTable` line wraps at a slightly different word boundary between
  `JdbcMarkdownGenerator` ("...emitted on each table's\n     *     own Markdown document") and
  `PostgreSQLMarkdownGenerator`/`MySQLMarkdownGenerator` ("...emitted on each\n     *     table's
  own Markdown document") — confirmed this is pure line-wrap placement with byte-identical
  rendered text, not a content drift; not touched (manufacturing a diff here would only reflow
  prose with zero readability gain, against the "don't force a change" convention). (2) the new
  `outputOmitsErDiagramOnTablePageWhenPerTableDisabled` tests added to
  `JdbcMarkdownPluginTest`/`PostgreSQLMarkdownPluginTest`/`MySQLMarkdownPluginTest` were diffed
  against each other and against their file's own established helper conventions
  (`definition(...)`, `resolverFor(...)`, `schemaInfoWithUsersAndOrders`/`schemaInfoWithUsersTable`)
  — all three already follow their file's existing pattern exactly (Postgres and MySQL are
  structurally identical to each other; Jdbc differs only in the pre-existing, already-documented
  instance-field-`@TempDir` vs. method-parameter-`@TempDir` convention gap from the 2026-07-27/28
  `definition(...)`/`resolverFor(...)` entry above) — no duplication or naming drift found, no
  change made.

- 2026-07-28 (`erDiagramPerTableMaxEntities` config field, tidy pass -- skipped, memory updated):
  the Green-phase diff added `@WithDefault("60") int erDiagramPerTableMaxEntities()` to
  `JdbcMarkdownDefinition` (Javadoc-complete, 6th ER-diagram field) plus a matching
  `@Override public int erDiagramPerTableMaxEntities() { return 60; }` in each of the 3 modules'
  existing shared `definition(...)` test helpers, plus `erDiagramPerTableMaxEntitiesDefaultValue()`
  in `JdbcMarkdownDefinitionTest`. All 3 assigned candidates evaluated, all clean, zero changes
  made: (1) Javadoc across the now-6 ER-diagram fields (`erDiagram`/`erDiagramKeysOnly`/
  `erDiagramLayout`/`erDiagramPerTable`/`erDiagramPerTableMaxEntities`) -- verbosity differences
  reflect genuinely differing semantic complexity (matches the same conclusion already reached for
  the 4-field state in the `erDiagramPerTable` entry above), no drift. (2) the 3 modules' anonymous
  test-double overrides -- each is a single-occurrence insert into an already-existing shared
  helper (no new duplication; matches the "field lands inside existing helper, correctly a no-op
  per-module" precedent). (3) the new test's naming/structure -- one-line body via the pre-extracted
  `defaultDefinition()` helper, identical shape to its sibling `erDiagramPerTableDefaultValue()`.
  Confirmed via source read (not memory) that this cycle did NOT wire the field into
  `JdbcMarkdownGenerator`'s constructor or any `*MarkdownPlugin.output()` -- the generator
  constructor remains at 7 args, `output()` still calls the 7-arg form. See the updated
  "Constructor Arity / API-Shape Tracking" section above for the full trigger/deferral decision
  the orchestrator recorded this cycle regarding the eventual 7-to-8-arg wiring step and the
  `ErDiagramOptions` extraction being explicitly deferred to its own future session.
  Default-value-60 limitation, recorded per orchestrator's request: entity count is only a
  proxy for rendered character count in the per-table neighborhood ER Diagram -- each entity costs
  roughly 45 chars of Mermaid boilerplate (id, alias brackets, `erDiagram` block lines) plus
  roughly 40 chars per rendered column line. For a typical 8-10-column table, 60 entities work out
  to roughly 22K-28K characters, comfortably under GitHub's ~50K-character Mermaid-in-Markdown
  rendering limit. However, for wide tables (20+ columns), 60 entities can exceed 50K characters --
  the fixed entity-count default does not scale with column width. A future session may want to
  switch this limit (or add a companion limit) to a character-count-based measure instead of a
  raw entity count; not attempted this cycle since no code change was in scope (Definition-field-only
  diff, no generator-side rendering logic touched yet). Verified via full-suite `run_test`
  (all green, every module task reported `UP-TO-DATE`, confirming no production/test file was
  altered this cycle) -- legitimate `tidy_status: skipped`.

- 2026-07-28 (`erDiagramPerTableMaxEntities` default-value duplication, applied -- the 7-arg to
  8-arg telescoping cycle's wiring finally landed and exposed the flagged risk): once
  `JdbcMarkdownGenerator`'s constructor grew to 8 args (see "Constructor Arity" section above), the
  default `60` for `erDiagramPerTableMaxEntities` existed as an independently hardcoded literal in
  two places: `JdbcMarkdownDefinition.erDiagramPerTableMaxEntities()`'s `@WithDefault("60")` and the
  7-arg constructor's `this(..., 60)` delegation. Fixed by adding `private static final int
  DEFAULT_ER_DIAGRAM_PER_TABLE_MAX_ENTITIES = 60;` to `JdbcMarkdownGenerator` (grouped with the
  other `private static final` fields: `DEFAULT_SCHEMA_EXCLUDE`, `MERMAID_SANITIZE_PATTERN`,
  `VALID_LAYOUT_NAME_PATTERN`) and referencing it from the 7-arg constructor's delegation instead of
  the bare literal; added a plain-prose (non-`{@link}`) comment on `JdbcMarkdownDefinition`'s
  `@WithDefault("60")` Javadoc explaining the two must be kept in sync and that `@WithDefault`'s
  argument must be a compile-time constant string (so it can't reference the generator's constant
  directly). Kept the constant `private` (not `public`) -- deliberately did not widen
  `JdbcMarkdownGenerator`'s public API surface just to give the Definition side a linkable target;
  a private field in another class can't be resolved by `{@link}` from outside that class anyway
  (private = class-scoped, not package-scoped), so the cross-reference in the Definition's Javadoc
  is necessarily a plain-text mention of the constant's simple name, not a compiled link -- this is
  fine and matches how a plain code comment would look, just placed in Javadoc prose instead.
  **Before applying, checked whether this is this file's established convention across all 5
  ER-diagram `@WithDefault` fields (per the orchestrator's explicit request to verify uniformity
  before fixing just one) -- found it is NOT uniform**: `erDiagram` (Definition default `"true"` /
  ctor delegation default `true`) and `erDiagramKeysOnly` (`"false"` / `false`) and
  `erDiagramPerTable` (`"true"` / `true`) all happen to match, but `erDiagramLayout` **diverges
  intentionally** -- Definition default is `"elk"` (a recommended real-world layout engine for
  config-driven/CLI usage) while the ctor's 5-arg-to-6-arg delegation default is `""` (a
  conservative "no frontmatter, do nothing extra" default for direct/library construction bypassing
  config). Since not every field mirrors the Definition default in the constructor, "constructor
  telescoping default always equals the Definition `@WithDefault`" is NOT an unconditional rule in
  this file -- it's a coincidence for boolean fields (where the sensible library-default and the
  sensible config-recommended-default happen to be the same value) but was deliberately NOT applied
  for `erDiagramLayout`. This means extracting a shared constant only for
  `erDiagramPerTableMaxEntities` does not violate an established uniform convention -- there wasn't
  one to begin with (the boolean fields don't currently share a constant either, since `true`/`false`
  literals don't benefit from a named constant the way a magic number like `60` does). **General
  lesson for this file**: when asked to check "is this duplicate-default pattern used
  identically for every field," always compare literal-for-literal across every field rather than
  assuming boolean-typed fields generalize the same way as string/int-typed fields -- a Y/N
  "defaults match" table per field is the fastest way to falsify a blanket "yes, it's the
  convention" assumption (as it did here for `erDiagramLayout`). Did NOT extract analogous
  `private static final boolean DEFAULT_ER_DIAGRAM = true` / `DEFAULT_ER_DIAGRAM_KEYS_ONLY = false`
  / `DEFAULT_ER_DIAGRAM_PER_TABLE = true` constants for the 3 boolean fields that *do* currently
  match -- out of scope for this cycle (only the `erDiagramPerTableMaxEntities` mismatch-risk was
  flagged), and a named constant for a bare `true`/`false` literal is unusual/low-value Java style
  compared to a magic number like `60`. Verified via full-suite `run_test` (green before and after)
  and `run_spotless` (reformatted `migraphe-plugin-jdbc`, only reflowed the two new Javadoc
  comments' line-wrapping, no further content change on re-read).

- 2026-07-28 (`erDiagramPerTableMaxEntities` wired into `JdbcMarkdownPlugin.output()` as the 8th
  constructor arg + `outputOmitsPerTableErDiagramWhenNeighborhoodExceedsMaxEntities` test added,
  tidy pass, applied): confirmed `JdbcMarkdownPlugin.output()` now passes
  `definition.erDiagramPerTableMaxEntities()` as the 8th arg to the (now 8-arg)
  `JdbcMarkdownGenerator` constructor -- the "Constructor Arity" section's flagged 7-to-8-arg
  telescoping step (with `ErDiagramOptions` extraction explicitly deferred) has landed and matches
  the orchestrator's pre-decision exactly (telescoping, not extraction). The test-side
  `definition(...)` helper telescoped cleanly to a 4-arg overload (`definition(name, erDiagram,
  erDiagramPerTable, erDiagramPerTableMaxEntities)`), with the pre-existing 3-arg overload
  delegating `definition(name, erDiagram, erDiagramPerTable, 60)` -- matches this file's established
  telescoping-test-helper convention exactly, no drift. **Applied tidy**: the new test
  (`outputOmitsPerTableErDiagramWhenNeighborhoodExceedsMaxEntities`) pushed the identical
  `schemaName`/`usersMd`-path-building block (`String schemaName = schemaInfo.schemas().get(0)
  .name(); var usersMd = tempDir.resolve(definition.name()).resolve(schemaName).resolve("tables")
  .resolve("USERS.md");`) to its **3rd** verbatim occurrence in this file (previously 2, in
  `outputExportedKeyLinksToReferencingTable` and `outputOmitsErDiagramOnTablePageWhenPerTableDisabled`)
  -- a real, established-pattern duplication (this file's "2nd occurrence is real" convention, now
  crossed a 3rd time). Extracted `private Path usersMdPath(String schemaName, JdbcMarkdownDefinition
  definition)` (parameterized on `schemaName`, NOT on `schemaInfo`, because the first call site
  (`outputExportedKeyLinksToReferencingTable`) independently needs the bare `schemaName` string for
  its own assertion text -- passing `schemaInfo` in and having the helper recompute
  `schemaInfo.schemas().get(0).name()` internally would have left that call site computing the same
  value a second time via a different path, the same "same literal, two purposes" trap already
  documented in the `definition.name()` entry above; keeping `schemaName` as an explicit parameter
  means every call site still declares `String schemaName = schemaInfo.schemas().get(0).name();`
  itself -- trivial 1-line duplication left in place -- while the 4-line `.resolve(...)` chain
  collapses to one method call). All 3 call sites updated; `run_spotless` reflowed the newly-added
  4-arg `definition(...)` overload's parameter list onto multiple lines (was briefly a >100-col
  single line before spotless ran) -- pure formatting, no additional content change on re-read.
  Verified via `run_test` (module=migraphe-plugin-jdbc, then full suite, both green before AND after
  `run_spotless`). **Evaluated, no change**: (a) the literal `60` in the test helper's 3-arg-to-4-arg
  delegation vs. `JdbcMarkdownDefinition`'s `@WithDefault("60")` and `JdbcMarkdownGenerator`'s
  `DEFAULT_ER_DIAGRAM_PER_TABLE_MAX_ENTITIES` private constant -- per the orchestrator's explicit
  instruction, left as a bare literal; a test double's return value is a per-test declaration of
  "this test uses 60," not a promise to track the production default, and
  `DEFAULT_ER_DIAGRAM_PER_TABLE_MAX_ENTITIES` is `private` so the test cannot reference it directly
  without a visibility-widening change that would be a bigger intervention than the literal
  duplication risk warrants -- matches the general principle already established elsewhere in this
  file (see the boolean-default-constants discussion in the `erDiagramPerTableMaxEntities
  default-value duplication` entry above) that not every Definition-default/constructor-default pair
  needs a shared named constant, especially when the consumer is test-only scaffolding rather than
  production code. (b) production code (`JdbcMarkdownGenerator`'s `appendTableErDiagram`/
  `neighborhoodOf`/`appendErDiagramOmittedSection`) was read and found already complete/clean from a
  prior cycle -- not touched, out of scope per the orchestrator's explicit "don't touch
  generator/plugin logic" instruction for this cycle (scoped to the test-helper/test-body diff
  only).

- 2026-07-28 (`protected`-widening of `DEFAULT_ER_DIAGRAM_PER_TABLE_MAX_ENTITIES` to collapse the
  subclasses' two-terminal-constructor structure back to one, applied -- corrects the prior
  "protected NOT required" prediction below): once `PostgreSQLMarkdownGenerator`/
  `MySQLMarkdownGenerator` grew an 8-arg constructor for `erDiagramPerTableMaxEntities` (see the
  "Next Cycle Design Note" entry below), each subclass ended up with **two terminal constructors**
  instead of one: the pre-existing 7-arg constructor still called `super(7 args)` directly (instead
  of delegating to the subclass's own 8-arg constructor) and independently assigned
  `this.pgInfo = schemaInfo` / `this.mysqlInfo = schemaInfo`, while the new 8-arg constructor did the
  same `super(8 args)` + field-assignment pattern in parallel. Both were individually correct (each
  path assigns the field exactly once, output byte-identical), but this diverged from
  `JdbcMarkdownGenerator` base class's own shape, which keeps exactly one terminal (the 8-arg
  constructor) with every shorter-arg constructor delegating via `this(...)`. **Fix**: (1) widened
  `JdbcMarkdownGenerator.DEFAULT_ER_DIAGRAM_PER_TABLE_MAX_ENTITIES` from `private` to `protected`
  (safe -- protected static fields are accessible via simple name from a subclass's own code
  regardless of package, per JLS 6.6.2's "accessed through a reference of the subclass type" carve-out
  not even applying to *static* members in the first place); (2) rewrote each subclass's 7-arg
  constructor body from `super(7 args); this.pgInfo/mysqlInfo = schemaInfo;` to `this(7 args,
  DEFAULT_ER_DIAGRAM_PER_TABLE_MAX_ENTITIES);` (delegating to its own 8-arg constructor). This makes
  the 8-arg constructor the subclass's sole terminal (field assigned exactly once, via the single
  `super(...)` + assignment there), mirroring the base class's telescoping shape exactly. Confirmed
  the default-value behavior is unchanged: constructing via the 7-arg constructor still yields
  `erDiagramPerTableMaxEntities == 60` (now supplied by the subclass instead of the base class, same
  numeric value). Verified via full-suite `run_test` (green before AND after) and `run_spotless`
  (reformatted `migraphe-plugin-jdbc`/`-postgresql`/`-mysql` due to cache invalidation, but produced
  no further content change on re-read -- the new `this(...)` delegation block wrapped identically to
  how spotless had already wrapped the old `super(...)` block). **Correction to the "Next Cycle Design
  Note" prediction below**: that note's reasoning ("the subclass's new (N+1)-arg constructor only
  needs to `super(...)`-delegate ... it does not need to *read* the default value itself") was true in
  isolation but missed a structural side effect -- if the *existing* shorter-arg constructor keeps its
  own direct `super(...)` call instead of being rewritten to delegate to the new longer constructor,
  adding the new constructor does NOT reduce the terminal count, it *increases* it by one. The note's
  own final caveat ("confirm whether each subclass's existing shorter-arg constructor ... delegates
  via `super(...)` directly ... or via `this(...)` self-delegation first") correctly flagged the
  question to check, but the conclusion drawn from a "yes, it's `super(...)` directly" answer should
  have been "then the existing constructor must be REWRITTEN to `this(...)`-delegate to the new one,"
  not "then no `protected` widening is needed." **General lesson, generalizing beyond this specific
  field**: when telescoping a subclass's public constructor chain by one more level (adding an
  (N+1)-arg constructor above an existing N-arg one), the correctness check is not just "does the new
  constructor call `super(...)` correctly and assign fields once" (true either way) but "after this
  change, is there still exactly one terminal constructor in the subclass" -- if the existing N-arg
  constructor currently calls `super(N args)` directly, it must be rewritten to `this(N args,
  default)` delegating to the new (N+1)-arg constructor, or the subclass silently accumulates a second
  terminal and a second copy of the field-assignment line. This is the *subclass* analogue of the
  base class's own established telescoping convention (see the very first "Constructor Arity" section
  below) and should be checked every time a subclass constructor gains a new trailing parameter, not
  just when the base class does.

## Next Cycle Design Note -- PostgreSQL/MySQL size-safety-valve wiring (recorded per orchestrator request)
> **SUPERSEDED 2026-07-28 -- see the "protected-widening" tidy entry immediately above this
> section.** The "protected NOT required" conclusion below turned out to be wrong in practice: the
> wiring cycle *did* leave each subclass's pre-existing 7-arg constructor calling `super(7 args)`
> directly instead of rewriting it to `this(...)`-delegate to the new 8-arg constructor, which
> produced two terminal constructors per subclass (harmless behaviorally, but structurally
> inconsistent with the base class). `protected` widening + rewriting the 7-arg constructor to
> delegate was applied as a follow-up tidy. Kept the original note below for historical record of
> the reasoning gap, not because its conclusion is still correct.
- The next cycle is expected to wire `erDiagramPerTableMaxEntities` through
  `PostgreSQLMarkdownGenerator`/`MySQLMarkdownGenerator` and their respective
  `*MarkdownPlugin.output()` methods, mirroring the `jdbc-markdown` wiring recorded in the entry
  immediately above.
- **`DEFAULT_ER_DIAGRAM_PER_TABLE_MAX_ENTITIES`'s `protected` visibility widening is NOT required**
  for that wiring. Reason: the subclasses' new (N+1)-arg constructor only needs to `super(...)`-
  delegate to the base `JdbcMarkdownGenerator`'s already-existing 8-arg constructor -- it does not
  need to *read* the default value itself, only pass through whatever value its own caller
  (`*MarkdownPlugin.output()`) supplies. This avoids widening `JdbcMarkdownGenerator`'s visibility
  surface and avoids any risk of double-assigning subclass-specific fields (`pgInfo`/`mysqlInfo`).
- **Caveat -- verify before implementing**: confirm whether each subclass's *existing* shorter-arg
  constructor (the one that will need to grow by one more telescoping level) currently delegates via
  `super(<N args>)` directly to the base class, or via `this(<N+1 args>, someDefault)`
  self-delegation first. The "no `protected` needed" conclusion above assumes a `super(...)`
  delegation chain (matching `JdbcMarkdownGenerator`'s own internal telescoping shape) -- if a
  subclass instead chains through `this(...)`, the new top-arity constructor still only needs to add
  one more `super(...)` call at the new top level, so the conclusion holds either way, but this
  should be confirmed by reading the current `PostgreSQLMarkdownGenerator`/`MySQLMarkdownGenerator`
  constructor bodies directly (not assumed from this note) before writing the next cycle's diff, per
  this file's own repeated "verify before trusting a memory claim" discipline (see the
  `erDiagram`-wiring and `excludes()` stale-note corrections elsewhere in this file).
