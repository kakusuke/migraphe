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
├── common/         # Result, ValidationResult
├── generator/      # GeneratorSourcePlugin<T>, GeneratorOutputPlugin, GeneratorDefinition, SourceContext, OutputContext
└── spi/            # MigraphePlugin, EnvironmentProvider, MigrationNodeProvider, HistoryRepositoryProvider, TaskDefinition, EnvironmentDefinition

io.github.kakusuke.migraphe.core/
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

---

## Changelog

### 2026-04-30 (Session 46)
- **Lockfile schema simplified: per-plugin `repository:` removed** (post-Phase 21 cleanup)
  - **Motivation**: The `repository:` field in each `LockedPlugin` was a sync-check-only mirror of the user's declaration — never consulted at resolution time, never used by SHA verification. It also created a misleading provenance: when Aether's local cache (`~/.m2`) already held the JAR (e.g., another project had fetched it via JitPack), `migraphe pin` would copy the declared `repository:` value into the lockfile without ever contacting a remote, recording a "source" that didn't reflect the true origin. SHA-256 is the actual integrity guarantee; the repository slot was redundant theatre.
  - **Schema change**: `LockedPlugin` is now `(coordinate, sha256, dependencies)` — `repositoryId` field deleted along with its blank-check. `LockFileWriter` no longer emits `repository:` keys; `LockFileReader` silently accepts (and discards) the legacy key for backward compat with lockfiles produced by the original Phase 21 build.
  - **Code touched**: `LockedPlugin.java`, `LockFileWriter.java`, `LockFileReader.java`, `LockFileBuilder.java` (constant `DEFAULT_REPOSITORY_ID` removed), `LockSyncChecker.java` (repository-comparison branch deleted; only coordinate GA presence + version drift are now checked).
  - **Tests**: `LockedPluginTest.rejectsBlankRepositoryId`, `LockFileBuilderTest.usesRepositoryRefWhenPresent`, `LockSyncCheckerTest.failsWhenRepositoryRefDiffers` / `treatsMissingRepositoryRefAsMavenCentral` removed (4 tests deleted). New `LockFileReaderTest.ignoresLegacyRepositoryKeyForBackwardCompatibility` confirms old lockfiles still load. All other test fixtures dropped the `"maven-central"` constructor argument and the legacy `repository: maven-central` YAML line. Net delta: −4 + 1 = −3 tests.
  - **What `migraphe.yaml` keeps**: The `repositories: [{id, url}]` block and `plugins:` map form `{coordinate, repository: <id>}` are unchanged — repository selection at fetch time is still configurable. Only the lockfile lost its mirror copy.
  - Tests: 793 total, 100% passing. Spotless + ErrorProne clean.

