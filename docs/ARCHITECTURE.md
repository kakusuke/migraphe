# Migraphe Architecture & Design Decisions

Detailed rationale for the key design decisions behind Migraphe. The one-line summaries live in [CLAUDE.md](../CLAUDE.md); the full descriptions are kept here.

1. **Task Separation**: MigrationNode (structure) vs Task (execution logic)
2. **Up/Down Migrations**: `upTask()` for forward, `downTask()` for rollback
3. **HistoryRepository**: Pluggable persistence (InMemory, JDBC/PostgreSQL/MySQL, etc.)
4. **DOWN Task Serialization**: Plain text SQL stored in ExecutionRecord
5. **MicroProfile Config + Variable Resolution**: YAML via `@ConfigMapping` (SmallRye). `ConfigLoader.loadConfig` registers ConfigSources with explicit ordinals so `${VAR}` expansion resolves in this order (highest first): Gradle-injected `variables` (600), `environments/*.yaml` profile (500), system properties (`-D`, 400), OS environment variables (300), then the multi-file YAML (100). **OS environment variables are deliberately namespaced under an `env.` prefix** — `System.getenv()` is registered as a `MapConfigSource` keyed `env.<NAME>`, so they are referenced only via `${env.VAR}`, never bare `${VAR}`. This prevents env vars from polluting the flat config-key space that `ConfigLoader.extractTargetIds` scans (a bare SmallRye `EnvConfigSource` would normalize `TARGET_FOO` → `target.foo` and inject phantom target IDs). System properties keep raw keys (explicit, trusted input, same tier as profiles/variables) and are registered as a `MapConfigSource` over `System.getProperties()`. `addDefaultSources()` is intentionally NOT used for this reason. Inline defaults are supported: `${env.VAR:default}` / `${VAR:default}`.
6. **Multi-file Configuration**: `migraphe.yaml`, `targets/*.yaml`, `tasks/**/*.yaml`, `environments/*.yaml`
7. **Auto Task ID**: Generated from file path (e.g., `tasks/db1/create.yaml` → `"db1/create"`)
8. **Plugin System (Phase 11)**: ServiceLoader + URLClassLoader for runtime loading
9. **Listener Pattern (Phase 14)**: Business logic (Core) separated from presentation (CLI/Gradle). `ExecutionListener` for progress notifications, `ExecutionGraphView` for graph rendering with `toString()`
10. **Gradle Plugin (Phase 15)**: `java-gradle-plugin` + Gradle TestKit. Custom `migraphePlugin` configuration for plugin JARs. `@Option` + `-P` property for task arguments. `PluginRegistry.loadFromClassLoader()` for Gradle's classloader
11. **Shared Logic**: `ExecutionContext.createHistoryRepository()`, `ExecutionPlan.filterNodesInOrder()`, `ExecutionGraphView.renderLines()`, `FormatUtils`
12. **DAG Stream Layout Pipeline (Phase 15)**: `MigrationGraph → LayoutSort → LayoutTree → GridCanvas → ExecutionGraphView`. LayoutSort uses Kahn's with comparator (-inDegree, -outDegree, id asc). LayoutTree decomposes DAG into stream tree (greedy chain extension). GridCanvas places streams on 2D grid with `Cell` sealed interface (13 variants), `addNonTreeEdge()` with lane routing, merge row reuse, and crossing detection. Grid extracted as inner class with Cell connectivity methods (`connectsUp()`, `connectsDown()`, etc.)
13. **Unified DAG Execution (Phase 16 → unified in Session 54)**: `DagExecutor(graph, history, listener, direction, maxParallelism)` — single executor for all UP/DOWN + sequential/parallel combinations. Opt-in parallelism via `execution.parallel: true` → consumer passes `maxParallelism` from config; default `maxParallelism=1` for sequential. Internally always uses Virtual Threads + `Semaphore` + `PriorityBlockingQueue` + `ReadyNodeTracker(direction)` (max=1 just bounds the Semaphore — overhead ~50μs/task, <0.1% for typical migrations). `direction` switches `upTask` ↔ `downTask`, `getDependencies` ↔ `getDependents`, `createExecutionPlanFor` ↔ `createReverseExecutionPlanFor`, `upSuccess` ↔ `downSuccess` records, and skip semantics (UP: "already executed", DOWN: "not executed" / "no down task"). **Fail-soft on failure**: tasks that do not (transitively) depend on the failed node continue to execute; tasks that do depend on it are surfaced via `onNodeSkipped` with reason `"dependency failed: <id>"`. `DagExecutor` auto-wraps `historyRepository` and `listener` in `SynchronizedHistoryRepository` / `SynchronizedExecutionListener` (with `instanceof` guard against double-wrap) so consumers pass plain instances. `determineRollbackTargets` is the DOWN-only API kept on `DagExecutor`. `Executor` interface (`determineTargetNodes` + `execute`) shared by all consumers.
14. **JDBC Plugin Extraction (Phase 17)**: Generic `migraphe-plugin-jdbc` module extracts common JDBC logic (connection, SQL execution, history). DB-specific plugins (`postgresql`, `mysql`) extend `JdbcTarget` with fixed driver/label and provide optimized DDL. `SqlStatements` utility for SQL splitting. `JdbcPlugin` (type="jdbc") works standalone for any JDBC database.
15. **Generator Plugin System (Phase 18)**: Generator SPI in `migraphe-api` (`io.github.kakusuke.migraphe.api.generator`). `SchemaInfoProvider<T>` on `MigraphePlugin` for schema extraction. `JdbcSchemaInfoProvider` uses `DatabaseMetaData` → `JdbcSchemaInfo` (19 record types). `JdbcMarkdownPlugin` (type="jdbc-markdown") generates Markdown docs with directory structure, cross-references, and exclude filtering. `GeneratorRegistry` + `GeneratorExecutor` in core. `GenerateCommand` in CLI (`migraphe generate --name`). `MigrapheGenerateTask` in Gradle plugin.
16. **Generator SPI Refactor — Source/Output Separation (Phase 19)**: Data extraction decoupled from rendering. `GeneratorSourcePlugin<T>` extracts typed data (`jdbc-schema` → `JdbcSchemaInfo`, `migration-tree` → `MigrationGraphView`). `GeneratorOutputPlugin` renders data (`jdbc-markdown`, `output-json`). Same data source can output in multiple formats. Legacy `GeneratorPlugin`/`Generator` interfaces removed; `migraphe-generator-api` module merged into `migraphe-api` (`io.github.kakusuke.migraphe.api.generator`). `MigrationGraphView` read-only interface in `migraphe-api`. `SourceContext` (nullable Target + nullable graph). `OutputContext` (definition + outputDir). `GeneratorExecutor.executeAll()` auto-routes based on `source.type` presence. `ProjectConfig.SourceSection` with `Optional<String> type()`. `MigrationTreeSourcePlugin` built into core. `migraphe-plugin-generator-json` module for JSON stdout output via Jackson.
18. **PostgreSQL Generator Plugins**: `PostgreSQLSchemaInfoProvider` (source type=`postgresql-schema`) delegates to `JdbcSchemaInfoProvider` for base JDBC schema, then queries `pg_catalog` for PG-specific objects (extensions, enums, sequences, functions, triggers, materialized views, partitions, policies). `PostgreSQLMarkdownPlugin` (output type=`postgresql-markdown`) extends `JdbcMarkdownGenerator` via Template Method pattern — protected hooks `appendIndexHeader()`, `appendSchemaIndexSections()`, `appendTableSections()` allow DB-specific content injection. Table files include related triggers, policies, and partition info.
19. **MySQL Generator Plugins**: `MySQLSchemaInfoProvider` (source type=`mysql-schema`) uses catalog-based schema discovery (`connection.getCatalog()` + `meta.getTables(catalog, null, ...)`) because MySQL JDBC returns databases as catalogs, not schemas. Queries `information_schema` for MySQL-specific objects (storage engines, table meta/ENGINE/collation, triggers, routines, events, partitions). 7 record types + `MySQLSchemaInfo implements JdbcSchemaInfo`. `MySQLMarkdownPlugin` (output type=`mysql-markdown`) extends `JdbcMarkdownGenerator` with same Template Method pattern as PostgreSQL. **Routine parameters (Session 67)**: read from `information_schema.PARAMETERS` and grouped by a `RoutineKey(schema, name, type)` record — `ROUTINE_TYPE` is part of the identity because a procedure and a function may share a name, and keying on the name alone merges their parameter lists (same failure shape as the Session 65 FK-aggregation bug). The query filters `ORDINAL_POSITION > 0` to drop the row that reports a function's return value. Rendered as a `## Parameters` table; **PostgreSQL deliberately keeps its arguments as the single formatted string from `pg_get_function_arguments()`** rather than mirroring the structured table, because that string is the server's own rendering of defaults/VARIADIC/OUT and decomposing it would be a regression risk. Routine bodies (MySQL `ROUTINE_DEFINITION`, PostgreSQL `pg_proc.prosrc` — body only, not `pg_get_functiondef()` which errors on aggregate/window functions) render through the shared `JdbcMarkdownGenerator.appendDefinitionSection()`, which omits the section when the body is null (insufficient privilege) and grows the code fence one backtick past the longest backtick run in the body so a body containing a Markdown fence cannot terminate the block early.
17. **CLI Maven Resolver (Phase 20)**: `migraphe.yaml` `plugins:` section declares Maven coordinates. `PluginConfigPreParser` (SnakeYAML) pre-parses before SmallRye Config. `MavenPluginResolver` (Maven Resolver 1.9.22 + maven-resolver-provider 3.9.9) resolves artifacts + transitive deps from `~/.m2` + Maven Central. `PluginResolver` orchestrates: YAML → resolve → URLClassLoader. `Main.java` passes classloader to `PluginRegistry` and `GeneratorRegistry`. Plugins are loaded from the classpath and these coordinates only — there is no directory scan. `DefaultServiceLocator` pattern (deprecated but functional). `session.setSystemProperties(System.getProperties())` required for POM profile activation.
20. **JitPack + Lockfile Pinning (Phase 21)**: `migraphe.yaml` gains `repositories:` (HTTPS-only) for additional Maven repos (e.g., `https://jitpack.io`); plugins reference them per-entry via map form `{coordinate, repository: <id>}`. `RepositoryConfig` / `RepositoryRegistry` (with implicit `maven-central`); `RepositoryConfig.testOnly` allows `file://` URLs for IT only. `migraphe.lock.yaml` (lockfile-version 1) pins each plugin and its transitive deps by SHA-256 — generated by `migraphe pin`, verified by `migraphe pin --check` and `migraphe validate`. `LockFileReader` / `LockFileWriter` (SnakeYAML BLOCK + header comment), `LockFileBuilder` (resolved groups → LockFile), `LockSyncChecker` (yaml ↔ lock GA/version drift), `PluginIntegrityVerifier` (SHA-256 verify), all integrated into `PluginResolver.resolve(baseDir)`. Common parent `PluginResolutionException` lets `Main.handleException` suppress stack traces. Lockfile is mandatory whenever `plugins:` is non-empty (no escape hatch). `MavenPluginResolver.resolveGroups` separates root from transitive deps for accurate per-plugin pinning. End-to-end IT (`PluginResolverIntegrationTest`) uses a `file://` `@TempDir` repo to mimic JitPack and exercise all four failure modes (match / missing / out-of-sync / tampered). **Lockfile schema deliberately omits per-plugin `repository:`** — repository selection lives only in `migraphe.yaml`, and SHA-256 is the sole authority for byte identity. Recording provenance in the lockfile would be misleading because Aether's local cache (`~/.m2`) is transparent: a JAR fetched into the cache by another project (e.g., via JitPack) would be served to `migraphe pin` without any remote lookup, so the lockfile would mirror the declaration's claimed source rather than the true origin. `LockFileReader` ignores any legacy `repository:` key for backward compat with lockfiles written by earlier Phase 21 builds.
21. **JitPack Distribution (Phase 22)**: Migraphe artefacts (plugin JARs + Gradle plugin) are published via JitPack as the **primary distribution channel** until Maven Central is live. `jitpack.yml` (JDK 21 via SDKMAN, `install:` step with `-PpublishGroup=com.github.kakusuke.migraphe publishToMavenLocal`) drives the build. `build.gradle.kts:9-16` switches `allprojects.group` to a property-driven `providers.gradleProperty("publishGroup").getOrElse("io.github.kakusuke.migraphe")` so that local `publishToMavenLocal` keeps the Maven-Central-compatible default while JitPack builds emit `com.github.kakusuke.migraphe`. Both groupIds coexist in `~/.m2/`; Java packages remain `io.github.kakusuke.migraphe.*` independent of Maven coordinate. **Gradle plugin id resolution**: the auto-generated plugin marker artifact uses the plugin id's group (`io.github.kakusuke.migraphe`) which is not served by JitPack — so end-user `settings.gradle.kts` must use `pluginManagement.resolutionStrategy.eachPlugin { ... useModule("com.github.kakusuke.migraphe:migraphe-gradle-plugin:${requested.version}") }` to bypass the marker. **End-user docs (`README*.md`, `docs/USER_GUIDE*.md`, `sample/*`) advertise JitPack coordinates with a stable git tag** (`com.github.kakusuke.migraphe:<module>:v0.3.0`); `main-SNAPSHOT` is deliberately avoided in user-facing docs because the current `LockSyncChecker` rejects yaml=`main-SNAPSHOT` against JitPack's resolved-version lockfile entries (`main-<tag>-<commit>-<n>`). When Maven Central distribution lands these will be rewritten in one pass to `io.github.kakusuke.migraphe:<module>:X.Y.Z`. `CONTRIBUTING.md` carries only operational notes (tag-vs-SNAPSHOT trade-offs, SHA-256 instability on every main push, JitPack cache refresh, local `-PpublishGroup` switch).
22. **SQL Statement Splitting — Parser-Combinator Toolkit (Session 55)**: SQL `up`/`down` bodies are split into individual statements by a parser-combinator engine instead of regex/string scanning, which could not honor dialect lexis. **Responsibility split**: `migraphe-plugin-jdbc` provides only a *generic* toolkit in `io.github.kakusuke.migraphe.jdbc.statement` — `SqlParser` (the combinator interface), `SqlParsers` (combinators: `literal`/`seq`/`or`/`anyChar`/`not`/`many`/`opt`/`keyword`/`ref`/`memoize`/`quoted`/`lineComment`/`delimited`/`standardRegion`/`whitespace`), `StatementSplitter` (the split engine), and `DelimiterDirective` (the DELIMITER hook). Each dialect defines its own grammar in its own plugin. `StatementSplitter.standard()` skips `;` inside string literals, quoted identifiers, `--` line comments, and `/* */` block comments; each segment is preserved raw (only outer-trimmed), so leading comments are retained and attached to the following statement (newlines preserved, e.g. a `--` line comment's newline stays inside the segment so the following statement is not commented out), and only empty/whitespace-only segments are dropped. It supports multi-char delimiters plus a DELIMITER-directive hook (the directive is probed after skipping only leading whitespace, not comments). `JdbcTarget.statementSplitter()` returns `standard()` by default. **PostgreSQL** (`PostgreSqlGrammar`, wired via `PostgreSQLTarget.statementSplitter()`) adds dollar-quoting (`$tag$...$tag$`) so `DO $$...$$` / `CREATE FUNCTION ... $$...$$` collapse into a single statement and the inner `;` does not split. It deliberately **has no keyword blocks** so that transaction-control statements like `BEGIN;`/`COMMIT;` split independently — adding a `BEGIN...END` block rule would mis-swallow a bare `BEGIN;`. **MySQL** (`MySqlGrammar`, wired via `MySQLTarget.statementSplitter()`) adds backtick identifiers, `#` and `-- ` (whitespace-required) comments, `\'`/`''` escaped strings, **recursive block grammar** (BEGIN/IF/CASE/LOOP/WHILE/REPEAT), and a **DELIMITER** directive. The recursive grammar naturally yields the "do not split on `;` inside a block" behavior. Because DELIMITER is stateful, it is handled via the splitter's loop state rather than the grammar. **Memoization (Session 66)**: the MySQL block parser is wrapped in `SqlParsers.memoize`, added to the toolkit for this purpose. A keyword that opens no block — the `IF` of `DROP TABLE IF EXISTS` — is rejected only after the body has scanned ahead for an `END` that never arrives, and because the body admits nested blocks that failing scan was repeated for every such keyword it passed over, costing O(2^k) for k of them (measured: 22 statements took 9.7s, 83 hung indefinitely). Memoizing computes each position once; the cached decisions are identical to the uncached ones, so **only cost changes, never which spans are recognized**. `MemoizingParser` keeps one `Memo` (`final String sql` + `final int[] results`) for the most recent input, invalidated via `String.equals` — which short-circuits on identity, and is sound to reuse for an equal-but-distinct input because parsers are pure functions of content. Results are stored verbatim: `parse` returns `-1` or a position in `[0, length]`, so the table is pre-filled with `UNCOMPUTED = -2`, which no real result can collide with. The table is published without synchronization: `Memo`'s final fields prevent a racing reader from seeing a half-built table (JLS 17.5), and purity makes a missed entry cost at most a recomputation. The pre-fill therefore has to happen **inside the `Memo` constructor** — the freeze covers the array contents only as of constructor exit, and a racing reader that saw default `0`s would read them as a cached "matched, consumed nothing". The remaining worst case is O(chars × non-opening keywords) — linear-time would require restricting block openers to statement-start positions, deliberately deferred as a separate grammar change. **Wiring**: `JdbcUpTask`/`JdbcDownTask` call `environment.statementSplitter().split()` and loop-execute the statements in **both** autocommit and transaction modes (transaction mode commits once at the end). The old `SqlStatements` utility was removed.
23. **Markdown ER Diagram (Mermaid) (Session 61)**: The Markdown output plugins (`jdbc-markdown`, `postgresql-markdown`, `mysql-markdown`) embed a single database-wide ER diagram into `index.md` as a fenced ```mermaid `erDiagram` block (no SVG files generated). `JdbcMarkdownGenerator.appendErDiagram` builds it: each output table becomes an entity whose rows carry the column type plus PK/FK markers (a column that is both a primary key and a foreign key is rendered as `PK, FK`); views are excluded. Foreign keys become relationships `<referenced> ||--o{ <fk-holder> : "<fk-name>"`; cardinality is currently fixed at one-to-many (`||--o{`). Relationship labels (FK names) are sanitized, with an empty name falling back to `fk`. FKs whose referenced entity is not in the output set (e.g., excluded) are skipped so no empty entity is emitted, and a schema whose non-excluded table set is empty omits the `erDiagram` fence entirely (Mermaid rejects an entity-less diagram). Toggled by the `erDiagram` flag on `JdbcMarkdownDefinition` (`@WithDefault true`; YAML key `er-diagram` under kebab-case `@ConfigMapping` naming, same convention as `output-dir`). A second flag `erDiagramKeysOnly` (`@WithDefault false`; YAML key `er-diagram-keys-only`) limits each entity's rendered columns to primary-key and foreign-key columns when `true` (relationships unaffected); the default `false` renders all columns. Each plugin wires both `definition.erDiagram()` and `definition.erDiagramKeysOnly()` into a 5-arg generator constructor (3-arg/4-arg constructors retained as delegating overloads for backward compatibility).

    **Entity identity & type/token sanitization (Session 63)** — each ER entity id is an *injective* encoding `sanitizeMermaid(schema) + "_" + sanitizeMermaid(table) + "_" + sha256(schema.length() + ":" + schema + table)[0..8]`; the length-prefixed hash tail keeps distinct `(schema, table)` pairs distinct even when the sanitized `schema_table` prefix would collide (e.g. `("a_b","c")` vs `("a","b_c")`). The readable table name is shown via Mermaid's alias syntax `id["table"]`. Column **type** names are reduced to their base name before sanitization — `cleanTypeName` strips double quotes and keeps the segment after the last dot, so a PostgreSQL enum/UDT type reported by JDBC as `"account"."user_account_status"` renders as `user_account_status`; the enum sentinel size (`COLUMN_SIZE == Integer.MAX_VALUE`) is suppressed rather than emitted as `(2147483647)`. `sanitizeMermaid` uses a precompiled `Pattern`, and exclusion regexes are compiled once in the constructor.

    **Multi-schema support (Session 63)** — the diagram is schema-aware: same-named tables across schemas no longer collide (distinct hashed entity ids), cross-schema foreign-key relationships are drawn, and cross-schema FK/"Referenced By" links in the per-table docs resolve to `../../<referencedSchema>/tables/<t>.md` with the referenced schema name normalized against `schemaInfo.schemas()` (guards driver case/spelling mismatches). It remains a **single combined diagram** — Mermaid `erDiagram` has no subgraph construct, so per-schema visual grouping (boxes) is not done. Verified end-to-end against a real PostgreSQL via a Testcontainers integration test (`PostgreSQLSchemaDocE2ETest`, Session 63). Future work: cardinality refinement (nullability / composite keys).

    **Layout engine — `er-diagram-layout` (Session 64)** — `JdbcMarkdownDefinition.erDiagramLayout()` (`@WithDefault("elk")`; YAML key `er-diagram-layout`) emits a YAML frontmatter block (`---\nconfig:\n  layout: <name>\n---`) as the first content inside the ```mermaid fence, selecting Mermaid's layout engine. The default is `elk` because the diagrams grow wide with heavily crossing edges and Mermaid's own documentation recommends ELK for large, complex diagrams. Only values fully matching `VALID_LAYOUT_NAME_PATTERN` (`[A-Za-z0-9_-]+`) are emitted; any other value — including empty or `null` — **omits the frontmatter entirely** rather than writing an invalid directive that would break the diagram. The field is a `@Nullable String` normalized to `""` in the constructor (same `nullToEmpty` convention used elsewhere in the generator). **Caveats**: frontmatter requires Mermaid 9.4+ (older renderers read the `---` as diagram content), and GitHub's Mermaid does not register `@mermaid-js/layout-elk`, so `layout: elk` silently falls back to dagre there (the diagram still renders).

    **Per-table neighborhood diagrams — `er-diagram-per-table` (Session 64)** — `erDiagramPerTable()` (`@WithDefault("true")`; YAML key `er-diagram-per-table`) emits a **neighborhood ER diagram** on each table page (`<outputDir>/<schema>/tables/<table>.md`), placed immediately after the table header and before `## Columns`. The neighborhood of `T` is `{T} ∪ ancestors*(T) ∪ descendants*(T)` — **not the undirected connected component**: traversal never turns around, so sibling directions ("another descendant of an ancestor", "another ancestor of a descendant") are excluded. Supporting types: a `TableRef` record, an `FkGraph` record (forward/backward adjacency maps + the canonically ordered table list), `buildFkGraph()`, `collectReachable()`, `neighborhoodOf()`, and a **lazily initialized** `fkGraph()`. Several constraints are load-bearing:
    - **Laziness is mandatory**: `nonExcludedTables()` calls the `protected` `isTableExcluded(...)`, so building the graph from the constructor trips ErrorProne's `ConstructorInvokesOverridable` and breaks the zero-warning gate.
    - **`collectReachable()` must keep a call-local `visited` set**. Sharing the accumulating `result` as `visited` would let the descendant pass start from nodes already reached by the ancestor pass, mixing the two directions and turning the neighborhood into the undirected component.
    - **Descendants are derived by inverting every table's `foreignKeys()` (imported), never from `exportedKeys()`**, because: (1) it keeps one single source of truth for edges, shared with the relationship rendering; (2) existing test fixtures do not populate `exportedKeys`; (3) at the time, `JdbcSchemaInfoProvider.buildKeyInfo` keyed its `LinkedHashMap` on `FK_NAME` alone, so on the exported side children silently vanished when constraint names collided (**fixed in Session 65** — see decision 24; the other three reasons still hold, so the inversion approach is unchanged); (4) `resolveReferencedSchema` is written for the imported direction.
    - **`appendErDiagramSection(StringBuilder, List<SchemaTable>)`** is shared between `index.md` and the table pages and keeps its **two-pass shape (all entities, then all relationships)**. Fusing the passes would silently reorder `index.md` output whenever a later table's entity is referenced by an earlier table's foreign key.
    - Cross-schema names are normalized once, at graph-build time, via `resolveReferencedSchema`. Output order is the canonical `orderedTables()` order (filtered), *not* BFS discovery order, so table pages and `index.md` share one ordering rule. `er-diagram: false` remains the master switch over all ER-diagram output.

    **Size guard — `er-diagram-per-table-max-entities` (Session 64)** — `erDiagramPerTableMaxEntities()` (`@WithDefault("60")`; YAML key `er-diagram-per-table-max-entities`) replaces the diagram with an omission message plus a link to the database-wide diagram (`../../index.md`, `../../../index.md` before Session 69) when a neighborhood exceeds the limit; `0` or lower means unlimited, and a neighborhood exactly at the limit still renders. This exists because the neighborhood is a *transitive closure*: a single hub table can pull in ~200 entities (~800KB per page), and **GitHub refuses to render Mermaid diagrams beyond roughly 50,000 characters** — so shipping `er-diagram-per-table` on by default would silently break existing users' diagrams. The traversal semantics are deliberately left unbounded; the cap is a **render-stage fallback only**. Note that entity count is only a *proxy* for character count (≈45 chars per entity + ≈40 chars per column), so 60 entities is safe at 8–10 columns (~22–28K chars) but can exceed 50K past ~20 columns — lower the value or combine with `er-diagram-keys-only: true`. All three plugins (`jdbc-markdown` / `postgresql-markdown` / `mysql-markdown`) wire all three settings end-to-end; the three generators were unified on an 8-arg telescoping constructor keeping the **single terminal constructor** shape (`DEFAULT_ER_DIAGRAM_PER_TABLE_MAX_ENTITIES` is `protected static final` so each subclass's 7-arg overload can delegate to the 8-arg one). Adding these three settings introduced **three new abstract methods on the public `JdbcMarkdownDefinition` interface** (hand-written implementors break at compile time; SmallRye proxy users are unaffected), and empty/non-numeric YAML values fail at config-load time with SmallRye `SRCFG00040`/`SRCFG00039` rather than falling back to the default — so an *empty* `er-diagram-layout:` is a configuration error, and suppressing the frontmatter requires a value outside the allowed character set (e.g. `" "`).

24. **Foreign-key aggregation key in `JdbcSchemaInfoProvider` (Session 65)**: `buildKeyInfo(ResultSet, boolean imported)` is a **single helper shared by both FK directions** — it is fed `DatabaseMetaData.getImportedKeys()` and `getExportedKeys()` alike, and aggregates the per-column rows of a multi-column foreign key into one `JdbcForeignKeyInfo`. That shared shape is the structural hazard: the aggregation key must be valid for the *widest* result set, not just the imported one. Keying on `FK_NAME` alone was only correct for `getImportedKeys()` (one child table per call); an `getExportedKeys()` result set interleaves rows from **many different child tables**, so two children sharing a constraint name merged into one builder — columns were appended twice and `referencedTable` was overwritten by the later row, making one child silently disappear. The map is therefore keyed on a `BuilderKey(fkTableSchem, fkTableName, fkName)` record, with `FKTABLE_SCHEM` / `FKTABLE_NAME` read **outside** the `imported` branch through `nullToEmpty(...)`. This is a **no-op for the imported direction**: per the JDBC contract those two columns are constant across all rows of a single-table call, and on schema-less databases a `NULL` `FKTABLE_SCHEM` normalizes uniformly to `""`, so the composite key still matches across rows. The blast radius is limited to consumers of `exportedKeys()` — i.e. the Markdown generators' `## Exported Keys` section; ER diagrams are unaffected because descendant traversal inverts imported FKs (decision 23). **Remaining gap**: when `FK_NAME` is `NULL` (normalized to `""`) and one child table carries several *unnamed* FK constraints, they still collapse into a single builder with mixed columns. This is a distinct axis from the cross-child merge fixed here and is hard to reproduce on H2, which auto-assigns unique names to unnamed constraints.

    **Same defect in `MySQLSchemaInfoProvider.buildKeyInfo` (Session 73)**: that method is a near-copy of this one and was missed by the Session 65 fix — it kept keying on a bare `FK_NAME`. It is now keyed on `BuilderKey(fkTableCat, fkTableName, fkName)`, using **`FKTABLE_CAT` rather than `FKTABLE_SCHEM`**: MySQL reports the database in the catalog column and leaves the schema `NULL`, so porting the JDBC field verbatim would give a key whose first component is always `""` — syntactically a composite key, semantically the old one. The reachability differs too: MySQL scopes FK-name uniqueness **per database**, so two children in the same database cannot collide at all; the collision requires them in *different* databases, which MySQL permits because cross-database foreign keys are legal. Conversely the unnamed-constraint gap above is **not** reachable on MySQL — InnoDB always auto-names foreign keys (`<table>_ibfk_N`), so `FK_NAME` is never `NULL`. The general lesson for this pair of near-copy providers: a fix to one is not portable to the other by copying, because the JDBC metadata columns that carry schema identity differ by dialect.

25. **History table portability to 5.5-generation MySQL/MariaDB (Session 67)**: `migraphe_history` could not be created on servers where InnoDB caps an index key prefix at **767 bytes** (MySQL/MariaDB 5.5, or any later server still on `innodb_file_format=Antelope` / `innodb_large_prefix=0`). Two independent violations existed in the MySQL DDL, both invisible on 5.7+/10.2+ where DYNAMIC row format raises the cap to 3072: `INDEX (node_id, environment_id)` needed 255×4×2 = **2040 bytes**, and `id VARCHAR(255) PRIMARY KEY` needed 255×4 = **1020 bytes** (the latter fails first and was missing from the original report). The fix keeps **utf8mb4 on every identifier column** and bounds only what is indexed: `id VARCHAR(64)` (values are always `UUID.randomUUID().toString()` = 36 chars, and no query filters on `id` — it is read back but never a lookup key), plus prefix lengths `INDEX (node_id(100), environment_id(60))` (640 bytes) and `INDEX (environment_id(60))` (240 bytes). Prefix indexes remain correct for the equality lookups the repository issues — MySQL narrows by the prefix and re-checks the full value on the row (verified: `type=ref, key_len=644`). **Narrowing the columns to `CHARACTER SET ascii` was rejected**: `node_id` is the task file path (`TaskIdGenerator`) and may be non-ASCII, and on such values a non-strict server accepts the INSERT with only `Warning 1366` (silently corrupting history) while a utf8mb4-connected client's `WHERE node_id = ?` fails outright with `ERROR 1267 Illegal mix of collations`. **No migration is shipped**: `initialize()` runs `CREATE TABLE IF NOT EXISTS`, so pre-existing tables are untouched and only new installs get the corrected shape; the wider legacy columns are harmless. The generic `migraphe-plugin-jdbc` DDL narrows `id` the same way for `type="jdbc"` against MySQL; the PostgreSQL DDL uses `TEXT` and needed no change.

    **Window functions removed from `executedNodes()` (Session 67)** — the query ranked rows with `ROW_NUMBER() OVER (PARTITION BY node_id ORDER BY executed_at DESC)`, which is MySQL 8.0+/MariaDB 10.2+ only and fails with `ERROR 1064` on the 5.5 generation. It now uses a correlated `MAX(executed_at)` subquery with `SELECT DISTINCT`, portable across H2/PostgreSQL/MySQL/MariaDB. **Tie semantics differ deliberately**: where several records for one node share the maximum `executed_at`, `ROW_NUMBER` picked one arbitrarily while the subquery counts the node as applied if *any* of the tied rows is a successful `UP`. `HistoryRepository.executedNodes` has no caller in core/CLI/Gradle (`up`/`down`/`status` all go through `wasExecuted`), so this only affects direct API consumers.

    **Discovered, not fixed — sub-second precision is lost on MariaDB**: Connector/J decides whether to send fractional seconds from the server version string, and MariaDB reports itself as `5.5.5-10.1.48-MariaDB`. The driver therefore concludes "older than MySQL 5.6.4" and truncates client-side, so `executed_at` holds **whole seconds** even though the column is `TIMESTAMP(6)` (probe: three inserts ~20ms apart all stored `micros=0`). Consequently `wasExecuted()`'s `ORDER BY executed_at DESC LIMIT 1` is already non-deterministic on MariaDB whenever an UP and a later DOWN of the same node land in the same second. A robust fix needs a monotonic tiebreaker (an insertion-order column), i.e. a schema change with a migration path — deliberately out of scope here. The affected test therefore writes explicit whole-second timestamps instead of relying on wall-clock ordering.

26. **Generator output layout — the generator name titles the docs but is not a path segment (Session 69)**: Markdown output plugins used to write detail pages under `<output-dir>/<generators[].name>/<schema>/` and to title `index.md` `# Database: <generators[].name>`, so a generator configured as `name: schema-docs` produced `docs/schema/schema-docs/readingfarm/tables/…` under the heading `# Database: schema-docs`. One YAML key was carrying three unrelated roles: the `--name` filter key, a directory namespace, and a database label. **The path role is removed** — pages now live at `<output-dir>/<schema>/` — while the *title* role is kept and made honest. The path level is redundant because `GeneratorSection.source()` is *singular*: one `SourceSection` whose `type`/`target` are single `Optional<String>` values, so **one generator always documents exactly one target** and the per-name namespace is structurally always a single element. Pointing two generators at one `output-dir` never aggregated either — each run writes `index.md` at the `output-dir` root, so the later run overwrites the earlier one; `output-dir` is the namespace. **The heading is `# <name>`, with no fixed prefix.** `source.target` was considered and rejected: a target id names a *connection* (this repository's own sample uses `target: mysql` / `target: pg`), so `# Database: mysql` is meaningless as a document title, whereas `name` is free-form text the user chooses. Correspondingly the `Database: ` prefix was dropped — once `name` is understood as an arbitrary label, hard-coding it as a database name is the actual error the original report noticed. Because `name` is a required field, no fallback is needed and **this change adds no public API**: `OutputContext` is untouched. `JdbcMarkdownGenerator`'s field/accessor were renamed `name`/`name()` → `title`/`title()`; both in-repo subclasses used `name()` *only* to build path prefixes, so leaving the old name would let an external subclass keep prefixing paths and silently emit a layout inconsistent with its base class — the rename converts that into a compile error. **Relative-link depth**: the FK cross-link `../../<schema>/tables/<t>.md` is unchanged because both endpoints moved up one level together, but the omitted-ER-diagram link to the index had to go from `../../../index.md` to `../../index.md`. An `everyRelativeLinkResolvesToAnExistingFile` test walks every generated Markdown file, resolves each relative link and asserts the target exists, so that whole class of depth bug now fails loudly instead of shipping broken links.

27. **History schema evolution — apply steps with optional detection (Session 70)**: `initialize()` used to read its dialect DDL resource as one string and hand the whole thing to a single `stmt.execute()`, so idempotency rested entirely on `CREATE TABLE IF NOT EXISTS` and **an existing table could never change shape** — editing a resource only affected fresh installs. Since the history table now has pending additions (a checksum column, an environment/profile identifier, a monotonic ordering column), the resource format became a list of **steps**: `--@apply` introduces the statements of a step and starts a new one, an optional `--@check` before it declares a detection query, and `SchemaStepParser` turns the file into `SchemaStep(label, checkSql, applySql)` records that `initialize()` walks in order, executing each statement separately. A step with a detection query returning **at least one row** is skipped; a step without one always runs.

    **Detection is optional on purpose, and the shipped resources do not use it.** Every step in them creates an object, and `IF NOT EXISTS` (available for `CREATE TABLE` on MySQL/MariaDB/H2/PostgreSQL 9.1+, and for `CREATE INDEX` on PostgreSQL 9.5+) already expresses that conditionally, in a form no *other* schema's identically-named table can confuse. A detection query for creation would have to answer "does this object exist **here**", and the generic JDBC resource cannot: it is shared by everything reached through `type="jdbc"`, and no single expression names the current schema across H2, MySQL and PostgreSQL (`SCHEMA()` covers H2 and MySQL but breaks PostgreSQL). An unqualified `UPPER(table_name)` match would then find a `migraphe_history` in *another* database on the same MySQL server, skip the creation step, and leave `INSERT` failing against a table that was never created. So the rule is: **creation leans on `IF NOT EXISTS`; detection is reserved for changes that have no portable conditional form** — chiefly `ALTER TABLE ... ADD COLUMN`, which MariaDB and PostgreSQL can write as `IF NOT EXISTS` but Oracle MySQL cannot. Schema-qualifying those future detection queries (binding `conn.getCatalog()`/`getSchema()` as parameters) is deferred to the PR that adds the first column.

    **Why no schema-version table**: idempotency is structural rather than bookkept — nothing can drift out of sync with a recorded version number, and a partially-applied run simply resumes where it stopped on the next command. A user who prefers to run DDL by hand applies it first, and either the `IF NOT EXISTS` or the detection query then makes the step a no-op, so no `auto-migrate` setting is needed; the escape hatch is the mechanism itself. No type-change step is shipped either: `id VARCHAR(255)` only hurts as an *index key*, and pre-existing tables were created where the limit is 3072 bytes.

    **Detection queries must be runnable before the table exists**, so they inspect `information_schema`/`pg_indexes` rather than `migraphe_history` itself, and **a failing detection query propagates** instead of being read as "not applied" — folding a permission error or a dropped connection into "missing" would turn it into a blind DDL attempt. **Concurrency** is handled by re-running the detection query after an apply failure: a competing process may have applied the same step in between, in which case the failure is benign and swallowed (the original exception is thrown with the re-check failure attached as suppressed if the re-check itself fails). For an unconditional step the re-check reports "not applied" and the failure propagates, which is right — `IF NOT EXISTS` has already absorbed the benign race. No dialect-specific advisory lock is involved.

    **All commands share one path** — `up`, `down` and `status` already called `initialize()` unconditionally, so `status` has always created the table; letting it also apply steps introduces no new class of behaviour and keeps `status` working once a step is needed by the *read* path (e.g. an ordering column referenced by `ORDER BY`). PostgreSQL keeps the table and each index as **separate steps**: every statement runs on its own (the old resource passed three of them to one `execute()`), diagnostics name the failing step, and a manually dropped index is recreated on the next run. A resource with **no** directive at all is parsed as one unconditional step, keeping plain-SQL resources passed to `JdbcHistoryRepository(env, path)` working; SQL appearing before the first directive is rejected rather than silently discarded.

    **Parsing is purely structural**: `--@check` always opens a step and `--@apply` fills the open apply slot or opens the next step, with labels playing no part in the grouping. Four invariants are then checked per step — it has an apply section, it declares no two *different* labels, a declared detection query is exactly one statement, and the apply section holds at least one. Label *uniqueness across* steps is deliberately not required, because a label reaches nothing but the `Failed to apply/detect schema step '<label>'` messages: it is never persisted, matched or compared, so enforcing uniqueness would only reject well-formed resources. The full rules live on `SchemaStepParser`'s class Javadoc, and "no label declared" is represented as `null` rather than an empty string — with the empty-string encoding, a bare `--@check` plus a bare `--@apply` reads as "both declared a label" and never falls through to the positional `step N` default.

28. **Time-ordered record ids and the `target_id` rename (Session 71)**: two changes to `migraphe_history`, shipped together because both rewrite the same queries and both want the migration to run once.

    **Ordering.** `wasExecuted()` and `findLatestRecord()` ordered by `executed_at DESC LIMIT 1`, which is undefined when several records for a node share a timestamp — and on MariaDB they routinely do, because Connector/J reads the server's `5.5.5-10.1.48-MariaDB` version string, concludes "older than MySQL 5.6.4" and drops fractional seconds client-side (measured: unavoidable through `sendFractionalSeconds` / `useServerPrepStmts`, in any combination; the MariaDB driver keeps them, so the cause is the driver alone). A `down` immediately followed by an `up` then lands on one second and the winner is whatever the storage engine returns — silently reporting a rolled-back node as applied. Rather than add a monotonic column, **`ExecutionRecord`'s factories now mint UUIDv7 (RFC 9562) instead of UUIDv4**, so the already-present `id` primary key sorts in creation order and breaks the tie: no schema change, no dialect-specific auto-increment syntax (which the generic JDBC resource could not have expressed portably), and the InnoDB primary key becomes append-ordered as a bonus. Ordering is `executed_at DESC, id DESC` — **`executed_at` stays the primary sort key** so rows written by older versions, whose ids are random UUIDv4 values, keep exactly their previous order; only same-timestamp ties consult the id. `executedNodes()` moves from matching `MAX(executed_at)` to a correlated scalar subquery using that same rule (`ORDER BY h2.executed_at DESC, h2.id DESC LIMIT 1`), which also drops the `DISTINCT` that the max-matching form needed. `RecordIds` is package-private in `migraphe-api`: `ExecutionRecord`'s canonical constructor still accepts any string, so the format is an implementation detail, not a published contract. Monotonicity within a millisecond comes from using the 12-bit `rand_a` field as a dedicated counter (RFC 9562 §6.2 method 1), borrowing from the next millisecond on overflow; a clock moving backwards keeps the previous timestamp and advances the counter instead, so ids stay strictly increasing for the process lifetime. The 62 random bits still carry cross-process uniqueness, which a counter cannot.

    **Rename.** The column called `environment_id` has only ever held a target id — the name a task's `target:` refers to, defined under `targets/` — never the `--env` overlay, which selects configuration values and never reaches the history table. It is now `target_id`, applied in place by a migration step. This is the first step to need detection (`ALTER TABLE` has no portable conditional form), which forced the question deferred in decision 26: **a detection query may now carry positional parameters, and every one of them is bound to the current schema**, read as `Connection.getSchema()` falling back to `getCatalog()`. That answers on H2 (`PUBLIC`), PostgreSQL (`public`) and MySQL/MariaDB (no schema, catalog = database name = what their `table_schema` holds), so the generic resource no longer risks matching a same-named table elsewhere on the server. The rename statement itself is per-dialect: MySQL uses `CHANGE COLUMN` (restating the type) because `RENAME COLUMN` needs MySQL 8.0 / MariaDB 10.5.2 and the resource still targets the 5.5 generation; PostgreSQL uses `RENAME COLUMN`; the generic resource spells it as add / backfill / drop, the only portable form. **Index names keep their `_env` suffix**: renaming an index needs another dialect-divergent step that 5.5-generation servers cannot express at all, and the names are internal — nothing reads them. The API-side rename followed later, in the drift-repair release: the types are now `Target`/`TargetId` (see "Drift and its repair" below — "Node identity, and the word 'target'"). Consequence for operators: **one history database must not be shared between migraphe versions across this change**, since older versions query `environment_id`.

29. **`--env` overlay reaches every command; a missing overlay is an error (Session 72)**: Migraphe has two orthogonal concepts that are both colloquially called "environments", and the overlap caused a user-visible surprise. A **target** (`targets/*.yaml`, modelled by the `Target`/`TargetId` API types — called `Environment`/`EnvironmentId` until the drift-repair release, which is exactly the confusion this decision describes) is a *connection*: tasks reference it by name and every history row records the one it was applied against. An **environment** (`environments/<name>.yaml`, selected by `--env`) is only a *config-source overlay* registered at ordinal 500 by `ConfigLoader.loadConfig`; it rewrites target **values** while the target **name** stays the same. Two gaps followed from the overlay being wired in only partially. (a) `GenerateCommand` called the `(baseDir, registry, variables)` overload of `ExecutionContext.load` — an easy mistake because that 3-arg overload's third parameter is `variables`, while the *other* 3-arg overload takes `envName` — so `generate` silently ignored `--env`; `ConfigValidator.validate(Path)` had no envName parameter at all, so `validate` did too; and the Gradle plugin had no notion of the overlay whatsoever. (b) `YamlFileScanner.findEnvironmentFile` returns `null` for a non-existent overlay and `ConfigLoader` treated that as "no overlay", so `--env prodction` ran against the base configuration and reported success. **Resolution**: `envName` is threaded to every configuration-reading entry point (`up`/`down`/`status`/`validate`/`generate`, CLI and Gradle alike), and `ConfigLoader.loadConfig` now throws `ConfigurationException` when an explicitly named overlay is absent, listing the path searched and the available overlay names (`YamlFileScanner.listEnvironmentNames`). `envName == null` (no `--env`) keeps the old no-overlay behavior, so only users who pass the flag are affected. **`validate` reports rather than throws**: it accumulates problems instead of aborting, so it catches the missing overlay as a validation error and additionally resolves each target's *effective* `type` through the overlay before checking it against the `PluginRegistry` — otherwise `validate` would pass on a configuration that `up` would reject. Its per-file error messages are keyed by path prefix for the grouped console output, so overlay errors are prefixed `environments/<name>.yaml:` and get their own check step. **Gradle precedence** is extension `env` (bound as a *convention*, not `set`, so an unset extension leaves room for the fallback) < `-Pmigraphe.env` < the `--env` task option, which Gradle applies after configuration; the `@Option` lives on `AbstractMigrapheTask` so all five tasks inherit it. **Deliberately not done**: a history row still records the target name and nothing about the overlay. Adding a `profile` column would break the legitimate case where two overlays point at the same physical database (they *should* share applied state), and the silent-skip hazard it would guard against only exists when `history.target` names a database shared across deployment environments — which is now documented as unsupported in `docs/USER_GUIDE.md`.

30. **UP content fingerprint — an opaque token whose only claim is "different means edited" (Session 74)**: `migraphe_history` now carries a `fingerprint` column, written on **UP success only**, so a later run can tell that a task's definition was edited after it was applied. The contract lives on `MigrationNode.fingerprint()` as a `default` method returning `null`, which keeps every existing plugin compiling and makes the feature opt-in per plugin. Four attempts at that javadoc were rejected in review for stating absolutes the implementation could not honor, and the surviving text deliberately says very little: the token is **opaque**, `null` means **unknown and never "unchanged"**, the derivation beyond the handed-in dependencies is the **plugin's** choice, it must be stable across JVMs, platforms and plugin versions, and **callers report, never auto-remediate**. Each rejected absolute failed the same way — it described `JdbcMigrationNode`'s particular hash rather than the interface. Notably "the token must change whenever the input does" is falsified by any digest of a *stripped* string (a trailing newline added to the SQL yields the same token), and "an autocommit flag only switches transaction semantics" is false because autocommit exists precisely *for* statements that cannot run in a transaction. `JdbcMigrationNode` implements it as the **SHA-256 of four things: `upSql.strip()`, `downSql.strip()`, the autocommit flag, and the transitive dependencies handed in** — no node id, no name, no target, no `no_way_back` reason, no line-ending normalization (SnakeYAML 2.4 already delivers `\n`, measured). Widening it past the UP SQL was decided in the same session and is decision 34; the framing that keeps it unambiguous is described there. Rather than parse the SQL to decide what is semantically significant, the file-backed builder methods (`upSqlFromFile`/`upSqlFromResource`/`downSqlFromFile`/`downSqlFromResource`) are `@Deprecated(forRemoval = true, since = "0.7.0")`: they were unused, and supporting them would have meant deciding whether an external file's line endings are part of the token. **Storage is `TEXT`, not a bounded width**, in all three dialect resources — the contract declares no token length, and a silently truncated token never again equals a freshly computed one, so an unchanged node would report as edited forever; the column is in no index, which is what a bounded width would otherwise buy. The column is added by a **detection-guarded step** (decision 27) since `ALTER TABLE ... ADD COLUMN` has no portable `IF NOT EXISTS`. `ExecutionRecord` gained an 11th component, a **breaking change to its canonical constructor** (10 → 11 arguments, ~10 call sites, accepted); `upSuccess` instead gained a 6-argument overload with the 5-argument form delegating with `null`, because that factory has ~50 call sites. How the read side reports the comparison — and why a boolean turned out to be the wrong shape for it — is decision 32. Consolidating `StatusCommand` onto `StatusService` was part of this work: the service had **zero production callers** before, so the CLI and the service were two independent implementations of the same status computation.

31. **`DagExecutor`'s completion accounting must survive an exception and count each node exactly once (Session 74)**: three defects of one shape were found and fixed, and a fourth of the same shape is knowingly still open. The mechanism behind all of them: the coordinator loop runs until a `CountDownLatch` sized to the node count reaches zero, each dispatched node counts itself down in its virtual thread's `finally`, and `propagateFailure` counts down for each node it marks skipped. Any deviation from **exactly one countdown per node** either hangs the run or ends it early. (a) `node.fingerprint()` was called from `recordSuccess` outside any guard; a plugin that throws there skipped `processCompletion`, so dependents never entered the ready queue and the coordinator polled an empty queue against a latch they still held counts on — `migraphe up` never returned. It now degrades to `null` (the contract's "unknown"), because the node's DDL has already committed and losing the record is the worse outcome: the next run would apply it again. The swallow is **silent** — `ExecutionListener` has no hook for a warning and adding one is a separate design change. (b) `history.record(...)` was likewise unguarded on both the success and failure paths, and `JdbcHistoryRepository.record` wraps every `SQLException` in a `JdbcException`, so a history database that drops its connection right after a node's DDL commits hung the run the same way — far more reachable than (a), which needs a JVM without SHA-256. The whole post-execute body is now wrapped; the `catch` reports the node failed and propagates the skip **without touching the repository again**, since writing a failure record would throw the identical exception. A node whose task applied but whose record was lost is treated as a *failure* so its branch stops: letting dependents run would record them on top of an unrecorded ancestor. (c) The coordinator checked `failedNodes` **before** `semaphore.acquire()`, so a node could pass the check, block on the permit, be reported "dependency failed" by another node's `propagateFailure` while it waited, and be dispatched anyway when the permit returned — **its migration ran after the console said it was skipped**. This needs no parallel configuration: at `maxParallelism=1` the permit wait *is* the window. It needs only a target set whose path to the dependent runs through an already-applied node, because `ReadyNodeTracker` counts in-degree over `targetNodes` while `propagateFailure`'s cone spans the whole graph — with `a→b→c` and `b` applied, `targetNodes` is `{a, c}` and `c` starts ready. A second check after the permit is acquired closes it; neither check counts the latch down, because `propagateFailure` already did. **(d) Fixed by an atomic claim (Session 75)**: a node marked skipped while it is *already running* was counted down twice — `propagateFailure` counted it and its own `finally` counted it again — so with `maxParallelism >= 2` (`execution.parallel: true`, whose default `maxParallelism` is `0` = unbounded, or `rebuild`, whose apply phase reads the project's setting) the latch reached zero while another node was mid-DDL, and `execute()` summarized and returned; virtual threads are daemon threads, so the CLI could exit during that statement. The same run also wrote a *success* history row for the node the console reported skipped. Another `failedNodes` check could not close it — the window between any check and `executeNode` is irreducible — so ownership is now explicit: a `claimed` set that the coordinator adds to **before** starting a node or skipping it, and that `propagateFailure` must win to report and count one. A cone member it cannot claim is already running, and is left to its own completion; the check after the permit became that same claim, so a node propagation reached first is no longer dispatched. The state is reachable with a plain project — `a→b→c` with `b` applied gives `targetNodes` `{a, c}`, and `c` starts ready alongside `a` because `ReadyNodeTracker` counts in-degree over the target set while the cone spans the graph — and `DagExecutorParallelUpTest` pins it by asserting `execute()` does not return while a selected node's task is still inside it. **(e) The same shape on the interrupt path, fixed with it**: `catch (InterruptedException)` summarized and returned without waiting, so a cancelled run — a build daemon shutting down mid-`up` — abandoned whatever was in flight, and the daemon threads could be killed inside a statement. Awaiting the latch is not available there: it still holds counts for every node that was never dispatched, so it would never return. The run now keeps the threads it started and joins them; nothing further is dispatched once the loop is left, so the wait is bounded by the work already begun. Interrupts arriving during that join are absorbed and re-raised on the way out — there is nothing left to interrupt, and the only thing still running is work that cannot be abandoned safely.

32. **Reporting drift in `status` — five states, because a null fingerprint means two different things (Session 74)**: the read side of decision 30 started as a boolean, `NodeStatus.upContentChanged()`, and that shape was wrong. A `null` on the **node** is the plugin declaring that it does not do fingerprints and is not to be judged by one (`noop`, or any plugin inheriting the interface default); a `null` on the **record** means the row predates the column and the question genuinely cannot be answered. The boolean folded both into "not changed" — correct for a caller asking "did it change?", and useless for a caller that has to display something. `NodeStatus.upContentState()` returns `UpContentState` instead: **`NOT_APPLICABLE`** (never applied, *or* the plugin opts out — `executed()` tells those apart), **`UNKNOWN`** (the plugin supplies a token, the row carries none), **`UNCHANGED`**, **`CHANGED`**, **`UNREADABLE`**. The boolean was deleted once the enum existed; it never appeared in a tagged release, and `validate`/`reconcile` will want to distinguish `CHANGED` from `UNKNOWN` rather than collapse them, so the boolean is the wrong shape for the work ahead too. **`UNREADABLE` is deliberately separate from `NOT_APPLICABLE`**: the interface default returns `null` and cannot throw, so a throw proves an override — a fault, not a declared opt-out — and the operator's next move differs (fix the plugin, versus baseline the history). **Evaluation order is load-bearing**: the `latestRecord == null` check comes first, so a broken plugin's *pending* node is never asked for a fingerprint it has no reason to supply. The two guards around `MigrationNode.fingerprint()` differ on purpose: the write path (`DagExecutor.fingerprintOf`, decision 31a) degrades a throw to `null` because the node's DDL has already committed and the record must still be written, while the read path reports `UNREADABLE` because a report can say "your plugin is broken" where a stored token cannot. **Markers**: `[ ]` not applied, `[✓]` unchanged or not applicable, `[!]` changed, `[?]` unknown, `[E]` unreadable. `[E]` is ASCII on purpose — `✓` is double-width in some terminals and already sets the alignment precedent, and `×` would read as "this migration failed", a meaning the marker column does not otherwise carry; all five markers are four characters including the trailing space. `StatusLineFormatter.markerFor` switches over the enum with **no `default` arm**, so adding a state stops compiling until the renderer decides how it looks rather than defaulting to "no change detected". **Rendering is shared**, in `migraphe-core`: the Gradle task was first consolidated onto `StatusService` (it had duplicated the whole computation, querying the repository from inside the rendering lambda and tallying into `int[]` holders), which made its lambda character-for-character identical to the CLI's and justified extracting `StatusLineFormatter`. That extraction also gave the Gradle side its first unit-level cover of the *executed* branch — `[✓]` plus the `(duration, timestamp)` suffix — which TestKit cannot reach at all, because the `noop` provider hands out a fresh `InMemoryHistoryRepository` per call, so `migrapheUp` and `migrapheStatus` in one build never share history. **Consequence for upgrades**: every row applied before the column existed reads `UNKNOWN`, so an existing installation's whole graph shows `[?]`. That is not transient. `up` filters out applied nodes (`!wasExecuted`), so it never backfills a fingerprint, and `down` selects the target *plus all its transitive dependents*, so a round-trip to refresh one early node tears down and rebuilds everything downstream of it against the live database. **Without a separate baseline operation the `[?]` is permanent** — which is why two commands are planned and why they are not the same thing: `baseline` records the current definition as applied *without executing anything* ("the database is right, fix the history"), and `reconcile` rolls back and re-applies what drifted ("the definition is right, fix the database").

33. **`amend` — retired; see "Drift and its repair" below (drift-repair release)**: this entry argued the shape `amend` had when it revised a row in place, and three of its load-bearing claims were later reversed rather than refined — that the write must be an `UPDATE` because "appending a row is structurally impossible"; that the scope is the drift set, with no migration argument and no `--all`; and that a `baseline <id>` command would be needed for a database that already has the schema. Amending appends, it takes a node argument, and the `baseline` case is one `amend <id>`. The capability interface the entry introduced, `HistoryFingerprintUpdater`, no longer exists. It carried a "superseded" note for a while, which is worse than nothing: the retracted text stays implementable by whoever reads the passage next. What survives is stated where it belongs — the command's whole design in "Drift and its repair" below, and the one durable side-decision, that there is deliberately **no `previous_fingerprint` column**, in [docs/USER_GUIDE.md](USER_GUIDE.md) beside the warning it explains.

34. **The fingerprint covers the whole recorded definition, and core computes the dependency closure (Session 75)**: decision 30 hashed the UP SQL alone. That is too narrow for the question the token is actually asked — *is this migration a candidate for rebuilding* — because a migration is also defined by what it stands on and by how it is undone. The token covers **`name:`, `no_way_back:`, `target:`, `up:` SQL, `down:` SQL, `autocommit`, and the transitive dependencies** — every attribute of the definition the history records. (Session 75 shipped the middle four; the first three followed in the drift-repair release. The full pre-image is stated in "Drift and its repair" below.)

    **Why `down:` is in it, having first been argued out of it.** The initial proposal kept `down:` out so that `[!]` could keep meaning "the database is stale": editing only a rollback changes no database object, so flagging it invites a destructive rebuild for nothing. That reasoning was withdrawn because it contradicts the root principle the repair commands are built on — migraphe **cannot read the database**, so it must never decide which side of a mismatch is authoritative; that is always the operator's call (decision 33). A token that tried to mean "the object is stale" would be making exactly that decision. Two further observations settled it: the *set* of nodes flagged is identical either way, since a stale `down:` has to be detected regardless, and the only thing a split would buy is the ability to say *which field* differs — information, not a decision. The cost is named rather than hidden: editing only `down:` moves the token, and a caller that resolves drift by re-applying will rebuild for it. `rebuild` is a development-time command, which is what makes that acceptable.

    **Core computes the closure; the plugin folds it in.** `MigrationNode.fingerprint()` became `fingerprint(List<NodeId> transitiveDependencies)`. A node knows only its *direct* dependencies, so it cannot compute a closure at all, and the three candidate shapes were: core composes the token itself (the plugin would then own only its own content), the plugin receives the graph and walks it, or the token covers direct dependencies only. The third fails the requirement outright — with `a → b → c`, cutting `a → b` must move `c` — and the second scatters the canonical ordering across every plugin, where one divergence makes every stored token read as changed. So core computes and normalizes, the plugin hashes what it is handed. `fingerprint()` did not exist in v0.6.0, so replacing the no-arg form breaks no published plugin.

    **The closure walks declarations, not the adjacency list.** `MigrationGraph.canonicalTransitiveDependencies` follows `MigrationNode.dependencies()` rather than the adjacency list, for the reason `unresolvedDependencies()` already states — *the node itself is the source of truth for what it declared*. `fromNodesUp`, which builds the one production graph, drops dependency ids naming nodes outside the supplied list; reading the closure from there meant that deleting `tasks/a.yaml` silently shrank every dependent's closure and reported nodes nobody had edited as `[!]`, in precisely the orphan scenario the fingerprint exists to handle. Ordering is ascending `NodeId.value()`: `getAllDependencies` returns a `HashSet`, and folding an unspecified iteration order into a persisted value would give one node different tokens on different runs. **One residual case is accepted and pinned by test**: when the deleted node had dependencies of its own, whatever stood behind it *and is not reachable along another declared path* drops out of the closure — a diamond survives untouched, a chain does not. Nothing in the graph recovers it; a `dependencies` column in the history could (an absent node's declarations would be readable from its recorded row), at the price of making the fingerprint a function of the history rather than the definitions alone. Deferred with `rebuild`, which needs that column anyway.

    **Framing.** The pre-image is built so no two distinct inputs produce the same string: each SQL text and each dependency id is written as `<length>:<text>`, the autocommit flag as a single `t`/`f`, and an **absent** `down:` as `-` — not a digit, so it can never begin a length prefix. Plain concatenation would let one dependency `ab` and two dependencies `a`, `b` hash alike; a separator character fails as soon as an id contains it. Absent and empty rollbacks are deliberately distinct: "the rollback was deleted" and "the rollback was blanked" are different edits. **Deliberately not added: a scheme prefix on the token.** It was proposed so that "computed by an older derivation" could be distinguished from "content changed", and rejected on the ground that the contract can only *ask* a plugin to carry one — the mechanism depends on the goodwill of the very party whose derivation change it guards against. The consequence is bounded and has a shipped remedy: a plugin that changes its derivation makes every existing row read `[!]`, and `amend` clears them.

    **Testing the wiring, not just the hash.** Every core call site (`DagExecutor`, `StatusService`, `AmendService`) passes `graph.canonicalTransitiveDependencies(node.id())`, and each has a test using a node double that *returns* what it is handed (`DependencyEchoingNode`) — because the pre-existing doubles return a fixed token and so pass whatever list the caller supplies, including the wrong one. Every such fixture puts a node **two hops** away, or substituting direct dependencies for the closure goes unnoticed. Every expected digest in the suite, including the two the CLI reads back out of a real PostgreSQL, was computed from the framing in a separate script *before* the implementation was changed, rather than pasted from a run.

35. **`latestApplies()` is a `default` on `HistoryRepository` that every wrapper must forward, and it refuses a malformed history (drift-repair release)**: the design asks several callers for *the row that applied a migration* rather than the newest row of any kind — the rollback payload, the graph the history describes, and the guard that keeps a fill from re-selecting what it just wrote — and the only read that existed, `findLatestRecord`, answers a different question. Three callers (`StatusService`, `RecordedGraph`, `DownService`) had each spelled the rule out for themselves, which is the divergence the tidy notes warn about. `latestApplies()` returns **one row per identifier**: the newest *successful* row carrying it, kept when that row is an apply. Failures change nothing, as they change nothing for `wasExecuted`. Two earlier shapes were wrong for the same underlying reason — that an identifier is unique across the project, so the set of what is applied is keyed by it and nothing else. Filtering to `UP` before folding let a placement a later `down` removed keep reporting itself. Keying by `(node, target)` then made an ordinary sequence — `down`, re-point `target:`, `up` — read as one migration standing in two places, because the rollback supersedes nothing outside its own pair. **The refusal for one identifier applied twice moved with it.** It was in `RecordedGraph`, comparing the rows of a set keyed by the pair; it is now in the fold itself, which walks each identifier's successful rows in order and refuses a second `EXECUTED` apply landing while the migration already stands. **The discriminator is `origin`, not the target** — `up` skips an identifier it finds applied, so no run writes a second execution *now*, while an `AMENDED` row is a claim and supersedes what stands, which is how `amend` re-homes a migration whose `target:` moved. A history from before the reads were keyed by the identifier can hold the refused shape, because `up` then asked per target and re-pointing a `target:` applied the same migration again; those are refused rather than answered, since the objects really do stand in two databases and that is the anomaly, not the reading of it. A rollback clears the placement whatever target its own row names, since the question is the sequence for one id. This is **the only branch on `origin` in the tree** — the design said nothing branched on it at all, which was a defect in the design rather than in the code, and that passage now describes this one reader. Reading the row that *applied* a migration is still the point: a fingerprint and a rollback payload exist only on an apply.

    **Why a `default` here when decision 33 rejected one.** What decision 33 rejected was a `default` that *throws*: a mutation capability every third-party repository would be handed and could not honour, silently inherited as a throwing stub by `SynchronizedHistoryRepository`, which overrides every method explicitly. This one is a pure derivation over `allRecords()`, so no implementation is obliged to write anything and none can fail to honour it; "does not implement it" is not a state that needs expressing in the type system. The half of that objection which **does** carry over is the wrapper: `SynchronizedHistoryRepository` forwards `latestApplies()` explicitly, because a repository that later overrides it to ask its store directly would otherwise be bypassed for every caller that goes through the wrapper — and `DagExecutor` wraps unconditionally. That forwarding is pinned by a test whose delegate answers with a sentinel; inheriting the default returns the delegate's *rows* but never the delegate's *answer*, so nothing else would catch it.

    **Now the only copy.** `DownService`, `StatusService` and `RecordedGraph` all read it and their own folds are gone. Switching the first two is what closed the split where one `down` answered "which row applied it" twice over — the guard from the repository, the rollback payload from `StatusService`'s fold — so an implementation whose answer differs from its own rows can no longer be honoured in one half of a command and ignored in the other. What stays local is the newest row of *any* kind (`StatusService.latestRecord`): no read answers that, and it is a different question, since a failed rollback is the newest thing a node has and applied nothing.

36. **`status` stays built from D, and shows irregularities where they fall (drift-repair release)**: the
    implementation plan had `status` re-based on the difference of the two graphs, so that a row whose
    `target_id` the project no longer configures would surface through the H side. Doing that
    faithfully means handing the configured targets to `StatusService`, and through it to
    `AmendService`, which is roughly thirty-six construction sites for a fact only the report reads —
    and a difference-driven `status` reads a still-applied migration whose recorded target is gone as
    "declared, not applied", which is the silence the design forbids. **The owner's call is that
    `status` is not bound by the drift design here**: it has to be displayable and it has to make an
    irregularity visible, and building it from D with orphans marked is an acceptable shape. So the
    plumbing is not done, `status` keeps its D-side walk and its orphan block, and the H-side graph
    stays what `down` and `rebuild` plan over. A row whose target no longer resolves is still visible
    as an orphan when its id is undeclared; when the id is still declared, the node reports normally
    and only a rollback discovers the missing connection.

## Drift and its repair

The design behind `status`'s markers, the fingerprint, `amend`, `rebuild`, orphan rollback and the
history's `dependencies` / `plugin_metadata` columns. **What an operator runs** is in
[USER_GUIDE.md](USER_GUIDE.md); **what a plugin author implements** is in
[PLUGIN_DEVELOPMENT.md](PLUGIN_DEVELOPMENT.md). This is the reasoning under both.

### The model

Three stores. migraphe can read two of them.

| | what it is | readable |
|---|---|---|
| **D** | the definitions — `tasks/**/*.yaml` | yes |
| **H** | the history table | yes |
| **W** | the database itself — the objects a migration created | **no** |

**Root principle.** Every mismatch has exactly two remedies: move W to match D, or move H to match D.
Which one is right depends on W, which migraphe cannot read. So the choice is the operator's and
**the tool never guesses**. Everything else follows.

Two corollaries that are easy to lose:

- A marker or a token meaning *"the database is stale"* would be making the forbidden judgement.
  Markers report a **disagreement**, never a **remedy**.
- Where the tool cannot tell two states apart, it **stops and says so**. "Not known to differ" is not
  "known to agree".

migraphe never inspects the database and never parses SQL to judge what a statement means. Drift is
*definitions vs record*, never *definitions vs database*.

The operation space is exhaustive: each writable store can be pushed towards D or away from it.
`status` observes the difference between D and H, `up`/`down`/`rebuild` write W, and `amend` writes H.

`rebuild` takes no argument: `rebuild <id>` would be `down <id>` + `up`, which already exists. Naming
one node is a way to leave the job half done, so it is **rejected with an error**, not ignored.
`status --check` is `status`'s flag alone; it never reaches a command that writes.

### `amend` — H ← D

No direction flag is needed, because the target state is derived from D.

**One operation: append a row saying what D says.** Not an update — a node accumulates rows as it is
applied, rolled back, retried and re-applied, and there is no principled answer to "which of them do I
edit". Not a deletion either: the history is a record of what happened, and `amend` did not un-happen
anything. Appending keeps *when the migration was really applied* readable next to the claim that
replaced it.

| the node is in D | H holds rows for it | what is appended |
|---|---|---|
| yes | yes or no | **UP + SUCCESS** — applied, against `target:`, content = the current definition |
| no (an orphan) | yes | **DOWN + SUCCESS** — no longer applied |
| no | no | **error: no such migration** |

This works because applied-ness is read from the **latest successful row**: append an UP and the node
reads as applied, append a DOWN and it reads as not. Nothing has to be deleted for the claim to take
effect, and a node D describes that H has never recorded is the first row with no special case.

**A row records whether it was executed or claimed.** Writing an UP row for a migration that never ran
is exactly what `amend` is for, but the history is an audit log and must not present a claim as an
execution: an operator asking "did migraphe ever run this DDL" has to be able to answer it. So the row
carries its **origin** — `EXECUTED` or `AMENDED`.

Origin is a **separate axis from direction**, not a value of it. An amended row still says the node is
now up or now down, so folding the two together would need `AMEND_UP` and `AMEND_DOWN` and every one
of the ~60 places that branch on direction would have to spell out both — and a place that forgot
would read the node as never applied and re-run its DDL.

**One thing branches on origin, and only one.** `wasExecuted` reads direction, the H-side graph reads
the payload; neither cares how the row came about. What does care is the integrity check performed
while folding the history: a second `EXECUTED` apply of a migration that already stands is a row no
run writes — `up` skips an identifier it finds applied — so it is refused, while an `AMENDED` one is
this command stating what the definition says now and supersedes what stands, including a definition
that has moved to another target. That is what tells a re-homing apart from a damaged history.

**Every column D determines is written**, because the row is built rather than patched: `target_id`,
`direction`, `status`, `description`, `no_way_back`, `fingerprint`, `dependencies`,
`serialized_down_task`, `plugin_metadata`, plus `origin` = `AMENDED`. Not written: `duration_ms` and
`error_message`, facts about an execution that did not happen. `executed_at` is when the `amend` ran.

`target_id` is the one a patching form would have missed most dangerously: the token covers `target:`,
so updating the token while leaving the old target behind would make H claim "applied against db1,
with the content of a definition that names db2" — a state that never existed.

**Everything reads the latest applied row, never any row.** That is what makes appending safe: an
older row carrying no fingerprint does not make the node read as incomplete once a newer one carries
it.

`amend` executes nothing, so core obtains D's rollback payload from the up task through
`RollbackPayloadProvider` — the one value a real apply gets from the plugin's `TaskResult` and this has
to get without running anything. **A plugin that does not implement it cannot be amended**: the row is
built from scratch, so there is no earlier payload to carry over, and core must not invent one because
the format is the plugin's.

**A node argument is required**, because a claim that overwrites what H said before is one the
operator has to make deliberately, node by node — and it is the only form. There was a bulk form
selecting rows whose fingerprint was absent; that is what a history an older release wrote looks
like, and completing it carries no decision about the migrations, so it moved to `upgrade-history`. Amend
states, before writing and unconditionally, that what H reports about the named migration becomes what
the definition says now. The notice is deliberately not per-row — a row without one would read as
untouched, which is false for every row in the plan.

### `rebuild` — H and W ← D

A development-time command, and a short one: **compare the two trees, hand whatever H holds that D
does not to `down`, then `up`.** It does not need to be precious about rebuilding one node more than
strictly necessary.

"What H holds that D does not" is read off the difference: a node D no longer declares, and a node
whose recorded content is not what D says now. Both come down.

**It does not compute a cascade of its own.** Handing those nodes to `down` is the whole of the
rollback phase, and `down` already knows that taking a node out means taking out what was built on it,
in the order the recorded edges give. Then `up`, with the project's own execution settings — "apply
everything" is the same apply `up` performs, not a private one. **`rebuild` decides nothing about how
either phase runs.**

Orphans are part of "what H holds that D does not"; the history-only side of the difference *is* the
orphan set. What `rebuild` rolls back normally comes straight back up, but an orphan does not, because
D no longer describes it. **So `rebuild` is partly a permanent removal** — the wanted behavior for a
development-time command, and worth knowing before running it, which is why the confirmation names
those migrations separately.

It stops before touching anything when a migration that has to come down declares `no_way_back:`, when
one that has to come back up is blocked, or when the history cannot say whether a node matches. The
blockers are checked before the destructive phase and before `--preview` returns: a preview that exits
zero on a plan the real run would fail is not a rehearsal.

### `down` — take out of W

`down` is a **pure function of H**: which nodes, the cascade, the SQL, and the target to connect to all
come from the history. D is not consulted.

| | where it comes from |
|---|---|
| which nodes | the applied rows |
| the order | the recorded edges → the H-side graph |
| the SQL | `serialized_down_task` on the **applied** row |
| where to connect | the row's own `target_id` → that target's configuration |

The connection comes from the row, never from the definition — for a declared node too, not only for
an orphan. The objects to remove are where they were put, which is what the row records; what D says
today is where the migration *would* go if applied now. The same is true of the order: a task whose
`dependencies:` was edited without being re-applied stood on what the row says it stood on.

Because of that, an **orphan is not a special case**. It is a node in H like any other. There is no
separate planning path, no synthesized-only-for-orphans node, and no one-at-a-time limit.

The rollback SQL is read from the **applied** row, never the latest row of any kind. A failed rollback
is the newest row a node has and carries no payload, so reading the latest one would hide the payload
forever after a single failure.

**When H holds no payload, the run stops** — and says which of two things it is, because the answers
differ. A row carrying a fingerprint can be read at face value: a `no_way_back:` reason in it means the
author declared this one-way, so the reason is quoted and no repair exists, and its absence means the
node really has neither, which cannot happen through the definitions and points at a hand-edited row. A
row carrying no fingerprint cannot say anything, and is one `amend` away.

Substituting the current definition's `down:` is never the answer. That would be running D where H was
asked for, the forbidden judgement: the recorded SQL is what matches the objects that exist.

### The two graphs

Both sides are a `MigrationGraph`. `addNode` records a node's dependencies as declared and does not
check the targets exist, so a graph assembled from history rows is an ordinary graph and every
traversal already written applies unchanged.

**Each side is built from its own store, with no fallback to the other.** The D side reads the task
files and stops where they stop; the H side reads the rows and stops where they stop. Letting one
borrow from the other is tempting wherever a side is missing something, and it destroys the
comparison: a D-side value completed from H is partly H, so comparing them compares H with itself.
`amend` writes H from D, so the borrowing also closes a loop — a token would come to depend on the
token written last time. Where a side cannot see something, that is a limit, not a gap to fill.

**The H side** is built from every row the history holds as applied — the latest UP+SUCCESS row per
node, not the latest row of any kind — and each node is placed in the target *its own row* names.
Nothing is enumerated to find them: asking the history for what it holds, rather than asking it about
targets already known from the definitions, is what makes an orphan visible at all.

**A row whose `target_id` no longer resolves is reported, not skipped.** Its migration cannot be
rolled back — there is no connection to reconstruct — but the objects are real and the history says
so, and silence there is the one thing the root principle forbids.

**Two states are refused rather than answered:** one `NodeId` applied against two targets (the graph is
keyed by id alone and cannot hold both, and which one the caller meant is not decidable), and recorded
edges that form a cycle (the reduction below is only defined on a DAG: two parents on a common cycle
each justify dropping the other, so a node above the cycle silently loses every edge and reads as a
root).

### The difference

A full outer join of the two graphs:

| | |
|---|---|
| **only in H** | applied, no longer declared — an orphan |
| **only in D** | declared, not applied |
| **content differs** | both sides hold it, and the recorded token is not the one the current definition folds |
| **cannot compare** | both sides hold it, the recorded token is absent, and the definition can still supply one |
| **cannot read** | both sides hold it, and the definition cannot supply a token — it threw, or it answered with none |

**Cannot-compare is not a difference.** A row predating a column reads that way, and treating it as
changed would make the first `rebuild` after an upgrade tear down and re-create the database. It is not
discarded either — it is what stops the command, one `upgrade-history` away from being resolved.

**Cannot-read is not the same state**, and is the one `status` marks `[E]`. A definition that cannot
supply a token is no more evidence of agreement, so it stops the same commands — but nothing the
operator runs turns it into an answer, so it is a fault to fix in the plugin rather than an upgrade to
finish. **Both ways of not supplying one land here**, because the operator's move is the same: an
accessor that throws, and one that returns null. Supplying a token is part of the plugin contract, and
the one null the design keeps is core's own adapter over a history row — so a *declared* node answering
null is out of contract, and reading it as "nothing to compare" would put it back among the states a
repair reaches.

A migration whose `target:` moved is **content that differs**, like any other edit — the token covers
the target, so nothing special detects it and nothing special repairs it. That is why each side must
resolve an id against **its own** graph: a consumer that took an H-side id and looked it up in the
D-side graph would plan the rollback against the target the migration was never applied to.

### The transitive reduction

Applied **when comparing, to both graphs — never when writing**. A redundant declared edge (`a→c` where
`a→b→c` exists) changes the edge set without changing the closure, so an edge comparison and the
fingerprint would otherwise disagree about whether anything happened. For a DAG the reduction is
unique, so reducing both sides removes the disagreement. Storage keeps what was declared.

### The fingerprint

**Every attribute of the definition that H records is covered by the token.** That is the rule, and it
is not a list to be curated: an attribute H holds but the token does not cover is one where `status`
reports `[✓]` while H says something D does not — the exact state the whole family exists to make
impossible. So the token covers `up:` SQL, `down:` SQL, `no_way_back:`, `autocommit`, `target:`,
`name:` and the dependencies.

**Covered is not the same as copied**, and the dependencies are where the two come apart: H stores what
the node **declared**, and the token folds the **transitive closure** of it.

It answers one question: *is this node a candidate for rebuilding?* — where "candidate" means H and D
disagree about something H records, not that the objects are known to be stale. migraphe cannot know
that, and a token that claimed to would be making the judgement the root principle forbids.

Two consequences, both accepted rather than carved out: editing only `down:`, `no_way_back:` or `name:`
moves the token although no database object differs, and renaming a task therefore shows as `[!]`. The
repair is `amend`, which is cheap; a carve-out would need a reason that stays true as those fields grow
uses.

Transitive, not direct: in `a → b → c`, cutting `a → b` must move `c`'s token, because everything past
the cut was built on a context that no longer exists.

**Core owns the whole fold; the plugin only supplies text.** Each `Task` returns a `signature()` for its
own direction, a `MigrationNode` composes its tasks' signatures, and core frames them, appends the
closure and hashes. A plugin never sees SHA-256, length-prefixing or the closure — one framing, one
ordering, in one place. **The plugin's half is the definition it owns** — the `up:` and `down:` bodies;
`name:`, `target:` and `no_way_back:` are on the `MigrationNode` interface and core writes them to their
columns, so core is what folds them. Core reaches them through the graph, from the node id it was
already given, so there is no argument a caller can get wrong.

The closure walks **declarations**, not the adjacency list, which drops ids outside the supplied node
list — reading it there makes deleting one task file shrink every dependent's closure.

Framing, in order: the three core reads off the node, then `<len>:<text>` per signature, a separator
byte, then `<len>:<id>` per closure member. The separator is load-bearing — without it a node with
`up: a` / `down: b` and no dependencies frames identically to `up: a`, no rollback, `dependencies: [b]`.
Arity carries whether a rollback exists. The three come **first**, at a fixed count, so they are read
before anything of variable length; `no_way_back:` is not always present, and a fixed position cannot
use arity to say so, so **absent is the single byte `-`** — a length always begins with a digit, so the
two cannot collide.

**Supplying a fingerprint is part of the plugin contract**, and not compiling is only the first
enforcement: `up` asks every node it is about to apply for its token **before it applies anything**, and
refuses the run naming the nodes whose plugin could not answer. Asking afterwards is what the recording
path does, and there the answer cannot be refused — the migration has already run, so a broken accessor
must not cost the success record. Checking first keeps that fallback from being the normal path, and
keeps a broken plugin from writing the unreadable rows every command then stops on.

### The `dependencies` column

The node's **declared direct dependencies**, as of when it was applied. Newline-separated TEXT in its
own column — node ids are path-derived and cannot contain a newline, so core needs no parser.

A closure would defeat the column's purpose: it cannot be inverted back into a DAG, so it cannot
reconstruct the graph the column exists to reconstruct, and its width grows with the project rather
than with a node's fan-in.

| | job | needs |
|---|---|---|
| fingerprint | *detection* — is this a candidate for rebuilding? | the **closure** |
| `dependencies` | *reconstruction* — order a rollback through nodes D no longer contains | the **edges** |

Empty means the node declared no dependencies — a root. There is no separate encoding for "this row
cannot say": that is what an absent fingerprint means, for every column at once.

### The rollback payload

`serialized_down_task` stays **plain SQL**, so an operator debugging a stuck migration can copy it
straight out of the column. `plugin_metadata` is TEXT, encoded and decoded by the plugin; core stores
and returns it without looking inside. It is named for its owner, not its first occupant — restoring a
`JdbcDownTask` needs `autocommit`, but nothing about the column is rollback-specific.

Turning the payload back into a `Task` is a **`Target` capability**, detected by `instanceof`. `Target`
holds the connection details and is keyed by the record's target id — the only handle an orphan leaves
behind. It returns a `Task` so the rollback travels the existing executor, listener and history-writer
path. A plugin that does not implement it cannot roll back anything, and that is visible in the type
system rather than at runtime. See [PLUGIN_DEVELOPMENT.md](PLUGIN_DEVELOPMENT.md) for both capabilities.

### A migration that cannot be rolled back says so

**A missing `down:` answers two different questions at once**: the author declared this migration
one-way, or the author forgot to write the rollback. Those want opposite responses — one is a decision
to respect, the other a gap to close — and nothing could tell them apart, so every missing rollback
froze whatever stood on it with no way to say that was deliberate.

So a task declares either a `down:` or a `no_way_back:` reason, and declaring neither is an error:
`validate` reports it and `up` refuses. `no_way_back:` carries the author's **reason**, not a boolean —
it is quoted back when a rollback has to leave the node standing.

A node with `no_way_back:` is **frozen**: it cannot come down, so nothing standing on it can either, and
`rebuild` stops rather than tearing down what it cannot put back.

**H records the reason, in its own column.** `no_way_back:` belongs to every plugin — it is on the node,
not in the plugin's payload — so core owns it and can read it back. Without it, an absent rollback in H
would answer two questions at once, which is the same ambiguity `no_way_back:` was introduced to remove
on the D side; and for an **orphan** there is nowhere else to look.

**The refusal is the whole run, not the named subgraph.** An `up <id>` that avoids the offending task
still leaves the project in a state where `rebuild` cannot work, and a partial answer would hide that.

### `autocommit` is direction-aware

`autocommit: {up: bool, down: bool} | bool`, a bare boolean meaning both. The specific key wins over the
bare one. The rollback of an `up:` that needed autocommit does not necessarily need it.

Both flags reach the fingerprint through their own direction's task signature — so the DOWN flag is
covered only when there is a rollback. Toggling `autocommit: {down: …}` on a task with no `down:` does
not move the token, and nothing it could have changed is executed or recorded either.

### Node identity, and the word "target"

The path-derived id, and **the id alone**. It is unique across the project, so it already determines
which target the node names — asking the history "is this id applied *against this target*" is
therefore redundant, and worse than redundant once a `target:` can move: the query answers "not
applied" for a node that is applied under the old target, and `up` applies it a second time somewhere
else. So `target_id` is **recorded data about a node, like its fingerprint** — where it was applied —
and never part of the key. There is one history table per project, fixed by `history.target`, so a
target-id argument on a read was never selecting a store, only filtering a column the id already
determines.

**A target is a connection — `targets/*.yaml` — and the API types say so.** They used to be called
`Environment` and `EnvironmentId`, from before *environment* came to mean the `--env` overlay, and a
name known to be wrong at the place that has already produced a user-visible bug is not one to keep.
*Environment* is then left meaning exactly one thing: the config-source overlay `environments/*.yaml`
selects. And **"target" does not mean "the node this run is aimed at"** — that was a third meaning,
now `selectedNodes` and `requestedNode`.

### An incomplete history stops every command but `status`

**The fingerprint is the one marker of an incomplete row.** A row that carries one was written by a
version that writes every attribute D determines, so the rest of it can be read at face value. A row
that carries none predates them all, and nothing in it about the definition can be trusted.

One marker, not one per column. The alternative — asking each column whether it is absent — makes every
column carry two meanings, forces `''` and `NULL` to stay distinguishable through drivers and dialects
that disagree about whether they are, and has to be re-argued each time a column is added. **This is an
invariant to keep, not an observation**: any column added later must be written whenever the fingerprint
is, or the marker stops meaning what it says.

`status` is the one exemption, because a project in that state must still be able to report itself —
reporting is what tells the operator a repair is needed. `amend` is not an exemption but the repair
itself: it writes a complete row from D rather than reading an incomplete one. `validate` and `generate`
read no history at all.

`up` is stopped too, although it reads no attribute. Asking only `wasExecuted` means it has nothing to
*guess*, but it still builds on rows it cannot read, and applying a migration on top of a foundation
whose recorded content cannot be trusted is the same act as rebuilding around one. **The refusal is the
whole run**, and it counts every applied row, an orphan's included.

**`up` stops on a row that reads perfectly well and says something else, too.** A migration edited
after it ran means the database is known not to match the definitions, which is a stronger reason to
stop than one that merely cannot be read — the gate would be backwards if the certain case passed
while the uncertain one refused. No remedy is the command's to choose, so it names them in the order an
operator reaches for them: `down <id>` takes the migration out so the next apply puts it back as it
now reads, `amend <id>` records that what is applied is what was meant, and `rebuild` does the first
for every difference at once — the heavier move, because it takes orphans out permanently along the
way. `rebuild` itself is exempt, because refusing it would stop the command by the state it
exists to remove; `down` and `status` never consult the definitions' side of the comparison at all.

An old row is not permanently degraded. It is one `upgrade-history` away for every row whose node
D still declares, and `amend <id>` for one it does not, which withdraws it. Every command that observes
a null names the repair in the same words, whether or not it stops.

### What `status` reports

Five states, because a boolean folded distinct answers into one: `[ ]` never applied, `[✓]` agrees,
`[!]` the recorded token is not the one the definition folds, `[?]` the row records no token, `[E]` the
plugin could not report what the node applied. Only `UNCHANGED` counts as agreement for `--check`: an
unevaluable comparison is not evidence of a match. Orphans get a footer block, never a marker — they are
not in the graph, which is what makes them orphans. The rendering is shared by both front ends, so they
cannot drift apart.

### Where the history lives, and what happens when it does not

`history.target` names a configured target. If it names none, **every command refuses to start.** There
is no fallback, because the fallback was worse than the failure: a typo used to resolve to an in-memory
repository, so `up` applied every migration to the real database and threw the record away at exit. The
next run re-applied everything and failed on "table already exists", and `validate` passed throughout.

### What the project refuses to load

A **cycle** is fatal: no order satisfies it, so nothing can be built from it.

An **unresolved dependency** is only incompleteness, and load succeeds. Deleting a task file that
something depends on used to take the whole tool down — `status`, `down --all` and everything else
failed with a stack trace, so the project could not even be inspected, and rolling back (the one thing
that would have cleaned up the objects left behind) was impossible. `validate` still reports both,
offline; `up` refuses, because applying a node whose declared ground is not described is the operation
that actually needs a complete graph.

### `down --all` with something frozen

`--all` means the whole of W, and part of it is not the whole. A frozen node makes the request
unsatisfiable, so `--all` **refuses before rolling anything back** and names what stopped it. Rolling
back everything else first would leave a shape nobody asked for, out of an operation whose entire
meaning is "all". Rolling back a *named* node is different: that request is satisfiable or it is not.

### `migraphe init`, and why reporting does not create

Every command used to call `initialize()`, so `status --check` in CI wrote DDL to a database while
reporting that nothing had been applied. A command whose whole job is to report should not change
anything, which is the same argument that moved the *alterations* behind `upgrade-history` — applied
to creation.

So creation is a command. `init` creates the history if it is absent and says so if it is not; it
never alters one an older release left, which stays `upgrade-history`'s.

**`up` is the exception and creates one when it finds none.** Applying the first migration is the
moment a project's history should come into being, and requiring a ceremony before a fresh project's
first `up` buys nothing — `up` is already writing to the database, so creating its own bookkeeping
there is not a surprise. Every other command refuses.

**The refusal names `init` and never `up`.** Answering "I cannot report" with "then apply your
migrations" is not a remedy; it is a much larger action offered as a shortcut, and a message that
offers it will eventually be followed.

**The order of the two checks is load-bearing.** `HistoryReadiness` asks "does it exist" before "does
it need upgrading", because a history that does not exist reports *every* upgrade as pending — each
detection query finds nothing, which is indistinguishable from "the change has not been made". Asked
the other way round, an operator with a fresh database would be told to run `upgrade-history`, which
would then try to alter a table that is not there. `upgrade-history` carries the same guard itself
for the same reason.

**`isInitialized()` has no default**, by the rule that decides every default in this SPI: is
"nothing" — here, "yes" — a correct answer for a backend that has not been asked. It is not. A
backend that forgot to override would report itself ready and fail on its first read, with an error
naming a missing table rather than a missing step. An in-memory backend answers `true` deliberately,
because its store cannot be absent.

### `migraphe upgrade-history`

**The problem it replaced.** `amend --incomplete` selected rows with no fingerprint, which is *this* upgrade's
shape. A later release that adds a column leaves rows that have a fingerprint and lack the new
thing, so the flag would not select them and a second flag or command would be needed. Worse, the
repair borrows `amend`'s meaning: `amend` is "record what D says now, because I decide that is
right", and it therefore writes **every** column D determines — which is why the guide has to warn
that running it after deleting a `down:` overwrites the recorded rollback with nothing. An upgrade
carries no such decision. It completes rows that an older version wrote incompletely.

**The command.** `migraphe upgrade-history` brings the history to the shape this version writes: schema
first, then the rows. Release notes and refusals name it and never change, whatever a given version
has to do.

**Initialize creates, upgrade alters.** `initialize()` creates a table with every column this
version writes and never alters an existing one; it stays automatic, so a fresh project needs
nothing. Every evolution lives behind `upgrade-history`, and every other command asks whether one is pending
and refuses, naming it — `status` included, because this is about columns that are not there to
select rather than rows whose content cannot be read. Before the split, all ten command paths called
`initialize()` and the generic resource's rename step **dropped `environment_id`**, so running
`migraphe status` against a shared history silently removed a column a deployment still on the older
version was reading. Two SQL resources per dialect now: `init_history_table.sql` creates, and
`upgrade_history_table.sql` holds the guarded steps.

The guard costs something worth stating: a current history is asked once per declared upgrade on
every command, so six `information_schema` queries. It short-circuits on the first *pending* one, not
on the last applied one.

**Upgrades are ordered, registered, and get the definitions.**

```java
public interface HistoryUpgrade {
    String description();                  // for the report
    boolean isPending();                   // does this history still need it
    void apply(UpgradeContext context);    // schema, rows, or both
}

public interface HistoryRepository {
    void initialize();                           // create current-shaped; never alter
    default List<HistoryUpgrade> upgrades() {    // ordered
        return List.of();
    }
}
```

Handing each upgrade the definitions is what lets one unit do both halves: 0.7.0's upgrade adds the
columns *and* fills them from the task files, rather than being a schema step plus a separate row
repair. `List`, because the order is load-bearing.

**Core lends the folding; the graph does not carry it.** `UpgradeContext` gives the upgrade the
definitions *and* `fingerprinterFor(NodeId)`, because a filled token has to be folded the way core
folds one or every filled row reads as edited on the next run. It is not on `MigrationGraphView`, and
the reason is the same rule that decides where a default belongs: "nothing" is a correct answer for a
backend with no older shapes, so `upgrades()` has a default — but a view that cannot fold a
fingerprint is not a view with nothing to fold, it is a broken one, and most implementors (the
generators, the layout, a test double) have nothing honest to return. One object rather than two
arguments, matching `SourceContext`/`OutputContext`, so a later release's upgrade can be handed what
it needs without changing the signature again.

**The list belongs to the repository, not to a ServiceLoader sweep.** There is one history table per
project, fixed by `history.target`, and its upgrades are that table's — written in its dialect,
against its shape. Collecting from every plugin on the classpath would hand a MySQL history upgrade
to a PostgreSQL history table whenever a project uses both, and would need a cross-plugin ordering
mechanism whose only job is to resolve the ambiguity the collection created. The ServiceLoader step
already happened one level up: the plugin was discovered, and `history.target` chose whose
repository is in play.

**`upgrades()` has a default; `fingerprint` deliberately does not.** The rule is whether "nothing" is
a correct answer. A plugin describing a task file always has content to fold, so a default there
would let an implementation silently skip something essential. A new history backend has no older
shapes to come from, so an empty list is the truth rather than an omission.

**An upgrade fills in place; it cannot append.** `amend` appends because it makes a new claim.
Completing a row is not a claim, and appending breaks both ways: an `AMENDED` row would tell an
audit that something executed was merely claimed, and an `EXECUTED` one is refused outright by the
integrity check in `latestApplies`, which rejects a second apply of a migration that already stands.
So the repository has a fill-only path — narrower than the `HistoryFingerprintUpdater` removed in
this release, which existed to *overwrite* a token. In the JDBC family it is one `UPDATE` using
`COALESCE`, over the standing applies from `latestApplies()`. Filling touches only columns that are
absent, which is what removes the deleted-`down:` hazard, and it writes only what D can supply:
`fingerprint`, `dependencies` and `no_way_back`. Not `origin`, whose null already reads as
`EXECUTED`; not `plugin_metadata`, which only the plugin that ran the node could know.

**The fill's pending-ness is the schema steps'.** It runs last, after the steps that add the columns
it writes, and reports itself pending exactly when any of them is. Asking instead whether a row still
carries a null fingerprint would never stop being true — a row whose task file is gone has no source
for one, and every command would refuse forever over the row `amend <id>` exists to withdraw.

**What filling asserts, and it is not nothing.** A row from before the column says a migration was
applied and nothing about its content. Writing today's token onto it asserts that W matches D, and
nothing can verify that, because migraphe does not read W. It is the assumption the operator makes by
running `upgrade-history`; the cost of it being wrong is one genuinely edited migration reading as unchanged,
once. This is consistent with the root principle rather than an exception to it: the tool is not
choosing between two readings of a disagreement, because an absent token records no reading at all.
Overwriting a token that *is* recorded and differs would be choosing, and that stays `amend`'s.

**What it does not reach.** A row for a migration the definitions no longer declare has no source
for its missing fingerprint, so the two-halves message survives: `upgrade-history` for rows the task files
still declare, `amend <id>` to withdraw one they do not.

### Accepted limits

- A `down:`-only edit moves the fingerprint although no database object differs.
- Deleting a task file destroys information on the D side: that node's own dependencies drop out of
  every closure unless another declared path reaches them, and a deleted *intermediate* makes the two
  sides reduce differently, so an untouched node can compare as changed. The false `[!]` is the price,
  and `amend` clears it.
- If a task file **and** its target's YAML are both deleted, the orphan can be **reported but not
  removed**: its row and its `target_id` are still there to name, but nothing resolves that id to a
  connection. Restoring the target's configuration is what makes it removable.
- `amend` on an orphan records that it is no longer applied and leaves the objects standing, so it is
  only correct after the objects are gone by another route. Claiming an orphan *is* applied is not
  offered at all: D does not describe it, so there is nothing for the record to agree with.
- A plugin that cannot report its rollback payload cannot use `amend` at all.
- A row carrying a fingerprint but a `NULL` `dependencies` — or a `NULL` `no_way_back` for a task that
  declares one — reads as "stood on nothing" and "recorded neither". No released version writes either:
  the columns arrive together, an insert names every column so a half-migrated table fails loudly, and
  the rows predating them carry no fingerprint, which `upgrade-history` fills. Reaching
  them takes a hand-edited row, and the design already says what a hand-edited row reads as.

## Implementation History

| Phase | Description | Status |
|-------|-------------|--------|
| 1–7 | Core (types, interfaces, graph, algorithms) | ✅ |
| 8 | History abstraction + PostgreSQL plugin | ✅ |
| 9 | MicroProfile Config (YAML) | ✅ |
| 10 | CLI (config loading, commands) | ✅ |
| 11 | API module separation + Plugin system (SPI) | ✅ |
| 12 | EnvironmentDefinition generification + NullAway | ✅ |
| 13 | Validate command | ✅ |
| 14 | Core logic extraction for Gradle plugin | ✅ |
| 15 | Gradle plugin (Extension, Tasks, TestKit) | ✅ |
| 16 | Virtual Threads parallel execution | ✅ |
| 17 | JDBC plugin extraction + MySQL plugin | ✅ |
| 18 | Generator plugin system + JDBC Markdown docs | ✅ |
| 19 | Generator SPI refactor (source/output separation) + JSON output | ✅ |
| 20 | CLI Maven Resolver — plugin dependency resolution | ✅ |
| 21 | JitPack support + SHA-256 lockfile pinning + `migraphe pin` | ✅ |
| 22 | JitPack distribution (primary channel until Maven Central) for Migraphe itself | ✅ |
| — | Parser-combinator SQL statement splitting + per-dialect grammars (Session 55) | ✅ |
| — | Markdown ER diagram (Mermaid `erDiagram`) in `index.md` + keys-only option (Sessions 61–62) | ✅ |
| — | Multi-schema hardening of the Markdown ER diagram / schema docs + PostgreSQL E2E (Session 63) | ✅ |
| — | ER diagram layout engine (ELK) + per-table neighborhood diagrams + entity cap (Session 64) | ✅ |

### Future Phases

- **`migraphe upgrade-history`** — replaced `amend --incomplete`, and moved history-schema changes out of
  every command's `initialize()`. The design is settled and recorded under "Drift and its repair";
  nothing is built.
- Additional database plugins (MongoDB, etc.)
- DB-specific generator plugins (MySQL HTML, JDBC TypeScript)
- **Extract an `ErDiagramOptions` record** — `JdbcMarkdownGenerator`'s terminal constructor has reached 8 parameters. This changes the shape of a public API, so it belongs to its own release cycle; the plan is to keep the existing constructors as `@Deprecated` delegates to a new options-record constructor.
- **Disambiguate *unnamed* FK constraints in `JdbcSchemaInfoProvider.buildKeyInfo`** — the cross-child-table merge was fixed in Session 65 (decision 24), but several unnamed constraints (`FK_NAME` `NULL` → `""`) on the *same* child table still aggregate into one entry with mixed columns. Would need a positional/`KEY_SEQ`-restart discriminator; not reproducible on H2, which names unnamed constraints automatically, nor on MySQL, where InnoDB always auto-names foreign keys — so this needs a backend that genuinely permits anonymous constraints before it can be driven by a test.
- **Fix the dangling Markdown link for cross-database exported keys (MySQL)** — with the Session 66 fix, a child table in another database now appears in `## Exported Keys`, but `referencedSchema` is `""` (MySQL leaves `FKTABLE_SCHEM` `NULL`) and `JdbcMarkdownGenerator.resolveReferencedSchema` falls back to the *current* schema, so the row links to a page that does not exist. The shape pre-dates the fix; the fix increases how often it appears. ER diagrams are unaffected — descendant traversal skips targets outside the known set.
- **Cover the case where `FKTABLE_CAT` is load-bearing** — `MySQLSchemaInfoProviderTest`'s collision test uses two child tables with *different* names, so `FKTABLE_NAME` alone would already discriminate. Only two children sharing a **table name** across databases exercise the catalog component of the key.
- **Split up `JdbcMarkdownGenerator` (~1,200 lines)** — extracting an `ErDiagramRenderer` is the obvious first cut.
- **Character-count-based ER diagram size cap** — `er-diagram-per-table-max-entities` uses entity count as a proxy; measuring the rendered diagram length would track Mermaid's real limit more accurately.
