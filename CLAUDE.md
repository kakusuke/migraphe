# Migraphe - Project Documentation for Claude

## Project Overview

Migraphe is a migration orchestration tool that manages database/infrastructure migrations across multiple environments using a directed acyclic graph (DAG) structure.

**Domain Essence**: Orchestration of migration tasks across multiple environments represented as a directed graph.

**Current Milestone**: PostgreSQL Plugin with History Abstraction (COMPLETED)

## Technical Stack

- **Language**: Java 21 LTS
- **Build Tool**: Gradle 8.5 (Kotlin DSL)
- **Dependency Management**: Gradle Version Catalog (centralized in settings.gradle.kts)
- **Design Approach**: Domain-Driven Design (DDD)
- **Development Method**: TDD (t-wada style)
- **Code Formatter**: Spotless (mandatory)
- **Testing**: JUnit 5 + AssertJ
- **Concurrency**: Virtual Threads (planned)

## Architecture & Design Decisions

### Core Concepts

1. **MigrationNode** - Interface for migration task nodes
   - Each node belongs to an Environment
   - Plugins implement this interface
   - Provides upTask() and downTask()

2. **MigrationGraph** - DAG aggregate root
   - Ensures graph integrity
   - Cycle detection
   - Topological sorting

3. **HistoryRepository** - Pluggable execution history persistence
   - Audit trail per environment
   - Tracks executed nodes
   - Supports multiple backends (in-memory, PostgreSQL, file, S3, etc.)
   - Abstraction introduced to enable different persistence strategies

### Interface-Driven Design (Pluggable Architecture)

**CRITICAL**: The following are INTERFACES that plugins must implement:

- `MigrationNode` - Node structure and metadata
- `Environment` - Environment configuration
- `Task` - Execution logic (up/down)
- `HistoryRepository` - Execution history persistence

**Core provides**: Interfaces + orchestration logic
**Plugins provide**: Concrete implementations

Reference implementations:
- **In `plugin` package**: `SimpleMigrationNode`, `SimpleEnvironment`, `SimpleTask`
- **In `history` package**: `InMemoryHistoryRepository` (default, no persistence)
- **In `postgresql` module**: `PostgreSQLEnvironment`, `PostgreSQLMigrationNode`, `PostgreSQLUpTask`, `PostgreSQLDownTask`, `PostgreSQLHistoryRepository`

### Key Design Decisions

#### 1. Task Separation Pattern

**Decision**: Separate `Task` from `MigrationNode`

- **MigrationNode**: Graph structure + metadata
- **Task**: Execution logic (up/down)
- **Rationale**: Separation of concerns, testability, flexibility

#### 2. Up/Down Migration Support

**Decision**: Use `upTask()` and `downTask()` terminology

- **UP**: Forward migration
- **DOWN**: Rollback (compensating action)
- Rejected: "forward/compensating" terminology

#### 3. Two Types of Rollback

1. **Transaction-level rollback**: BEGIN/COMMIT/ROLLBACK within Task
2. **Migration-level rollback**: Compensating action to undo applied migration

**DownTask Serialization Flow**:
```
UP execution → serialize DownTask → store in ExecutionRecord
           → later: deserialize → execute for rollback
```

#### 4. No createdAt in MigrationNode

**Decision**: MigrationNode does NOT have `createdAt` field

**Rationale**: Execution history is managed by `MigrationHistory`, not the node itself.

#### 5. ExecutionRecord Fields

**Required fields**:
- `id` (UUID)
- `nodeId`
- `environmentId`
- `direction` (UP/DOWN)
- `status` (SUCCESS/FAILURE/SKIPPED)
- `executedAt` (timestamp)
- `description`
- `serializedDownTask` (Optional, UP only)
- `durationMs`
- `errorMessage` (Optional)

**Validation rules**:
- DOWN execution MUST NOT have `serializedDownTask`
- FAILURE status MUST have `errorMessage`

#### 6. Gradle Version Catalog