### 2026-04-30 (Session 45)
- **Phase 21: JitPack + SHA-256 lockfile pinning (`migraphe pin`)**
  - **Configuration shape**: `migraphe.yaml` gains optional `repositories: [{id, url}]` (HTTPS-only at user-input boundary in `PluginConfigPreParser`); `plugins:` accepts both string `"g:a:v"` and map `{coordinate, repository}` form. `RepositoryConfig` / `RepositoryRegistry` (with implicit `maven-central`); `RepositoryConfig.testOnly` allows `file://` for IT only. `PluginDeclaration(coord, Optional<repositoryRef>)` is the new value object passed to `MavenPluginResolver.resolve` / `resolveGroups`.
  - **Lockfile model & I/O**: `LockFile(version=1, plugins)` → `LockedPlugin(coord, repositoryId, sha256, deps)` → `LockedDependency(coord, sha256)`. SHA-256 is enforced 64-char lowercase hex via record compact constructor. `LockFileReader` / `LockFileWriter` use SnakeYAML BLOCK with header comment `# This file is auto-generated by 'migraphe pin'. DO NOT EDIT.`. `Sha256Calculator` uses streaming 8 KiB digest.
  - **Pipeline**: `PluginResolver.resolve(baseDir)` now does `parse → LockFileReader.read → LockSyncChecker.check → MavenPluginResolver.resolve → PluginIntegrityVerifier.verify → URLClassLoader`. Lockfile is **mandatory** when `plugins:` is non-empty (`LockFileNotFoundException`); drift fails (`LockOutOfSyncException`); tampered JARs fail (`ChecksumMismatchException`); resolved-but-not-pinned artifacts fail (`MissingChecksumPinException`). All four are `extends PluginResolutionException`, which `Main.handleException` recognises to suppress stack traces and print only the message.
  - **`migraphe pin` command**: Generates `migraphe.lock.yaml` by orchestrating `MavenPluginResolver.resolveGroups` → `LockFileBuilder.build` → `LockFileWriter.write`. `--check` mode re-resolves and compares without writing; non-zero exit on missing/divergent lockfile (CI-friendly). `Main` switch dispatches `pin` before plugin resolution; `printUsage` lists it. `ValidateCommand` runs `LockSyncChecker` as a 6th step (`Checking plugin lockfile...`) when `plugins:` is non-empty.
  - **Integration test**: `PluginResolverIntegrationTest` mimics JitPack via `@TempDir` `file://` Maven repo. Covers: lockfile match (success), missing lock (`LockFileNotFoundException`), yaml ↔ lock divergence (`LockOutOfSyncException`), and JAR tampering after pin (`ChecksumMismatchException`). Uses `maven.repo.local` system property to point Maven Resolver at the temp repo.
  - **Docs**: `docs/USER_GUIDE.md` / `.ja.md` add a "Lockfile" subsection covering `migraphe pin`, `--check`, custom repositories block, and the `plugins:` map form. `docs/PHASE_21_PLAN.md` (created at session start) drove the 12-step TDD sequence.
  - Tests: 796 total, 100% passing. Spotless + ErrorProne clean. 17 commits across the phase, one per micro-cycle.

### 2026-04-20 (Session 44)
- **Index-table extension hooks + PostgreSQL OWNER / MySQL DEFINER surface in Markdown docs**
  - **Phase A — `jdbc-markdown` foundation**:
    - Converted `index.md` Tables/Views from bullet list to pipe table (`| Name | ... | Remarks |`).
    - Added 4 column-injection hooks (`extraTableIndexHeaders` / `extraTableIndexCells` / `extraViewIndexHeaders` / `extraViewIndexCells`, default `List.of()`) — subclasses inject cells between `Name` and `Remarks`.
    - Added 2 file-header hooks (`appendTableFileHeader` / `appendViewFileHeader`) called right after title+remarks paragraph in `tables/<name>.md` / `views/<name>.md`.
    - Size-mismatch between headers and cells throws `IllegalStateException` (config-error guard).
    - Index cells normalise `|` → `\|` and `\r?\n` → space via shared `formatIndexRemarks` helper to keep pipe tables intact.
  - **Phase B — PostgreSQL OWNER**:
    - `PostgreSQLSchemaInfoProvider` now extracts `pg_get_userbyid(relowner)` for tables/views/extensions/enums/sequences/functions/materialized views.
    - `extractSequences()` SQL now JOINs `pg_depend` (`deptype='a'`) + `pg_class ot` + `pg_attribute oa` so `ownerTable` / `ownerColumn` (from `OWNED BY`) finally fill in — previously hard-coded `null`.
    - Record evolution pattern: added `@Nullable String owner` (or `Map<String,String> tableOwners/viewOwners`) with a backward-compat constructor calling the canonical one — existing test fixtures untouched.
    - `PostgreSQLMarkdownGenerator` overrides `extra*IndexHeaders/Cells` + `appendTableFileHeader` / `appendViewFileHeader` to render `Owner` column / `Owner: <role>` row.
    - Sequences index now has two distinct columns: `Owned By` (dependent `table.column`) and `Owner` (role) — these are deliberately not conflated.
    - Extensions / Enum Types / Functions / Materialized Views gained `Owner` column or `Owner` property row.
  - **Phase C — MySQL DEFINER**:
    - `MySQLSchemaInfoProvider` now selects `DEFINER` from `information_schema.TRIGGERS` / `ROUTINES` / `EVENTS` + new `extractViewDefiners()` querying `information_schema.VIEWS.DEFINER` into a `Map<"<schema>.<name>", definer>`.
    - Record evolution: `MySQLTriggerInfo` / `MySQLRoutineInfo` / `MySQLEventInfo` gained `@Nullable String definer`; `MySQLSchemaInfo` gained `Map<String,String> viewDefiners` — all with backward-compat constructors.
    - `MySQLMarkdownGenerator` overrides the view hooks to render `Definer` column / `Definer: <user>` row; per-table and per-schema Triggers / Events tables and Routines per-file property table gained a `Definer` column/row.
    - MySQL tables themselves have no DEFINER, so Tables index remains bullet-compatible structure (just the new pipe-table layout, no extra column).
    - Column naming is deliberate: PostgreSQL uses `Owner` (role), MySQL uses `Definer` (SQL SECURITY user) — same slot, different semantics, and readers should not confuse them.
  - Test coverage: Testcontainers-backed `PostgreSQLSchemaInfoProviderTest` / `MySQLSchemaInfoProviderTest` assert that owner/definer strings are non-blank; pure-unit `JdbcMarkdownGeneratorTest` / `PostgreSQLMarkdownGeneratorTest` / `MySQLMarkdownGeneratorTest` assert rendering.
  - Docs updated: `docs/USER_GUIDE.md` + `.ja.md` describe the new `Owner` / `Owned By` / `Definer` columns and title-below header rows.
  - Tests: all modules 100% passing; Spotless + ErrorProne clean.

