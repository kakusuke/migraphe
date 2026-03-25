# Migraphe - Project Documentation for Claude

> **IMPORTANT**: This file MUST always be written in English. Never translate it to Japanese or any other language, even partially. This rule is permanent.

## Overview

DAG-based migration orchestration tool for database/infrastructure migrations across multiple environments.

**Tech Stack**: Java 21, Gradle 8.5 (Kotlin DSL), MicroProfile Config + SmallRye (YAML), JUnit 5 + AssertJ, Spotless, jspecify + NullAway
**Current Phase**: 16 (Virtual Threads Parallel Execution) - COMPLETE
**Tests**: 431, 100% passing

## Module Structure

```
migraphe-api/              # Lightweight interfaces (no external deps) - for plugin developers
migraphe-core/             # Orchestration logic, algorithms, config loading, factories
migraphe-plugin-postgresql/ # PostgreSQL plugin (Environment, MigrationNode, HistoryRepository)
migraphe-cli/              # CLI entry point, commands, console output
migraphe-gradle-plugin/    # Gradle plugin (migrapheUp/Down/Status/Validate tasks)
```

## Core Interfaces (Plugins implement these)

- `MigrationNode` - Node structure + metadata, provides `upTask()`/`downTask()`
- `Environment` - Environment configuration
- `Task` - Execution logic (up/down)
- `HistoryRepository` - Execution history persistence

## Package Structure

```
io.github.kakusuke.migraphe.api/
├── environment/    # Environment, EnvironmentId
├── graph/          # MigrationNode (interface), NodeId
├── task/           # Task, TaskResult, ExecutionDirection
├── history/        # HistoryRepository (interface), ExecutionRecord, ExecutionStatus
├── execution/      # ExecutionListener, ExecutionPlanInfo, ExecutionSummary
├── common/         # Result, ValidationResult
└── spi/            # MigraphePlugin, EnvironmentProvider, MigrationNodeProvider, HistoryRepositoryProvider, TaskDefinition, EnvironmentDefinition

io.github.kakusuke.migraphe.core/
├── graph/          # MigrationGraph, ExecutionPlan, ExecutionLevel, TopologicalSort, FormatUtils
│   └── layout/     # ExecutionGraphView, LayoutSort, LayoutOrder, LayoutTree, LayoutStream, NonTreeEdge, Cell, GridCanvas, NodeLineInfo, GraphVisualizer
├── execution/      # MigrationExecutor, RollbackExecutor, StatusService, ExecutionResult, ExecutionContext
│                   # + ParallelMigrationExecutor, ReadyNodeTracker, SynchronizedExecutionListener, Executor (interface)
├── history/        # InMemoryHistoryRepository, SynchronizedHistoryRepository
├── config/         # ProjectConfig, TargetConfig, TaskConfig, ConfigLoader, ConfigValidator, YamlFileScanner
├── factory/        # EnvironmentFactory, MigrationNodeFactory (generic, uses PluginRegistry)
├── plugin/         # PluginRegistry, PluginLoadException
├── plugin/         # SimpleMigrationNode, SimpleEnvironment, SimpleTask (reference impl)
└── plugin/noop/    # NoopPlugin + providers (type="noop", InMemory history, noop execution)

io.github.kakusuke.migraphe.postgresql/
├── PostgreSQL{Environment,MigrationNode,UpTask,DownTask,HistoryRepository}.java
├── PostgreSQLPlugin.java, PostgreSQL{Environment,MigrationNode,HistoryRepository}Provider.java
└── META-INF/services/io.github.kakusuke.migraphe.api.spi.MigraphePlugin

io.github.kakusuke.migraphe.cli/
├── Main.java
├── command/        # Command, UpCommand, DownCommand, StatusCommand, ValidateCommand
├── listener/       # ConsoleExecutionListener
└── util/           # AnsiColor

io.github.kakusuke.migraphe.gradle/
├── MigrapheGradlePlugin.java     # Plugin entry point
├── MigrapheExtension.java        # DSL extension (baseDir)
├── AbstractMigrapheTask.java     # Base task (PluginRegistry, ExecutionContext)
├── Migraphe{Up,Down,Status,Validate}Task.java  # Gradle tasks
└── GradleExecutionListener.java  # Gradle Logger-based listener
```

## Key Design Decisions