**Decision**: Use Gradle Version Catalog for centralized dependency management

**Location**: `settings.gradle.kts`

**Rationale**:
- Centralized version management across all modules
- Type-safe dependency references (e.g., `libs.junit.jupiter`)
- Easier version upgrades and consistency
- Better IDE support with auto-completion

**Defined versions**:
- JUnit: 5.10.1
- AssertJ: 3.25.1
- Spotless: 6.25.0
- Google Java Format: 1.19.2

**Usage**:
```kotlin
// In build.gradle.kts
dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}
```

#### 7. History Abstraction

**Decision**: Abstract history persistence into `HistoryRepository` interface

**Rationale**:
- Support multiple persistence backends (PostgreSQL, file, S3, etc.)
- Core should not dictate persistence strategy
- Plugins choose appropriate persistence for their use case

**Key Design**:
- `initialize()` method for setup (schema creation, file creation, etc.)
- `InMemoryHistoryRepository` as default (no persistence, for testing)
- Each implementation manages its own storage

**Migration**: `MigrationHistory` class deleted, replaced with `HistoryRepository` abstraction

#### 8. PostgreSQL Plugin Architecture

**Decision**: First database plugin implementation with full persistence

**Components**:
- `PostgreSQLEnvironment` - JDBC connection management
- `PostgreSQLMigrationNode` - Builder pattern with SQL from file/resource/string
- `PostgreSQLUpTask` / `PostgreSQLDownTask` - Transaction-managed execution
- `PostgreSQLHistoryRepository` - Persistent history in PostgreSQL table

**Transaction Management**:
- Each task manages its own transaction (setAutoCommit(false))
- Explicit COMMIT on success, ROLLBACK on SQLException
- PostgreSQL supports transactional DDL (unlike MySQL/Oracle)

**DOWN Task Serialization**:
- **Decision**: Store DOWN SQL as plain text (not JSON)
- **Rationale**: Environment context already available in ExecutionRecord
- Simpler and sufficient for rollback execution

**Testing Strategy**:
- Unit tests for logic and validation
- Integration tests with Testcontainers (real PostgreSQL)
- Test isolation via @AfterEach cleanup (DROP tables, TRUNCATE history)

### Package Structure

```
io.github.migraphe.core/
├── graph/           # MigrationNode, MigrationGraph, NodeId, ExecutionPlan,
│                    # TopologicalSort, ExecutionLevel, GraphVisualizer
├── task/            # Task, TaskResult, ExecutionDirection
├── environment/     # Environment, EnvironmentId, EnvironmentConfig
├── history/         # HistoryRepository (interface), InMemoryHistoryRepository,
│                    # ExecutionRecord, ExecutionStatus
├── plugin/          # Reference implementations (SimpleMigrationNode,
│                    # SimpleEnvironment, SimpleTask)
└── common/          # Result, ValidationResult

io.github.migraphe.postgresql/
├── PostgreSQLEnvironment.java
├── PostgreSQLMigrationNode.java
├── PostgreSQLUpTask.java
├── PostgreSQLDownTask.java
├── PostgreSQLHistoryRepository.java
└── PostgreSQLException.java
```

## Development Process

### TDD (t-wada style) - MANDATORY

1. **Red**: Write failing test
2. **Green**: Minimum code to pass
3. **Refactor**: Improve while keeping tests green

**IMPORTANT**: All tests MUST pass 100% at the end of each phase.

### Spotless Code Formatting - MANDATORY

- Apply before commit: `./gradlew spotlessApply`
- Check in CI: `./gradlew spotlessCheck`

### Test Coverage

**Current**: 114 tests, 100% passing
- Core: 93 tests (includes InMemoryHistoryRepository)
- PostgreSQL: 21 tests (13 unit + 8 integration with Testcontainers)

## Implementation Status

### Phase 1-7: COMPLETED ✅

