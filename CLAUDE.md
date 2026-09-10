# Migraphe - Project Documentation for Claude

> **IMPORTANT**: This file MUST always be written in English. Never translate it to Japanese or any other language, even partially. This rule is permanent.

## Overview

DAG-based migration orchestration tool for database/infrastructure migrations across multiple targets.

**Tech Stack**: Java 21, Gradle 9.5.1 (Kotlin DSL), MicroProfile Config + SmallRye (YAML), JUnit 5 + AssertJ, Spotless, jspecify + NullAway

## Module Structure

```
migraphe-api/              # Lightweight interfaces (no external deps) - for plugin developers (incl. generator SPI)
migraphe-core/             # Orchestration logic, algorithms, config loading, factories
migraphe-plugin-jdbc/      # Generic JDBC plugin (type="jdbc") - standalone or base for DB-specific plugins
migraphe-plugin-postgresql/ # PostgreSQL plugin (extends jdbc, driver/DDL fixed, postgresql-markdown/schema generators)
migraphe-plugin-mysql/     # MySQL plugin (extends jdbc, driver/DDL fixed, mysql-markdown/schema generators)
migraphe-plugin-generator-json/ # JSON output plugin (type="output-json") - outputs any data as JSON to stdout
migraphe-cli/              # CLI entry point, commands, console output
migraphe-gradle-plugin/    # Gradle plugin (migrapheUp/Down/Status/Amend/Rebuild/Validate/Generate tasks)
```

## Core Interfaces (Plugins implement these)

- `MigrationNode` - Node structure + metadata, provides `upTask()`/`downTask()`
- `Target` - a connection under `targets/` that migrations run against
- `Task` - Execution logic (up/down)
- `HistoryRepository` - Execution history persistence
- `SchemaInfoProvider<T>` - Schema info extraction from a Target
- `GeneratorSourcePlugin<T>` - Data extraction SPI (SourceContext → typed data)
- `GeneratorOutputPlugin` - Data rendering SPI (Object data + OutputContext)
- `GeneratorDefinition` - Generator configuration record
- `MigrationGraphView` - Read-only view of MigrationGraph

## Package Structure

