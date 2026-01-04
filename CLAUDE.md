# Migraphe - Project Documentation for Claude

## Project Overview

Migraphe is a migration orchestration tool that manages database/infrastructure migrations across multiple environments using a directed acyclic graph (DAG) structure.

**Domain Essence**: Orchestration of migration tasks across multiple environments represented as a directed graph.

**Current Milestone**: CLI Implementation (COMPLETED)

## Technical Stack

- **Language**: Java 21 LTS
- **Build Tool**: Gradle 8.5 (Kotlin DSL)
- **Dependency Management**: Gradle Version Catalog (centralized in settings.gradle.kts)
- **Configuration**: MicroProfile Config 3.1 + SmallRye Config 3.9.1 (YAML support)
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
- MicroProfile Config: 3.1
- SmallRye Config: 3.9.1

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

#### 9. Configuration Management with MicroProfile Config

**Decision**: Use MicroProfile Config (SmallRye Config) for all configuration management

**Components**:
- `ProjectConfig` - Project-level settings (name, history target)
- `TargetConfig` - Database connection settings (type, jdbc_url, username, password)
- `TaskConfig` - Migration task definitions (name, target, dependencies, up/down SQL)
- `ConfigurationException` - Configuration error handling

**Key Features**:
- **Type-safe binding**: `@ConfigMapping` interfaces for compile-time safety
- **Automatic variable expansion**: `${VAR}` syntax resolved by MP Config
- **YAML support**: SmallRye Config YAML ConfigSource for configuration files
- **Optional properties**: Using `Optional<T>` for non-required fields

**Configuration Format**: YAML (originally planned as TOML, switched to YAML for better ecosystem support)

**Location**: Configuration interfaces in `migraphe-core/config/` package (shared across CLI and Gradle plugin)

**Rationale**:
- Jakarta EE standard compliance
- Eliminates need for custom variable expansion logic
- Extensible ConfigSource architecture (can add environment variables, system properties, etc.)
- Better type safety than string-based configuration
- Shared configuration model across multiple tools (CLI, Gradle plugin)

#### 10. CLI Architecture and Multi-File Configuration

**Decision**: Multi-file YAML configuration with automatic Task ID generation

**Components**:
- `YamlFileScanner` - Discovers YAML files in conventional directory structure
- `TaskIdGenerator` - Generates Task IDs from file paths (e.g., `tasks/db1/create.yaml` → `"db1/create"`)
- `MultiFileYamlConfigSource` - Custom ConfigSource that merges multiple YAML files
- `ConfigLoader` - Orchestrates configuration loading with environment overrides
- `ExecutionContext` - Immutable context holding all runtime state
- `EnvironmentFactory` / `MigrationNodeFactory` - Convert config to domain objects

**Directory Structure**:
```
project/
├── migraphe.yaml           # Project config
├── targets/*.yaml          # Database connections
├── tasks/**/*.yaml         # Migration tasks (hierarchical)
└── environments/*.yaml     # Environment overrides
```

**Key Design Points**:
- **Convention over Configuration**: Task IDs derived from file paths, no manual assignment needed
- **Separation of Concerns**: Config files organized by type (targets, tasks) for maintainability
- **Environment Overrides**: `environments/{envName}.yaml` overrides base configuration
- **Factory Pattern**: Clean conversion from config (data) to domain objects (behavior)
- **Immutable Context**: ExecutionContext as record prevents accidental state mutation
- **Topological Sort in Context**: Nodes pre-sorted by dependencies for consistent iteration order

**Rationale**:
- Reduces configuration boilerplate (no manual Task ID assignment)
- Clear separation between different config types
- Supports multiple environments without code duplication
- Factory pattern keeps CLI layer thin (delegates to core domain logic)
- Immutable context prevents bugs from shared mutable state
- Pre-sorted nodes ensure consistent execution order

### Package Structure

