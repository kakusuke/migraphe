# Migraphe - Project Documentation for Claude

## Overview

DAG-based migration orchestration tool for database/infrastructure migrations across multiple environments.

**Tech Stack**: Java 21, Gradle 8.5 (Kotlin DSL), MicroProfile Config + SmallRye (YAML), JUnit 5 + AssertJ, Spotless, jspecify + NullAway
**Current Phase**: 15 (Gradle Plugin) - COMPLETE
**Tests**: 257, 100% passing

## Module Structure

```
migraphe-api/       # Lightweight interfaces (no external deps) - for plugin developers
migraphe-core/      # Orchestration logic, algorithms, config loading, factories
migraphe-plugin-postgresql/ # PostgreSQL plugin (Environment, MigrationNode, HistoryRepository)
migraphe-cli/       # CLI entry point, commands, console output
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
├── graph/          # MigrationGraph, ExecutionPlan, TopologicalSort, ExecutionGraphView, NodeLineInfo, FormatUtils
├── execution/      # MigrationExecutor, RollbackExecutor, StatusService, ExecutionResult, ExecutionContext
├── history/        # InMemoryHistoryRepository
├── config/         # ProjectConfig, TargetConfig, TaskConfig, ConfigLoader, ConfigValidator, YamlFileScanner
├── factory/        # EnvironmentFactory, MigrationNodeFactory (generic, uses PluginRegistry)
├── plugin/         # PluginRegistry, PluginLoadException
└── plugin/         # SimpleMigrationNode, SimpleEnvironment, SimpleTask (reference impl)

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
11. **Shared Logic**: `ExecutionContext.createHistoryRepository()` for HistoryRepository creation, `ExecutionPlan.filterNodesInOrder()` for DFS-order filtering, `ExecutionGraphView.renderLines()` for graph rendering loop, `FormatUtils` for duration/datetime formatting

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

1. **Keep CLAUDE.md compact**: When editing this file, maintain brevity. Avoid verbose explanations; use tables, bullet points, and concise descriptions.
2. **Think in English, respond in Japanese**: Internal reasoning should be in English for efficiency. User-facing output should be translated to Japanese.
3. **Changelog maintenance**: Keep only the last 2-3 sessions. Remove older entries to prevent file bloat.

## Development Process

### TDD (t-wada style) - MANDATORY
1. Red → Green → Refactor
2. All tests MUST pass 100%

### Build Commands
```bash
./gradlew build          # Build
./gradlew test           # Run tests
./gradlew spotlessApply  # Format (MANDATORY before commit)
```

### Documentation - MANDATORY
Update when code changes:
- `README.md`, `README.ja.md` - Project overview
- `docs/USER_GUIDE.md`, `docs/USER_GUIDE.ja.md` - Detailed usage

## Implementation Status

| Phase | Description | Status |
|-------|-------------|--------|
| 1-7 | Core (types, interfaces, graph, algorithms) | ✅ Complete |
| 8 | History abstraction + PostgreSQL plugin | ✅ Complete |
| 9 | MicroProfile Config (YAML) | ✅ Complete |
| 10 | CLI (config loading, commands) | ✅ Complete |
| 11-0 | API module separation | ✅ Complete |
| 11-1 | SPI foundation (PluginRegistry) | ✅ Complete |
| 11-2 | PostgreSQL plugin adaptation | ✅ Complete |
| 11-3 | Generic factories | ✅ Complete |
| 11-4 | ExecutionContext generalization | ✅ Complete |
| 11-5 | Command integration (HistoryRepository via plugin) | ✅ Complete |
| 11-6 | Main CLI integration | ✅ Complete |
| 11-7 | Cleanup and documentation | ✅ Complete |
| 12-1 | EnvironmentDefinition generification | ✅ Complete |
| 12-2 | @Nullable introduction (Optional removal) | ✅ Complete |
| 12-3 | NullAway compile-time checks enabled | ✅ Complete |
| 13 | Validate command | ✅ Complete |
| 14 | Core logic extraction for Gradle plugin | ✅ Complete |
| 15-0 | Shared infra CLI → Core migration | ✅ Complete |
| 15-1 | Gradle plugin module creation | ✅ Complete |
| 15-2 | Extension DSL + Plugin class | ✅ Complete |
| 15-3 | AbstractMigrapheTask + Listener | ✅ Complete |
| 15-4 | Task implementations (Up/Down/Status/Validate) | ✅ Complete |
| 15-5 | Tests (Unit + Gradle TestKit) | ✅ Complete |

### Future Phases
- `history` command
- GraalVM Native Image packaging
- Additional database plugins (MySQL, MongoDB)
- Virtual Threads for parallel execution
- Gradle configuration cache support

## Design Principles

1. **KISS**: Simple and focused
2. **SRP**: Task separated from Node
3. **Interface Segregation**: Small, focused interfaces
4. **Dependency Inversion**: Depend on interfaces
5. **Immutability**: Records and immutable collections
6. **Null Safety**: `@Nullable` (jspecify) + NullAway (compile-time checks), `Optional` only for SmallRye @ConfigMapping
7. **Type Safety**: Sealed interfaces, pattern matching

## Session End Procedure

1. Update `CLAUDE.md` with design decisions and progress
2. Update user-facing docs (`README*.md`, `USER_GUIDE*.md`)
3. Ensure all tests pass (100%)
4. Run `./gradlew spotlessApply`
5. Commit if working on a feature

---

## Changelog

### 2026-01-30 (Session 18)
- **Gradle/CLI 共通処理抽出**: 重複コードを Core に共通化
  - `ExecutionContext.createHistoryRepository()`: HistoryRepository 生成を一元化（CLI 3箇所 + Gradle 3箇所の重複を解消）
  - `ExecutionPlan.filterNodesInOrder()`: Plan ノード DFS 順フィルタを一元化（CLI 2箇所 + Gradle 2箇所）
  - `ExecutionGraphView.renderLines(Function)`: グラフ行レンダリングループを一元化（CLI Up/Down + Gradle Up/Down）
  - `FormatUtils`: Duration/DateTime フォーマットを Core に新規追加（CLI StatusCommand + Gradle MigrapheStatusTask）
  - `HistoryRepositoryHelper.java` **削除**（`ExecutionContext.createHistoryRepository()` に統合）
  - 新規テスト 8 件追加（createHistoryRepository ×2, filterNodesInOrder ×2, renderLines ×3, FormatUtils ×3 ※ネストクラス含む）
- Tests: 265+, 100% passing

### 2026-01-28 (Session 17)
- **Gradle Plugin 実装 (Phase 15)**: `migraphe-gradle-plugin` モジュール新規作成
- Tests: 257, 100% passing

---

**Last Updated**: 2026-01-30
**Shared Logic Extraction Complete** - Next: history command, Native Image, configuration cache
