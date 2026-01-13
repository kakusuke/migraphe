# Migraphe

A migration orchestration tool that manages database and infrastructure migrations across multiple environments using a directed acyclic graph (DAG) structure.

[日本語版 README](README.ja.md)

## Features

- **DAG-based Migration**: Define complex dependencies between migration tasks
- **Multi-Environment Support**: Manage migrations across development, staging, and production
- **Pluggable Architecture**: Support for PostgreSQL, with extensibility for other databases
- **YAML Configuration**: Simple, readable configuration files
- **Execution History**: Track migration execution history with rollback support
- **Type-Safe**: Built with Java 21, leveraging modern language features

## Quick Start

### Prerequisites

- Java 21 or later
- PostgreSQL database (for running migrations)

### Build

```bash
./gradlew fatJar
```

This creates a standalone JAR file at `migraphe-cli/build/libs/migraphe-cli-all.jar`.

### Create a Project

1. Create a project directory:

```bash
mkdir my-project
cd my-project
```

2. Create the configuration structure:

```bash
mkdir -p targets tasks/db1
```

3. Create `migraphe.yaml`:

```yaml
project:
  name: my-project

history:
  target: history
```

4. Create `targets/db1.yaml`:

```yaml
type: postgresql
jdbc_url: jdbc:postgresql://localhost:5432/mydb
username: myuser
password: mypassword
```

5. Create `targets/history.yaml`:

```yaml
type: postgresql
jdbc_url: jdbc:postgresql://localhost:5432/migraphe_history
username: myuser
password: mypassword
```

6. Create `tasks/db1/001_create_users.yaml`:

```yaml
name: Create users table
target: db1
up: |
  CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL
  );
down: |
  DROP TABLE IF EXISTS users;
```

### Run Migrations

```bash
# Check migration status
java -jar path/to/migraphe-cli-all.jar status

# Execute migrations
java -jar path/to/migraphe-cli-all.jar up
```

## Documentation

- [User Guide](docs/USER_GUIDE.md) - Detailed documentation
- [User Guide (Japanese)](docs/USER_GUIDE.ja.md) - 日本語ユーザーガイド

## Project Structure

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

## Architecture

Migraphe is built with:

- **Domain-Driven Design (DDD)**: Clear separation of concerns
- **Interface-Driven Architecture**: Pluggable components
- **Java 21**: Modern language features (records, sealed interfaces, pattern matching)
- **MicroProfile Config**: Type-safe configuration management
- **NullAway + jspecify**: Compile-time null safety checks
- **Gradle**: Build automation with Kotlin DSL

### Core Concepts

- **MigrationNode**: A single migration task with dependencies
- **MigrationGraph**: DAG that ensures execution order
- **Environment**: Database connection configuration
- **Task**: Executable migration logic (up/down)
- **HistoryRepository**: Tracks executed migrations

## Development

### Build from Source

```bash
# Clone repository
git clone https://github.com/yourusername/migraphe.git
cd migraphe

# Build project
./gradlew build

# Run tests
./gradlew test

# Apply code formatting
./gradlew spotlessApply
```

### Running Tests

```bash
# All tests
./gradlew test

# Specific module
./gradlew :migraphe-core:test
./gradlew :migraphe-postgresql:test
./gradlew :migraphe-cli:test
```

Test coverage: 177+ tests, 100% passing

## Contributing

This project follows:

- **TDD (Test-Driven Development)**: All features are test-first
- **Code Formatting**: Spotless with Google Java Format
- **100% Test Pass Rate**: All tests must pass before commit

## License

[Your License Here]

## Links

- [User Guide](docs/USER_GUIDE.md)
- [Architecture Documentation](CLAUDE.md) - Detailed design decisions
