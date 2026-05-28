# Migraphe - Project Documentation for Claude

> **IMPORTANT**: This file MUST always be written in English. Never translate it to Japanese or any other language, even partially. This rule is permanent.

## Overview

DAG-based migration orchestration tool for database/infrastructure migrations across multiple environments.

**Tech Stack**: Java 21, Gradle 8.5 (Kotlin DSL), MicroProfile Config + SmallRye (YAML), JUnit 5 + AssertJ, Spotless, jspecify + NullAway
**Current Phase**: 21 (JitPack support + SHA-256 lockfile pinning) - COMPLETE
**Tests**: 796, 100% passing

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
├── execution/      # MigrationExecutor, RollbackExecutor, StatusService, ExecutionResult, ExecutionContext
│                   # + ParallelMigrationExecutor, ReadyNodeTracker, SynchronizedExecutionListener, Executor (interface)
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
├── JdbcEnvironmentDefinition, SqlTaskDefinition, SqlStatements, JdbcException
├── schema/         # JdbcSchemaInfo, JdbcSchemaDetail, JdbcTableInfo, JdbcViewInfo, JdbcColumnInfo, etc. (19 types)
│                   # JdbcSchemaInfoProvider (DatabaseMetaData → JdbcSchemaInfo)
├── markdown/       # JdbcMarkdownPlugin (type="jdbc-markdown"), JdbcMarkdownGenerator, JdbcMarkdownDefinition
└── META-INF/services/ # MigraphePlugin + GeneratorSourcePlugin + GeneratorOutputPlugin

io.github.kakusuke.migraphe.postgresql/
├── PostgreSQLEnvironment (extends JdbcEnvironment), PostgreSQLException (extends JdbcException)
├── PostgreSQLPlugin, PostgreSQL{Environment,MigrationNode,HistoryRepository}Provider
├── PostgreSQLEnvironmentDefinition
├── schema/         # PostgreSQLSchemaInfo, PostgreSQLSchemaInfoProvider (delegates JDBC base + pg_catalog extras)
│                   # PG-specific: extensions, enums, sequences, functions, triggers, materialized views, partitions, policies
├── markdown/       # PostgreSQLMarkdownPlugin (type="postgresql-markdown"), PostgreSQLMarkdownGenerator (extends JdbcMarkdownGenerator)
└── META-INF/services/ # MigraphePlugin + GeneratorSourcePlugin + GeneratorOutputPlugin

io.github.kakusuke.migraphe.mysql/
├── MySQLEnvironment (extends JdbcEnvironment), MySQLException (extends JdbcException)
├── MySQLPlugin, MySQL{Environment,MigrationNode,HistoryRepository}Provider
├── MySQLEnvironmentDefinition
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

