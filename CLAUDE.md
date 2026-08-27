# Migraphe - Project Documentation for Claude

> **IMPORTANT**: This file MUST always be written in English. Never translate it to Japanese or any other language, even partially. This rule is permanent.

## Overview

DAG-based migration orchestration tool for database/infrastructure migrations across multiple environments.

**Tech Stack**: Java 21, Gradle 9.5.1 (Kotlin DSL), MicroProfile Config + SmallRye (YAML), JUnit 5 + AssertJ, Spotless, jspecify + NullAway
**Current Phase**: 22 (JitPack distribution) - COMPLETE; latest work: UP-content `fingerprint` recorded in history and reported by `status` as five drift states, plus three exception-safety defects fixed in `DagExecutor`'s completion accounting, a fourth knowingly left open (Session 74)
**Tests**: 1,152, 100% passing

## Module Structure

```
migraphe-api/              # Lightweight interfaces (no external deps) - for plugin developers (incl. generator SPI)
migraphe-core/             # Orchestration logic, algorithms, config loading, factories
migraphe-plugin-jdbc/      # Generic JDBC plugin (type="jdbc") - standalone or base for DB-specific plugins
migraphe-plugin-postgresql/ # PostgreSQL plugin (extends jdbc, driver/DDL fixed, postgresql-markdown/schema generators)
migraphe-plugin-mysql/     # MySQL plugin (extends jdbc, driver/DDL fixed, mysql-markdown/schema generators)
migraphe-plugin-generator-json/ # JSON output plugin (type="output-json") - outputs any data as JSON to stdout
migraphe-cli/              # CLI entry point, commands, console output
migraphe-gradle-plugin/    # Gradle plugin (migrapheUp/Down/Status/Validate/Generate tasks)
```

## Core Interfaces (Plugins implement these)

- `MigrationNode` - Node structure + metadata, provides `upTask()`/`downTask()`
- `Environment` - Environment configuration
- `Task` - Execution logic (up/down)
- `HistoryRepository` - Execution history persistence
- `SchemaInfoProvider<T>` - Schema info extraction from Environment
- `GeneratorSourcePlugin<T>` - Data extraction SPI (SourceContext → typed data)
- `GeneratorOutputPlugin` - Data rendering SPI (Object data + OutputContext)
- `GeneratorDefinition` - Generator configuration record
- `MigrationGraphView` - Read-only view of MigrationGraph

## Package Structure

