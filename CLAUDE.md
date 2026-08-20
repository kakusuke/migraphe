# Migraphe - Project Documentation for Claude

> **IMPORTANT**: This file MUST always be written in English. Never translate it to Japanese or any other language, even partially. This rule is permanent.

## Overview

DAG-based migration orchestration tool for database/infrastructure migrations across multiple environments.

**Tech Stack**: Java 21, Gradle 9.5.1 (Kotlin DSL), MicroProfile Config + SmallRye (YAML), JUnit 5 + AssertJ, Spotless, jspecify + NullAway
**Current Phase**: 22 (JitPack distribution) - COMPLETE; latest work: gave the history schema an evolution path — dialect DDL resources are now lists of `--@apply` steps (with an optional `--@check` detection query) that `initialize()` walks, so existing tables can gain columns and indexes without a schema-version table (Session 70)
**Tests**: 1,085, 100% passing

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
├── history/        # HistoryRepository (interface), ExecutionRecord, ExecutionStatus
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
5. **Subagent delegation**: Main agent = orchestrator. Delegate broad exploration to `Explore` subagent; use `Glob`/`Grep`/`Read` only for targeted lookup of known locations. Do not duplicate subagent research.
6. **jdtls-lsp first**: For Java symbol lookup (class/method definitions, cross-references), prefer jdtls-lsp tools over `Read`/`Grep`.
7. **Large output**: Commands producing many lines — always limit with `sed -n 'X,Yp'`, `grep -n pattern | head -N`, or `wc -l`. Never consume full large output in main context.

## Development Process

### TDD — MANDATORY

**Every code change MUST go through the `/tdd-cycle` skill, one cycle at a time.**

The `/tdd-cycle` skill runs: `micro-plan → test-writer → minimal-fix → regression-guard → tidy-after-green`

Call it repeatedly to advance implementation incrementally. Never write production code outside this cycle.

Each phase subagent verifies through the `migraphe-build` MCP server (`mcp__migraphe-build__run_test` / `run_spotless` / `run_errorprone_check`) — the parent orchestrates and does **not** invoke `./gradlew` directly. The skill is scoped to incremental changes; net-new classes ≥200 lines or multi-file scaffolding are out of scope and route to `general-purpose` instead.

| Phase | Rule |
|-------|------|
| **Red** | Write a failing test first. No production code yet. |
| **Green** | Write the minimum code to make the test pass. |
| **Refactor** | Remove duplication, improve readability. Tests must stay green. Never skip this phase. |

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

### 2026-08-20 (Session 70)
- Gave the history table an evolution path. `initialize()` used to hand its whole dialect DDL resource to a single `stmt.execute()`, so idempotency rested on `CREATE TABLE IF NOT EXISTS` alone and **an existing table could never change shape** — editing a resource only affected fresh installs. Resources are now lists of steps: `--@apply` introduces a step's statements, an optional `--@check` before it declares a detection query, `SchemaStepParser` produces `SchemaStep` records, and `initialize()` walks them one statement at a time — skipping a step whose query returns a row, always running one without a query.
- **Detection is optional and the shipped resources use none.** Every shipped step creates an object, and `IF NOT EXISTS` expresses that conditionally in a form no other schema's same-named table can confuse. A creation-time detection query would have to answer "does this exist *here*", which the generic JDBC resource cannot: no expression names the current schema across H2, MySQL and PostgreSQL (`SCHEMA()` covers H2+MySQL but breaks `type="jdbc"` on PostgreSQL), and an unqualified `UPPER(table_name)` match would find a `migraphe_history` in another database on the same MySQL server, skip creation, and leave `INSERT` failing. So: creation leans on `IF NOT EXISTS`; **detection is reserved for changes with no portable conditional form** — chiefly `ALTER TABLE ADD COLUMN`, which Oracle MySQL cannot write as `IF NOT EXISTS`. Schema-qualifying those future queries is deferred to the PR adding the first column.
- **No schema-version table, no config key**: idempotency is structural, a partial run resumes on the next command, and a user preferring manual DDL applies it first so the step becomes a no-op. No type-change step ships (`id VARCHAR(255)` only hurts as an index key, and existing tables were created where the limit is 3072 B).
- Detection queries inspect `information_schema`/`pg_indexes` so they run before the table exists, and **their failures propagate** instead of reading as "not applied" — folding a permission error into "missing" would become a blind DDL attempt. An apply failure re-runs detection to absorb a lost race (original exception thrown with the re-check failure suppressed if that fails too); for an unconditional step the failure rightly propagates, since `IF NOT EXISTS` already absorbed the race. No dialect lock.
- All commands share the path: `up`/`down`/`status` already called `initialize()` unconditionally, so `status` has always created the table.
- PostgreSQL keeps the table and each index as separate steps: each statement runs on its own (fixing three being passed to one `execute()`), diagnostics name the failing step, and a manually dropped index self-heals on the next run.
- Backward compatible: a resource with no directive is one unconditional step, so custom plain-SQL resources passed to `JdbcHistoryRepository(env, path)` keep working; SQL before the first directive is rejected rather than silently dropped.
- Labels: either directive may name a step and whichever declares it wins, but a step declaring **two different** labels is rejected (previously `--@apply`'s label was silently ignored whenever `--@check` opened the step — so `--@check` + `--@apply B` now yields `B` instead of `step N`, and conflicting labels now fail instead of silently taking the first).
- Tests: +31 (1,085 total) across H2, MySQL 8.0, MariaDB 10.1 and PostgreSQL 16. The detection mechanism and its lost-race swallow stay covered by **test-only resources** (the swallow reproduced deterministically by applying the same `CREATE TABLE` twice); the shipped resources are covered by idempotency instead. See [docs/CHANGELOG.md](docs/CHANGELOG.md).

---

**Last Updated**: 2026-08-20
**Current Work**: History-schema evolution mechanism — dialect DDL resources became step lists (`--@apply`, optional `--@check`) walked by `initialize()`, so existing history tables can gain columns and indexes with no schema-version table, no new config key, and no special casing for `status`. Shipped steps create objects and rely on `IF NOT EXISTS`, keeping behaviour equivalent to before; detection exists for the follow-ups (#3 checksum, #5 environment identity, MariaDB ordering column), where `ALTER TABLE ADD COLUMN` has no portable conditional form. See [docs/CHANGELOG.md](docs/CHANGELOG.md).
