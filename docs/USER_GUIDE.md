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
- A supported database (PostgreSQL, MySQL, MariaDB, or any JDBC-compatible database). Verified
  against PostgreSQL, MySQL 8.0 and MariaDB 10.1
  - **MySQL** 5.6.4 or later: the history table stores `TIMESTAMP(6)`, which Oracle MySQL 5.5
    cannot parse, so 5.5 is not supported
  - **MariaDB** from the 5.5 generation onwards: the history table's index key lengths stay within
    InnoDB's 767-byte limit, so it can be created on servers without `innodb_large_prefix`

### Install with mise (Recommended)

The release tarball ships `bin/` and `lib/` at its root, so mise's GitHub backend can pick it up with no extra options:

```bash
mise use github:kakusuke/migraphe
```

### Download a Release

```bash
# Tarball — Linux / macOS (extracts bin/ and lib/ into the target directory)
mkdir -p ~/.local/migraphe
curl -L https://github.com/kakusuke/migraphe/releases/download/v0.5.0/migraphe-0.5.0.tar.gz | tar xz -C ~/.local/migraphe
export PATH="$HOME/.local/migraphe/bin:$PATH"

# Zip — Windows
curl -L -o migraphe.zip https://github.com/kakusuke/migraphe/releases/download/v0.5.0/migraphe-0.5.0.zip
unzip migraphe.zip -d ~/.local/migraphe
export PATH="$HOME/.local/migraphe/bin:$PATH"

# Fat JAR — single file
curl -L -o migraphe.jar https://github.com/kakusuke/migraphe/releases/download/v0.5.0/migraphe-0.5.0-all.jar
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
| [`migraphe-plugin-postgresql`](../migraphe-plugin-postgresql/README.md) | `postgresql` | PostgreSQL database support (includes `postgresql-schema` source and `postgresql-markdown` output plugins) |
| [`migraphe-plugin-mysql`](../migraphe-plugin-mysql/README.md) | `mysql` | MySQL 8.0+ database support (includes `mysql-schema` source and `mysql-markdown` output plugins) |
| [`migraphe-plugin-jdbc`](../migraphe-plugin-jdbc/README.md) | `jdbc` | Generic JDBC support (works with any JDBC database) |
| [`migraphe-plugin-generator-json`](../migraphe-plugin-generator-json/README.md) | `output-json` | JSON output generator plugin |

Each plugin's `README.md` documents its target fields, connection examples, and database-specific behavior in full. Click a plugin name above for details.

#### Method 1: Maven Coordinates (Recommended)

Add a `plugins` section to `migraphe.yaml` with Maven coordinates. Migraphe plugins are distributed via JitPack — declare the JitPack repository and reference each plugin via the map form:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.5.0
    repository: jitpack
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-generator-json:v0.5.0
    repository: jitpack

project:
  name: my-project
history:
  target: history
```

`maven-central` is always available implicitly; you do not need to redeclare it. Transitive dependencies (e.g., JDBC drivers, Jackson) are resolved automatically from Maven Central.

##### Additional Repositories

You can add other HTTPS Maven repositories the same way and select them per plugin entry:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io
  - id: my-internal
    url: https://maven.internal.example.com/releases

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.5.0
    repository: jitpack
  - coordinate: com.example:internal-plugin:1.0.0
    repository: my-internal
```

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
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.5.0
    repository: jitpack

project:
  name: my-project

history:
  target: history  # Target name for storing execution history
```

**Fields:**
- `plugins` (optional): List of Maven coordinates (`groupId:artifactId:version`) for CLI plugin resolution
- `project.name` (required): Project identifier
- `project.scan-root` (optional): Base directory for locating `tasks/`, `targets/`, `environments/`, and `plugins/`. Accepts a relative path (resolved against the directory containing `migraphe.yaml`) or an absolute path. Defaults to the directory containing `migraphe.yaml`. The same setting is honored by both the CLI and the Gradle plugin.
- `history.target` (required): Target name where migration history is stored

**Example: `scan-root` to keep migration assets under a subdirectory**

```yaml
project:
  name: my-app
  scan-root: config
history:
  target: main
```

With this configuration, Migraphe reads tasks from `config/tasks/`, targets from `config/targets/`, environments from `config/environments/`, and the legacy plugin directory from `config/plugins/` — all relative to the directory containing `migraphe.yaml`.

### Target Configuration

Target files define database connections. Place them in the `targets/` directory.

**Example: `targets/db1.yaml`**

```yaml
type: postgresql
jdbc_url: jdbc:postgresql://localhost:5432/mydb
username: myuser
password: mypassword
```