1. **Task Separation**: MigrationNode (structure) vs Task (execution logic)
2. **Up/Down Migrations**: `upTask()` for forward, `downTask()` for rollback
3. **HistoryRepository**: Pluggable persistence (InMemory, JDBC/PostgreSQL/MySQL, etc.)
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
14. **JDBC Plugin Extraction (Phase 17)**: Generic `migraphe-plugin-jdbc` module extracts common JDBC logic (connection, SQL execution, history). DB-specific plugins (`postgresql`, `mysql`) extend `JdbcEnvironment` with fixed driver/label and provide optimized DDL. `SqlStatements` utility for SQL splitting. `JdbcPlugin` (type="jdbc") works standalone for any JDBC database.
15. **Generator Plugin System (Phase 18)**: Generator SPI in `migraphe-api` (`io.github.kakusuke.migraphe.api.generator`). `SchemaInfoProvider<T>` on `MigraphePlugin` for schema extraction. `JdbcSchemaInfoProvider` uses `DatabaseMetaData` → `JdbcSchemaInfo` (19 record types). `JdbcMarkdownPlugin` (type="jdbc-markdown") generates Markdown docs with directory structure, cross-references, and exclude filtering. `GeneratorRegistry` + `GeneratorExecutor` in core. `GenerateCommand` in CLI (`migraphe generate --name`). `MigrapheGenerateTask` in Gradle plugin.
16. **Generator SPI Refactor — Source/Output Separation (Phase 19)**: Data extraction decoupled from rendering. `GeneratorSourcePlugin<T>` extracts typed data (`jdbc-schema` → `JdbcSchemaInfo`, `migration-tree` → `MigrationGraphView`). `GeneratorOutputPlugin` renders data (`jdbc-markdown`, `output-json`). Same data source can output in multiple formats. Legacy `GeneratorPlugin`/`Generator` interfaces removed; `migraphe-generator-api` module merged into `migraphe-api` (`io.github.kakusuke.migraphe.api.generator`). `MigrationGraphView` read-only interface in `migraphe-api`. `SourceContext` (nullable Environment + nullable graph). `OutputContext` (definition + outputDir). `GeneratorExecutor.executeAll()` auto-routes based on `source.type` presence. `ProjectConfig.SourceSection` with `Optional<String> type()`. `MigrationTreeSourcePlugin` built into core. `migraphe-plugin-generator-json` module for JSON stdout output via Jackson.
18. **PostgreSQL Generator Plugins**: `PostgreSQLSchemaInfoProvider` (source type=`postgresql-schema`) delegates to `JdbcSchemaInfoProvider` for base JDBC schema, then queries `pg_catalog` for PG-specific objects (extensions, enums, sequences, functions, triggers, materialized views, partitions, policies). `PostgreSQLMarkdownPlugin` (output type=`postgresql-markdown`) extends `JdbcMarkdownGenerator` via Template Method pattern — protected hooks `appendIndexHeader()`, `appendSchemaIndexSections()`, `appendTableSections()` allow DB-specific content injection. Table files include related triggers, policies, and partition info.
19. **MySQL Generator Plugins**: `MySQLSchemaInfoProvider` (source type=`mysql-schema`) uses catalog-based schema discovery (`connection.getCatalog()` + `meta.getTables(catalog, null, ...)`) because MySQL JDBC returns databases as catalogs, not schemas. Queries `information_schema` for MySQL-specific objects (storage engines, table meta/ENGINE/collation, triggers, routines, events, partitions). 7 record types + `MySQLSchemaInfo implements JdbcSchemaInfo`. `MySQLMarkdownPlugin` (output type=`mysql-markdown`) extends `JdbcMarkdownGenerator` with same Template Method pattern as PostgreSQL.
17. **CLI Maven Resolver (Phase 20)**: `migraphe.yaml` `plugins:` section declares Maven coordinates. `PluginConfigPreParser` (SnakeYAML) pre-parses before SmallRye Config. `MavenPluginResolver` (Maven Resolver 1.9.22 + maven-resolver-provider 3.9.9) resolves artifacts + transitive deps from `~/.m2` + Maven Central. `PluginResolver` orchestrates: YAML → resolve → URLClassLoader. `Main.java` passes classloader to `PluginRegistry` and `GeneratorRegistry`. `plugins/` directory still supported for backward compat. `DefaultServiceLocator` pattern (deprecated but functional). `session.setSystemProperties(System.getProperties())` required for POM profile activation.
20. **JitPack + Lockfile Pinning (Phase 21)**: `migraphe.yaml` gains `repositories:` (HTTPS-only) for additional Maven repos (e.g., `https://jitpack.io`); plugins reference them per-entry via map form `{coordinate, repository: <id>}`. `RepositoryConfig` / `RepositoryRegistry` (with implicit `maven-central`); `RepositoryConfig.testOnly` allows `file://` URLs for IT only. `migraphe.lock.yaml` (lockfile-version 1) pins each plugin and its transitive deps by SHA-256 — generated by `migraphe pin`, verified by `migraphe pin --check` and `migraphe validate`. `LockFileReader` / `LockFileWriter` (SnakeYAML BLOCK + header comment), `LockFileBuilder` (resolved groups → LockFile), `LockSyncChecker` (yaml ↔ lock GA/version drift), `PluginIntegrityVerifier` (SHA-256 verify), all integrated into `PluginResolver.resolve(baseDir)`. Common parent `PluginResolutionException` lets `Main.handleException` suppress stack traces. Lockfile is mandatory whenever `plugins:` is non-empty (no escape hatch). `MavenPluginResolver.resolveGroups` separates root from transitive deps for accurate per-plugin pinning. End-to-end IT (`PluginResolverIntegrationTest`) uses a `file://` `@TempDir` repo to mimic JitPack and exercise all four failure modes (match / missing / out-of-sync / tampered). **Lockfile schema deliberately omits per-plugin `repository:`** — repository selection lives only in `migraphe.yaml`, and SHA-256 is the sole authority for byte identity. Recording provenance in the lockfile would be misleading because Aether's local cache (`~/.m2`) is transparent: a JAR fetched into the cache by another project (e.g., via JitPack) would be served to `migraphe pin` without any remote lookup, so the lockfile would mirror the declaration's claimed source rather than the true origin. `LockFileReader` ignores any legacy `repository:` key for backward compat with lockfiles written by earlier Phase 21 builds.
21. **JitPack Distribution (Phase 22)**: Migraphe artefacts (plugin JARs + Gradle plugin) are published via JitPack as the **primary distribution channel** until Maven Central is live. `jitpack.yml` (JDK 21 via SDKMAN, `install:` step with `-PpublishGroup=com.github.kakusuke.migraphe publishToMavenLocal`) drives the build. `build.gradle.kts:9-16` switches `allprojects.group` to a property-driven `providers.gradleProperty("publishGroup").getOrElse("io.github.kakusuke.migraphe")` so that local `publishToMavenLocal` keeps the Maven-Central-compatible default while JitPack builds emit `com.github.kakusuke.migraphe`. Both groupIds coexist in `~/.m2/`; Java packages remain `io.github.kakusuke.migraphe.*` independent of Maven coordinate. **Gradle plugin id resolution**: the auto-generated plugin marker artifact uses the plugin id's group (`io.github.kakusuke.migraphe`) which is not served by JitPack — so end-user `settings.gradle.kts` must use `pluginManagement.resolutionStrategy.eachPlugin { ... useModule("com.github.kakusuke.migraphe:migraphe-gradle-plugin:${requested.version}") }` to bypass the marker. **End-user docs (`README*.md`, `docs/USER_GUIDE*.md`, `sample/*`) advertise JitPack coordinates with a stable git tag** (`com.github.kakusuke.migraphe:<module>:v0.3.0`); `main-SNAPSHOT` is deliberately avoided in user-facing docs because the current `LockSyncChecker` rejects yaml=`main-SNAPSHOT` against JitPack's resolved-version lockfile entries (`main-<tag>-<commit>-<n>`). When Maven Central distribution lands these will be rewritten in one pass to `io.github.kakusuke.migraphe:<module>:X.Y.Z`. `CONTRIBUTING.md` carries only operational notes (tag-vs-SNAPSHOT trade-offs, SHA-256 instability on every main push, JitPack cache refresh, local `-PpublishGroup` switch).

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
./gradlew clean build --warning-mode all 2>&1 | grep 警告  # ErrorProne check (MANDATORY before commit)
```

### ErrorProne Warnings — MANDATORY

All ErrorProne warnings must be fixed by modifying source code. **Never use `@SuppressWarnings`** without explicit user permission.

| Warning | Fix |
|---------|-----|
| `MissingOverride` | Add `@Override` annotation |
| `UnusedVariable` / `ModifiedButNotUsed` | Remove unused variable and imports |
| `StringSplitter` | `split(regex)` → `split(regex, -1)` |
| `DefaultCharset` | Specify `StandardCharsets.UTF_8` explicitly |
| `StringCaseLocaleUsage` | `toUpperCase()` → `toUpperCase(Locale.ROOT)` |
| Other warnings | Fix root cause per warning message |

Verify with: `./gradlew clean build --warning-mode all 2>&1 | grep 警告`

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
| 17 | JDBC plugin extraction + MySQL plugin | ✅ |
| 18 | Generator plugin system + JDBC Markdown docs | ✅ |
| 19 | Generator SPI refactor (source/output separation) + JSON output | ✅ |
| 20 | CLI Maven Resolver — plugin dependency resolution | ✅ |
| 21 | JitPack support + SHA-256 lockfile pinning + `migraphe pin` | ✅ |
| 22 | JitPack distribution (primary channel until Maven Central) for Migraphe itself | ✅ |

### Future Phases

- Additional database plugins (MongoDB, etc.)
- DB-specific generator plugins (MySQL HTML, JDBC TypeScript)

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

**Version bumps**: when changing the version in docs/samples, **always bump `gradle.properties` in the same change** — it is the canonical version that drives the built artifacts (the doc/sample/JitPack-tag edits are cosmetic without it). See the full Release procedure in [CONTRIBUTING.md](CONTRIBUTING.md#release-procedure).

---

## Changelog

### 2026-05-28 (Session 52)
- **`project.scan-root`: tasks/targets/environments/plugins の親ディレクトリを `migraphe.yaml` 直下から切り離せるように**
  - **Motivation**: ユーザーから「`migraphe.yaml` 本体は repo ルートに置きつつ、`tasks/`, `targets/`, `environments/`, `plugins/` をサブディレクトリにまとめたい」という要望。これまでは `migraphe.yaml` の親 (`baseDir`) 直下に全部置く前提だった。
  - **設計**: `migraphe.yaml` の `project.scan-root` (Optional<String>) を 1 つ追加すれば、その値が tasks/targets/environments/plugins の探索起点を一括で切り替える。値は `migraphe.yaml` の親ディレクトリ起点の相対パス、もしくは絶対パス。未指定なら従来通り `baseDir` と同じ (= 完全な後方互換)。**命名は TypeScript の `rootDir` 由来ではなく Liquibase の `searchPath` 系の "scan の起点" を直接表現する `scan-root` を採用** — ユーザーが `config-dir` / `root-dir` 両案にしっくり来ず、Liquibase / Flyway / Cargo の慣習を見直した上で再選択した経緯あり。
  - **CLI と Gradle で挙動を完全一致させる**: 両方とも同じ `migraphe.yaml` フィールド経由でのみ指定 (Gradle DSL に新規プロパティは追加しない)。`MigrapheExtension.baseDir` は引き続き `migraphe.yaml` の親、`ConfigLoader` 内部で `scan-root` を解決する。
  - **実装**:
    - `ProjectConfig.ProjectSection.scanRoot()` (Optional<String>) を追加
    - `ConfigLoader.resolveScanRoot(baseDir, projectConfigFile)` を private helper として導入し、`loadConfig` 内で `scanRoot` を解決。targets/tasks/environments の `YamlFileScanner` 呼出 + `TaskIdGenerator.generateTaskId` の baseDir を全て scanRoot に統一
    - `ConfigLoader.resolveScanRoot(baseDir)` を public ラッパーとして公開 (Gradle plugin / `ExecutionContext` で再利用するため)
    - `ExecutionContext` record に `Path scanRoot` を追加し、`load()` factory で計算
    - `PluginConfigPreParser` (SnakeYAML 直読み) でも `project.scan-root` を pre-parse し、`PluginConfigParseResult.scanRoot()` で取り出せるように
    - CLI `Main.resolvePluginsDir(baseDir, parsed)` を新設し、`initializePluginRegistry` と `GenerateCommand` の plugins ディレクトリ参照を `scanRoot.resolve("plugins")` に統一
    - Gradle plugin `MigrapheGenerateTask` の `context.baseDir().resolve("plugins")` を `context.scanRoot().resolve("plugins")` に差し替え
  - **`migraphe.yaml` の二重 parse は許容**: scan-root を取り出すために `ConfigLoader.loadConfig` 内で projectConfig 限定の SmallRyeConfig を組む。`ExecutionContext.load` でも `resolveScanRoot` を再度呼ぶ。性能影響軽微で、`scan-root` キーに `${VAR}` を含める用途は想定しない (= 変数展開なしで取り出す現方式で十分)。
  - **適用対象外**: `migraphe.yaml` 本体, `migraphe.lock.yaml`, `plugins:` / `repositories:` セクション (これらは `migraphe.yaml` 内のキーなので scan-root の影響を受けない)。`generators` の `outputDir` も現状 baseDir 相対のままで scope out (将来必要なら別途設計)。
  - **テスト**: `ConfigLoaderTest` に 4 件 (scanRoot 未指定 / `scan-root: subdir` の targets / tasks / environments / 絶対パス)、`PluginConfigPreParserTest` に 1 件、`MainTest` に 2 件 (Optional あり/なし)、`ExecutionContextTest` に 1 件 (`shouldResolveScanRootFromProjectConfig`)、`MigrapheValidateTaskFunctionalTest` に 1 件 (Gradle TestKit + scan-root レイアウト) を追加。`MigrapheGenerateTaskFunctionalTest` で `subdir/plugins/` 経由でプラグインを発見できるかを直接検証する案は、Jackson 等の transitive 依存を手動コピーする必要があり TestKit では現実的でないため断念 → 代わりに `ExecutionContext.scanRoot()` の core unit test で保証する形に整理した。
  - **`PluginConfigParseResult` のシグネチャ変更**: record に第 3 コンポーネント `Optional<String> scanRoot` を追加し、既存テスト 6 箇所のコンストラクタ呼出を `, Optional.empty()` 追記で更新。compact constructor で null guard あり。
  - **E2E で発見した bug 2 件 (修正済み)**:
    - `ConfigValidator.validate(baseDir)` が `scanner.scanTargetFiles(baseDir)` / `scanner.scanTaskFiles(baseDir)` をハードコードしていたため、`migraphe validate` (CLI) / `migrapheValidate` (Gradle) で `scan-root` 配下の tasks/targets を 0 件と認識していた。`new ConfigLoader().resolveScanRoot(baseDir)` で解決した scanRoot を渡すよう修正。`ConfigValidatorTest.shouldValidateUsingScanRootForTargetsAndTasks` で回帰ガード。
    - CLI の `ValidateCommand.displayCheckResults` で「Checking targets (X files)」の件数表示が `scanner.scanTargetFiles(baseDir)` を使っており、ConfigValidator が修正されても表示だけが 0 のままだった。同じく scanRoot 経由に修正。
  - **Sample プロジェクトでの E2E 確認 (実施済み)**: `sample/cli` と `sample/gradle` を `/tmp` にコピーして `scan-root: config` レイアウトに移行し、`migraphe validate` と `./gradlew migrapheValidate` 両方で `targets 2 / tasks 19 / Validation successful.` を確認。Sample 本体は無変更。
  - Tests: 全モジュール 100% passing. `./gradlew clean build --warning-mode all` で警告ゼロ、Spotless / ErrorProne クリーン。10+ micro TDD cycles で進めた。

### 2026-05-25 (Session 51)
- **リリースアーカイブをフラット化 (PR #29) + バージョン 0.3.0 への bump + リリース手順の明文化**
  - **Archive flatten**: `migraphe-cli/build.gradle.kts` の `distTar` / `distZip` の `eachFile` を「トップ階層を `migraphe` に置換」から「トップ階層 (`migraphe-<version>/`) を丸ごと除去」に変更 (`replaceFirst(Regex("^migraphe-[^/]+/"), "")`)。結果アーカイブは `bin/` `lib/` がルート直下に並ぶ。これで mise の github バックエンドが追加オプション無しで `mise use github:kakusuke/migraphe` で取り込める (github バックエンドは展開ルート直下の `bin/` を優先探索し、起動スクリプトの `../lib` 参照も同ルートで解決)。実ビルド + 展開 + `bin/migraphe --version` で動作確認済み。
  - **Breaking note**: 素の `curl ... | tar xz` がカレントに `bin/` `lib/` を直接展開するようになった (旧: `migraphe/` ディレクトリ作成)。`README*.md` / `docs/USER_GUIDE*.md` の手動インストール手順を展開先ディレクトリ指定 (`tar xz -C` / `unzip -d`) + mise 推奨に書き換え。
  - **Version bump 0.2.1 → 0.3.0**: 0.x の破壊的変更は MINOR を上げる慣習 (Cargo/npm の `^` 互換境界) に従った。SemVer 2.0.0 §4 は `0.x` で「anything MAY change」とするのみで増分ルールは規定しないため、これは spec 要求ではなく慣習。`gradle.properties` + 全 docs/sample の `0.2.1`/`v0.2.1` を一括置換。
  - **Release procedure 明文化**: バージョン bump 時に `gradle.properties` を忘れがちな問題に対し、`CONTRIBUTING.md` に "Release procedure" セクションを新設 (gradle.properties が canonical、docs/sample は cosmetic、tag push で `release.yml` 起動) + 0.x の MINOR 扱いの注記を追加。`CLAUDE.md` の Session End Procedure にも version bump 時の `gradle.properties` 注意を追記。
  - **Doc/config-only change**: 本番 Java コードは無変更。

### 2026-05-20 (Session 50)
- **配布方針転換: JitPack をエンドユーザー向け配布チャネルに昇格 + 全 user-facing ドキュメント書き換え**
  - **Motivation**: プラグイン JAR と Gradle プラグインがまだ Maven Central に未公開のため、`README` の "publishToMavenLocal してください" 手順では `git clone` 不要でサンプルを動かしたいユーザーが詰まる。Phase 22 で JitPack 配信路は既に整っているので、Maven Central 着地までの間はこれをそのまま公式ルートにする（"contributor-only beta" 位置づけを撤回）。
  - **Version pin = 安定 git タグ (`v0.2.0`)**: 当初 `main-SNAPSHOT` を案内する設計だったが、JitPack は `main-SNAPSHOT` を `main-<tag>-<commit>-<n>` (例: `main-v0.2.0-ga997b7b-1`) のような具体バージョンに解決する一方、`LockSyncChecker` は yaml と lock のバージョン文字列リテラル一致を要求するため、エンドユーザーが `migraphe pin` 後の `migraphe validate` で同期エラーになる既知バグが存在する。回避のため、エンドユーザー向けドキュメントは安定 Git タグ (`v0.2.0`) を直接案内する形に倒した。`main-SNAPSHOT` のサポートは将来 LockSyncChecker 側の SNAPSHOT 対応バグ修正で復活させる予定（[CONTRIBUTING.md](CONTRIBUTING.md) の "Tag vs main-SNAPSHOT" セクション参照）。
  - **Doc-only change — production code に変更なし**: `build.gradle.kts` / `jitpack.yml` / 本番 Java コードは無変更。CLI の `migraphe.yaml` repositories + map 形式 plugin 宣言は Phase 21 で既に実装済みなので、書き換えはすべてドキュメントとサンプル設定で完結。
  - **Gradle plugin id の resolution 問題と対応**: `java-gradle-plugin` が自動生成する plugin marker artifact は plugin id (`io.github.kakusuke.migraphe`) の group に publish されるため、`-PpublishGroup=com.github.kakusuke.migraphe` を渡しても marker は `io.github.kakusuke.migraphe:io.github.kakusuke.migraphe.gradle.plugin` のまま → JitPack URL (`com/github/kakusuke/migraphe/...`) では取れない。回避策として `settings.gradle.kts` で `pluginManagement.resolutionStrategy.eachPlugin { useModule("com.github.kakusuke.migraphe:migraphe-gradle-plugin:${requested.version}") }` を案内し、plugin marker をバイパスして実体モジュールに直接解決させる構成にした。`sample/gradle/settings.gradle.kts`, `README*.md`, `docs/USER_GUIDE*.md` すべてこの形に統一。
  - **書き換え範囲**:
    - `README.md`, `README.ja.md` — Quick Start の `migraphe.yaml`、Run Migrations コマンド (`publishToMavenLocal` 削除)、Gradle Plugin セクション全体
    - `docs/USER_GUIDE.md`, `docs/USER_GUIDE.ja.md` — Method 1: Maven Coordinates、Project Configuration、Gradle Plugin Setup、トラブルシュート ("Failed to resolve plugin" の例)、Distribution Roadmap 表
    - `docs/PLUGIN_DEVELOPMENT.md`, `docs/PLUGIN_DEVELOPMENT.ja.md` — プラグイン開発者向け `migraphe-api` 依存の例
    - `sample/cli/migraphe.yaml` — repositories: + 全 3 プラグインを map 形式 `{coordinate, repository: jitpack}` へ
    - `sample/cli/migraphe.lock.yaml` — 削除（ユーザー環境で `migraphe pin` 実行を前提に削除 → `README.md` に手順を追加）
    - `sample/cli/README.md` — セットアップから "プラグインをローカル Maven に公開" 削除、エイリアス設定後に `migraphe pin` ステップ追加
    - `sample/gradle/build.gradle.kts` — `id(...) version "v0.2.0"`、`mavenLocal()` 削除、`maven("https://jitpack.io")` 追加、`migraphePlugin` 座標も `com.github.*` に
    - `sample/gradle/settings.gradle.kts` — `pluginManagement` に JitPack + `resolutionStrategy.eachPlugin`、`dependencyResolutionManagement` にも JitPack
    - `sample/gradle/README.md` — `publishToMavenLocal` 手順とトラブルシュートの該当箇所を JitPack 案内に
    - `CONTRIBUTING.md` — "Pre-release builds via JitPack (beta channel)" セクションを大幅縮小。重複していた導入手順は README/USER_GUIDE に集約し、コントリビューター固有の運用注意（main 毎の SHA 不安定、JitPack キャッシュリフレッシュ、`-PpublishGroup` での local publish 切替、`jitpack.yml` の役割）のみを残す
  - **暫定座標の警告は意図的に省略**: ユーザー指示により、警告ボックスや "Maven Central 着地で座標が変わる" 注意書きは付けない方針。Distribution Roadmap 表でのみ Maven Central 公開予定を示し、Maven Central 着地時に全座標を一括書き換えする前提。
  - **保留**: 実 JitPack ビルドでの sample 動作確認は未実施（初回ユーザーが `v0.2.0` を要求した時に JitPack ビルドがトリガーされる想定）。Phase 22 で `~/.m2/com/github/kakusuke/migraphe/...` への local publish 動作は既に確認済みなので、JitPack 側のビルドが通れば同じ artefact が配信される。

### 2026-05-19 (Session 49)
- **DOWN コマンド単独ノード指定時のクラッシュ修正 + `MigrationGraph` の方向別サブグラフ構築 API 整理**
  - **Bug**: `migraphe down mysql/02_catalog/003_products` のように依存先 (parents) を持つノードを単独ロールバック指定すると、`DownCommand.displayRollbackPlan` (line 147) → `new ExecutionGraphView(sortedNodes)` → 内部の `MigrationGraph.create() + addNode` で「`Dependency node does not exist: 001_categories (required by 003_products)`」と IllegalArgumentException。`RollbackExecutor.determineRollbackTargets` は `target ∪ getDependents(target)` の閉包 (= 依存"元" の集合) を返すため、依存"先" (parents) は sortedNodes に含まれず eager 検査で破綻していた。`UpCommand` 側は実行プランが依存先を必ず含むので発症しない。
  - **Root cause framing**: `MigrationGraph.addNode` の eager 検査が **UP 方向の不変条件 (「各ノードの依存先 parents がサブグラフ内に存在する」)** のみを表現しており、ロールバックの DOWN 方向 (「各ノードの依存元 dependents がサブグラフ内に存在する」) と非対称だった。さらに `addNode` の検査は挿入順序依存 (UP 順) を呼び出し側に強要する負債でもあった。
  - **Fix — 方向別サブグラフ構築 API**: `MigrationGraph` に静的ファクトリ 2 つを新設。
    - `fromNodesUp(List<MigrationNode>)`: 現在の `create() + addNode` ループと等価 (UP 方向)。
    - `fromNodesDown(List<MigrationNode>)`: **reversed adjacency** + **リスト外フィルタ**。各ノードの `dependencies()` (= parents) を子方向に反転 (`adjacencyList[parent].add(child)`) し、リスト外を指す参照は `parentAdjacency == null` 経路で自然に除外。これにより DOWN サブグラフは adjacency がリスト内に閉じ、`LayoutSort` の inDegree 計算が正しく機能する (旧実装では dangling adjacency により inDegree がズレ、layout から該当ノードが消える silent failure になっていた)。
  - **`ExecutionGraphView(List, boolean reversed)` 追加**: 既存 `(List)` コンストラクタは `(List, false)` への委譲に整理。reversed=true で `fromNodesDown`、false で `fromNodesUp` を選ぶ。`DownCommand` / `MigrapheDownTask` の呼び出し箇所を `(sortedNodes, true)` に切替。`UpCommand` / `MigrapheUpTask` は false のまま (= 既存挙動)。
  - **`MigrationGraph.getRoots()` の整合性修正**: 旧実装は `MigrationNode::hasNoDependencies` (= 元の `node.dependencies()` が空か) を直接見ていたため、DOWN グラフ (reversed adjacency) で「DOWN 視点の起点」を取れなかった。`adjacencyList.getOrDefault(id, Set.of()).isEmpty()` ベースに変更し、adjacency に追従するようにした。consumer は `GraphVisualizer` のみで現バグ経路には載らないが、将来の地雷を予防。
  - **後片付け (Sessions 8-10)**: 設計合意に基づき以下も整理。
    - **`addNode` の eager 検査削除**: `validate()` と完全に重複していた dangling-dep チェックを削除。代わりに `ExecutionContext.create` で `graph.validate()` を呼んで safety net 化 (cycle / dangling を `IllegalStateException` で surface)。`MigrationGraph.validate()` は dead 寸前だったが **production safety net として復活**。
    - **`addDependency` 削除**: production 0 caller の dead public API。test 側 4 箇所 (`TopologicalSortTest`, `MigrationGraphTest` ×3, `MigrationTreeSourcePluginTest` ×2) は `node().dependencies(...)` を使った宣言的な cycle 構築に書き換えた。
    - **`create()` / `addNode()` は保持**: `addNode` は `fromNodesUp` 内部 + テストの低レベル builder として、`create()` はテスト用 builder として多数の利用箇所があるため、API としては残す判断。
  - **Sample 動作確認**: `migraphe down mysql/02_catalog/003_products --dry-run` で例外なく 5 ノード (003 + dependents の 004, 005, product_indexes, reviews) が DOWN 順で正しく可視化されること、葉ノード単独 (`mysql/04_indexes/001_product_indexes`) の単独 down も例外なく動くこと、`down --all --dry-run` で全 19 ノードが正しい DOWN レイアウトで出ることを実 CLI で確認。
  - Tests: 798 total, 100% passing. `./gradlew clean build --warning-mode all` で警告ゼロ・Spotless / ErrorProne クリーン。10 micro TDD cycle (cycle 1-7 + 8 + 8.5 + 10) で進めた。

---

**Last Updated**: 2026-05-28
**Current Work**: `project.scan-root` の追加 (Session 52)。tasks/targets/environments/plugins の探索起点を `migraphe.yaml` 直下から `migraphe.yaml` の親ディレクトリ起点の相対パス (or 絶対パス) に切替可能にした。CLI/Gradle 両方で同一 `migraphe.yaml` フィールド経由でのみ指定。未指定時は完全な後方互換。実装は `ConfigLoader.resolveScanRoot` + `ExecutionContext.scanRoot()` accessor + `PluginConfigPreParser` 拡張 + `Main.resolvePluginsDir`。直前の Session 51 ではリリースアーカイブをフラット化 (`bin/` `lib/` をルート直下に配置) + バージョン 0.3.0 への bump を実施済み。