```
io.github.migraphe.core/
├── graph/           # MigrationNode, MigrationGraph, NodeId, ExecutionPlan,
│                    # TopologicalSort, ExecutionLevel, GraphVisualizer
├── task/            # Task, TaskResult, ExecutionDirection
├── environment/     # Environment, EnvironmentId, EnvironmentConfig
├── history/         # HistoryRepository (interface), InMemoryHistoryRepository,
│                    # ExecutionRecord, ExecutionStatus
├── config/          # Configuration interfaces (ProjectConfig, TargetConfig,
│                    # TaskConfig, ConfigurationException)
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

io.github.migraphe.cli/
├── Main.java                            # CLI entry point
├── ExecutionContext.java                # Execution context (config, environments, nodes, graph)
├── command/                             # Command implementations
│   ├── Command.java                     # Command interface
│   ├── UpCommand.java                   # Execute migrations
│   └── StatusCommand.java               # Show migration status
├── config/                              # Configuration loading
│   ├── ConfigLoader.java                # Config loader with SmallRyeConfig
│   ├── MultiFileYamlConfigSource.java   # Multi-file YAML integration
│   ├── TaskIdGenerator.java             # Generate Task IDs from file paths
│   └── YamlFileScanner.java             # Discover YAML files
└── factory/                             # Object factories
    ├── EnvironmentFactory.java          # Create Environments from config
    └── MigrationNodeFactory.java        # Create MigrationNodes from config
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

### Documentation Maintenance - MANDATORY

**Rule**: All user-facing documentation MUST be kept up-to-date with code changes.

**Documentation Files**:
- `README.md` - English project overview
- `README.ja.md` - Japanese project overview
- `docs/USER_GUIDE.md` - English detailed user guide
- `docs/USER_GUIDE.ja.md` - Japanese detailed user guide
- `CLAUDE.md` - Architecture and design decisions (this file)

**When to Update Documentation**:
1. **New Features**: Update user guides with new commands, configuration options, or behaviors
2. **API Changes**: Update examples in README and user guides
3. **Configuration Changes**: Update YAML examples and field descriptions
4. **Breaking Changes**: Highlight in README and provide migration guide
5. **Bug Fixes**: Update troubleshooting section if relevant
6. **Design Decisions**: Update CLAUDE.md with new architectural choices

**Language Requirement**:
- Primary documentation: English (README.md, USER_GUIDE.md)
- Japanese translation: Must be kept in sync (README.ja.md, USER_GUIDE.ja.md)
- Both languages must have equivalent content

**IMPORTANT**: Documentation updates are NOT optional. They are part of the Definition of Done for any feature.

### Test Coverage

**Current**: ~150 tests, 100% passing
- Core: 79 tests (includes InMemoryHistoryRepository + Config interfaces)
- PostgreSQL: 21 tests (13 unit + 8 integration with Testcontainers)
- CLI: ~50 tests (includes ConfigLoader, YamlFileScanner, Factories, ExecutionContext, UpCommand with Testcontainers)

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

### Phase 9: Configuration Management - COMPLETED ✅

**Objective**: Standardize configuration management using MicroProfile Config

**Implementation**:
- ✅ Migrated from Jackson TOML to MicroProfile Config with YAML
- ✅ Added dependencies: `microprofile-config-api`, `smallrye-config`, `smallrye-config-source-yaml`
- ✅ Created configuration interfaces in `migraphe-core/config/`:
  - `ProjectConfig` - Project-level settings with `@ConfigMapping`
  - `TargetConfig` - Database connection configuration
  - `TaskConfig` - Migration task definitions with optional fields
  - `ConfigurationException` - Configuration error handling
- ✅ Removed custom `VariableExpander` (MP Config handles variable expansion)
- ✅ Converted from `record` to `interface` for `@ConfigMapping` compatibility
- ✅ Created MP Config-based tests (7 new tests)
- ✅ All tests passing: 100 tests, 100% passing

**Benefits**:
- Jakarta EE standard compliance
- Automatic `${VAR}` variable expansion
- Type-safe configuration with compile-time checking
- Shared configuration model across CLI and Gradle plugin
- Reduced custom code (eliminated VariableExpander)

### Phase 10: CLI Implementation - COMPLETED ✅

**Objective**: Implement command-line interface for executing migrations with YAML configuration

**Phase 10-1: YAML Configuration Loading** ✅
- ✅ Implemented `YamlFileScanner` - Discovers YAML files (project, targets, tasks, environments)
- ✅ Implemented `TaskIdGenerator` - Generates Task IDs from file paths (e.g., `tasks/db1/create_users.yaml` → `"db1/create_users"`)
- ✅ Implemented `MultiFileYamlConfigSource` - Custom MicroProfile ConfigSource for multi-file YAML integration
- ✅ Implemented `ConfigLoader` - Loads and merges YAML configurations with environment-specific overrides
- ✅ Tests: YamlFileScannerTest, TaskIdGeneratorTest, ConfigLoaderTest, MultiFileYamlConfigSourceTest

**Phase 10-2: Factories and Execution Context** ✅
- ✅ Implemented `EnvironmentFactory` - Creates PostgreSQLEnvironment instances from config
- ✅ Implemented `MigrationNodeFactory` - Creates PostgreSQLMigrationNode instances from TaskConfig
- ✅ Implemented `ExecutionContext` - Manages project state (config, environments, nodes, graph)
  - Auto-loads configuration from project directory
  - Topological sorting for dependency-ordered node list
  - Graph construction with cycle detection
- ✅ Tests: EnvironmentFactoryTest, MigrationNodeFactoryTest, ExecutionContextTest

**Phase 10-3: CLI Commands** ✅
- ✅ Implemented `Main.java` - CLI entry point with command routing
- ✅ Implemented `Command` interface - Unified command execution pattern
- ✅ Implemented `UpCommand` - Execute pending migrations
  - Level-by-level execution based on ExecutionPlan
  - Automatic skip of already-executed tasks (via HistoryRepository)
  - Detailed progress output with timing
  - Failure handling with error recording
- ✅ Implemented `StatusCommand` - Display migration status
  - Shows executed vs pending tasks
  - Summary statistics
- ✅ Tests: UpCommandTest (integration test with Testcontainers)
- ✅ Total CLI tests: ~50, 100% passing

**Project Structure**:
```
project/
├── migraphe.yaml              # Project config (name, history.target)
├── targets/                   # Database connection configs
│   ├── db1.yaml              # Target: db1 (type, jdbc_url, username, password)
│   └── history.yaml          # Target: history
├── tasks/                     # Migration task definitions
│   ├── db1/
│   │   ├── create_users.yaml # Task: db1/create_users (name, target, up/down SQL)
│   │   └── create_posts.yaml # Task: db1/create_posts (dependencies)
│   └── db2/
│       └── initial.yaml
└── environments/              # Environment-specific overrides (optional)
    ├── development.yaml       # Override for development
    └── production.yaml        # Override for production
