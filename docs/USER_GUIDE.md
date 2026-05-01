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
9. [Schema Documentation Generation (generate)](#schema-documentation-generation-generate)
10. [Environment Management](#environment-management)
11. [Advanced Features](#advanced-features)
12. [Gradle Plugin](#gradle-plugin)
13. [Troubleshooting](#troubleshooting)

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
- A supported database (PostgreSQL, MySQL 8.0+, or any JDBC-compatible database)

### Download a Release (Recommended)

```bash
# Tarball — Linux / macOS
curl -L https://github.com/kakusuke/migraphe/releases/download/v0.1.0/migraphe-0.1.0.tar.gz | tar xz
export PATH="$PWD/migraphe-0.1.0/bin:$PATH"

# Zip — Windows
curl -L -o migraphe.zip https://github.com/kakusuke/migraphe/releases/download/v0.1.0/migraphe-0.1.0.zip
unzip migraphe.zip
export PATH="$PWD/migraphe-0.1.0/bin:$PATH"

# Fat JAR — single file
curl -L -o migraphe.jar https://github.com/kakusuke/migraphe/releases/download/v0.1.0/migraphe-0.1.0-all.jar
alias migraphe="java -jar $PWD/migraphe.jar"
```

### Build from Source

```bash
# Clone the repository
git clone https://github.com/kakusuke/migraphe.git
cd migraphe

# Build the CLI
./gradlew :migraphe-cli:installDist

# The CLI is created at:
# migraphe-cli/build/install/migraphe/bin/migraphe
export PATH="$PWD/migraphe-cli/build/install/migraphe/bin:$PATH"
```

The rest of this guide assumes `migraphe` is on your `PATH`.

### Installing Plugins

Migraphe uses a plugin architecture where database support is provided by separate plugins.

**Available Plugins:**

| Plugin | Type | Description |
|--------|------|-------------|
| `migraphe-plugin-postgresql` | `postgresql` | PostgreSQL database support (includes `postgresql-schema` source and `postgresql-markdown` output plugins) |
| `migraphe-plugin-mysql` | `mysql` | MySQL 8.0+ database support (includes `mysql-schema` source and `mysql-markdown` output plugins) |
| `migraphe-plugin-jdbc` | `jdbc` | Generic JDBC support (works with any JDBC database) |
| `migraphe-plugin-generator-json` | `output-json` | JSON output generator plugin |

#### Method 1: Maven Coordinates (Recommended)

Add a `plugins` section to `migraphe.yaml` with Maven coordinates. The CLI automatically resolves dependencies from `~/.m2/repository` and Maven Central:

```yaml
plugins:
  - io.github.kakusuke.migraphe:migraphe-plugin-postgresql:0.1.0-SNAPSHOT
  - io.github.kakusuke.migraphe:migraphe-plugin-generator-json:0.1.0-SNAPSHOT

project:
  name: my-project
history:
  target: history
```

If using locally built plugins, publish them first:

```bash
./gradlew publishToMavenLocal
```

Transitive dependencies (e.g., JDBC drivers, Jackson) are resolved automatically from Maven Central.

##### Custom Repositories (e.g., JitPack)

Add a `repositories:` block with extra entries (HTTPS only) and reference them per plugin via the map form:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.user:custom-plugin:v1.2.3
    repository: jitpack
  - io.github.kakusuke.migraphe:migraphe-plugin-postgresql:0.1.0-SNAPSHOT
```

`maven-central` is always available implicitly; you do not need to redeclare it.

##### Lockfile (`migraphe.lock.yaml`)

Migraphe pins every plugin and transitive JAR by SHA-256 in a lockfile. **A lockfile is required whenever `plugins:` is declared** — the CLI refuses to start otherwise.

Generate or refresh the lockfile after editing `plugins:`:

```bash
migraphe pin
```

This resolves each plugin from the configured repositories, computes SHA-256 hashes for all resolved JARs, and writes `migraphe.lock.yaml`. Commit this file to source control alongside `migraphe.yaml`.

For CI, use `--check` to verify the committed lockfile is up to date without writing:

```bash
migraphe pin --check
```

Exit code is non-zero when the lockfile is missing or differs from what re-resolution would produce. `migraphe validate` performs an offline lock-sync check as well.

If a JAR is tampered with after pinning (for example, a corrupted local cache), startup fails with a checksum mismatch error pointing to the affected coordinate.

#### Method 2: plugins/ Directory (Legacy)

Place plugin JAR files directly in the `plugins/` directory of your project:

```
my-project/
├── migraphe.yaml
├── plugins/                      # Plugin directory
│   └── migraphe-plugin-postgresql-x.x.x.jar
├── targets/
└── tasks/
```

**Note:** Both methods can be used simultaneously. Maven-resolved plugins are loaded first, then `plugins/` directory.

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
plugins:
  - io.github.kakusuke.migraphe:migraphe-plugin-postgresql:0.1.0-SNAPSHOT

project:
  name: my-project

history:
  target: history  # Target name for storing execution history
```

**Fields:**
- `plugins` (optional): List of Maven coordinates (`groupId:artifactId:version`) for CLI plugin resolution
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
- `type` (required): Database type (`postgresql`, `mysql`, or `jdbc`)
- `jdbc_url` (required): JDBC connection URL
- `username` (required): Database username
- `password` (required): Database password
- `driver_class` (required for `jdbc` type): Fully qualified JDBC driver class name
- `db_label` (optional, `jdbc` type only): Display label for the database (e.g., "MariaDB")

Note: The target name is derived from the filename (e.g., `db1.yaml` → target name `db1`).

**Example: `targets/history.yaml`**

```yaml
type: postgresql
jdbc_url: jdbc:postgresql://localhost:5432/migraphe_history
username: historyuser
password: historypass
```

**Example: MySQL target (`targets/mysql_db.yaml`)**

```yaml
type: mysql
jdbc_url: jdbc:mysql://localhost:3306/myapp
username: dbuser
password: secret
```

**Example: Generic JDBC target (`targets/mariadb.yaml`)**

```yaml
type: jdbc
driver_class: org.mariadb.jdbc.Driver
db_label: MariaDB
jdbc_url: jdbc:mariadb://localhost:3306/myapp
username: user
password: secret
```

The generic JDBC plugin (`type: jdbc`) can be used with any JDBC-compatible database. You need to provide the `driver_class` and ensure the JDBC driver JAR is available on the classpath.

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
migraphe status
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
migraphe up

# Skip confirmation prompt
migraphe up -y

# Show execution plan only (don't actually execute)
migraphe up --preview

# Execute up to a specific migration (only the specified ID and its dependencies)
migraphe up <id>

# Combine options
migraphe up -y --preview db1/002_create_posts
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
| `--preview` | Show execution plan only without executing |

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
migraphe up --env production

# Load development environment overrides
migraphe up --env development
```

## Rollback (down)

The `down` command rolls back migrations to a specified version.

### Basic Usage

```bash
# Rollback migrations that depend on the specified version
migraphe down <version>

# Rollback all migrations
migraphe down --all

# Skip confirmation prompt
migraphe down -y <version>
migraphe down -y --all

# Show execution plan only (don't actually execute)
migraphe down --preview <version>
migraphe down --preview --all
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
$ migraphe down --all

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
$ migraphe down db1/001_create_users

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
$ migraphe down --preview db1/001_create_users

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
migraphe validate
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

## Schema Documentation Generation (generate)

The `generate` command generates documentation and data exports from various sources. The generator system uses a **source/output plugin architecture** — source plugins extract data, and output plugins render it in the desired format. The same data source can be output in multiple formats.

### Configuration

Add a `generators` section to `migraphe.yaml`:

```yaml
project:
  name: my-project

history:
  target: history

generators:
  # Schema documentation as Markdown
  - name: schema-docs
    type: jdbc-markdown
    source:
      type: jdbc-schema
      target: db1
    output-dir: docs/schema
    excludes:
      - schema: "information_schema"
      - schema: "public"
        table: "tmp_.*"

  # Migration tree as JSON to stdout
  - name: tree
    type: output-json
    source:
      type: migration-tree
    output-dir: docs
```

**Fields:**
- `name` (required): Identifier for this generator
- `type` (required): Output plugin type (e.g., `jdbc-markdown`, `output-json`)
- `source` (required for source/output flow):
  - `type`: Source plugin type (e.g., `jdbc-schema`, `migration-tree`)
  - `target` (optional): Target name for source plugins that need a database connection
- `output-dir` (optional, default: `docs/schema`): Directory where generated files are written
- `excludes` (optional): List of exclusion filters (regex patterns)
  - `schema`: Regex pattern to match schema names
  - `table`: Regex pattern to match table names (used with `schema`)

### Available Source Plugins

| Plugin | Type | Data | Description |
|--------|------|------|-------------|
| `migraphe-plugin-jdbc` | `jdbc-schema` | `JdbcSchemaInfo` | Extracts database schema metadata via JDBC DatabaseMetaData |
| `migraphe-plugin-postgresql` | `postgresql-schema` | `PostgreSQLSchemaInfo` | Extracts JDBC base schema + PostgreSQL-specific metadata (extensions, enums, sequences, functions, triggers, materialized views, partitions, policies) from pg_catalog |
| `migraphe-plugin-mysql` | `mysql-schema` | `MySQLSchemaInfo` | Extracts JDBC base schema + MySQL-specific metadata (storage engines, table meta, triggers, routines, events, partitions) from information_schema |
| (built-in) | `migration-tree` | `MigrationGraphView` | Provides the migration DAG structure |

### Available Output Plugins

| Plugin | Type | Description |
|--------|------|-------------|
| `migraphe-plugin-jdbc` | `jdbc-markdown` | Generates Markdown documentation from `JdbcSchemaInfo` |
| `migraphe-plugin-postgresql` | `postgresql-markdown` | Generates Markdown documentation with PostgreSQL-specific objects (extensions, enums, sequences, functions, triggers, materialized views, partitions, policies) |
| `migraphe-plugin-mysql` | `mysql-markdown` | Generates Markdown documentation with MySQL-specific objects (storage engines, table metadata, triggers, routines, events, partitions) |
| `migraphe-plugin-generator-json` | `output-json` | Outputs any data as pretty-printed JSON to stdout |

### Basic Usage

```bash
# Generate documentation for all configured generators
migraphe generate

# Generate documentation for a specific generator only
migraphe generate --name mydb
```

### Output Structure (jdbc-markdown)

The `jdbc-markdown` generator produces the following directory structure:

```
docs/schema/
└── mydb/
    └── public/
        ├── index.md              # Schema overview (table/view listing)
        ├── tables/
        │   ├── users.md          # Table details (columns, keys, indexes)
        │   └── posts.md
        └── views/
            └── recent_posts.md   # View details
```

Each table documentation includes:
- Column definitions (name, type, nullable, default)
- Primary key and unique constraints
- Foreign key references with cross-links to referenced tables
- Indexes

#### Foreign-Key Rendering: Imported vs. Exported Keys

JDBC distinguishes two perspectives on a foreign-key relationship; the generator renders both for each table:

| Section in `tables/<name>.md` | JDBC source | Meaning | Link target |
|---|---|---|---|
| **Foreign Keys** | `DatabaseMetaData.getImportedKeys()` | FK columns *on this table* that reference another table's primary key | The referenced table |
| **Referenced By** | `DatabaseMetaData.getExportedKeys()` | FK columns *on other tables* that reference this table's primary key | The referencing (child) table |

Each row uses two distinct column lists:

- `columns` — the FK columns local to the table being rendered.
- `referencedColumns` — the primary-key columns on the linked table.

Concretely, when rendering `tables/users.md`:

- A row in **Foreign Keys** like `manager_id → users(id)` means `users.manager_id` references `users(id)`.
- A row in **Referenced By** like `posts(user_id) → id` means `posts.user_id` references `users.id`; the link points to `posts.md`, not back to `users.md`.

This distinction was a recent fix — earlier versions of the exported-key rendering pointed the link at the referenced (PK-side) table instead of the referencing (FK-side) table, which made `Referenced By` self-referential and useless.

### PostgreSQL-Specific Documentation

For PostgreSQL databases, use the `postgresql-markdown` output plugin with the `postgresql-schema` source plugin to generate comprehensive documentation that includes PostgreSQL-specific objects:

```yaml
generators:
  - name: mydb
    type: postgresql-markdown
    source:
      type: postgresql-schema
      target: db1
    output-dir: docs/schema
```

In addition to standard JDBC schema information (tables, views, columns, keys, indexes), the generated documentation includes:
- **Extensions** (e.g., `pgcrypto`, `uuid-ossp`) — with `Owner` column
- **Enum types** with their values — with `Owner` column
- **Sequences** with current values and parameters — with `Owned By` (dependent table.column from `pg_depend`) and `Owner` (role) columns
- **Functions** with argument types and return types — individual file shows `Owner` property
- **Triggers** with timing, events, and associated functions
- **Materialized views** with column definitions — individual file shows `Owner` property
- **Partitioned tables** with partition strategy and key
- **Row-Level Security (RLS) policies** with roles, commands, and expressions

Table-specific files also include related triggers, policies, and partition information for each table.

**Role ownership:** The Tables/Views index tables include an `Owner` column (PostgreSQL role name from `pg_get_userbyid(relowner)`), and each `tables/<name>.md` / `views/<name>.md` file prints an `Owner: <role>` line below the title.

### MySQL-Specific Documentation

For MySQL databases, use the `mysql-markdown` output plugin with the `mysql-schema` source plugin to generate comprehensive documentation that includes MySQL-specific objects:

```yaml
generators:
  mysql-docs:
    source:
      type: mysql-schema
      environment: db1
    output:
      type: mysql-markdown
    name: my-database
```

In addition to standard JDBC schema information (tables, views, columns, keys, indexes), the generated documentation includes:
- **Storage engines** available in the MySQL instance
- **Table metadata** including ENGINE, collation, and row format
- **Triggers** with timing, events, SQL statements, and `Definer`
- **Routines** (stored procedures and functions) with parameters, return types, and `Definer`
- **Events** with schedule, status, SQL body, and `Definer`
- **Partitioned tables** with partition method, expression, and partition details

Table-specific files also include related triggers and partition information for each table.

**`DEFINER` attribution:** The Views index table includes a `Definer` column (from `information_schema.VIEWS.DEFINER`), and each `views/<name>.md` file prints a `Definer: <user>` line below the title. Triggers, routines, and events carry their DEFINER through to the index tables / individual files as well. MySQL tables themselves have no DEFINER, so the Tables index is unchanged.

**Note:** MySQL JDBC returns databases as catalogs (not schemas). The `mysql-schema` source plugin uses catalog-based schema discovery via `connection.getCatalog()`.

### Exclude Filtering

Use `excludes` to skip schemas or tables matching regex patterns:

```yaml
generators:
  - name: mydb
    type: jdbc-markdown
    source:
      type: jdbc-schema
      target: db1
    output-dir: docs/schema
    excludes:
      - schema: "information_schema"     # Exclude entire schema
      - schema: "pg_catalog"             # Exclude PostgreSQL system schema
      - schema: "public"
        table: "tmp_.*"                  # Exclude temp tables in public schema
      - schema: ".*"
        table: "flyway_schema_history"   # Exclude specific table in all schemas
```

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

migraphe up --env production
```

## Advanced Features

### Parallel Execution

Migraphe supports opt-in parallel execution using Java Virtual Threads. When enabled, nodes whose dependencies have all completed are executed concurrently.

**Configuration (`migraphe.yaml`):**

```yaml
project:
  name: my-project

history:
  target: history

execution:
  parallel: true        # Enable parallel execution (default: false)
  max-parallelism: 4    # Limit concurrent tasks (0 = unlimited, default: 0)
```

- `execution.parallel`: Set to `true` to enable parallel execution. When `false` (default), migrations run sequentially in topological order.
- `execution.max-parallelism`: Limits the number of concurrently executing tasks. Set to `0` (default) for unlimited concurrency.

**How it works:**

Nodes at the same dependency level execute in parallel using Virtual Threads. A ready-based approach ensures that as soon as all dependencies of a node are satisfied, it becomes eligible for execution. If any task fails, fail-fast behavior stops new task submission immediately.

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

> **Note:** The plugin is not yet published to Maven Central / Gradle Plugin Portal. Use `./gradlew publishToMavenLocal` in the migraphe repository to install it locally.

### Setup

Add the plugin repository and version to your `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("io.github.kakusuke.migraphe") version "0.1.0-SNAPSHOT"
    }
}
```

Add to your `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.kakusuke.migraphe")
}

repositories {
    mavenLocal()
    mavenCentral()
}

migraphe {
    baseDir.set(layout.projectDirectory.dir("db")) // default: project directory
}

dependencies {
    // Choose the plugin(s) for your database:
    migraphePlugin("io.github.kakusuke.migraphe:migraphe-plugin-postgresql:0.1.0-SNAPSHOT")
    // migraphePlugin("io.github.kakusuke.migraphe:migraphe-plugin-mysql:0.1.0-SNAPSHOT")
    // migraphePlugin("io.github.kakusuke.migraphe:migraphe-plugin-jdbc:0.1.0-SNAPSHOT")
}
```

### Available Tasks

| Task | Description |
|------|-------------|
| `migrapheValidate` | Validate configuration files (offline, no DB connection) |
| `migrapheStatus` | Show migration execution status |
| `migrapheUp` | Execute forward (UP) migrations |
| `migrapheDown` | Execute rollback (DOWN) migrations |
| `migrapheGenerate` | Generate schema documentation |

### Task Options

**migrapheUp**:
- `--target=<nodeId>` — Migrate up to a specific node
- `--preview` — Preview without executing

**migrapheDown**:
- `--target=<nodeId>` — Rollback to a specific node
- `--all` — Rollback all executed migrations
- `--preview` — Preview without executing

**migrapheGenerate**:
- `--name=<name>` — Generate for a specific generator only

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
```

**Solution:**
- Add the plugin Maven coordinate to the `plugins` section in `migraphe.yaml`
- Run `./gradlew publishToMavenLocal` if using locally built plugins
- Alternatively, place plugin JAR file in `plugins/` directory
- See [Installing Plugins](#installing-plugins) section

#### 1b. "Failed to resolve plugin" Error

**Problem:**
```
Failed to resolve plugin: io.github.kakusuke.migraphe:migraphe-plugin-postgresql:0.1.0-SNAPSHOT
```

**Solution:**
- Ensure `./gradlew publishToMavenLocal` has been run
- Check that the Maven coordinate in `migraphe.yaml` is correct
- Verify `~/.m2/repository` contains the plugin artifacts
- Check network connectivity for Maven Central (required for transitive dependencies)

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
   migraphe status --verbose
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

## Distribution Roadmap

Migraphe artefacts are available through the following channels:

| Channel | Status | groupId | Scope |
|---------|--------|---------|-------|
| GitHub Releases (fat JAR) | ✅ Available | — | CLI binary |
| JitPack | 🚧 Contributor beta — see [CONTRIBUTING.md](../CONTRIBUTING.md#pre-release-builds-via-jitpack-beta-channel) | `com.github.*` | Plugin JARs (pre-release verification only) |
| Maven Central | 📅 Planned | `io.github.kakusuke.migraphe` | Plugin JARs (production) |

End users should wait for the Maven Central release. The JitPack channel is intended for Migraphe core contributors and plugin developers who need to verify against the latest `main` branch — its coordinates (`com.github.kakusuke.migraphe:*:main-SNAPSHOT`) are temporary and will not be supported once Maven Central distribution is live.

## Next Steps

- Explore the [Architecture Documentation](../CLAUDE.md) for design details
- Check the [Japanese User Guide](USER_GUIDE.ja.md) for translations
- Review example projects in `examples/` directory (if available)

## Support

For issues and questions:
- GitHub Issues: https://github.com/kakusuke/migraphe/issues
- Documentation: https://github.com/kakusuke/migraphe/tree/main/docs