**Common fields:** every target needs a `type` (the plugin that backs it) plus that plugin's connection settings (typically `jdbc_url`, `username`, `password`). **The exact field set is defined by each plugin** — for example the generic `jdbc` type additionally requires `driver_class`. See each plugin's README for the complete field list (required/optional, defaults) and per-database examples:

| Plugin | Type | Target fields & examples |
|--------|------|--------------------------|
| [`migraphe-plugin-postgresql`](../migraphe-plugin-postgresql/README.md) | `postgresql` | PostgreSQL connection fields |
| [`migraphe-plugin-mysql`](../migraphe-plugin-mysql/README.md) | `mysql` | MySQL connection fields |
| [`migraphe-plugin-jdbc`](../migraphe-plugin-jdbc/README.md) | `jdbc` | Generic JDBC fields (incl. `driver_class`, `db_label`) |

Note: The target name is derived from the filename (e.g., `db1.yaml` → target name `db1`).

**Example: `targets/history.yaml`** (used as the history store)

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
    password: ${env.DB_PASSWORD}  # OS environment variable

```

Variable substitution using `${VAR}` is supported via MicroProfile Config. Values resolve in this priority order (highest first): Gradle-injected variables, `environments/*.yaml` profiles, system properties (`-D`), then `migraphe.yaml`/`targets`/`tasks`. **OS environment variables must be referenced with the `env.` prefix — `${env.VAR}`, not `${VAR}`** — so that environment variables cannot leak into config keys such as `target.*`. Inline defaults are supported: `${env.VAR:default}`.

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

A single `up` / `down` may contain multiple statements separated by `;`. Migraphe splits the script using the **target's SQL dialect** and runs the statements sequentially — even in the default transaction mode (autocommit is **not** required for multiple statements). Dialect-specific constructs such as PostgreSQL dollar-quoting (`$$ ... $$`) and MySQL `BEGIN ... END` blocks / the `DELIMITER` directive are recognized so inner `;` characters do not split a routine body.

```yaml
name: Add indexes
target: db1
dependencies:
  - db1/001_create_users
up: |
  CREATE INDEX idx_users_email ON users(email);
  CREATE INDEX idx_users_created_at ON users(created_at);
down: |
  DROP INDEX IF EXISTS idx_users_email;
  DROP INDEX IF EXISTS idx_users_created_at;
```

The dialect rules and stored-procedure / function body examples (PostgreSQL dollar-quoting, MySQL `BEGIN ... END` / `DELIMITER`, generic `;` splitting) live in each plugin's README:

- PostgreSQL: [`migraphe-plugin-postgresql`](../migraphe-plugin-postgresql/README.md)
- MySQL: [`migraphe-plugin-mysql`](../migraphe-plugin-mysql/README.md)
- Generic JDBC: [`migraphe-plugin-jdbc`](../migraphe-plugin-jdbc/README.md)

### Comments in Migrations

SQL comments are preserved, not stripped. A leading comment stays attached to the statement that follows it — line comments (`--`, MySQL `#`) keep their trailing newline so the next statement is not accidentally commented out. Dialect-specific *executable* comments are honored: MySQL version-conditional comments (`/*! ... */`, `/*!50110 ... */`) are sent to the server and executed, and optimizer hints (`/*+ ... */`) are kept on the statement. Only empty or whitespace-only segments are dropped; a comment-only line is a harmless no-op.

### Autocommit Mode

> Autocommit is **not** required to run multiple statements — the default transaction mode already splits and executes them sequentially. Autocommit is only for statements that cannot run inside a transaction (e.g. `CREATE DATABASE`, `CREATE INDEX CONCURRENTLY`).

Some SQL statements cannot run inside a transaction. For these cases, set `autocommit: true` on the task; each statement is then committed immediately rather than wrapped in one transaction:

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

Which statements require autocommit is database-specific (e.g. PostgreSQL `CREATE INDEX CONCURRENTLY`, `VACUUM`, `CLUSTER`). See the plugin READMEs for dialect-specific use cases: [postgresql](../migraphe-plugin-postgresql/README.md), [mysql](../migraphe-plugin-mysql/README.md), [jdbc](../migraphe-plugin-jdbc/README.md).

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

Pass `--env <name>` to overlay `environments/<name>.yaml` on top of your `targets/` configuration. The overlay takes highest precedence and overrides target connection settings (e.g. `jdbc_url`, `username`, `password`). Supported by the `up`, `down`, and `status` commands:

```bash
# Apply environments/production.yaml overrides
migraphe up --env production
migraphe status --env production