```
io.github.kakusuke.migraphe.api/
├── environment/    # Environment, EnvironmentId
├── graph/          # MigrationNode (interface), NodeId
├── task/           # Task, TaskResult, ExecutionDirection
├── history/        # HistoryRepository (interface), ExecutionRecord, ExecutionStatus, RecordIds (UUIDv7, package-private)
├── execution/      # ExecutionListener, ExecutionPlanInfo, ExecutionSummary
├── schema/         # SchemaInfoProvider<T>
├── common/         # Result
├── generator/      # GeneratorSourcePlugin<T>, GeneratorOutputPlugin, GeneratorDefinition, SourceContext, OutputContext
└── spi/            # MigraphePlugin, EnvironmentProvider, MigrationNodeProvider, HistoryRepositoryProvider, TaskDefinition, EnvironmentDefinition

io.github.kakusuke.migraphe.core/
├── common/         # ValidationResult (internal — not part of plugin SPI)
├── graph/          # MigrationGraph, ExecutionPlan, ExecutionLevel, TopologicalSort, FormatUtils
│   └── layout/     # ExecutionGraphView, LayoutSort, LayoutOrder, LayoutTree, LayoutStream, NonTreeEdge, Cell, GridCanvas, NodeLineInfo, GraphVisualizer
├── execution/      # DagExecutor (unified UP/DOWN + sequential/parallel), StatusService, ExecutionResult, ExecutionContext
│                   # + ReadyNodeTracker (direction-aware), SynchronizedExecutionListener, Executor (interface)
├── generator/      # GeneratorRegistry, GeneratorExecutor
│   └── tree/       # MigrationTreeSourcePlugin (type="migration-tree")
├── history/        # InMemoryHistoryRepository, SynchronizedHistoryRepository
├── config/         # ProjectConfig (incl. GeneratorSection), TargetConfig, TaskConfig, ConfigLoader, ConfigValidator, YamlFileScanner
├── factory/        # EnvironmentFactory, MigrationNodeFactory (generic, uses PluginRegistry)
├── plugin/         # PluginRegistry, PluginLoadException
├── plugin/         # SimpleMigrationNode, SimpleEnvironment, SimpleTask (reference impl)
└── plugin/noop/    # NoopPlugin + providers (type="noop", InMemory history, noop execution)

io.github.kakusuke.migraphe.jdbc/
├── JdbcEnvironment, JdbcUpTask, JdbcDownTask, JdbcMigrationNode, JdbcHistoryRepository
├── JdbcPlugin, Jdbc{Environment,MigrationNode,HistoryRepository}Provider
├── JdbcEnvironmentDefinition, SqlTaskDefinition, JdbcException
├── SchemaStep, SchemaStepParser  # history-schema steps: --@apply statements + optional --@check detection (package-private)
├── statement/      # SQL splitting toolkit: SqlParser, SqlParsers (combinators), StatementSplitter (StatementSplitter.standard()), DelimiterDirective
├── schema/         # JdbcSchemaInfo, JdbcSchemaDetail, JdbcTableInfo, JdbcViewInfo, JdbcColumnInfo, etc. (19 types)
│                   # JdbcSchemaInfoProvider (DatabaseMetaData → JdbcSchemaInfo)
├── markdown/       # JdbcMarkdownPlugin (type="jdbc-markdown"), JdbcMarkdownGenerator, JdbcMarkdownDefinition
└── META-INF/services/ # MigraphePlugin + GeneratorSourcePlugin + GeneratorOutputPlugin

io.github.kakusuke.migraphe.postgresql/
├── PostgreSQLEnvironment (extends JdbcEnvironment), PostgreSQLException (extends JdbcException)
├── PostgreSQLPlugin, PostgreSQL{Environment,MigrationNode,HistoryRepository}Provider
├── PostgreSQLEnvironmentDefinition
├── statement/      # PostgreSqlGrammar (dollar-quote $tag$; no keyword blocks) — wired via PostgreSQLEnvironment.statementSplitter()
├── schema/         # PostgreSQLSchemaInfo, PostgreSQLSchemaInfoProvider (delegates JDBC base + pg_catalog extras)
│                   # PG-specific: extensions, enums, sequences, functions, triggers, materialized views, partitions, policies
├── markdown/       # PostgreSQLMarkdownPlugin (type="postgresql-markdown"), PostgreSQLMarkdownGenerator (extends JdbcMarkdownGenerator)
└── META-INF/services/ # MigraphePlugin + GeneratorSourcePlugin + GeneratorOutputPlugin

io.github.kakusuke.migraphe.mysql/
├── MySQLEnvironment (extends JdbcEnvironment), MySQLException (extends JdbcException)
├── MySQLPlugin, MySQL{Environment,MigrationNode,HistoryRepository}Provider
├── MySQLEnvironmentDefinition
├── statement/      # MySqlGrammar (backtick id, # / -- comments, recursive BEGIN/END blocks, DELIMITER) — wired via MySQLEnvironment.statementSplitter()
├── schema/         # MySQLSchemaInfo, MySQLSchemaInfoProvider (catalog-based, information_schema queries)
│                   # MySQL-specific: storage engines, table meta, triggers, routines, events, partitions
├── markdown/       # MySQLMarkdownPlugin (type="mysql-markdown"), MySQLMarkdownGenerator (extends JdbcMarkdownGenerator)
└── META-INF/services/ # MigraphePlugin + GeneratorSourcePlugin + GeneratorOutputPlugin

io.github.kakusuke.migraphe.output.json/
└── JsonOutputPlugin (type="output-json") — outputs any data as pretty-printed JSON to stdout
    META-INF/services/ # GeneratorOutputPlugin

io.github.kakusuke.migraphe.cli/
├── Main.java
├── command/        # Command, UpCommand, DownCommand, StatusCommand, ValidateCommand, GenerateCommand
├── resolver/       # MavenArtifactCoordinate, PluginConfigPreParser, MavenPluginResolver, PluginResolver
├── listener/       # ConsoleExecutionListener
└── util/           # AnsiColor

io.github.kakusuke.migraphe.gradle/
├── MigrapheGradlePlugin.java     # Plugin entry point
├── MigrapheExtension.java        # DSL extension (baseDir)
├── AbstractMigrapheTask.java     # Base task (PluginRegistry, ExecutionContext)
├── Migraphe{Up,Down,Status,Validate,Generate}Task.java  # Gradle tasks
└── GradleExecutionListener.java  # Gradle Logger-based listener
```