- ✅ Phase 1: Gradle project setup
- ✅ Phase 2: Basic types (NodeId, EnvironmentId, EnvironmentConfig, ExecutionStatus, ValidationResult, Result)
- ✅ Phase 3: Interface definitions (MigrationNode, Environment, Task, TaskResult, ExecutionDirection, ExecutionRecord)
- ✅ Phase 4: Aggregate roots (MigrationGraph, MigrationHistory → refactored to HistoryRepository)
- ✅ Phase 5: Algorithms (TopologicalSort, ExecutionPlan)
- ✅ Phase 6: Graph visualization + reference implementations
- ✅ Phase 7: Testing (84 tests, 100% passing)

### Phase 8: History Abstraction + PostgreSQL Plugin - COMPLETED ✅

**Milestone 1: Core History Abstraction**
- ✅ Created `HistoryRepository` interface with `initialize()` method
- ✅ Implemented `InMemoryHistoryRepository` (migrated from MigrationHistory)
- ✅ Deleted `MigrationHistory` class (breaking change, pre-release)
- ✅ All core tests updated and passing (93 tests)

**Milestone 2: PostgreSQL Plugin**
- ✅ Created `migraphe-postgresql` Gradle module
- ✅ Implemented PostgreSQL-specific components:
  - `PostgreSQLEnvironment` - JDBC connection management
  - `PostgreSQLMigrationNode` - Builder with SQL from file/resource/string
  - `PostgreSQLUpTask` / `PostgreSQLDownTask` - Transaction-managed execution
  - `PostgreSQLHistoryRepository` - Persistent history in PostgreSQL
- ✅ Added Testcontainers integration tests (8 tests)
- ✅ All tests passing (21 PostgreSQL tests: 13 unit + 8 integration)
- ✅ Total: 114 tests, 100% passing

### Next Steps (Future Phases)

1. **File History Repository**: File-based history persistence
2. **Additional Database Drivers**: MySQL, MongoDB specific implementations
3. **Parsers**: SQL file parsers, YAML configuration, etc.
4. **Gradle Plugin**: Build integration
5. **CLI**: Command-line interface
6. **Virtual Threads**: Parallel execution implementation

## Critical Files

### Gradle Configuration
- `settings.gradle.kts` - Project settings + **Version Catalog** (centralized dependency versions)
- `build.gradle.kts` - Root build configuration (multi-module setup, Spotless)
- `migraphe-core/build.gradle.kts` - Core module dependencies (uses version catalog)
- `migraphe-postgresql/build.gradle.kts` - PostgreSQL plugin dependencies

### Core Interfaces (Plugins implement these)
- `MigrationNode.java` - **INTERFACE**
- `Environment.java` - **INTERFACE**
- `Task.java` - **INTERFACE**
- `HistoryRepository.java` - **INTERFACE** (history persistence abstraction)

### Aggregate Roots
- `MigrationGraph.java` - DAG with cycle detection

### History Management
- `HistoryRepository.java` - **INTERFACE** for pluggable persistence
- `InMemoryHistoryRepository.java` - Default in-memory implementation
- `ExecutionRecord.java` - Execution record value object
- `ExecutionStatus.java` - Execution status enum

### Algorithms
- `TopologicalSort.java` - Kahn's algorithm for execution ordering
- `ExecutionPlan.java` - Parallel execution plan

### Reference Implementations (Core)
- `SimpleMigrationNode.java` - Builder pattern
- `SimpleEnvironment.java`
- `SimpleTask.java`

### PostgreSQL Plugin
- `PostgreSQLEnvironment.java` - JDBC connection management
- `PostgreSQLMigrationNode.java` - Node with SQL from file/resource/string
- `PostgreSQLUpTask.java` - UP migration with transaction management
- `PostgreSQLDownTask.java` - DOWN migration (rollback)
- `PostgreSQLHistoryRepository.java` - Persistent history in PostgreSQL table
- `PostgreSQLException.java` - Plugin-specific exception
- `init_history_table.sql` - Schema definition for history table