# Apply environments/development.yaml overrides
migraphe up --env development
```

If `environments/<name>.yaml` does not exist, the flag is ignored (base configuration is used). `validate` and `generate` do not currently read `--env`.

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
- `er-diagram` (optional, default: `true`): For Markdown output plugins, embed a Mermaid ER diagram in `index.md`. This is the master switch for **all** ER-diagram output: setting it to `false` suppresses the diagram in `index.md` *and* on the individual table pages, regardless of `er-diagram-per-table`.
- `er-diagram-keys-only` (optional, default: `false`): When `true`, each ER-diagram entity lists only its primary-key and foreign-key columns (relationships are unaffected). The default `false` shows all columns.
- `er-diagram-layout` (optional, default: `elk`): Mermaid layout engine requested through a YAML frontmatter block emitted at the top of every generated `erDiagram` fence. Mermaid's official layout names are `elk`, `dagre`, `tidy-tree`, and `cose-bilkent`. Only values matching `[A-Za-z0-9_-]+` are honored; a value containing any other character omits the frontmatter entirely, so the fence starts directly with `erDiagram` as before. To opt out, give it a value outside that character set such as `er-diagram-layout: " "` — note that leaving the value blank (`er-diagram-layout:`) is a configuration error, not an opt-out.
- `er-diagram-per-table` (optional, default: `true`): When `true`, each table page also gets an `## ER Diagram` section (placed right after the page header, before `## Columns`) showing a neighborhood diagram centered on that table. Set to `false` to keep the ER diagram in `index.md` only.
- `er-diagram-per-table-max-entities` (optional, default: `60`): Upper bound on the number of entities a per-table neighborhood diagram may contain. Table pages whose neighborhood exceeds the limit render a short omission note plus a link to the full `index.md` diagram instead of the diagram itself. A value of `0` or lower means unlimited; a neighborhood of exactly the limit is still rendered.
- `excludes` (optional): List of exclusion filters (regex patterns)
  - `schema`: Regex pattern to match schema names
  - `table`: Regex pattern to match table names (used with `schema`)

The available source/output types and their **full per-type option tables** are documented in each plugin's README (linked below).

### Available Source Plugins

| Plugin | Type | Data | Description |
|--------|------|------|-------------|
| [`migraphe-plugin-jdbc`](../migraphe-plugin-jdbc/README.md) | `jdbc-schema` | `JdbcSchemaInfo` | Extracts database schema metadata via JDBC DatabaseMetaData |
| [`migraphe-plugin-postgresql`](../migraphe-plugin-postgresql/README.md) | `postgresql-schema` | `PostgreSQLSchemaInfo` | Extracts JDBC base schema + PostgreSQL-specific metadata (extensions, enums, sequences, functions, triggers, materialized views, partitions, policies) from pg_catalog |
| [`migraphe-plugin-mysql`](../migraphe-plugin-mysql/README.md) | `mysql-schema` | `MySQLSchemaInfo` | Extracts JDBC base schema + MySQL-specific metadata (storage engines, table meta, triggers, routines, events, partitions) from information_schema |
| (built-in) | `migration-tree` | `MigrationGraphView` | Provides the migration DAG structure |

### Available Output Plugins

| Plugin | Type | Description |
|--------|------|-------------|
| [`migraphe-plugin-jdbc`](../migraphe-plugin-jdbc/README.md) | `jdbc-markdown` | Generates Markdown documentation from `JdbcSchemaInfo` |
| [`migraphe-plugin-postgresql`](../migraphe-plugin-postgresql/README.md) | `postgresql-markdown` | Generates Markdown documentation with PostgreSQL-specific objects (extensions, enums, sequences, functions, triggers, materialized views, partitions, policies) |
| [`migraphe-plugin-mysql`](../migraphe-plugin-mysql/README.md) | `mysql-markdown` | Generates Markdown documentation with MySQL-specific objects (storage engines, table metadata, triggers, routines, events, partitions) |
| [`migraphe-plugin-generator-json`](../migraphe-plugin-generator-json/README.md) | `output-json` | Outputs any data as pretty-printed JSON to stdout |

### Basic Usage

```bash
# Generate documentation for all configured generators
migraphe generate

# Generate documentation for a specific generator only
migraphe generate --name mydb
```

### Output Structure

Markdown output plugins (`jdbc-markdown`, `postgresql-markdown`, `mysql-markdown`) write a single database-wide `index.md` directly under `output-dir`, plus one directory per schema (`<output-dir>/<name>/<schema>/`) containing a `tables/` and a `views/` directory. Every table page lists column definitions (name, type, nullable, default), primary/unique keys, foreign keys with cross-links — both the **Foreign Keys** (imported keys) and **Referenced By** (exported keys) perspectives — and indexes. The exact directory layout and the imported-vs-exported foreign-key rendering are documented in the [`migraphe-plugin-jdbc` README](../migraphe-plugin-jdbc/README.md).