## Key Design Decisions

One-line summaries below. Full rationale: see [Architecture & Design Decisions](docs/ARCHITECTURE.md).

1. **Task Separation**: MigrationNode (structure) vs Task (execution logic)
2. **Up/Down Migrations**: `upTask()` for forward, `downTask()` for rollback
3. **HistoryRepository**: Pluggable persistence (InMemory, JDBC/PostgreSQL/MySQL, etc.)
4. **DOWN Task Serialization**: Plain text SQL stored in ExecutionRecord
5. **MicroProfile Config**: YAML with `@ConfigMapping`; `${VAR}` resolves from variables(600)/profiles(500)/sysprops(400)/YAML(100); OS env only via `${env.VAR}` (namespaced at ordinal 300 to avoid `target.*` key collisions; no `addDefaultSources()`). See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
6. **Multi-file Configuration**: `migraphe.yaml`, `targets/*.yaml`, `tasks/**/*.yaml`, `environments/*.yaml`
7. **Auto Task ID**: Generated from file path (e.g., `tasks/db1/create.yaml` → `"db1/create"`)
8. **Plugin System (Phase 11)**: ServiceLoader + URLClassLoader for runtime loading
9. **Listener Pattern (Phase 14)**: Core business logic separated from CLI/Gradle presentation (`ExecutionListener`, `ExecutionGraphView`)
10. **Gradle Plugin (Phase 15)**: `java-gradle-plugin` + TestKit, `migraphePlugin` config, `@Option`/`-P` args
11. **Shared Logic**: `ExecutionContext.createHistoryRepository()`, `ExecutionPlan.filterNodesInOrder()`, `ExecutionGraphView.renderLines()`, `FormatUtils`
12. **DAG Stream Layout Pipeline (Phase 15)**: `MigrationGraph → LayoutSort → LayoutTree → GridCanvas → ExecutionGraphView`; `Cell` sealed interface (13 variants)
13. **Unified DAG Execution (Phase 16 → unified Session 54)**: single `DagExecutor(graph, history, listener, direction, maxParallelism)` for all UP/DOWN + sequential/parallel; vthreads + `ReadyNodeTracker(direction)`; **fail-soft** on failure; auto-wraps sync repository/listener
14. **JDBC Plugin Extraction (Phase 17)**: generic `migraphe-plugin-jdbc`; `postgresql`/`mysql` extend `JdbcEnvironment` with fixed driver/DDL
15. **Generator Plugin System (Phase 18)**: Generator SPI in `migraphe-api`; `JdbcSchemaInfoProvider`, `JdbcMarkdownPlugin`, `GeneratorRegistry`/`GeneratorExecutor`. Markdown output embeds Mermaid ER diagrams (tables=entities, FKs=`||--o{`): database-wide in `index.md`, plus a per-table neighborhood diagram (`{T} ∪ ancestors* ∪ descendants*`) on each table page. YAML keys: `er-diagram` (default true, master switch), `er-diagram-keys-only` (default false = all columns / true = PK+FK only), `er-diagram-layout` (default `elk`, emitted as Mermaid frontmatter), `er-diagram-per-table` (default true), `er-diagram-per-table-max-entities` (default 60, `<=0` = unlimited). Output layout is `<output-dir>/index.md` + one directory per schema (`<output-dir>/<schema>/tables|views/`); `generators[].name` is the `--name` filter key and the documentation title (`# <name>`, no fixed prefix) but never a path segment (Session 69) — see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
16. **Generator SPI Refactor — Source/Output Separation (Phase 19)**: `GeneratorSourcePlugin<T>` (data) decoupled from `GeneratorOutputPlugin` (render); `SourceContext`/`OutputContext`; JSON output module
17. **CLI Maven Resolver (Phase 20)**: `plugins:` Maven coordinates resolved via Maven Resolver from `~/.m2` + Central into a URLClassLoader
18. **PostgreSQL Generator Plugins**: `postgresql-schema` source (pg_catalog extras) + `postgresql-markdown` output via Template Method hooks
19. **MySQL Generator Plugins**: `mysql-schema` source (catalog-based, information_schema) + `mysql-markdown` output via the same Template Method pattern
20. **JitPack + Lockfile Pinning (Phase 21)**: `repositories:` (HTTPS-only), `migraphe.lock.yaml` SHA-256 pinning via `migraphe pin`/`--check`/`validate`
21. **JitPack Distribution (Phase 22)**: JitPack is the primary distribution channel until Maven Central; `-PpublishGroup` switch, plugin-marker workaround, tag-based user docs
22. **SQL Statement Splitting (Session 55)**: parser-combinator toolkit in `migraphe-plugin-jdbc` (`...jdbc.statement`); dialect grammars defined per-plugin (PostgreSQL dollar-quote, no keyword blocks; MySQL recursive BEGIN/END blocks + DELIMITER); unified split-and-loop execution in both autocommit/transaction modes; old `SqlStatements` removed. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
23. **History Schema Evolution (Session 70)**: dialect DDL resources are lists of steps — `--@apply` (statements, starts a step) with an optional preceding `--@check` (detection query) — parsed by `SchemaStepParser` and walked by `initialize()`, one statement at a time; a step whose query returns a row is skipped, one without a query always runs. **Shipped resources use no detection**: creation leans on `IF NOT EXISTS`, which no other schema's same-named table can confuse, whereas the generic resource cannot name the current schema portably. Detection is reserved for changes lacking a portable conditional form (`ALTER TABLE ADD COLUMN` — no `IF NOT EXISTS` on Oracle MySQL). Detection queries run before the table exists (`information_schema`) and their failures propagate rather than read as "not applied"; an apply failure re-runs detection to absorb a lost race. No schema-version table, no config key. All commands share the path (`status` already created the table). A resource with no directive is one unconditional step, keeping custom plain-SQL resources working. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
24. **Time-Ordered Record Ids + `target_id` Rename (Session 71)**: `ExecutionRecord`'s factories mint **UUIDv7** instead of UUIDv4, so the existing `id` primary key sorts in creation order; "latest" now orders by `executed_at DESC, id DESC`. This fixes a silent misread on MariaDB, where Connector/J drops fractional seconds (the server reports itself as `5.5.5-…`), so a `down` followed by an `up` ties on the second and the winner was whatever the storage engine returned. `executed_at` stays the primary sort key so rows with legacy random ids keep their order. `executedNodes()` uses the same rule via a correlated scalar subquery. `RecordIds` is package-private (the canonical constructor still takes any string); monotonic within a millisecond via the `rand_a` counter, never rewinding on a backwards clock. Separately, `environment_id` → **`target_id`** (it always held a target name, never the `--env` overlay), applied by the first detection-guarded step — which is why **detection queries may now carry positional parameters, all bound to the current schema** (`getSchema()`, falling back to `getCatalog()`). Rename statement is per-dialect (MySQL `CHANGE COLUMN`, PostgreSQL `RENAME COLUMN`, generic add/backfill/drop); index names keep `_env`. **Do not share one history DB across versions spanning this change.** See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

