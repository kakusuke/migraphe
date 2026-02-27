# Migraphe - Project Documentation for Claude

## Overview

DAG-based migration orchestration tool for database/infrastructure migrations across multiple environments.

**Tech Stack**: Java 21, Gradle 8.5 (Kotlin DSL), MicroProfile Config + SmallRye (YAML), JUnit 5 + AssertJ, Spotless, jspecify + NullAway
**Current Phase**: 15 (Gradle Plugin) - COMPLETE
**Tests**: 305, 100% passing

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
4. **Subagent delegation**: Main agent = orchestrator. Delegate broad exploration to `Explore` subagent; use direct `Glob`/`Grep`/`Read` only for targeted lookup of known file/line locations. Do not duplicate research that subagents already perform.
5. **jdtls-lsp first**: For Java symbol lookup (class/method definitions, cross-references), all agents should prefer jdtls-lsp tools over `Read`/`Grep` to save context.
6. **Large output**: Commands producing many lines (e.g., `status` on sample/) — always limit with `sed -n 'X,Yp'`, `grep -n pattern | head -N`, or `wc -l` first. Never consume full large output in main context.

## Development Process

### TDD (t-wada style) - MANDATORY
**コードを書く際は必ず `/tdd-cycle` スキルを使って1サイクルずつ進めること。**
- `/tdd-cycle` は micro-plan → test-writer → minimal-fix → regression-guard → tidy-after-green の順で1サイクルを実行する
- 繰り返し呼び出して incremental に実装を進める

1. **Red**: 失敗するテストを書く
2. **Green**: テストを通す最小限の実装
3. **Refactor**: 重複除去・可読性向上（テストが通り続けることを確認）。Green で終わらず必ずこのフェーズを実施すること
4. All tests MUST pass 100%

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

### 2026-02-26 (Session 24)
- **GraphCanvas マージ行の ┼ 誤表示バグ修正**: `status` コマンドで接続先のない `┼` が大量表示される不具合を修正
  - 原因: マージ行（ノード行 i の直前）で `laneActive[i][l]` を参照していたため、行 i から始まるレーン（ノード i が非支配木辺のソース）が誤って `┼` と表示された
  - 修正: `isLaneActiveAtRow(i, l, ...)` → `isLaneActiveAtRow(i - 1, l, ...)` に変更（1行修正）
  - 新テスト: `GraphCanvasTest.mergeRowShouldNotShowSpuriousCrossCharactersBeforeClosing`
- Tests: 306, 100% passing

### 2026-02-26 (Session 23)
- **GraphCanvas レーン割り当てバグ修正**: `status` コマンドで `┘` の右側に余計な `│` が出る不具合を修正
  - 原因: 重複 interval のグループで endRow が大きい（長い）グループが高い lane 番号を取得していた
  - 修正: `GraphCanvas.assignLanesAndInsertMergeRows()` のソート順を endRow 降順に変更 + lane 再利用条件に「上位 lane の invariant 維持チェック（condition 2）」を追加
  - 不変条件: 重複するグループ間では endRow が大きいグループが低い lane 番号を持つ
  - 新テスト: `GraphCanvasTest.mergeRowShouldNotShowExtraVerticalBarsAfterClosing`
- Tests: 305, 100% passing

### 2026-02-25 (Session 22)
- **ExecutionGraphView.java 分割完了**: 1,014行 → 54行（オーケストレーターのみ）
  - 新ファイル: `DominatorTree.java`, `GraphCanvas.java`, `NonDomEdge.java`, `BranchClassification.java`, `GroupInfo.java`
  - 新テスト: `DominatorTreeTest.java`, `GraphCanvasTest.java`, `NonDomEdgeTest.java` など
  - `GraphCanvas.render()` が全行（NodeRow + ConnectorRow + MergeRow + BlankRow）を返すよう修正
  - `ExecutionGraphView` は `DominatorTree` + `GraphCanvas` を組み合わせるだけ（54行）
  - 4件の pre-existing Red テストは引き続き `@Disabled`
- Tests: 304, 100% passing

---

**Last Updated**: 2026-02-26
**Current Work**: GraphCanvas レーン割り当てバグ修正 - COMPLETE
