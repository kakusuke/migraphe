# Migraphe - Project Documentation for Claude

## Overview

DAG-based migration orchestration tool for database/infrastructure migrations across multiple environments.

**Tech Stack**: Java 21, Gradle 8.5 (Kotlin DSL), MicroProfile Config + SmallRye (YAML), JUnit 5 + AssertJ, Spotless, jspecify + NullAway
**Current Phase**: 12 (Refactoring) - COMPLETE
**Tests**: 177+, 100% passing

## Module Structure

```
migraphe-api/       # Lightweight interfaces (no external deps) - for plugin developers
migraphe-core/      # Orchestration logic, algorithms, reference implementations
migraphe-postgresql/ # PostgreSQL plugin (Environment, MigrationNode, HistoryRepository)
migraphe-cli/       # CLI entry point, config loading, commands
```

## Core Interfaces (Plugins implement these)

- `MigrationNode` - Node structure + metadata, provides `upTask()`/`downTask()`
- `Environment` - Environment configuration
- `Task` - Execution logic (up/down)
- `HistoryRepository` - Execution history persistence

## Package Structure

```
io.github.migraphe.api/
├── environment/    # Environment, EnvironmentId
├── graph/          # MigrationNode (interface), NodeId
├── task/           # Task, TaskResult, ExecutionDirection
├── history/        # HistoryRepository (interface), ExecutionRecord, ExecutionStatus
├── common/         # Result, ValidationResult
└── spi/            # MigraphePlugin, EnvironmentProvider, MigrationNodeProvider, HistoryRepositoryProvider, TaskDefinition, EnvironmentDefinition

io.github.migraphe.core/
├── graph/          # MigrationGraph, ExecutionPlan, TopologicalSort, GraphVisualizer
├── history/        # InMemoryHistoryRepository
├── config/         # ProjectConfig, TargetConfig, TaskConfig (@ConfigMapping)
├── plugin/         # PluginRegistry, PluginLoadException
└── plugin/         # SimpleMigrationNode, SimpleEnvironment, SimpleTask (reference impl)

io.github.migraphe.postgresql/
├── PostgreSQL{Environment,MigrationNode,UpTask,DownTask,HistoryRepository}.java
├── PostgreSQLPlugin.java, PostgreSQL{Environment,MigrationNode,HistoryRepository}Provider.java
└── META-INF/services/io.github.migraphe.api.spi.MigraphePlugin

io.github.migraphe.cli/
├── Main.java, ExecutionContext.java
├── command/        # Command, UpCommand, StatusCommand
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

## CLI Project Structure

```
project/
├── migraphe.yaml        # project.name, history.target
├── targets/*.yaml       # type, jdbc_url, username, password (flat structure)
├── tasks/**/*.yaml      # name, target, dependencies, up, down, autocommit (flat structure)
└── environments/*.yaml  # Environment-specific overrides
```

Commands: `migraphe status`, `migraphe up`

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

### Future Phases
- `down`, `history`, `validate` commands
- GraalVM Native Image packaging
- Gradle plugin
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

### 2026-01-16 (Session 11)
- **Autocommit Mode**: PostgreSQL プラグインに autocommit オプション追加
  - `SqlTaskDefinition.autocommit()`: YAML で `autocommit: true` をパース
  - `PostgreSQLMigrationNode`: autocommit フィールド + Builder メソッド
  - `PostgreSQLUpTask`/`PostgreSQLDownTask`: autocommit モード分岐実装
  - `PostgreSQLMigrationNodeProvider`: SqlTaskDefinition から autocommit 読み取り
  - 統合テスト追加: shouldExecuteUpMigrationWithAutocommit, shouldExecuteDownMigrationWithAutocommit
  - ドキュメント更新: USER_GUIDE.md / USER_GUIDE.ja.md に Autocommit Mode セクション追加
- Use case: `CREATE DATABASE`, `CREATE INDEX CONCURRENTLY` など、トランザクション内で実行不可の SQL
- Tests: 183, 100% passing

### 2026-01-13 (Session 10)
- **Plugin Dynamic Loading**: migraphe-cli から migraphe-postgresql 依存を分離
  - `build.gradle.kts`: `implementation` → `testImplementation` に変更
  - 本番は `./plugins/` ディレクトリから JAR を動的ロード
  - テストはクラスパスから ServiceLoader でロード（既存テスト維持）
  - `PluginNotFoundException`: プラグイン未発見時の詳細エラーメッセージ
  - `PluginRegistry.getRequiredPlugin()`: null チェック不要の必須取得メソッド
  - ドキュメント更新: `USER_GUIDE.md`/`USER_GUIDE.ja.md` にプラグイン配置方法追加
- Tests: 180, 100% passing

### 2026-01-13 (Session 9)
- **Phase 12**: Refactoring - COMPLETED (EnvironmentDefinition, @Nullable, NullAway)
- Tests: 177+, 100% passing

---

**Last Updated**: 2026-01-16
**Phase 12 Complete** - Next: Future phases (down command, Native Image, etc.)