25. **UP Content Fingerprint (Session 74)**: `migraphe_history.fingerprint`, written on **UP success only**, so a later run can tell a task was edited after it was applied. `MigrationNode.fingerprint()` is a `default null` **opaque** token — `null` means "unknown", never "unchanged" — and the derivation is the plugin's choice; `JdbcMigrationNode` uses the SHA-256 hex of `upSql.strip()` (the file-backed builder methods are `@Deprecated(forRemoval, since="0.7.0")` rather than deciding whether an external file's line endings count). Column is `TEXT` in all three dialects (a truncated token would report "changed" forever), added by a detection-guarded step. `ExecutionRecord`'s canonical ctor is **10 → 11 args (breaking)**; `upSuccess` got an overload instead. `StatusCommand` now goes through `StatusService` rather than recomputing status itself. How the read side reports the comparison is decision 27 below. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) decision 30.
26. **DagExecutor Completion Accounting (Session 74)**: **exactly one latch countdown per node**, or the run hangs or ends while a node is still applying. Fixed three defects of that shape: a throwing `fingerprint()` (now degrades to the contract's `null`), a throwing `history.record()` (whole post-execute body guarded; the `catch` never touches the repository again), and dispatch of a node that failure propagation had already reported skipped while it waited for a permit (re-check after `acquire()`). **Still open**: a node marked skipped while *already running* is counted twice, reachable with `execution.parallel: true`; it needs an atomic claim where only one of the coordinator and `propagateFailure` owns a node. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) decision 31.

27. **Drift Reporting in `status` (Session 74)**: a `null` fingerprint means two different things — on the **node** the plugin is opting out of the comparison, on the **record** the row predates the column — so a boolean folded distinct answers into one. `NodeStatus.upContentState()` returns `UpContentState`: `NOT_APPLICABLE` (never applied, or plugin opts out; `executed()` separates them), `UNKNOWN`, `UNCHANGED`, `CHANGED`, `UNREADABLE`. `UNREADABLE` is its own state because the interface default returns `null` and cannot throw, so a throw proves an override — a fault, not an opt-out. Markers `[ ] [✓] [!] [?] [E]`, rendered by the shared `StatusLineFormatter` in core (both `StatusCommand` and `MigrapheStatusTask` use it); the switch has no `default` arm so a new state stops compiling. **Upgrade consequence**: pre-column rows read `UNKNOWN` permanently — `up` skips applied nodes and `down` cascades to all dependents — which is why `baseline` (record the current definition as applied, without executing) is planned separately from `reconcile` (roll back and re-apply what drifted). See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) decision 32.