```

**CLI Commands**:
```bash
# Show migration status
migraphe status

# Execute pending migrations
migraphe up
```

**Benefits**:
- Multi-file YAML configuration for better organization
- Environment-specific configuration overrides
- Automatic Task ID generation from file paths
- Type-safe configuration with MicroProfile Config
- Reusable factories for clean separation of concerns
- Comprehensive integration tests with real PostgreSQL

### Next Steps (Future Phases)

1. **CLI Executable Packaging**: GraalVM Native Image or application plugin for standalone executable
2. **Additional CLI Commands**:
   - `down` - Rollback migrations
   - `history` - View execution history
   - `validate` - Validate configuration and dependencies
3. **Gradle Plugin**: Build integration for running migrations from Gradle
4. **File History Repository**: File-based history persistence (alternative to PostgreSQL)
5. **Additional Database Drivers**: MySQL, MongoDB specific implementations
6. **Virtual Threads**: Parallel execution implementation for independent migration levels

## Critical Files

### Gradle Configuration
- `settings.gradle.kts` - Project settings + **Version Catalog** (centralized dependency versions)
- `build.gradle.kts` - Root build configuration (multi-module setup, Spotless)
- `migraphe-core/build.gradle.kts` - Core module dependencies (includes MicroProfile Config)
- `migraphe-postgresql/build.gradle.kts` - PostgreSQL plugin dependencies
- `migraphe-cli/build.gradle.kts` - CLI module dependencies

### Core Interfaces (Plugins implement these)
- `MigrationNode.java` - **INTERFACE**
- `Environment.java` - **INTERFACE**
- `Task.java` - **INTERFACE**
- `HistoryRepository.java` - **INTERFACE** (history persistence abstraction)

### Configuration Interfaces (MicroProfile Config)
- `ProjectConfig.java` - **INTERFACE** with `@ConfigMapping` (project-level settings)
- `TargetConfig.java` - **INTERFACE** with `@ConfigMapping` (database connection config)
- `TaskConfig.java` - **INTERFACE** with `@ConfigMapping` (migration task definitions)
- `ConfigurationException.java` - Configuration error handling

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

### CLI Module
- `Main.java` - CLI entry point with command routing
- `ExecutionContext.java` - Execution context (config, environments, nodes, graph)
- `Command.java` - Command interface
- `UpCommand.java` - Execute pending migrations
- `StatusCommand.java` - Display migration status
- `ConfigLoader.java` - Load and merge YAML configurations
- `MultiFileYamlConfigSource.java` - Custom MicroProfile ConfigSource for multi-file YAML
- `YamlFileScanner.java` - Discover YAML files in project directory
- `TaskIdGenerator.java` - Generate Task IDs from file paths
- `EnvironmentFactory.java` - Create Environments from config
- `MigrationNodeFactory.java` - Create MigrationNodes from config

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

## Using the CLI

### Project Setup

Create a project directory with the following structure:

```
my-project/
├── migraphe.yaml              # Project configuration
├── targets/                   # Database connection configs
│   ├── db1.yaml
│   └── history.yaml
├── tasks/                     # Migration task definitions
│   ├── db1/
│   │   ├── 001_create_users.yaml
│   │   └── 002_create_posts.yaml
│   └── db2/
│       └── 001_initial_schema.yaml
└── environments/              # Optional: environment-specific overrides
    ├── development.yaml
    └── production.yaml