```
io.github.kakusuke.migraphe.api/
├── target/         # Target, TargetId, DownTaskRestorer (capability: rebuild a recorded rollback)
├── graph/          # MigrationNode (interface), NodeId
├── task/           # Task, TaskResult, ExecutionDirection
├── history/        # HistoryRepository (interface), ExecutionRecord, ExecutionStatus, ExecutionOrigin, RecordIds (UUIDv7, package-private)
├── execution/      # ExecutionListener, ExecutionPlanInfo, ExecutionSummary
├── schema/         # SchemaInfoProvider<T>
├── common/         # Result
├── generator/      # GeneratorSourcePlugin<T>, GeneratorOutputPlugin, GeneratorDefinition, SourceContext, OutputContext
└── spi/            # MigraphePlugin, TargetProvider, MigrationNodeProvider, HistoryRepositoryProvider, TaskDefinition, TargetDefinition

io.github.kakusuke.migraphe.core/
├── common/         # ValidationResult (internal — not part of plugin SPI)
├── graph/          # MigrationGraph, ExecutionPlan, ExecutionLevel, TopologicalSort, FormatUtils
│   └── layout/     # ExecutionGraphView, LayoutSort, LayoutOrder, LayoutTree, LayoutStream, NonTreeEdge, Cell, GridCanvas, NodeLineInfo, GraphVisualizer
├── execution/      # DagExecutor (unified UP/DOWN + sequential/parallel), StatusService, ExecutionResult, ExecutionContext
│                   # + AmendService, RebuildService, OrphanNode, UpContentState, StatusLineFormatter (shared by CLI + Gradle)
│                   # + ReadyNodeTracker (direction-aware), SynchronizedExecutionListener, Executor (interface)
├── generator/      # GeneratorRegistry, GeneratorExecutor
│   └── tree/       # MigrationTreeSourcePlugin (type="migration-tree")
├── history/        # InMemoryHistoryRepository, SynchronizedHistoryRepository
├── config/         # ProjectConfig (incl. GeneratorSection), TargetConfig, TaskConfig, ConfigLoader, ConfigValidator, YamlFileScanner
├── factory/        # TargetFactory, MigrationNodeFactory (generic, uses PluginRegistry)
├── plugin/         # PluginRegistry, PluginLoadException
├── plugin/         # SimpleMigrationNode, SimpleTarget, SimpleTask (reference impl)
└── plugin/noop/    # NoopPlugin + providers (type="noop", InMemory history, noop execution)

io.github.kakusuke.migraphe.jdbc/
├── JdbcTarget, JdbcUpTask, JdbcDownTask, JdbcMigrationNode, JdbcHistoryRepository
├── JdbcPlugin, Jdbc{Target,MigrationNode,HistoryRepository}Provider
├── JdbcTargetDefinition, SqlTaskDefinition, JdbcException
├── SchemaStep, SchemaStepParser  # history-schema steps: --@apply statements + optional --@check detection (package-private)
├── statement/      # SQL splitting toolkit: SqlParser, SqlParsers (combinators), StatementSplitter (StatementSplitter.standard()), DelimiterDirective
├── schema/         # JdbcSchemaInfo, JdbcSchemaDetail, JdbcTableInfo, JdbcViewInfo, JdbcColumnInfo, etc. (19 types)
│                   # JdbcSchemaInfoProvider (DatabaseMetaData → JdbcSchemaInfo)
├── markdown/       # JdbcMarkdownPlugin (type="jdbc-markdown"), JdbcMarkdownGenerator, JdbcMarkdownDefinition
└── META-INF/services/ # MigraphePlugin + GeneratorSourcePlugin + GeneratorOutputPlugin

io.github.kakusuke.migraphe.postgresql/
├── PostgreSQLTarget (extends JdbcTarget), PostgreSQLException (extends JdbcException)
├── PostgreSQLPlugin, PostgreSQL{Target,MigrationNode,HistoryRepository}Provider
├── PostgreSQLTargetDefinition
├── statement/      # PostgreSqlGrammar (dollar-quote $tag$; no keyword blocks) — wired via PostgreSQLTarget.statementSplitter()
├── schema/         # PostgreSQLSchemaInfo, PostgreSQLSchemaInfoProvider (delegates JDBC base + pg_catalog extras)
│                   # PG-specific: extensions, enums, sequences, functions, triggers, materialized views, partitions, policies
├── markdown/       # PostgreSQLMarkdownPlugin (type="postgresql-markdown"), PostgreSQLMarkdownGenerator (extends JdbcMarkdownGenerator)
└── META-INF/services/ # MigraphePlugin + GeneratorSourcePlugin + GeneratorOutputPlugin

io.github.kakusuke.migraphe.mysql/
├── MySQLTarget (extends JdbcTarget), MySQLException (extends JdbcException)
├── MySQLPlugin, MySQL{Target,MigrationNode,HistoryRepository}Provider
├── MySQLTargetDefinition
├── statement/      # MySqlGrammar (backtick id, # / -- comments, recursive BEGIN/END blocks, DELIMITER) — wired via MySQLTarget.statementSplitter()
├── schema/         # MySQLSchemaInfo, MySQLSchemaInfoProvider (catalog-based, information_schema queries)
│                   # MySQL-specific: storage engines, table meta, triggers, routines, events, partitions
├── markdown/       # MySQLMarkdownPlugin (type="mysql-markdown"), MySQLMarkdownGenerator (extends JdbcMarkdownGenerator)
└── META-INF/services/ # MigraphePlugin + GeneratorSourcePlugin + GeneratorOutputPlugin

io.github.kakusuke.migraphe.output.json/
└── JsonOutputPlugin (type="output-json") — outputs any data as pretty-printed JSON to stdout
    META-INF/services/ # GeneratorOutputPlugin

io.github.kakusuke.migraphe.cli/
├── Main.java
├── command/        # Command, UpCommand, DownCommand, StatusCommand, AmendCommand, RebuildCommand, ValidateCommand, GenerateCommand
├── resolver/       # MavenArtifactCoordinate, PluginConfigPreParser, MavenPluginResolver, PluginResolver
├── listener/       # ConsoleExecutionListener
└── util/           # AnsiColor

io.github.kakusuke.migraphe.gradle/
├── MigrapheGradlePlugin.java     # Plugin entry point
├── MigrapheExtension.java        # DSL extension (baseDir)
├── AbstractMigrapheTask.java     # Base task (PluginRegistry, ExecutionContext)
├── Migraphe{Up,Down,Status,Amend,Rebuild,Validate,Generate}Task.java  # Gradle tasks
└── GradleExecutionListener.java  # Gradle Logger-based listener
```

## Key Design Decisions

One-line summaries below. Full rationale: see [Architecture & Design Decisions](docs/ARCHITECTURE.md).

