# Migraphe - Project Documentation for Claude

## Project Overview

Migraphe is a migration orchestration tool that manages database/infrastructure migrations across multiple environments using a directed acyclic graph (DAG) structure.

**Domain Essence**: Orchestration of migration tasks across multiple environments represented as a directed graph.

**Current Milestone**: Graph visualization in terminal (COMPLETED)

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

### Core Concepts (The Trinity)

1. **MigrationNode (= Task)** - Interface for migration task nodes
   - Each node belongs to an Environment
   - Plugins implement this interface

2. **MigrationGraph** - DAG aggregate root
   - Ensures graph integrity
   - Cycle detection
   - Topological sorting

3. **MigrationHistory** - Execution history per environment
   - Audit trail
   - Tracks executed nodes

### Interface-Driven Design (Pluggable Architecture)

**CRITICAL**: The following are INTERFACES that plugins must implement:

- `MigrationNode` - Node structure and metadata
- `Environment` - Environment configuration
- `Task` - Execution logic (up/down)

**Core provides**: Interfaces + orchestration logic
**Plugins provide**: Concrete implementations

Reference implementations are provided in `plugin` package:
- `SimpleMigrationNode`
- `SimpleEnvironment`
- `SimpleTask`

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

### Package Structure

```
io.github.migraphe.core/
├── graph/           # MigrationNode, MigrationGraph, NodeId, ExecutionPlan,
│                    # TopologicalSort, ExecutionLevel, GraphVisualizer
├── task/            # Task, TaskResult, ExecutionDirection
├── environment/     # Environment, EnvironmentId, EnvironmentConfig
├── history/         # MigrationHistory, ExecutionRecord, ExecutionStatus
├── plugin/          # Reference implementations (SimpleMigrationNode,
│                    # SimpleEnvironment, SimpleTask)
└── common/          # Result, ValidationResult
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

**Current**: 84 tests, 100% passing

## Implementation Status

### Phase 1-7: COMPLETED ✅

- ✅ Phase 1: Gradle project setup
- ✅ Phase 2: Basic types (NodeId, EnvironmentId, EnvironmentConfig, ExecutionStatus, ValidationResult, Result)
- ✅ Phase 3: Interface definitions (MigrationNode, Environment, Task, TaskResult, ExecutionDirection, ExecutionRecord)
- ✅ Phase 4: Aggregate roots (MigrationGraph, MigrationHistory)
- ✅ Phase 5: Algorithms (TopologicalSort, ExecutionPlan)
- ✅ Phase 6: Graph visualization + reference implementations
- ✅ Phase 7: Testing (84 tests, 100% passing)

### Next Steps (Future Phases)

1. **Database Drivers**: PostgreSQL, MySQL, MongoDB specific implementations
2. **Parsers**: SQL, YAML, etc.
3. **Gradle Plugin**: Build integration
4. **CLI**: Command-line interface
5. **Virtual Threads**: Parallel execution implementation

## Critical Files

### Gradle Configuration
- `settings.gradle.kts` - Project settings + **Version Catalog** (centralized dependency versions)
- `build.gradle.kts` - Root build configuration (multi-module setup, Spotless)
- `migraphe-core/build.gradle.kts` - Core module dependencies (uses version catalog)

### Core Interfaces (Plugins implement these)
- `MigrationNode.java` - **INTERFACE**
- `Environment.java` - **INTERFACE**
- `Task.java` - **INTERFACE**

### Aggregate Roots
- `MigrationGraph.java` - DAG with cycle detection
- `MigrationHistory.java` - Execution history management

### Algorithms
- `TopologicalSort.java` - Kahn's algorithm for execution ordering
- `ExecutionPlan.java` - Parallel execution plan

### Reference Implementations
- `SimpleMigrationNode.java` - Builder pattern
- `SimpleEnvironment.java`
- `SimpleTask.java`

### Visualization
- `GraphVisualizer.java` - Terminal ASCII output

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
**Phase**: 1-7 Complete (Initial Milestone Achieved)
**Next Milestone**: Database driver implementation