```

### Configuration Files

**migraphe.yaml** (Project configuration):
```yaml
project:
  name: my-project
  history:
    target: history
```

**targets/db1.yaml** (Database target):
```yaml
target:
  db1:
    type: postgresql
    jdbc_url: jdbc:postgresql://localhost:5432/mydb
    username: myuser
    password: mypassword
```

**targets/history.yaml** (History storage):
```yaml
target:
  history:
    type: postgresql
    jdbc_url: jdbc:postgresql://localhost:5432/migraphe_history
    username: myuser
    password: mypassword
```

**tasks/db1/001_create_users.yaml** (Migration task):
```yaml
task:
  name: Create users table
  target: db1
  up:
    sql: |
      CREATE TABLE users (
        id SERIAL PRIMARY KEY,
        name VARCHAR(100) NOT NULL,
        email VARCHAR(255) UNIQUE NOT NULL
      );
  down:
    sql: |
      DROP TABLE IF EXISTS users;
```

**tasks/db1/002_create_posts.yaml** (Task with dependency):
```yaml
task:
  name: Create posts table
  target: db1
  dependencies:
    - db1/001_create_users
  up:
    sql: |
      CREATE TABLE posts (
        id SERIAL PRIMARY KEY,
        user_id INTEGER REFERENCES users(id),
        title VARCHAR(200) NOT NULL,
        content TEXT
      );
  down:
    sql: |
      DROP TABLE IF EXISTS posts;
```

**environments/development.yaml** (Optional environment overrides):
```yaml
target:
  db1:
    jdbc_url: jdbc:postgresql://localhost:5432/mydb_dev
    password: dev_password
```

### Running Migrations

```bash
# Navigate to project directory
cd my-project

# Check migration status
migraphe status

# Output:
# Migration Status
# ================
#
# [ ] db1/001_create_users - Create users table
# [ ] db1/002_create_posts - Create posts table
#
# Summary:
#   Total: 2
#   Executed: 0
#   Pending: 2

# Execute migrations
migraphe up

# Output:
# Executing migrations...
#
# Execution Plan:
#   Levels: 2
#   Total Tasks: 2
#
# Level 0:
#   [RUN]  Create users table ... OK (45ms)
#
# Level 1:
#   [RUN]  Create posts table ... OK (32ms)
#
# Migration completed successfully. 2 migrations executed.

# Check status again
migraphe status

# Output:
# Migration Status
# ================
#
# [✓] db1/001_create_users - Create users table
# [✓] db1/002_create_posts - Create posts table
#
# Summary:
#   Total: 2
#   Executed: 2
#   Pending: 0
```

### Task ID Generation

Task IDs are automatically generated from file paths relative to the `tasks/` directory:

- `tasks/db1/001_create_users.yaml` → Task ID: `"db1/001_create_users"`
- `tasks/db2/schema/initial.yaml` → Task ID: `"db2/schema/initial"`
- `tasks/common/setup.yaml` → Task ID: `"common/setup"`

### Environment-Specific Configuration

Use environment files to override configuration for different environments:

```bash
# Load development environment
migraphe up --env development