## CLI Project Structure

```
project/
├── migraphe.yaml        # plugins, project.name, history.target
├── targets/*.yaml       # type, jdbc_url, username, password (flat structure)
├── tasks/**/*.yaml      # name, target, dependencies, up, down, autocommit (flat structure)
└── environments/*.yaml  # Environment-specific overrides
```

Commands: `migraphe status`, `migraphe up`, `migraphe down`, `migraphe validate`, `migraphe generate [--name <name>]`

## Instructions for Claude

1. **CLAUDE.md language**: This file must always be in English — no exceptions, ever.
2. **Keep CLAUDE.md compact**: Maintain brevity. Use tables, bullet points, concise descriptions. No verbose prose. Record new design-decision *detail* in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and keep only a one-line summary (+ link) here.
3. **Respond in Japanese**: All user-facing output must be in Japanese. Internal reasoning may be in English.
4. **Changelog maintenance**: Append every new session record to [docs/CHANGELOG.md](docs/CHANGELOG.md) (full history lives there). In CLAUDE.md keep only the latest session as a short summary + link.
5. **Subagent delegation**: Delegate broad exploration to `Explore`, and independent judgment (e.g. `cycle-verifier`) to a fresh context. Do *not* delegate work that reads the same few files repeatedly — TDD cycles run in the main context (see `/tdd-cycle`). Do not duplicate subagent research.
6. **LSP first**: For Java symbol lookup (definitions, references, hover), prefer the `LSP` tool over `Read`/`Grep`. Note that subagents do not have it — another reason to keep file-level work in the main context.
7. **Large output**: Commands producing many lines — always limit with `sed -n 'X,Yp'`, `grep -n pattern | head -N`, or `wc -l`. Never consume full large output in main context.

## Development Process

### TDD — MANDATORY

**Every code change MUST go through the `/tdd-cycle` skill, one cycle at a time.**

The `/tdd-cycle` skill runs: `Plan → Red → Green → Tidy` **in the main context**, then `cycle-verifier` (Opus, fresh context) audits the finished cycle.

Call it repeatedly to advance implementation incrementally. Never write production code outside this cycle.

| Phase | Rule |
|-------|------|
| **Plan** | Name one observable behavior + concrete expected values. |
| **Red** | Write a failing test first. **Gate**: a non-zero `run_test` exit must be observed *before* the first production edit. |
| **Green** | Write the minimum code to make the test pass. |
| **Tidy** | Remove duplication, improve readability, behavior identical. Tests stay green. Never skip — report explicitly if nothing to do. |
| **Verify** | `cycle-verifier` subagent audits: gate / test-validity / over-implementation / scope / regression. |

Tests run through the `migraphe-build` MCP server (`run_test` / `run_spotless`) — never `./gradlew` directly. Scope every run with `module` + `test_filter`. `run_errorprone_check` is session-end only (12+ min clean build).

The first four phases share one context because they touch the same two files; splitting them across subagents only re-reads those files. What that gives up in structural enforcement is recovered by the Red gate (verifiable evidence, not a promise) and the independent Verify audit. The skill is scoped to incremental changes; net-new classes ≥200 lines or multi-file scaffolding route to `general-purpose` instead.

All tests MUST pass at 100% before committing.

### Build / Pre-commit / Session End

Build/test/spotless/ErrorProne commands and the pre-commit / session-end checklist (incl. doc updates, version-bump rules) live in the `migraphe-session-end` skill.

ErrorProne/NullAway warning fixes: see the `migraphe-errorprone` skill.

## Implementation Status