### 2026-04-20 (Session 43)
- **Table/View `remarks` rendering in JDBC Markdown generator**
  - Gap: `JdbcTableInfo.remarks()` / `JdbcViewInfo.remarks()` were populated from `DatabaseMetaData.getTables()` REMARKS but never written to Markdown output. `COMMENT ON TABLE` (PostgreSQL) and `COMMENT='...'` (MySQL) therefore vanished from docs.
  - Render sites added in `JdbcMarkdownGenerator`:
    - `tables/<name>.md` and `views/<name>.md`: remarks appear as a paragraph directly under the H1 title (empty/blank → omitted entirely, no blank line).
    - `index.md` Tables/Views list: link followed by `\u2014` (em-dash) and collapsed remarks (newlines → spaces) so the bullet stays single-line.
  - Extracted two private static helpers (`appendRemarksParagraph`, `appendIndexRemarks`) to avoid 4-way duplication. Subclasses (`PostgreSQLMarkdownGenerator`, `MySQLMarkdownGenerator`) inherit the behaviour automatically — no override needed.
  - Sample YAML DDL updated: 15 unique tables across `sample/cli/tasks/` and `sample/gradle/tasks/` (8 MySQL + 7 PostgreSQL) gained `COMMENT ON TABLE` / `COMMENT='...'` and per-column comments. CLI/Gradle sample trees remain bitwise-identical.
  - Tests: 4 new `JdbcMarkdownGeneratorTest` cases covering table/view file paragraph and index link suffix in both ASCII and Japanese. Empty-remarks negative case covered implicitly by existing fixtures (all fixture tables have blank remarks).
  - Tests: 680 total, 100% passing. Spotless + ErrorProne clean.

---

**Last Updated**: 2026-04-30
**Current Work**: Phase 21 complete — JitPack support and SHA-256 lockfile pinning. `migraphe.lock.yaml` is mandatory whenever `plugins:` is non-empty; `migraphe pin` generates it, `migraphe pin --check` and `migraphe validate` verify integrity. Custom HTTPS-only repositories supported via `repositories:` block; `RepositoryConfig.testOnly` bypasses HTTPS for `file://` integration tests only. Lockfile schema simplified post-completion: per-plugin `repository:` field removed (SHA-256 is the sole identity authority; `LockFileReader` accepts legacy key for backward compat).
