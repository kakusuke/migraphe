# Migraphe User Guide

[日本語版はこちら](USER_GUIDE.ja.md)

## Table of Contents

1. [Introduction](#introduction)
2. [Installation](#installation)
3. [Project Setup](#project-setup)
4. [Configuration](#configuration)
5. [Writing Migrations](#writing-migrations)
6. [Running Migrations](#running-migrations)
7. [Rollback (down)](#rollback-down)
8. [Configuration Validation (validate)](#configuration-validation-validate)
9. [Environment Management](#environment-management)
10. [Advanced Features](#advanced-features)
11. [Gradle Plugin](#gradle-plugin)
12. [Troubleshooting](#troubleshooting)

## Introduction

Migraphe is a migration orchestration tool designed to manage complex database migrations across multiple environments. It uses a directed acyclic graph (DAG) to represent dependencies between migration tasks, ensuring they execute in the correct order.

### Key Concepts

- **Migration Task**: A single unit of migration work (e.g., creating a table)
- **Target**: A database connection configuration
- **Environment**: Execution context (development, staging, production)
- **Task ID**: Automatically generated from file path (e.g., `tasks/db1/001_create_users.yaml` → `db1/001_create_users`)
- **Dependency**: Relationship between tasks that determines execution order
- **History**: Record of executed migrations stored in a database

## Installation

### Prerequisites

- Java 21 or later
- PostgreSQL database

### Build from Source

```bash
# Clone the repository
git clone https://github.com/yourusername/migraphe.git
cd migraphe

# Build the Fat JAR
./gradlew fatJar

# The executable JAR is created at:
# migraphe-cli/build/libs/migraphe-cli-all.jar
```

### Create an Alias (Optional)

For convenience, create an alias in your shell:

```bash
# Add to ~/.bashrc or ~/.zshrc
alias migraphe='java -jar /path/to/migraphe-cli-all.jar'

# Reload shell configuration
source ~/.bashrc  # or source ~/.zshrc

# Now you can use:
migraphe status
migraphe up
```

### Installing Plugins

Migraphe uses a plugin architecture where database support is provided by separate plugins.

**Plugin Placement:**

Place plugin JAR files in the `plugins/` directory of your project:

```
my-project/
├── migraphe.yaml
├── plugins/                      # Plugin directory
│   └── migraphe-plugin-postgresql-x.x.x.jar
├── targets/
└── tasks/
```

**Available Plugins:**

| Plugin | Description |
|--------|-------------|
| `migraphe-plugin-postgresql` | PostgreSQL database support |

**Getting Plugin JARs:**

```bash
# Build fat JAR (includes JDBC driver)
./gradlew :migraphe-plugin-postgresql:fatJar

# Copy fat JAR to plugins/
mkdir -p my-project/plugins
cp migraphe-plugin-postgresql/build/libs/migraphe-plugin-postgresql-*-all.jar my-project/plugins/
```

**Note:** Use the `-all.jar` (fat JAR) for CLI usage. The thin JAR is for Gradle/Maven dependency management.

## Project Setup

### Directory Structure

Create the following directory structure for your migration project:

```
my-project/
├── migraphe.yaml              # Project configuration
├── targets/                   # Database connection configs
│   ├── db1.yaml
│   ├── db2.yaml
│   └── history.yaml
├── tasks/                     # Migration task definitions
│   ├── db1/
│   │   ├── 001_create_schema.yaml
│   │   ├── 002_create_users.yaml
│   │   └── 003_create_posts.yaml
│   └── db2/
│       └── 001_initial_schema.yaml
└── environments/              # Optional: environment-specific overrides
    ├── development.yaml
    └── production.yaml
```

### Minimum Required Files

At minimum, you need:

1. `migraphe.yaml` - Project configuration
2. `targets/history.yaml` - History storage configuration
3. At least one target file (e.g., `targets/db1.yaml`)
4. At least one task file (e.g., `tasks/db1/001_initial.yaml`)

## Configuration

### Project Configuration (`migraphe.yaml`)

```yaml
project:
  name: my-project

history:
  target: history  # Target name for storing execution history
```

**Fields:**
- `project.name` (required): Project identifier
- `history.target` (required): Target name where migration history is stored

### Target Configuration

Target files define database connections. Place them in the `targets/` directory.

**Example: `targets/db1.yaml`**

```yaml
type: postgresql
jdbc_url: jdbc:postgresql://localhost:5432/mydb
username: myuser
password: mypassword
```

**Fields:**
- `type` (required): Database type (currently only `postgresql` supported)
- `jdbc_url` (required): JDBC connection URL
- `username` (required): Database username
- `password` (required): Database password

Note: The target name is derived from the filename (e.g., `db1.yaml` → target name `db1`).

**Example: `targets/history.yaml`**

```yaml
type: postgresql
jdbc_url: jdbc:postgresql://localhost:5432/migraphe_history
username: historyuser
password: historypass
```

### Task Configuration

Task files define individual migrations. Place them in the `tasks/` directory.

**Task ID Generation:**
Task IDs are automatically generated from the file path relative to `tasks/`:
- `tasks/db1/001_create_users.yaml` → Task ID: `db1/001_create_users`
- `tasks/db1/schema/initial.yaml` → Task ID: `db1/schema/initial`

**Example: `tasks/db1/001_create_users.yaml`**

```yaml
name: Create users table
target: db1
up: |
  CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );
down: |
  DROP TABLE IF EXISTS users;
```

**Fields:**
- `name` (required): Human-readable task description
- `target` (required): Target name (must match a target configuration)
- `dependencies` (optional): List of task IDs this task depends on
- `up` (required): SQL to execute for forward migration
- `down` (optional): SQL to execute for rollback
- `autocommit` (optional): Execute without transaction (see [Autocommit Mode](#autocommit-mode))

### Environment-Specific Configuration

Environment files override base configuration for specific environments.

**Example: `environments/production.yaml`**

```yaml
target:
  db1:
    jdbc_url: jdbc:postgresql://prod-db.example.com:5432/mydb
    password: ${DB_PASSWORD}  # Environment variable substitution
```

Variable substitution using `${VAR}` is supported via MicroProfile Config.

## Writing Migrations

### Basic Migration

```yaml
name: Create posts table
target: db1
up: |
  CREATE TABLE posts (
    id SERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );
down: |
  DROP TABLE IF EXISTS posts;
```

### Migration with Dependencies

```yaml
name: Create comments table
target: db1
dependencies:
  - db1/001_create_users
  - db1/002_create_posts
up: |
  CREATE TABLE comments (
    id SERIAL PRIMARY KEY,
    post_id INTEGER REFERENCES posts(id) ON DELETE CASCADE,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );
down: |
  DROP TABLE IF EXISTS comments;
```

### Multi-Statement Migrations

PostgreSQL supports transactional DDL, so multiple statements are safe:

```yaml
name: Add indexes
target: db1
dependencies:
  - db1/001_create_users
up: |
  CREATE INDEX idx_users_email ON users(email);
  CREATE INDEX idx_users_created_at ON users(created_at);

  COMMENT ON TABLE users IS 'User account information';
  COMMENT ON COLUMN users.email IS 'Unique user email address';
down: |
  DROP INDEX IF EXISTS idx_users_email;
  DROP INDEX IF EXISTS idx_users_created_at;
```

### Autocommit Mode

Some SQL statements cannot run inside a transaction. For these cases, use `autocommit: true`:

**Common Use Cases:**
- `CREATE DATABASE` / `DROP DATABASE`
- `CREATE INDEX CONCURRENTLY`
- `VACUUM`
- `CLUSTER`

**Example: Create database**

```yaml
# tasks/admin/001_create_database.yaml
name: Create application database
target: admin
autocommit: true
up: |
  CREATE DATABASE myapp;
down: |
  DROP DATABASE myapp;
```

**Important Notes:**
- Autocommit migrations do NOT have automatic rollback on failure
- If the SQL fails partway through, partial changes may persist
- Use with caution and only when necessary

### Best Practices

1. **Always provide DOWN migrations**: Enables rollback capability
2. **Use sequential numbering**: Makes ordering obvious (001, 002, 003...)
3. **One logical change per task**: Easier to understand and rollback
4. **Use descriptive names**: Clear task names improve readability
5. **Test migrations locally**: Verify both UP and DOWN work correctly

## Running Migrations

### Check Migration Status

```bash
java -jar migraphe-cli-all.jar status
```

**Output:**
```
Migration Status
================

● [ ] db1/001_create_users - Create users table
│
● [ ] db1/002_create_posts - Create posts table
│
● [✓] db1/003_create_comments - Create comments table (58ms, 2026-01-23 10:30:00)

Summary: Total: 3 | Executed: 1 | Pending: 2
```

### Execute Migrations

```bash
# Execute all pending migrations
java -jar migraphe-cli-all.jar up

# Skip confirmation prompt
java -jar migraphe-cli-all.jar up -y

# Show execution plan only (don't actually execute)
java -jar migraphe-cli-all.jar up --dry-run

# Execute up to a specific migration (only the specified ID and its dependencies)
java -jar migraphe-cli-all.jar up <id>

# Combine options
java -jar migraphe-cli-all.jar up -y --dry-run db1/002_create_posts
```

**Example Output:**
```
Migrations to execute:

● [ ] db1/001_create_users - Create users table
│
● [ ] db1/002_create_posts - Create posts table

2 migrations will be executed.

Proceed? [y/N]: y

Executing migrations...

[OK]   Create users table (45ms)
[OK]   Create posts table (32ms)

Migration completed successfully. 2 migrations executed.
```

### Command Options

| Option | Description |
|--------|-------------|
| `<id>` | Execute only the specified migration and its dependencies |
| `-y` | Skip confirmation prompt |
| `--dry-run` | Show execution plan only without executing |

### Colored Output

Migration results are displayed with colors:

- **[OK]** (green): Migration succeeded
- **[SKIP]** (yellow): Already executed, skipped
- **[FAIL]** (red): Migration failed

Color output can be disabled by setting the `NO_COLOR` environment variable.

### Failure Details

When a migration fails, detailed information is displayed:

```
[FAIL] Create posts table (12ms)

=== MIGRATION FAILED ===

Environment:
  Target: db1

SQL Content:
   1 | CREATE TABLE posts (
   2 |   id SERIAL PRIMARY KEY,
   3 |   title VARCHAR(200) NOT NULL
   4 | );

Error:
  relation "posts" already exists
```

### Environment-Specific Execution

```bash
# Load production environment overrides
java -jar migraphe-cli-all.jar up --env production

# Load development environment overrides
java -jar migraphe-cli-all.jar up --env development
```

## Rollback (down)

The `down` command rolls back migrations to a specified version.

### Basic Usage

```bash
# Rollback migrations that depend on the specified version
java -jar migraphe-cli-all.jar down <version>

# Rollback all migrations
java -jar migraphe-cli-all.jar down --all

# Skip confirmation prompt
java -jar migraphe-cli-all.jar down -y <version>
java -jar migraphe-cli-all.jar down -y --all

# Show execution plan only (don't actually execute)
java -jar migraphe-cli-all.jar down --dry-run <version>
java -jar migraphe-cli-all.jar down --dry-run --all
```

### How It Works

#### Version-Specific Rollback

The `down <version>` command rolls back the specified version (node) **itself** and all migrations that **directly or indirectly depend on** it.

**Example:**
```
Dependency graph:
V001 <- V002 <- V003
  ↑
V004 (depends only on V001)

migraphe down V002 execution:
✓ V003 rolled back (depends on V002)
✓ V002 rolled back (specified version)
✗ V004 unchanged (doesn't depend on V002)
✗ V001 unchanged (V002's dependency)
```

#### --all Option

The `down --all` command rolls back **all** executed migrations. They are executed in reverse dependency order to maintain data integrity.

**Example:**
```bash
$ java -jar migraphe-cli-all.jar down --all

The following migrations will be rolled back:
  - db1/003_create_comments: Create comments table
  - db1/002_create_posts: Create posts table
  - db1/001_create_users: Create users table

Rolling back all migrations.

Proceed with rollback? [y/N]: y

Rolling back...
  [DOWN] Create comments table ... OK (15ms)
  [DOWN] Create posts table ... OK (12ms)
  [DOWN] Create users table ... OK (10ms)

Rollback complete. 3 migrations rolled back.
```

### Execution Flow

```bash
$ java -jar migraphe-cli-all.jar down db1/001_create_users

The following migrations will be rolled back:
  - db1/003_create_comments: Create comments table
  - db1/002_create_posts: Create posts table
  - db1/001_create_users: Create users table

Rollback includes: db1/001_create_users (Create users table)

Proceed with rollback? [y/N]: y

Rolling back...
  [DOWN] Create comments table ... OK (15ms)
  [DOWN] Create posts table ... OK (12ms)
  [DOWN] Create users table ... OK (10ms)

Rollback complete. 3 migrations rolled back.
```

### dry-run Option

Preview what would be rolled back without actually executing:

```bash
$ java -jar migraphe-cli-all.jar down --dry-run db1/001_create_users

[DRY RUN] The following migrations would be rolled back:
  - db1/003_create_comments: Create comments table
  - db1/002_create_posts: Create posts table
  - db1/001_create_users: Create users table

Rollback includes: db1/001_create_users (Create users table)

No changes made (dry run).
```

### Important Notes

1. **DOWN migration required**: Tasks must have `down` SQL defined for rollback
2. **Dependency order**: Migrations that are depended upon are rolled back first
3. **Recorded in history**: Rollbacks are recorded in the history table (direction: DOWN)
4. **Only executed migrations**: Only migrations marked as executed in history are rolled back

## Configuration Validation (validate)

The `validate` command validates configuration files offline. It checks all files without connecting to the database and displays all errors at once.

### Basic Usage

```bash
java -jar migraphe-cli-all.jar validate
```

### What Gets Validated

1. **Project configuration**: Existence and validity of `migraphe.yaml`
2. **Target configuration**: Required fields in `targets/*.yaml` (e.g., `type`)
3. **Task configuration**: Required fields in `tasks/**/*.yaml` (e.g., `name`, `target`, `up`)
4. **Dependencies**: Whether `dependencies` reference existing task IDs
5. **Graph structure**: No circular dependencies (cycles)

### Success Output

```
Validation
==========

Checking project configuration... OK
Checking targets (2 files)... OK
Checking tasks (5 files)... OK
Checking dependencies... OK
Checking graph structure... OK

Validation successful.
```

### Error Output

```
Validation
==========

Checking project configuration... OK
Checking targets (2 files)... FAIL
  × targets/test-db.yaml: Missing required property 'type'
Checking tasks (5 files)... FAIL
  × tasks/db1/create_users.yaml: Missing required property 'name'
  × tasks/db1/add_index.yaml: Target 'nonexistent' not found
Checking dependencies... FAIL
  × tasks/db1/add_index.yaml: Dependency 'db1/missing' not found
Checking graph structure... FAIL
  × Circular dependency detected: db1/a -> db1/b -> db1/a

Validation failed with 5 errors.
```

### Use Cases

- Pre-check in CI/CD pipelines
- Pull request validation
- Configuration file debugging
- Pre-production deployment verification

### Exit Codes

| Exit Code | Meaning |
|-----------|---------|
| 0 | Validation successful (no errors) |
| 1 | Validation failed (one or more errors) |

## Environment Management

### Development Environment

**`environments/development.yaml`**

```yaml
target:
  db1:
    jdbc_url: jdbc:postgresql://localhost:5432/mydb_dev
    username: devuser
    password: devpass

  history:
    jdbc_url: jdbc:postgresql://localhost:5432/migraphe_history_dev
```

### Production Environment

**`environments/production.yaml`**

```yaml
target:
  db1:
    jdbc_url: jdbc:postgresql://prod-db.company.com:5432/mydb
    username: produser
    password: ${PROD_DB_PASSWORD}  # From environment variable

  history:
    jdbc_url: jdbc:postgresql://prod-db.company.com:5432/migraphe_history
    password: ${PROD_HISTORY_PASSWORD}
```

### Using Environment Variables

Set environment variables before running:

```bash
export PROD_DB_PASSWORD=secretpassword
export PROD_HISTORY_PASSWORD=historypassword

java -jar migraphe-cli-all.jar up --env production
```

## Advanced Features

### Parallel Execution

Migraphe automatically parallelizes independent migrations at the same dependency level:

```
Level 0 (executed in parallel):
  - db1/001_create_users
  - db2/001_create_products

Level 1 (executed in parallel after Level 0):
  - db1/002_create_posts (depends on db1/001_create_users)
  - db2/002_create_orders (depends on db2/001_create_products)
```

### Complex Dependency Graphs

You can create complex dependency structures:

```yaml
# tasks/db1/005_final_setup.yaml
name: Final setup
target: db1
dependencies:
  - db1/001_create_users
  - db1/002_create_posts
  - db1/003_create_comments
  - db1/004_add_indexes
up: |
  -- Final setup that requires all previous migrations
  CREATE VIEW recent_posts AS
  SELECT p.*, u.name as author_name
  FROM posts p
  JOIN users u ON p.user_id = u.id
  WHERE p.created_at > NOW() - INTERVAL '30 days';
down: |
  DROP VIEW IF EXISTS recent_posts;
```

### Execution History

Migration history is stored in the `migraphe_history` table:

```sql
-- Query execution history
SELECT * FROM migraphe_history
ORDER BY executed_at DESC;

-- Check specific migration
SELECT * FROM migraphe_history
WHERE node_id = 'db1/001_create_users';
```

**History Table Schema:**
- `id`: Unique execution ID (UUID)
- `node_id`: Task ID
- `environment_id`: Environment name
- `direction`: UP or DOWN
- `status`: SUCCESS, FAILURE, or SKIPPED
- `description`: Task name
- `executed_at`: Execution timestamp
- `duration_ms`: Execution duration
- `serialized_down_task`: Rollback SQL (UP migrations only)
- `error_message`: Error details (FAILURE status only)

## Gradle Plugin

Migraphe provides a Gradle plugin for integrating migrations into your build process.

> **Note:** The plugin is not yet published to Maven Central / Gradle Plugin Portal. A local build is required to use it.

### Setup

Add to your `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.kakusuke.migraphe")
}

migraphe {
    baseDir.set(layout.projectDirectory.dir("db")) // default: project directory
}

dependencies {
    migraphePlugin("io.github.kakusuke:migraphe-plugin-postgresql:0.1.0")
}
```

### Available Tasks

| Task | Description |
|------|-------------|
| `migrapheValidate` | Validate configuration files (offline, no DB connection) |
| `migrapheStatus` | Show migration execution status |
| `migrapheUp` | Execute forward (UP) migrations |
| `migrapheDown` | Execute rollback (DOWN) migrations |

### Task Options

**migrapheUp**:
- `--target=<nodeId>` — Migrate up to a specific node
- `--dry-run` — Preview without executing

**migrapheDown**:
- `--target=<nodeId>` — Rollback to a specific node
- `--all` — Rollback all executed migrations
- `--dry-run` — Preview without executing

Options can also be specified via project properties (`-P`):

```bash
./gradlew migrapheUp -Pmigraphe.up.target=db1/create_users
./gradlew migrapheDown -Pmigraphe.down.all=true
```

## Troubleshooting

### Common Issues

#### 1. "No plugin found for type" Error

**Problem:**
```
No plugin found for type 'postgresql'.
No plugins are currently loaded.

To use this plugin type:
  1. Place the plugin JAR file in ./plugins/ directory
  2. Ensure the JAR contains META-INF/services/io.github.kakusuke.migraphe.api.spi.MigraphePlugin
```

**Solution:**
- Place plugin JAR file in `plugins/` directory
- Verify the plugin JAR is the correct version
- See [Installing Plugins](#installing-plugins) section

#### 2. "Target not found" Error

**Problem:**
```
Error: Target 'db1' not found in configuration
```

**Solution:**
- Verify `targets/db1.yaml` exists
- Check target name matches exactly (case-sensitive)
- Ensure YAML syntax is correct

#### 2. "Cyclic dependency detected" Error

**Problem:**
```
Error: Cyclic dependency detected in migration graph
```

**Solution:**
- Review task dependencies
- Remove circular references
- Dependencies must form a DAG (directed acyclic graph)

#### 3. Connection Failures

**Problem:**
```
Error: Could not connect to database
```

**Solution:**
- Verify database is running
- Check JDBC URL, username, password
- Test connection manually: `psql -h localhost -U myuser -d mydb`
- Check firewall settings

#### 4. Migration Already Executed

**Behavior:**
Migraphe automatically skips already-executed migrations:

```
Level 0:
  [SKIP] Create users table (already executed)
```

This is expected behavior. To re-run, manually delete from history:

```sql
DELETE FROM migraphe_history WHERE node_id = 'db1/001_create_users';
```

#### 5. Migration Failure

**Problem:**
```
Level 0:
  [FAIL] Create users table - ERROR: syntax error at or near "CRATE"
```

**Solution:**
- Fix SQL syntax in task file
- Delete failed record from history
- Re-run migration

```sql
-- Check error details
SELECT error_message FROM migraphe_history
WHERE node_id = 'db1/001_create_users' AND status = 'FAILURE';

-- Remove failed record to retry
DELETE FROM migraphe_history
WHERE node_id = 'db1/001_create_users' AND status = 'FAILURE';
```

### Debug Tips

1. **Check configuration loading:**
   ```bash
   # Add verbose logging (future feature)
   java -jar migraphe-cli-all.jar status --verbose
   ```

2. **Validate YAML syntax:**
   ```bash
   # Use yamllint or similar tool
   yamllint migraphe.yaml targets/ tasks/
   ```

3. **Test database connection:**
   ```bash
   psql -h localhost -U myuser -d mydb
   ```

4. **Review execution history:**
   ```sql
   SELECT node_id, status, executed_at, duration_ms, error_message
   FROM migraphe_history
   ORDER BY executed_at DESC
   LIMIT 10;
   ```

## Next Steps

- Explore the [Architecture Documentation](../CLAUDE.md) for design details
- Check the [Japanese User Guide](USER_GUIDE.ja.md) for translations
- Review example projects in `examples/` directory (if available)

## Support

For issues and questions:
- GitHub Issues: https://github.com/kakusuke/migraphe/issues
- Documentation: https://github.com/kakusuke/migraphe/tree/main/docs