2. **Task Separation**: MigrationNode (structure) vs Task (execution logic)
4. **Up/Down Migrations**: `upTask()` for forward, `downTask()` for rollback
5. **HistoryRepository**: Pluggable persistence (InMemory, JDBC/PostgreSQL/MySQL, etc.)
6. **DOWN Task Serialization**: Plain text SQL stored in ExecutionRecord
7. **MicroProfile Config**: YAML with `@ConfigMapping`; `${VAR}` resolves from variables(600)/profiles(500)/sysprops(400)/YAML(100); OS env only via `${env.VAR}` (namespaced at ordinal 300 to avoid `target.*` key collisions; no `addDefaultSources()`). See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
8. **Multi-file Configuration**: `migraphe.yaml`, `targets/*.yaml`, `tasks/**/*.yaml`, `environments/*.yaml`
8. **Auto Task ID**: Generated from file path (e.g., `tasks/db1/create.yaml` → `"db1/create"`)
8. **Plugin System (Phase 11)**: ServiceLoader + URLClassLoader for runtime loading
9. **Listener Pattern (Phase 14)**: Core business logic separated from CLI/Gradle presentation (`ExecutionListener`, `ExecutionGraphView`)
10. **Gradle Plugin (Phase 15)**: `java-gradle-plugin` + TestKit, `migraphePlugin` config, `@Option`/`-P` args
11. **Shared Logic**: `ExecutionContext.createHistoryRepository()`, `ExecutionPlan.filterNodesInOrder()`, `ExecutionGraphView.renderLines()`, `FormatUtils`
12. **DAG Stream Layout Pipeline (Phase 15)**: `MigrationGraph → LayoutSort → LayoutTree → GridCanvas → ExecutionGraphView`; `Cell` sealed interface (13 variants)
13. **Unified DAG Execution (Phase 16 → unified Session 54)**: single `DagExecutor(graph, history, listener, direction, maxParallelism)` for all UP/DOWN + sequential/parallel; vthreads + `ReadyNodeTracker(direction)`; **fail-soft** on failure; auto-wraps sync repository/listener
14. **JDBC Plugin Extraction (Phase 17)**: generic `migraphe-plugin-jdbc`; `postgresql`/`mysql` extend `JdbcTarget` with fixed driver/DDL
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