### Visualization
- `GraphVisualizer.java` - Terminal ASCII output

## Using the PostgreSQL Plugin

### Setup

Add dependency in your `build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":migraphe-postgresql"))
}
```

### Basic Usage

```java
// 1. Create PostgreSQL environment
PostgreSQLEnvironment environment = PostgreSQLEnvironment.create(
    "production",
    "jdbc:postgresql://localhost:5432/mydb",
    "username",
    "password"
);

// 2. Initialize history repository
HistoryRepository historyRepo = new PostgreSQLHistoryRepository(environment);
historyRepo.initialize();  // Creates migraphe_history table

// 3. Create migration nodes
PostgreSQLMigrationNode node1 = PostgreSQLMigrationNode.builder()
    .id("V001")
    .name("Create users table")
    .environment(environment)
    .upSql("CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(100));")
    .downSql("DROP TABLE IF EXISTS users;")
    .build();

PostgreSQLMigrationNode node2 = PostgreSQLMigrationNode.builder()
    .id("V002")
    .name("Create posts table")
    .environment(environment)
    .dependencies(NodeId.of("V001"))
    .upSqlFromFile(Path.of("migrations/V002__create_posts.sql"))
    .downSqlFromFile(Path.of("migrations/V002__drop_posts.sql"))
    .build();

// 4. Build and execute migration graph
MigrationGraph graph = MigrationGraph.builder()
    .nodes(node1, node2)
    .build();

ExecutionPlan plan = graph.createExecutionPlan();

// 5. Execute migrations and record history
for (ExecutionLevel level : plan.levels()) {
    for (MigrationNode node : level.nodes()) {
        if (!historyRepo.wasExecuted(node.id(), environment.id())) {
            Result<TaskResult, String> result = node.upTask().execute();

            if (result.isOk()) {
                ExecutionRecord record = ExecutionRecord.upSuccess(
                    node.id(),
                    environment.id(),
                    node.name(),
                    result.value().get().serializedDownTask(),
                    100  // duration in ms
                );
                historyRepo.record(record);
            }
        }
    }
}
```

### Loading SQL from Resources

```java
PostgreSQLMigrationNode node = PostgreSQLMigrationNode.builder()
    .id("V001")
    .name("Initial schema")
    .environment(environment)
    .upSqlFromResource("/migrations/V001__init.sql")
    .downSqlFromResource("/migrations/V001__rollback.sql")
    .build();
```

### Rollback Example

```java
// Get latest execution record
Optional<ExecutionRecord> record =
    historyRepo.findLatestRecord(nodeId, environmentId);

if (record.isPresent() && record.get().serializedDownTask().isPresent()) {
    // Deserialize and execute DOWN task
    String downSql = record.get().serializedDownTask().get();
    PostgreSQLDownTask downTask = PostgreSQLDownTask.create(environment, downSql);

    Result<TaskResult, String> result = downTask.execute();

    if (result.isOk()) {
        ExecutionRecord rollbackRecord = ExecutionRecord.downSuccess(
            nodeId,
            environmentId,
            "Rollback " + record.get().description(),
            50  // duration in ms
        );
        historyRepo.record(rollbackRecord);
    }
}
```

### History Queries

```java
// Check if a migration was executed
boolean executed = historyRepo.wasExecuted(nodeId, environmentId);

// Get all executed nodes for an environment
List<NodeId> executedNodes = historyRepo.executedNodes(environmentId);

// Get all execution records
List<ExecutionRecord> allRecords = historyRepo.allRecords(environmentId);

// Find latest record for a specific node
Optional<ExecutionRecord> latest =
    historyRepo.findLatestRecord(nodeId, environmentId);
```

## Important Patterns & Practices

### 1. Sealed Interfaces for Type Safety

```java
public sealed interface Result<T, E> permits Result.Ok, Result.Err {
    // Railway-oriented programming
}
```

### 2. Records for Immutable Value Objects

