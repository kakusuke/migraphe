# Migraphe - Project Documentation for Claude

## Overview

DAG-based migration orchestration tool for database/infrastructure migrations across multiple environments.

**Tech Stack**: Java 21, Gradle 8.5 (Kotlin DSL), MicroProfile Config + SmallRye (YAML), JUnit 5 + AssertJ, Spotless, jspecify + NullAway
**Current Phase**: 14 (Core Logic Extraction) - COMPLETE
**Tests**: 159+, 100% passing

## Module Structure

```
migraphe-api/       # Lightweight interfaces (no external deps) - for plugin developers
migraphe-core/      # Orchestration logic, algorithms, reference implementations
migraphe-plugin-postgresql/ # PostgreSQL plugin (Environment, MigrationNode, HistoryRepository)
migraphe-cli/       # CLI entry point, config loading, commands
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
├── graph/          # MigrationGraph, ExecutionPlan, TopologicalSort, ExecutionGraphView, NodeLineInfo
├── execution/      # MigrationExecutor, RollbackExecutor, StatusService, ExecutionResult
├── history/        # InMemoryHistoryRepository
├── config/         # ProjectConfig, TargetConfig, TaskConfig (@ConfigMapping)
├── plugin/         # PluginRegistry, PluginLoadException
└── plugin/         # SimpleMigrationNode, SimpleEnvironment, SimpleTask (reference impl)

io.github.kakusuke.migraphe.postgresql/
├── PostgreSQL{Environment,MigrationNode,UpTask,DownTask,HistoryRepository}.java
├── PostgreSQLPlugin.java, PostgreSQL{Environment,MigrationNode,HistoryRepository}Provider.java
└── META-INF/services/io.github.kakusuke.migraphe.api.spi.MigraphePlugin

io.github.kakusuke.migraphe.cli/
├── Main.java, ExecutionContext.java
├── command/        # Command, UpCommand, DownCommand, StatusCommand, ValidateCommand
├── listener/       # ConsoleExecutionListener
├── config/         # ConfigLoader, MultiFileYamlConfigSource, YamlFileScanner, TaskIdGenerator
└── factory/        # EnvironmentFactory, MigrationNodeFactory (generic, uses PluginRegistry)
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

### Future Phases
- `history` command
- GraalVM Native Image packaging
- Gradle plugin (can now use Core APIs)
- Additional database plugins (MySQL, MongoDB)
- Virtual Threads for parallel execution

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

### 2026-01-26 (Session 16)
- **Core Logic Extraction**: CLI のビジネスロジックを Core に移動（Gradle plugin 準備）
  - **Listener パターン導入**: 実行ロジック (Core) とプレゼンテーション (CLI/Gradle) の分離
  - **API モジュール追加**:
    - `ExecutionListener`: 進捗通知インターフェース
    - `ExecutionPlanInfo`, `ExecutionSummary`: 実行プラン・サマリー record
  - **Core モジュール追加**:
    - `MigrationExecutor`: UP マイグレーション実行サービス
    - `RollbackExecutor`: DOWN マイグレーション実行サービス
    - `StatusService`: ステータス取得サービス
    - `ExecutionResult`: 実行結果 record
    - `ExecutionGraphView`: git log --graph 風 ASCII グラフ表示 (toString() でプレーンテキスト)
    - `NodeLineInfo`: 各ノードの行情報 record
  - **CLI モジュール追加**:
    - `ConsoleExecutionListener`: ANSI 色付き出力
  - **リファクタリング**:
    - `UpCommand`: MigrationExecutor + ConsoleExecutionListener 使用 (381行→219行)
    - `DownCommand`: RollbackExecutor + ConsoleExecutionListener 使用 (401行→228行)
  - **削除**: 旧 `GraphRenderer` (Core の `ExecutionGraphView` に移動)
- Tests: 159+, 100% passing

### 2026-01-23 (Session 15)
- **Validate Command Implementation**: `migraphe validate` コマンド実装
- Tests: 159+, 100% passing

---

**Last Updated**: 2026-01-26
**Core Logic Extraction Complete** - Next: Gradle plugin, history command, Native Image