25. **DagExecutor Completion Accounting**: **exactly one latch countdown per node**, or the run hangs or ends while a node is still applying. Ownership is a claim: the coordinator claims a node before starting it or skipping it, and `propagateFailure` counts only what nobody has claimed — a cone member that is already running is left to its own completion. Reachable, and fixed: propagation walks the *transitive* cone while `ReadyNodeTracker` gates on *direct* predecessors, so a node whose only declared predecessor was applied by an earlier run starts alongside the node that then fails above it (`execution.parallel: true`, or `rebuild`, whose apply phase reads the project's parallelism). A throwing `fingerprint()` degrades to `null` — but `up` now asks before applying, so a broken plugin refuses the run instead of reaching it; a throwing `history.record()` is guarded for the whole post-execute body (the `catch` never touches the repository again). **Cancellation waits**: an interrupt stops dispatching and then joins the threads already started, because the latch still holds counts for nodes that never began and virtual threads are daemon threads. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) decision 31.
26. **Drift detection and repair** — the fingerprint, the `dependencies` and `plugin_metadata` columns, `status` drift markers, `amend`, `rebuild`, orphan rollback, direction-aware `autocommit`: the design is in **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — "Drift and its repair"**.
27. **"target" means a connection, and only that**: the API types are `Target`/`TargetId` (`TargetDefinition`, `TargetProvider`) — they were `Environment`/`EnvironmentId` from before `--env` came to mean the overlay. *Environment* is now only `environments/*.yaml`, and the nodes a run operates on are `selectedNodes` / `requestedNode`, never "targets". See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — "Node identity, and the word 'target'".
28. **The plugin API breaks once, in this release**: `Environment`/`EnvironmentId` → `Target`/`TargetId` (definitions and providers with them), `HistoryRepository`'s reads lose their target-id argument, `ExecutionRecord` and `TaskResult` gain components (`origin`, `no_way_back`), and `MigrationNode.fingerprint` / `Task.signature` become required with no default to inherit. The rename makes it an **edit, not a rebuild** — which is why it is one release rather than two. A jar built against the previous release loads and then fails with `AbstractMethodError` at the first call, so **plugins must be rebuilt, not re-resolved**, and that belongs in the release note. The list a plugin author works from is in [docs/PLUGIN_DEVELOPMENT.md](docs/PLUGIN_DEVELOPMENT.md) — "What changed in 0.7.0".

## CLI Project Structure

```
project/
├── migraphe.yaml        # plugins, project.name, history.target
├── targets/*.yaml       # type, jdbc_url, username, password (flat structure)
├── tasks/**/*.yaml      # name, target, dependencies, up, down, autocommit (flat structure)
└── environments/*.yaml  # Environment-specific overrides
```

Commands: `migraphe status [--check]`, `migraphe up`, `migraphe down`, `migraphe amend`, `migraphe rebuild`, `migraphe validate`, `migraphe generate [--name <name>]`

## Instructions for Claude

0. **Before touching drift, fingerprints, `amend`, `rebuild`, orphans, or the history's `dependencies` / `plugin_metadata` columns, read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — "Drift and its repair" in full.** It states what is built and why, not how it got there.
0a. **When a discussion settles something, edit the record in place — never append a correction.** A "superseded" note leaves the old text implementable by whoever reads that passage next. Rewrite the passage itself, then grep the retired claim's own words across `docs/`, `CLAUDE.md`, the memory directory and `.claude/skills/` — a phrase that reached one file usually reached three. **A limit a change removes must be retracted as deliberately as a decision it reverses.**
0a1. **A decision is not recorded until it is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).** Commit
messages carried the full reasoning for several decisions in this repo — why `no_way_back:` exists, why
`history.target` has no fallback, why an unresolved dependency loads while a cycle does not — and none
of it was in the design file. An audit reported all three as "no design record
anywhere", which was false and cost a correction. **Before calling anything unjustified, run
`git log -S '<the identifier>' --reverse` and read the message.** When you settle something, write it
into the design file in the same change — a message nobody greps is not a record.
0b. **Check a design claim against the code before acting on it.** When a passage enumerates parts, verify each separately: a partial implementation reads as complete unless counted. When it depends on a data shape, check nothing else changed that shape. Two audits and I compared the code against the *columns* rather than against the *commands' stated meaning*, and missed defects for months.
1. **When in doubt, ask — `AskUserQuestion`, not a guess.** If two readings would lead to materially different work — which design was already settled, what is in scope, which of two remedies applies — ask. Guessing has repeatedly re-proposed rejected designs and cost a correction. Do not ask what the code, [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), or this file already answers.
2. **CLAUDE.md language**: This file must always be in English — no exceptions, ever.
3. **Keep CLAUDE.md compact**: Maintain brevity. Use tables, bullet points, concise descriptions. No verbose prose. Record new design-decision *detail* in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and keep only a one-line summary (+ link) here.
4. **Respond in Japanese**: All user-facing output must be in Japanese. Internal reasoning may be in English.
5. **Changelog maintenance**: Append every new session record to [docs/CHANGELOG.md](docs/CHANGELOG.md) (full history lives there). In CLAUDE.md keep only the latest session as a short summary + link.
6. **Subagent delegation**: Delegate broad exploration to `Explore`, and independent judgment (e.g. `cycle-verifier`) to a fresh context. Do *not* delegate work that reads the same few files repeatedly — TDD cycles run in the main context (see `/tdd-cycle`). Do not duplicate subagent research.
7. **LSP first**: For Java symbol lookup (definitions, references, hover), prefer the `LSP` tool over `Read`/`Grep`. Note that subagents do not have it — another reason to keep file-level work in the main context.
8. **Large output**: Commands producing many lines — always limit with `sed -n 'X,Yp'`, `grep -n pattern | head -N`, or `wc -l`. Never consume full large output in main context.

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

## Design Principles

2. **KISS**: Simple and focused
3. **SRP**: Task separated from Node
4. **Interface Segregation**: Small, focused interfaces
5. **Dependency Inversion**: Depend on interfaces
6. **Immutability**: Records and immutable collections
7. **Null Safety**: `@Nullable` (jspecify) + NullAway (compile-time checks), `Optional` only for SmallRye `@ConfigMapping`
8. **Type Safety**: Sealed interfaces, pattern matching

## Session End Procedure

Pre-commit / session-end steps (incl. CLAUDE.md / CHANGELOG.md / ARCHITECTURE.md routing, user-doc updates, and version-bump rules): run the `migraphe-session-end` skill.


---
## Where things are written down

| what | where |
|---|---|
| design decisions, including drift, fingerprints, `amend`, `rebuild`, orphans | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — the numbered entries, then "Drift and its repair" |
| what a plugin author implements | [docs/PLUGIN_DEVELOPMENT.md](docs/PLUGIN_DEVELOPMENT.md) + `.ja.md` |
| session records, what changed when | [docs/CHANGELOG.md](docs/CHANGELOG.md) |
| what the user runs | [docs/USER_GUIDE.md](docs/USER_GUIDE.md) + `.ja.md` |

This file holds **norms only** — how to work in this repo. It does not track progress, list defects,
or record what a past session did. Those go in the files above, and keeping them out of here is what
stops the norms from being buried.