# Load production environment
migraphe up --env production
```

Environment files override base configuration with higher priority.

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

2. Update user-facing documentation:
   - Update `README.md` and `README.ja.md` if new features or usage changes
   - Update `docs/USER_GUIDE.md` and `docs/USER_GUIDE.ja.md` if configuration or commands changed
   - Ensure English and Japanese versions are in sync

3. Ensure all tests are passing (100%)

4. Apply Spotless formatting: `./gradlew spotlessApply`

5. Commit changes if working on a feature

This ensures continuity across sessions and maintains project knowledge.

---

## Changelog

### 2026-01-05 (Session 6)
- **Documentation Creation**
  - Created comprehensive user documentation:
    - `README.md` - English project overview with quick start guide
    - `README.ja.md` - Japanese translation of README
    - `docs/USER_GUIDE.md` - English detailed user guide covering:
      - Installation and project setup
      - Configuration (project, targets, tasks, environments)
      - Writing migrations with best practices
      - Running migrations (status, up commands)
      - Environment management
      - Advanced features (parallel execution, complex dependencies)
      - Troubleshooting guide
    - `docs/USER_GUIDE.ja.md` - Japanese translation of user guide
  - Added **Documentation Maintenance Rules** to CLAUDE.md:
    - Mandatory documentation updates for all user-facing changes
    - English and Japanese must be kept in sync
    - Documentation is part of Definition of Done
  - Updated Session End Procedure to include documentation checks
  - **Distribution Strategy**: Decided on Fat JAR as primary distribution method
    - Simple single-file distribution
    - Supports plugin dynamic loading
    - Works on all platforms with Java 21+

### 2026-01-04 (Session 5)
- **Phase 10: CLI Implementation - COMPLETED**
  - **Phase 10-1: YAML Configuration Loading**
    - Implemented `YamlFileScanner` - Discovers YAML files (migraphe.yaml, targets/*.yaml, tasks/**/*.yaml, environments/*.yaml)
    - Implemented `TaskIdGenerator` - Generates Task IDs from file paths (e.g., `tasks/db1/create_users.yaml` → `"db1/create_users"`)
    - Implemented `MultiFileYamlConfigSource` - Custom MicroProfile ConfigSource for multi-file YAML integration
    - Implemented `ConfigLoader` - Loads and merges YAML configurations with environment-specific overrides
    - Tests: YamlFileScannerTest, TaskIdGeneratorTest, ConfigLoaderTest, MultiFileYamlConfigSourceTest
  - **Phase 10-2: Factories and Execution Context**
    - Implemented `EnvironmentFactory` - Creates PostgreSQLEnvironment instances from TargetConfig
    - Implemented `MigrationNodeFactory` - Creates PostgreSQLMigrationNode instances from TaskConfig
    - Implemented `ExecutionContext` - Manages project state (config, environments, nodes, graph)
      - Auto-loads configuration from project directory
      - Topological sorting for dependency-ordered node list
      - Graph construction with cycle detection
    - Tests: EnvironmentFactoryTest, MigrationNodeFactoryTest, ExecutionContextTest (4 tests)
  - **Phase 10-3: CLI Commands**
    - Implemented `Main.java` - CLI entry point with command routing
    - Implemented `Command` interface - Unified command execution pattern
    - Implemented `UpCommand` - Execute pending migrations with ExecutionPlan, HistoryRepository integration, progress output
    - Implemented `StatusCommand` - Display migration status with summary statistics
    - Tests: UpCommandTest (integration test with Testcontainers)
  - Total CLI tests: ~50, 100% passing
  - **Overall test coverage**: ~150 tests (79 core + 21 PostgreSQL + ~50 CLI), 100% passing
  - **Design Decisions**:
    - Multi-file YAML configuration for better organization (project, targets, tasks, environments)
    - Automatic Task ID generation from file paths (no manual ID assignment needed)
    - Environment-specific overrides via `environments/{envName}.yaml` files
    - Reusable factories for clean separation of concerns (Config → Domain objects)
    - ExecutionContext as immutable record holding all runtime state
    - Comprehensive integration tests with real PostgreSQL via Testcontainers

### 2026-01-04 (Session 4)
- **Phase 9: Configuration Management with MicroProfile Config**
  - Migrated from Jackson TOML to MicroProfile Config with YAML support
  - Added SmallRye Config dependencies (microprofile-config-api, smallrye-config, smallrye-config-source-yaml)
  - Created configuration interfaces in `migraphe-core/config/`:
    - `ProjectConfig` - Project settings
    - `TargetConfig` - Database connection configuration
    - `TaskConfig` - Migration task definitions
    - `ConfigurationException` - Error handling
  - Converted data models from `record` to `interface` with `@ConfigMapping`
  - Removed custom `VariableExpander` (MP Config handles variable expansion automatically)
  - Created MP Config-based tests (7 new tests)
  - Test coverage: 100 tests (79 core + 21 PostgreSQL), 100% passing
  - **Design Decisions**:
    - YAML instead of TOML for better SmallRye Config support
    - Configuration interfaces in core (shared across CLI and Gradle plugin)
    - `Optional<T>` for optional configuration properties
    - Type-safe configuration binding with `@ConfigMapping`

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

**Last Updated**: 2026-01-04
**Phase**: 1-10 Complete (CLI Implementation Milestone Achieved)
**Next Milestone**: CLI Executable Packaging & Additional Commands
