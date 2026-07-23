# Migraphe - Project Documentation for Claude

> **IMPORTANT**: This file MUST always be written in English. Never translate it to Japanese or any other language, even partially. This rule is permanent.

## Overview

DAG-based migration orchestration tool for database/infrastructure migrations across multiple environments.

**Tech Stack**: Java 21, Gradle 9.5.1 (Kotlin DSL), MicroProfile Config + SmallRye (YAML), JUnit 5 + AssertJ, Spotless, jspecify + NullAway
**Current Phase**: 22 (JitPack distribution) - COMPLETE; latest work: Mermaid ER-diagram output in the Markdown generators, with `er-diagram` (default true) and `er-diagram-keys-only` (default false) options (Session 61–62)
**Tests**: 968, 100% passing

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
15. **Generator Plugin System (Phase 18)**: Generator SPI in `migraphe-api`; `JdbcSchemaInfoProvider`, `JdbcMarkdownPlugin`, `GeneratorRegistry`/`GeneratorExecutor`. Markdown output embeds a Mermaid ER diagram in `index.md` (YAML `er-diagram`, default true; `er-diagram-keys-only`, default false = all columns / true = PK+FK columns only; tables=entities, FKs=`||--o{`) — see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
16. **Generator SPI Refactor — Source/Output Separation (Phase 19)**: `GeneratorSourcePlugin<T>` (data) decoupled from `GeneratorOutputPlugin` (render); `SourceContext`/`OutputContext`; JSON output module
17. **CLI Maven Resolver (Phase 20)**: `plugins:` Maven coordinates resolved via Maven Resolver from `~/.m2` + Central into a URLClassLoader
18. **PostgreSQL Generator Plugins**: `postgresql-schema` source (pg_catalog extras) + `postgresql-markdown` output via Template Method hooks
19. **MySQL Generator Plugins**: `mysql-schema` source (catalog-based, information_schema) + `mysql-markdown` output via the same Template Method pattern
20. **JitPack + Lockfile Pinning (Phase 21)**: `repositories:` (HTTPS-only), `migraphe.lock.yaml` SHA-256 pinning via `migraphe pin`/`--check`/`validate`
21. **JitPack Distribution (Phase 22)**: JitPack is the primary distribution channel until Maven Central; `-PpublishGroup` switch, plugin-marker workaround, tag-based user docs
22. **SQL Statement Splitting (Session 55)**: parser-combinator toolkit in `migraphe-plugin-jdbc` (`...jdbc.statement`); dialect grammars defined per-plugin (PostgreSQL dollar-quote, no keyword blocks; MySQL recursive BEGIN/END blocks + DELIMITER); unified split-and-loop execution in both autocommit/transaction modes; old `SqlStatements` removed. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

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

### 2026-07-22 (Session 62)
- Added a `er-diagram-keys-only` option to the Markdown ER diagram (Session 61 feature). `JdbcMarkdownDefinition.erDiagramKeysOnly()` (boolean, `@WithDefault false`; YAML key `er-diagram-keys-only`, kebab-case per SmallRye `@ConfigMapping`, same as `output-dir`): when `true` each entity lists only PK/FK columns (relationships unaffected); default `false` shows all columns. jdbc/postgresql/mysql generators grew a 5-arg constructor (3/4-arg retained as delegating overloads); each plugin wires `definition.erDiagramKeysOnly()`. 2 tests added; all modules green, clean build + ErrorProne/NullAway clean. Also fixed a USER_GUIDE (en/ja) doc bug: the ER-diagram YAML key was written camelCase (`erDiagram`) but the kebab-case config mapping means only `er-diagram` is honored. Verified end-to-end in the `wrs-japan` project by overlaying locally-built `migraphe-plugin-jdbc`/`-postgresql` jars into the `~/.m2` JitPack cache (`com.github.kakusuke.migraphe:…:v0.4.2`), re-pinning `migraphe.lock.yaml`, and regenerating schema docs against a live DB. Note: some Session 61 edits (identifier sanitization, PG/MySQL wiring) had not actually persisted to disk due to a tool-output fault; this was caught by `clean build` and re-applied via direct edits with grep-verified persistence. See [docs/CHANGELOG.md](docs/CHANGELOG.md).

---

**Last Updated**: 2026-07-22
**Current Work**: Markdown ER diagram gained an `er-diagram-keys-only` option (default `false` = all columns; `true` = PK/FK columns only) across `jdbc`/`postgresql`/`mysql-markdown`; fixed the ER-diagram YAML key docs to kebab-case (`er-diagram`), and verified the feature end-to-end in the wrs-japan project via `~/.m2` jar overlay + lockfile re-pin (Session 62). See [docs/CHANGELOG.md](docs/CHANGELOG.md) for details.