1. **Task Separation**: MigrationNode (structure) vs Task (execution logic)
2. **Up/Down Migrations**: `upTask()` for forward, `downTask()` for rollback
3. **HistoryRepository**: Pluggable persistence (InMemory, PostgreSQL, etc.)
4. **DOWN Task Serialization**: Plain text SQL stored in ExecutionRecord
5. **MicroProfile Config**: YAML with `@ConfigMapping`, automatic `${VAR}` expansion
6. **Multi-file Configuration**: `migraphe.yaml`, `targets/*.yaml`, `tasks/**/*.yaml`, `environments/*.yaml`
7. **Auto Task ID**: Generated from file path (e.g., `tasks/db1/create.yaml` → `"db1/create"`)
8. **Plugin System (Phase 11)**: ServiceLoader + URLClassLoader for runtime loading
9. **Listener Pattern (Phase 14)**: Business logic (Core) separated from presentation (CLI/Gradle). `ExecutionListener` for progress notifications, `ExecutionGraphView` for graph rendering with `toString()`
10. **Gradle Plugin (Phase 15)**: `java-gradle-plugin` + Gradle TestKit. Custom `migraphePlugin` configuration for plugin JARs. `@Option` + `-P` property for task arguments. `PluginRegistry.loadFromClassLoader()` for Gradle's classloader
11. **Shared Logic**: `ExecutionContext.createHistoryRepository()`, `ExecutionPlan.filterNodesInOrder()`, `ExecutionGraphView.renderLines()`, `FormatUtils`
12. **DAG Stream Layout Pipeline (Phase 15)**: `MigrationGraph → LayoutSort → LayoutTree → GridCanvas → ExecutionGraphView`. LayoutSort uses Kahn's with comparator (-inDegree, -outDegree, id asc). LayoutTree decomposes DAG into stream tree (greedy chain extension). GridCanvas places streams on 2D grid with `Cell` sealed interface (13 variants), `addNonTreeEdge()` with lane routing, merge row reuse, and crossing detection. Grid extracted as inner class with Cell connectivity methods (`connectsUp()`, `connectsDown()`, etc.)
13. **Parallel Execution (Phase 16)**: Opt-in via `execution.parallel: true`. `ParallelMigrationExecutor` uses Virtual Threads + `PriorityBlockingQueue` + `ReadyNodeTracker` (ready-based approach). Fail-fast on failure. `Semaphore` for `execution.max-parallelism`. `SynchronizedHistoryRepository`/`SynchronizedExecutionListener` decorators for thread safety. `Executor` interface shared by sequential/parallel.

## CLI Project Structure

```
project/
├── migraphe.yaml        # project.name, history.target
├── targets/*.yaml       # type, jdbc_url, username, password (flat structure)
├── tasks/**/*.yaml      # name, target, dependencies, up, down, autocommit (flat structure)
└── environments/*.yaml  # Environment-specific overrides
```

Commands: `migraphe status`, `migraphe up`, `migraphe down`, `migraphe validate`

## Instructions for Claude

1. **CLAUDE.md language**: This file must always be in English — no exceptions, ever.
2. **Keep CLAUDE.md compact**: Maintain brevity. Use tables, bullet points, concise descriptions. No verbose prose.
3. **Respond in Japanese**: All user-facing output must be in Japanese. Internal reasoning may be in English.
4. **Changelog maintenance**: Keep only the last 2-3 sessions. Remove older entries to prevent file bloat.
5. **Subagent delegation**: Main agent = orchestrator. Delegate broad exploration to `Explore` subagent; use `Glob`/`Grep`/`Read` only for targeted lookup of known locations. Do not duplicate subagent research.
6. **jdtls-lsp first**: For Java symbol lookup (class/method definitions, cross-references), prefer jdtls-lsp tools over `Read`/`Grep`.
7. **Large output**: Commands producing many lines — always limit with `sed -n 'X,Yp'`, `grep -n pattern | head -N`, or `wc -l`. Never consume full large output in main context.

## Development Process

### TDD — MANDATORY

**Every code change MUST go through the `/tdd-cycle` skill, one cycle at a time.**

The `/tdd-cycle` skill runs: `micro-plan → test-writer → minimal-fix → regression-guard → tidy-after-green`

Call it repeatedly to advance implementation incrementally. Never write production code outside this cycle.

| Phase | Rule |
|-------|------|
| **Red** | Write a failing test first. No production code yet. |
| **Green** | Write the minimum code to make the test pass. |
| **Refactor** | Remove duplication, improve readability. Tests must stay green. Never skip this phase. |

All tests MUST pass at 100% before committing.

### Build Commands

```bash
./gradlew build          # Build
./gradlew test           # Run tests
./gradlew spotlessApply  # Format (MANDATORY before commit)
```

### Documentation — MANDATORY

Update when code changes:
- `README.md`, `README.ja.md` — Project overview
- `docs/USER_GUIDE.md`, `docs/USER_GUIDE.ja.md` — Detailed usage

## Implementation Status

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

### Future Phases

- GraalVM Native Image packaging
- Additional database plugins (MySQL, MongoDB)
- Gradle configuration cache support

## Design Principles

1. **KISS**: Simple and focused
2. **SRP**: Task separated from Node
3. **Interface Segregation**: Small, focused interfaces
4. **Dependency Inversion**: Depend on interfaces
5. **Immutability**: Records and immutable collections
6. **Null Safety**: `@Nullable` (jspecify) + NullAway (compile-time checks), `Optional` only for SmallRye `@ConfigMapping`
7. **Type Safety**: Sealed interfaces, pattern matching

## Session End Procedure

1. Update `CLAUDE.md` (English only) with design decisions and progress
2. Update user-facing docs (`README*.md`, `USER_GUIDE*.md`)
3. Ensure all tests pass (100%)
4. Run `./gradlew spotlessApply`
5. Commit if working on a feature

---

## Changelog

### 2026-03-25 (Session 31)
- **Phase 16: Virtual Threads parallel execution**
  - Step 0: Package refactor — layout classes to `core/graph/layout/`
  - New classes: `ReadyNodeTracker`, `ParallelMigrationExecutor`, `SynchronizedHistoryRepository`, `SynchronizedExecutionListener`, `Executor` interface
  - `ProjectConfig.ExecutionSection` with `parallel` and `maxParallelism` settings
  - Config-based dispatch in CLI `UpCommand` and Gradle `MigrapheUpTask`
  - Semaphore-based concurrency limiting
  - Tests: 431 (core 331, cli 44, gradle 15, postgresql 41), 100% passing

---

**Last Updated**: 2026-03-25
**Current Work**: Phase 16 complete. Virtual Threads parallel execution with ready-based approach.