```java
public record NodeId(String value) {
    public NodeId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("NodeId value must not be blank");
        }
    }
}
```

### 3. Builder Pattern for Complex Objects

```java
SimpleMigrationNode.builder()
    .id("node-1")
    .name("Create users table")
    .environment(env)
    .dependencies(dep1, dep2)
    .upTask(task)
    .build();
```

### 4. Optional for Nullable Values

- Use `Optional<T>` instead of null
- Example: `Optional<Task> downTask()` - rollback may not be supported

### 5. Factory Methods for Creation

```java
ValidationResult.valid()
ValidationResult.invalid(errors)
ExecutionRecord.upSuccess(...)
ExecutionRecord.downSuccess(...)
```

## Testing Strategy

### Test Helpers

`TestHelpers.java` provides:
- `TestEnvironment` - Simple environment for tests
- `TestTask` - Simple task that always succeeds
- `TestMigrationNode` - Node with builder pattern
- `TestNodeBuilder` - Fluent API for creating test nodes

### Test Naming Convention

```java
void shouldDoSomethingWhenCondition()
```

## Build Commands

```bash
# Build project
./gradlew build

# Run tests
./gradlew test

# Apply code formatting
./gradlew spotlessApply

# Check code formatting
./gradlew spotlessCheck
```

## Toolchain Management

Using `mise` for tool version management:

```bash
mise trust
mise install
```

See `.mise.toml` for configured versions.

## Design Principles

1. **KISS**: Keep implementations simple and focused
2. **SRP**: Single Responsibility Principle (Task separation from Node)
3. **Interface Segregation**: Small, focused interfaces
4. **Dependency Inversion**: Depend on interfaces, not implementations
5. **Immutability**: Use records and immutable collections
6. **Null Safety**: Use Optional instead of null
7. **Type Safety**: Use sealed interfaces and pattern matching

## Session End Procedure

**IMPORTANT**: At the end of each development session, Claude should:

1. Update this `CLAUDE.md` file with:
   - New design decisions made during the session
   - Changes to architecture or patterns
   - Updated implementation status
   - New critical files created
   - Important learnings or gotchas

2. Ensure all tests are passing (100%)

3. Apply Spotless formatting: `./gradlew spotlessApply`

4. Commit changes if working on a feature

This ensures continuity across sessions and maintains project knowledge.

---

## Changelog

### 2026-01-03 (Session 3)
- **Phase 8: History Abstraction + PostgreSQL Plugin**
  - Abstracted history persistence with `HistoryRepository` interface
  - Migrated `MigrationHistory` to `InMemoryHistoryRepository`
  - Implemented PostgreSQL plugin:
    - `PostgreSQLEnvironment` - JDBC connection management
    - `PostgreSQLMigrationNode` - Builder pattern with SQL from file/resource/string
    - `PostgreSQLUpTask` / `PostgreSQLDownTask` - Transaction-managed execution
    - `PostgreSQLHistoryRepository` - Persistent history in PostgreSQL table
  - Added Testcontainers integration tests
  - Test coverage: 114 tests (93 core + 21 PostgreSQL), 100% passing
  - **Design Decisions**:
    - DOWN SQL stored as plain text (not JSON)
    - `initialize()` method in HistoryRepository for setup
    - Breaking change: deleted MigrationHistory (pre-release, no backward compatibility needed)

### 2026-01-03 (Session 2)
- **Refactoring**: Migrated to Gradle Version Catalog
  - Centralized all dependency versions in `settings.gradle.kts`
  - Updated all build files to use type-safe catalog references
  - Maintains 100% test pass rate (84 tests)

### 2026-01-03 (Session 1)
- Initial implementation: Phase 1-7 complete
- 84 tests, 100% passing
- Graph visualization in terminal

---

**Last Updated**: 2026-01-03
**Phase**: 1-8 Complete (PostgreSQL Plugin Milestone Achieved)
**Next Milestone**: File-based history repository / Additional database drivers