By default, `index.md` also embeds a single database-wide **ER diagram** in Mermaid `erDiagram` notation (a fenced ```mermaid block, rendered inline by GitHub and most Markdown viewers). Each table becomes an entity with its columns (type plus PK/FK markers; a column that is both is marked `PK, FK`), and foreign keys become relationships (`||--o{`). The diagram is schema-aware: tables from different schemas are distinct entities even when they share a name, cross-schema foreign keys are drawn, and cross-schema table links in the per-table pages resolve to the referenced schema's directory. Column types are shown by their base name (e.g. a PostgreSQL enum type is rendered as `user_account_status`, not its quoted schema-qualified form). It stays a single combined diagram — Mermaid `erDiagram` has no grouping construct, so tables are not boxed per schema. Set `er-diagram: false` on the generator to suppress this section, or `er-diagram-keys-only: true` to keep the diagram compact by showing only primary-key and foreign-key columns for each entity.

#### Per-Table ER Diagrams

By default (`er-diagram-per-table: true`) each table page also carries its own `## ER Diagram` section, placed right after the page header and before `## Columns`. Instead of the whole database it shows only the table's **neighborhood**: the table itself, every table transitively reachable by following its foreign keys towards the referenced side (its ancestors, and their ancestors, and so on), and every table that transitively references it (its descendants, and their descendants). Relationships are drawn for every foreign key with both ends inside that set.

The neighborhood is deliberately *not* the whole undirected connected component: sibling branches — "another descendant of an ancestor", or "another ancestor of a descendant" — are not pulled in, which keeps the diagram focused even in densely linked schemas. Circular references, self-references, and cross-schema foreign keys are all handled, and tables removed by `excludes` cut the traversal, so a neighborhood never reaches through an excluded table.

When a neighborhood grows past `er-diagram-per-table-max-entities` (default `60`), the diagram is replaced by a note and a link to the full diagram:

```markdown
## ER Diagram

ER diagram omitted: this table's neighborhood includes 82 entities, exceeding the configured limit of 60. See the full [ER diagram](../../../index.md) in the database index instead.
```

#### ER Diagram Rendering Notes

With the default `er-diagram-layout: elk`, every generated diagram fence opens with a Mermaid frontmatter block selecting the layout engine:

````markdown
## ER Diagram

```mermaid
---
config:
  layout: elk
---
erDiagram
  ...
```
````

- **The layout frontmatter requires Mermaid 9.4 or newer.** On older renderers the leading `---` block is parsed as part of the diagram itself and can produce a syntax error. If your renderer predates Mermaid 9.4, set `er-diagram-layout` to a value containing a character outside `[A-Za-z0-9_-]` (for example `er-diagram-layout: " "`) — the fence then starts directly with `erDiagram`, exactly as before this option existed.
- **GitHub does not register `@mermaid-js/layout-elk`, so `layout: elk` silently falls back to `dagre` there.** The diagrams still render correctly on GitHub — you simply do not get the ELK layout improvements. `elk` takes effect in renderers that load the ELK plugin, such as [mermaid.live](https://mermaid.live) or a VitePress site configured with the ELK layout package.
- **The default limit of `60` entities is a proxy, not a hard character budget.** Entity count only approximates the rendered size: one entity costs roughly 45 characters plus about 40 characters per column, so for tables with 8–10 columns, 60 entities land around 22,000–28,000 characters — comfortably inside GitHub's roughly 50,000-character limit for a single Mermaid diagram. Schemas dominated by wide tables (20+ columns) can exceed 50,000 characters at only 60 entities. If your diagrams get truncated or rejected, lower `er-diagram-per-table-max-entities` and/or combine it with `er-diagram-keys-only: true`, which cuts each entity down to its PK/FK columns.
- **Empty or non-numeric YAML values fail fast.** Writing `er-diagram-per-table-max-entities:` with no value, or a non-numeric one such as `er-diagram-per-table-max-entities: abc`, makes SmallRye throw while loading the configuration (`SRCFG00040` / `SRCFG00039`) — it does not silently fall back to the default. The same applies to a blank `er-diagram-layout:`. This matches the behavior of other options such as `execution.max-parallelism`.

### Database-Specific Documentation

The PostgreSQL and MySQL plugins ship dedicated source/output pairs that enrich the generated Markdown with database-specific objects beyond the standard JDBC schema (tables, views, columns, keys, indexes).

For example, the PostgreSQL pair (`postgresql-schema` source + `postgresql-markdown` output) adds extensions, enums, sequences, functions, triggers, materialized views, partitions, and RLS policies, while the MySQL pair (`mysql-schema` + `mysql-markdown`) adds storage engines, table metadata, triggers, routines, events, and partitions.

```yaml
generators:
  - name: mydb
    type: postgresql-markdown
    source:
      type: postgresql-schema
      target: db1
    output-dir: docs/schema
    er-diagram: false            # optional; omit or set true to embed the Mermaid ER diagram
    # er-diagram-keys-only: true # optional; show only PK/FK columns in the ER diagram
    # er-diagram-layout: elk     # optional; Mermaid layout engine (elk, dagre, tidy-tree, cose-bilkent)
    # er-diagram-per-table: true # optional; also emit a neighborhood ER diagram on each table page
    # er-diagram-per-table-max-entities: 60 # optional; omit the per-table diagram above this size (0 or lower = unlimited)
```

The full list of database-specific objects, ownership/definer attribution, and per-table content is documented in each plugin's README:

- PostgreSQL: [`migraphe-plugin-postgresql/README.md`](../migraphe-plugin-postgresql/README.md)
- MySQL: [`migraphe-plugin-mysql/README.md`](../migraphe-plugin-mysql/README.md)

### Exclude Filtering

Markdown generators accept an `excludes` list to skip schemas or tables by regex (`schema` and `table` patterns). The full option reference and examples are in each plugin's Generator Fields section: [postgresql](../migraphe-plugin-postgresql/README.md), [mysql](../migraphe-plugin-mysql/README.md), [jdbc](../migraphe-plugin-jdbc/README.md).

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
    password: ${env.PROD_DB_PASSWORD}  # From OS environment variable

  history:
    jdbc_url: jdbc:postgresql://prod-db.company.com:5432/migraphe_history
    password: ${env.PROD_HISTORY_PASSWORD}
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

Nodes at the same dependency level execute in parallel using Virtual Threads. A ready-based approach ensures that as soon as all dependencies of a node are satisfied, it becomes eligible for execution.

**Failure handling (fail-soft):** When a task fails, tasks that do not (transitively) depend on the failed node continue to execute. Tasks that do depend on the failed node are surfaced via the listener as skipped with reason `dependency failed: <id>`. After every runnable task has finished, the overall result is reported as `failure` if any node failed. The same behaviour applies to UP / DOWN and to sequential / parallel execution alike.

This design keeps reruns idempotent: the set of tasks that runs across "first attempt fails + rerun completes" is the same as the set that runs in "single successful attempt".

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

### Setup

Configure plugin resolution in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.github.kakusuke.migraphe") {
                useModule("com.github.kakusuke.migraphe:migraphe-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Add to your `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.kakusuke.migraphe") version "v0.5.0"
}

migraphe {
    baseDir.set(layout.projectDirectory.dir("db")) // default: project directory
}

dependencies {
    // Choose the plugin(s) for your database:
    migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.5.0")
    // migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-mysql:v0.5.0")
    // migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-jdbc:v0.5.0")
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
- Run `migraphe pin` to (re)generate the lockfile
- Alternatively, place plugin JAR file in `plugins/` directory
- See [Installing Plugins](#installing-plugins) section

#### 1b. "Failed to resolve plugin" Error

**Problem:**
```
Failed to resolve plugin: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.5.0
```

**Solution:**
- Check that the Maven coordinate and `repository:` reference in `migraphe.yaml` are correct
- Confirm the JitPack build of `v0.5.0` finished successfully at <https://jitpack.io/#kakusuke/migraphe>
- Verify network connectivity to JitPack and Maven Central
- Re-run `migraphe pin` to refresh the lockfile

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
| JitPack | ✅ Available | `com.github.kakusuke.migraphe` | Plugin JARs + Gradle plugin |
| Maven Central | 📅 Planned | `io.github.kakusuke.migraphe` | Plugin JARs + Gradle plugin |

Plugin JARs and the Gradle plugin are currently distributed via JitPack at `com.github.kakusuke.migraphe:<module>:v0.5.0`. Maven Central publication is planned; the groupId will switch to `io.github.kakusuke.migraphe` at that point.

## Next Steps

- Explore the [Architecture Documentation](../CLAUDE.md) for design details
- Check the [Japanese User Guide](USER_GUIDE.ja.md) for translations
- Review example projects in `examples/` directory (if available)

## Support

For issues and questions:
- GitHub Issues: https://github.com/kakusuke/migraphe/issues
- Documentation: https://github.com/kakusuke/migraphe/tree/main/docs