Current phase: 22 (JitPack distribution) — COMPLETE. Full phase history (Phase 1–22) + Future Phases: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#implementation-history).

## Design Principles

1. **KISS**: Simple and focused
2. **SRP**: Task separated from Node
3. **Interface Segregation**: Small, focused interfaces
4. **Dependency Inversion**: Depend on interfaces
5. **Immutability**: Records and immutable collections
6. **Null Safety**: `@Nullable` (jspecify) + NullAway (compile-time checks), `Optional` only for SmallRye `@ConfigMapping`
7. **Type Safety**: Sealed interfaces, pattern matching

## Session End Procedure

Pre-commit / session-end steps (incl. CLAUDE.md / CHANGELOG.md / ARCHITECTURE.md routing, user-doc updates, and version-bump rules): run the `migraphe-session-end` skill.

---

## Changelog

Latest session only — full history: [docs/CHANGELOG.md](docs/CHANGELOG.md).

### 2026-08-27 (Session 74)
- Implemented `--preview` in the CLI as a synonym for `--dry-run`; it existed in the docs and the Gradle plugin but not in the CLI, where `migraphe up --preview` failed with `Error: Target not found: --preview`. Also stopped `Unknown command` printing after a correct argument error.
- Added the **UP content fingerprint**: `migraphe_history.fingerprint`, `MigrationNode.fingerprint()` (`default null`, opaque), SHA-256 of `upSql.strip()` in `JdbcMigrationNode`, and a detection-guarded `TEXT` column in all three dialects. The interface javadoc was rejected **four times** in review for stating absolutes the implementation could not honor; the surviving text claims only that the token is opaque and that `null` means unknown. Groundwork for rolling back orphan migrations — prompted by empirically disproving a claim in an intro-article draft: deleting a task file makes the node vanish from `status` and `down --all`, stranding the DB objects.
- Fixed **three exception-safety defects in `DagExecutor`'s completion accounting**, all one shape: a throwing `fingerprint()` and a throwing `history.record()` each hung the whole run (`processCompletion` skipped, dependents never queued, latch never zero), and a node reported "dependency failed" while it waited for a semaphore permit was dispatched anyway — its migration ran after the console said it was skipped, with no parallel configuration needed.
- Established that the **generic history resource works on PostgreSQL**. The PostgreSQL resource's comment claimed a bare positional parameter cannot be compared against `information_schema`'s `sql_identifier` domain, and on that basis `type="jdbc"` with a PostgreSQL driver was believed broken. Nothing had tested it; it initializes cleanly. Comment corrected, test kept as the first coverage of that pair.
- **`status` now reports drift**, in five states rather than a boolean, after the user pointed out that a `null` fingerprint on the node (the plugin opting out) and a `null` on the record (a row predating the column) are not the same thing. `UpContentState` + the markers `[ ] [✓] [!] [?] [E]`, rendered by a `StatusLineFormatter` shared by the CLI and Gradle — extracted only after consolidating the Gradle task onto `StatusService` made the two rendering lambdas identical, which also gave the Gradle side its first unit cover of the executed branch. The boolean `upContentChanged()` was deleted once the enum existed (never released). Guarded `node.fingerprint()` on the read path first, since wiring the marker made a throwing plugin able to take `status` down.
- **Release notes**: breaking change (minor bump). `ExecutionRecord`'s canonical constructor goes 10 → 11 arguments, so a plugin calling it directly must recompile; `MigrationNode.fingerprint()` is a `default` method, so existing plugins are unaffected. For users, the two `DagExecutor` fixes both bite in the **default sequential configuration**. **One defect is knowingly left open**: a node marked skipped while already running is counted down twice, so with `execution.parallel: true` the run can summarize and exit while another node is mid-DDL, and a node the console reported skipped gets a success history row. See [docs/CHANGELOG.md](docs/CHANGELOG.md) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) decisions 30-31.

---

**Last Updated**: 2026-08-27
**Current Work**: UP-content fingerprinting is recorded and now rendered by `status` as five drift states. Next: `baseline` (adopt the current definitions without executing — required before the `[?]` an upgrade shows can ever clear), `reconcile` (roll back and re-apply what drifted), a `dependencies` column so orphan migrations can be rolled back, and drift warnings in `validate`. `DagExecutor`'s completion accounting was hardened against exceptions on the success path; one accounting defect is deliberately deferred to its own change. See [docs/CHANGELOG.md](docs/CHANGELOG.md).
