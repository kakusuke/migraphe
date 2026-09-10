# Migraphe User Guide

[日本語版はこちら](USER_GUIDE.ja.md)

## Table of Contents

0. [Upgrading to 0.7.0](#upgrading-to-070)
1. [Introduction](#introduction)
2. [Installation](#installation)
3. [Project Setup](#project-setup)
4. [Configuration](#configuration)
5. [Writing Migrations](#writing-migrations)
6. [Running Migrations](#running-migrations)
7. [Rollback (down)](#rollback-down)
8. [Recording Definitions as Applied (amend)](#recording-definitions-as-applied-amend)
9. [Creating the History (init)](#creating-the-history-init)
10. [Upgrading the History (upgrade-history)](#upgrading-the-history-upgrade-history)
11. [Rebuilding What Drifted (rebuild)](#rebuilding-what-drifted-rebuild)
12. [Configuration Validation (validate)](#configuration-validation-validate)
13. [Schema Documentation Generation (generate)](#schema-documentation-generation-generate)
14. [Environment Management](#environment-management)
15. [Advanced Features](#advanced-features)
16. [Gradle Plugin](#gradle-plugin)
17. [Troubleshooting](#troubleshooting)

## Upgrading to 0.7.0

Four things about this release need doing before you run it, in this order.

**Rebuild your plugins — re-resolving is not enough.** The plugin API breaks in this release,
deliberately and all at once, so a plugin is migrated once rather than twice: `Environment` and
`EnvironmentId` are now `Target` and `TargetId` (with their definitions and providers), the history's
read methods no longer take a target id, `ExecutionRecord` and `TaskResult` carry more, and
`MigrationNode.fingerprint` and `Task.signature` are required with no default to inherit. A plugin
whose *source* is not migrated will not compile, which is the intent. A plugin **jar** built against
0.6.0 will load and then fail with `AbstractMethodError` at the first call, so update the coordinate
to a rebuilt artifact rather than pointing at the old one.

**`up` refuses while anything is drifted.** A migration edited after it ran means the database no
longer matches your task files, and applying more on top of that builds on ground they no longer
describe. `status` shows those as `[!]`. Clear one with `migraphe down <id>`, which takes it out so
your next `up` puts it back as the file now reads, or with `migraphe amend <id>` if what is already
applied is what you meant. `migraphe rebuild` does that for every difference at once — the heavier
move, since it also takes out migrations no task file declares any more, permanently. `rebuild`
itself is never blocked by drift; removing it is its job.

**Every task must declare either `down:` or `no_way_back:`, or `up` will not run at all.** A missing
rollback used to answer two questions at once — the author decided this migration is one-way, or the
author forgot — and nothing could tell them apart. Declaring neither is an error now: `validate`
reports it and `up` refuses. The refusal is the whole run, not the named subgraph, because an `up`
that avoids the offending task still leaves the project in a state `rebuild` cannot work in. On an
existing project this is real work, and it is the price of the distinction.

**Run `migraphe upgrade-history` first — every other command refuses until you do.** This release's history
table has columns the previous one did not, and until they are there a command that selects them
fails. So the schema change is a step you schedule rather than something that happens to whoever runs
`status` first: that matters if a second deployment is still on the older version and still reading
the column this renames.

```bash
migraphe upgrade-history
```

It adds the columns and, in the same pass, fills what your task files can still supply — the
fingerprint, the recorded dependencies, and the declared reason a migration is one-way — on the rows
an older release left without them. It touches only columns that carry nothing: a value already
recorded is left alone, whether it agrees with the definitions or not.

**What filling asserts.** A row written before the fingerprint column says a migration was applied
and nothing about its content. Writing today's token onto it asserts that your database matches
today's definition, and migraphe cannot check that — it never reads your schema. If a migration was
edited after it was applied, this is the one run where that goes unnoticed; from the next run on, an
edit shows as `[!]` as usual. If you know a migration was edited since it was applied, roll it back
and apply it again rather than relying on the upgrade.

**Rows the task files no longer declare are the half `upgrade` cannot reach.** There is no definition
to fill them from, so `status` keeps showing them, and naming one withdraws it:

```bash
migraphe amend <id>              # the task files no longer declare it — withdraws it
```

That leaves its objects in the database, so it is right only once you have removed them another way.
`migraphe down <id>` is the route that removes them, and it works as soon as the upgrade is done.

Run `migraphe upgrade-history` once per history, after installing this release. Running it again does
nothing and says so.

**Do not point two versions at one history database.** This release renames the history's
`environment_id` column to `target_id` and mints time-ordered record ids. A 0.6.0 binary reading a
migrated history, or the reverse, will not see what it expects. Migrate a copy first if you need to
compare.

## Introduction

Migraphe is a migration orchestration tool designed to manage complex database migrations across multiple environments. It uses a directed acyclic graph (DAG) to represent dependencies between migration tasks, ensuring they execute in the correct order.

### Key Concepts

- **Migration Task**: A single unit of migration work (e.g., creating a table)
- **Target**: A database connection configuration
- **Environment**: Execution context (development, staging, production)
- **Task ID**: Automatically generated from file path (e.g., `tasks/db1/001_create_users.yaml` → `db1/001_create_users`)
- **Dependency**: Relationship between tasks that determines execution order
- **History**: Record of executed migrations stored in a database

### Which command do I need?

migraphe reads two things and writes two things. It reads your **task files** and its **history
table**; it writes the **database** (`up`, `down`, `rebuild`) and the **history** (`amend`). It never
reads the database itself to decide anything — so where the two disagree, which side is right is your
call, and the tool reports the disagreement rather than choosing a fix.

| what happened | what to run |
|---|---|
| Switching branches mid-development left the database out of step with the task files | `migraphe rebuild` — fix everything that differs |
| You rewrote a migration's SQL and want to apply it again | `migraphe down <id>` then `migraphe up` |
| You are adopting migraphe on a database that already has its schema | write one bootstrap task holding what exists, then `migraphe amend <id>` — **one node is the whole job**, there is no bulk "mark as applied" |
| CI has to check that the database and the migrations agree | `migraphe status --check` — non-zero exit unless everything agrees |
| You installed a new migraphe and every command refuses | `migraphe upgrade-history` — brings the history table to the shape this version writes |
| A brand-new project, or a database migraphe has never run against | `migraphe init` — creates the history table (`migraphe up` also creates one when it finds none) |

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
curl -L https://github.com/kakusuke/migraphe/releases/download/v0.6.0/migraphe-0.6.0.tar.gz | tar xz -C ~/.local/migraphe
export PATH="$HOME/.local/migraphe/bin:$PATH"

# Zip — Windows
curl -L -o migraphe.zip https://github.com/kakusuke/migraphe/releases/download/v0.6.0/migraphe-0.6.0.zip
unzip migraphe.zip -d ~/.local/migraphe
export PATH="$HOME/.local/migraphe/bin:$PATH"

# Fat JAR — single file
curl -L -o migraphe.jar https://github.com/kakusuke/migraphe/releases/download/v0.6.0/migraphe-0.6.0-all.jar
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

#### Maven Coordinates

Add a `plugins` section to `migraphe.yaml` with Maven coordinates. Migraphe plugins are distributed via JitPack — declare the JitPack repository and reference each plugin via the map form:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.6.0
    repository: jitpack
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-generator-json:v0.6.0
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
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.6.0
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
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.6.0
    repository: jitpack

project:
  name: my-project

history:
  target: history  # Target name for storing execution history
```

**Fields:**
- `plugins` (optional): List of Maven coordinates (`groupId:artifactId:version`) for CLI plugin resolution
- `project.name` (required): Project identifier
- `project.scan-root` (optional): Base directory for locating `tasks/`, `targets/`, and `environments/`. Accepts a relative path (resolved against the directory containing `migraphe.yaml`) or an absolute path. Defaults to the directory containing `migraphe.yaml`. The same setting is honored by both the CLI and the Gradle plugin.
- `history.target` (required): Target name where migration history is stored

**Example: `scan-root` to keep migration assets under a subdirectory**

```yaml
project:
  name: my-app
  scan-root: config
history:
  target: main
```

With this configuration, Migraphe reads tasks from `config/tasks/`, targets from `config/targets/`, and environments from `config/environments/` — all relative to the directory containing `migraphe.yaml`.

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
- `autocommit` (optional): Execute without transaction. A bare boolean sets both directions; `{up, down}` sets them separately (see [Autocommit Mode](#autocommit-mode))

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

### Declaring a rollback, or why there is none

**Every task declares one of the two, and declaring neither is an error** — `validate` reports it and
`up` refuses the whole run, not just the offending task.

A missing `down:` used to answer two different questions at once: the author decided this migration is
one-way, or the author forgot to write the rollback. Those want opposite responses, and nothing could
tell them apart, so a forgotten rollback silently froze everything built on top of it.

```yaml
name: Drop the legacy audit table
target: db1
up: |
  DROP TABLE legacy_audit;
no_way_back: the rows cannot be reconstructed
```

`no_way_back:` carries a **reason**, not a flag. It is quoted back to whoever meets it later:

```
Error: db1/003_drop_audit cannot be rolled back — no way back: the rows cannot be reconstructed
```

A migration declared this way is **frozen**: it cannot come down, so nothing standing on it can come
down either, and `rebuild` stops rather than tearing down what it cannot put back. Prefer a real
`down:` wherever one exists; reach for `no_way_back:` when the rollback would be a lie.

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

#### Setting it per direction

An apply and its rollback do not always need the same mode — `CREATE INDEX CONCURRENTLY` requires
autocommit, while the `DROP INDEX` that undoes it may not. Write the two separately:

```yaml
name: Add index concurrently
target: db1
autocommit:
  up: true
  down: false
up: |
  CREATE INDEX CONCURRENTLY idx_users_name ON users(name);
down: |
  DROP INDEX idx_users_name;
```

Either direction may be omitted. A bare `autocommit: true` still sets both, and when both forms are
present the named direction wins:

| written | UP | DOWN |
|---|---|---|
| `autocommit: true` | autocommit | autocommit |
| `autocommit: {up: true, down: false}` | autocommit | transaction |
| `autocommit: {down: true}` | transaction | autocommit |
| nothing | transaction | transaction |

Changing either flag on a migration that is already applied moves its fingerprint, so `status` will
report it as `[!]`. Since no database object changed, [`migraphe amend`](#recording-definitions-as-applied-amend) is the whole fix.

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

**Markers:**

| Marker | Meaning |
|--------|---------|
| `[ ]` | Not applied yet |
| `[✓]` | Applied, and no change detected in its definition |
| `[!]` | Applied, but the definition has changed since — its `up:` SQL, its `down:` SQL, its `autocommit` setting, or what it depends on. Whether the database or the history is the side that needs moving depends on which of those you changed, so migraphe reports it instead of choosing: see [`migraphe amend`](#recording-definitions-as-applied-amend) |
| `[?]` | The plugin supplies a fingerprint but the applied row carries none, so a change cannot be detected. Rows written before 0.7.0 read this way. **Every command but `status` refuses while one exists**; [`migraphe upgrade-history`](#upgrading-the-history-upgrade-history) fills the rows your task files still declare, and [`migraphe amend <id>`](#recording-definitions-as-applied-amend) withdraws one they do not |
| `[E]` | The plugin could not report what this migration applied — its accessor threw, or it answered with none. The plugin itself is at fault, and no `amend` clears it |

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
| `--preview` | Show execution plan only without executing (`--dry-run` is accepted as an alias) |

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

Pass `--env <name>` to overlay `environments/<name>.yaml` on top of your `targets/` configuration. The overlay takes highest precedence and overrides target connection settings (e.g. `jdbc_url`, `username`, `password`). Supported by **all** commands that read the configuration — `up`, `down`, `status`, `validate` and `generate`:

```bash
# Apply environments/production.yaml overrides
migraphe up --env production
migraphe status --env production
migraphe validate --env production
migraphe generate --env production

# Apply environments/development.yaml overrides
migraphe up --env development
```

If `environments/<name>.yaml` does not exist, the command **fails** with the path it looked for and the list of overlays that do exist, so a typo such as `--env prodction` is caught immediately. Omitting `--env` altogether is always valid and uses the base configuration.

From Gradle, the same overlay is selected with the `env` extension property, the `-Pmigraphe.env=<name>` project property, or the `--env <name>` task option (in increasing order of precedence):

```kotlin
migraphe {
    env = "production"
}
```

```bash
./gradlew migrapheStatus --env production
./gradlew migrapheStatus -Pmigraphe.env=production
```

#### Targets and environments are different things

These two concepts are easy to confuse, because both are commonly called "environments":

| | What it is | Where it lives |
|---|---|---|
| **target** | A connection: the thing migrations run against. Tasks reference it by name (`target: db1`). | `targets/*.yaml` |
| **environment** (`--env`) | A set of configuration overrides applied on top of the targets. | `environments/*.yaml` |

A target keeps its **name** regardless of `--env`; only its **values** change. So `--env production` does not create a new target — it rewrites the settings of the existing ones.

This matters for migration history: what a row records is the **target name**, not the `--env` overlay it ran under. Running `migraphe up --env production` records the same target id as `migraphe up --env development` would. That is safe as long as each deployment environment has its own history database, which is the intended setup (`history.target` normally points at the same database the migrations run against). **Do not point `history.target` at a database shared across deployment environments** — the applied/not-applied state of different databases would be conflated.

## Rollback (down)

The `down` command rolls back a migration and everything built on top of it.

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

**`--all` refuses outright if anything cannot come down.** "All" means the whole database, and part of
it is not the whole — so rather than rolling back everything else and leaving a shape nobody asked for,
the command names what stopped it and touches nothing:

```
Error: --all means all, and 1 applied migration(s) cannot be rolled back. Nothing was rolled back:
  db1/003_drop_audit — no way back: the rows cannot be reconstructed
```

Rolling back a *named* migration is different: that request is either satisfiable or it is not, so a
frozen migration simply refuses, and one standing under a frozen one is reported as held down by it.

**The rollback follows the history, not your task files.** Which migrations come down, in what order,
what SQL runs and which database it connects to all come from the rows that recorded the applies —
because that is what matches the objects that exist. Editing a task's `target:` or `dependencies:`
after it ran does not move its rollback.

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

## Recording Definitions as Applied (amend)

The `amend` command rewrites the **history** so that it agrees with your current task files. For every migration whose recorded fingerprint is missing or differs from its definition, it records the current fingerprint. **No database objects are touched.**

Use it when `status` shows `[?]` or `[!]`:

- `[?]` — the migration was applied by a version older than 0.7.0, which recorded no fingerprint. **This is not optional to clear.** A row nothing can be read from stops every command but `status`, so `up`, `down` and `rebuild` all refuse while one exists. `amend` is the only thing that clears it: a row is filled by rebuilding it from the definition, and `up` never gets that far.
- `[!]` — the definition changed after it was applied, and the change needs no rollback. Clearing it is a decision: you are stating that what the database already contains is correct.

Only an `up:` edit can ever need one. Editing `down:` or `autocommit:`, or changing what the migration depends on, moves the fingerprint without changing a single database object — and the rollback a re-apply would run is the edited one either way, so `amend` is the whole fix. An `up:` edit is the case to think about: a comment or a formatter run needs no rollback either, but a changed statement does.

If the *database* is the side that is wrong, roll the migration back and re-apply it (`migraphe down <id>` followed by `migraphe up`) instead. `amend` will not do that for you.

### Basic Usage

`amend` has two forms, and which one you want follows from what `status` is showing.

**Both forms append a row saying what the definition says now.** `amend` means one thing — from here on, what the history reports about a migration is what the definition says — and the row it writes carries every attribute the definitions determine, not a chosen few. Nothing is edited and nothing is deleted: the rows already there stay readable, so when the migration was really applied is still there next to the claim that replaced it. Both forms say so before they write:

```
For each migration listed, a row is appended saying what the definition says
now, so what the history reports about it becomes the current definition. The
rows already there are neither changed nor removed.
```

**One migration, named:**

```bash
$ migraphe amend db1/002_create_posts

Amend plan (history only — no database changes):

  [!] → [✓]  db1/002_create_posts - Create posts table

For each migration listed, a row is appended saying what the definition says
now, so what the history reports about it becomes the current definition. The
rows already there are neither changed nor removed.

1 fingerprint will be recorded.

Record 1 fingerprint? [y/N]: y

Recorded 1 fingerprint.
```

> **`amend` is not a "fill in the blanks" operation.** It writes every attribute the definition
> determines on the migration you name, not only the part that was missing. Upgrading is exactly
> where that matters: if you have since deleted a migration's `down:` block, amending it replaces the
> recorded rollback with nothing — and the recorded rollback is what `down` runs. Check `git diff` on
> your task files before naming a migration. Completing rows that an older release wrote without a
> fingerprint is **not** this command: that is `migraphe upgrade-history`, which touches only columns that
> carry nothing.

Preview first — no prompt, no writes:

```bash
migraphe amend --preview db1/002_create_posts
```

Running `migraphe amend` without naming a migration is an error: this is a claim you make one
migration at a time. When the named migration has nothing to do, the command prints `Nothing to
amend.` and exits 0.

### Command Options

| Option | Description |
|--------|-------------|
| `<migration>` | Record the current definition of one migration. Required |
| `--preview` | Display the plan without recording anything (`--dry-run` is accepted as a legacy alias) |
| `-y` | Skip the confirmation prompt |
| `--env <name>` | Apply the `environments/<name>.yaml` overlay |

There is one scope, and it is deliberate: amending replaces what the history reported for the whole
migration, so it is done by naming one. There used to be a bulk form for rows carrying no
fingerprint; that is what a history an older release wrote looks like, completing it carries no
decision about your migrations, and it now belongs to `migraphe upgrade-history`.

### Important Notes

1. **Nothing is overwritten, but what the history *reports* changes**: `amend` appends a row saying what the definition says now, and everything reads the latest applied row. The earlier row stays, so when the migration really ran is still there to read — but nothing marks that the definition ever differed.
2. **The appended row carries everything the definition determines**: the fingerprint, the dependencies, the rollback SQL, the plugin's metadata, the target and the `no_way_back:` reason. Its `executed_at` is when you ran `amend`, and its duration is zero — those are facts about an execution that did not happen — so `status` reports the amend's timestamp from then on.
3. **`[!]` discards evidence**: the fingerprint of what really ran is replaced by the fingerprint of what the files say now. If an `up:` edit needs to reach the database, roll back instead. This is why the plan marks those rows with a warning.
4. **What the history said first is not a condition**: naming a migration is a deliberate claim that it is applied, so it is appended whether the history has never recorded it, already agrees, or holds it as rolled back. A failed rollback or a failed re-apply does not hide it either — the row that *applied* it is what is read, not the newest row of any kind. Only a migration whose plugin reports no fingerprint is refused, because the row would say nothing about itself.
5. **The plugin has to be able to report its rollback**: `amend` runs nothing, so it asks the up task what rollback it would record instead of getting it from a run. Every bundled plugin can. A migration whose plugin cannot is refused by name, rather than having the superseded row's rollback copied into a row that claims to say what the definition says. Any history repository works — appending needs no capability beyond recording.

### Exit Codes

| Exit Code | Meaning |
|-----------|---------|
| 0 | Everything planned was recorded — also when there was nothing to amend, the run was a preview, or you answered `N` at the prompt |
| 1 | A planned row could not be recorded (it was gone by the time the write ran), or the command failed |

## Creating the History (init)

`migraphe init` creates the history table in the target `history.target` names. Run it once per
database, before anything else.

```bash
$ migraphe init
Created the migration history.

$ migraphe init
The migration history already exists.
```

It exists so that creating the history is something you do, rather than something that happens to
whichever command you happen to run first. Every command used to create it, which meant
`migraphe status --check` in CI wrote DDL to a database while reporting that nothing had been
applied — a command whose whole job is to report should not change anything.

**`migraphe up` is the exception and creates one when it finds none**, because applying the first
migration is the moment a project's history should come into being; a fresh project still needs no
ceremony before its first `up`. Every other command refuses:

```
Error: the migration history has not been created here.
Run 'migraphe init'.
```

That refusal names `init` and never `up`, deliberately: answering "I cannot report" with "then apply
your migrations" is not an answer.

`init` does not alter a history an older release created — that is
[`migraphe upgrade-history`](#upgrading-the-history-upgrade-history). Against such a history, `init`
says it already exists and the other commands go on refusing until you upgrade it.

### Command Options

| Option | Description |
|--------|-------------|
| `--env <name>` | Apply the `environments/<name>.yaml` overlay |

## Upgrading the History (upgrade-history)

`migraphe upgrade-history` brings the migration history to the shape the installed version writes. It is the
only command that changes a table an older release created: every other command creates the history
if it is absent and otherwise leaves its shape alone.

That split exists because a history can be shared. If a second deployment is still running the older
version, it is still reading the columns an upgrade renames — so the change is something an operator
schedules, not something that happens to whoever runs `status` first.

```bash
$ migraphe upgrade-history-history

Upgrading the history
=====================

  rename environment_id to target_id
  add fingerprint column
  add plugin_metadata column
  add dependencies column
  add origin column
  add no_way_back column
  fill what the definitions still declare

Applied 7 upgrades.
```

Every other command refuses while an upgrade is outstanding, and names this one:

```
Error: the migration history was written by an older release and needs 7 upgrade(s) before this
version can read it:
  rename environment_id to target_id
  ...
Run 'migraphe upgrade-history'.
```

**It fills rows as well as adding columns.** The last step writes what your task files can still
supply — the fingerprint, the recorded dependencies, and the declared reason a migration is one-way
— onto the rows an older release left without them. It touches only columns that carry nothing, so a
value already recorded is left alone whether or not it agrees with the definitions.

Filling an absent fingerprint asserts that your database matches today's definition, and migraphe
cannot check that: it never reads your schema. See [Upgrading to 0.7.0](#upgrading-to-070) for what
that means in practice.

**Rows your task files no longer declare are not reached.** There is nothing to fill them from, so
they keep showing in `status`; `migraphe amend <id>` withdraws one.

There is no `--preview` and no confirmation prompt. Each step is guarded by its own detection, so
running the command against a history that is already current writes nothing:

```bash
$ migraphe upgrade-history-history
The history is already up to date.
```

Run it once per history after installing a new version. The command's name does not change between
releases: whatever a given version has to do to a history it did not write, this is what you run.

### Command Options

| Option | Description |
|--------|-------------|
| `--env <name>` | Apply the `environments/<name>.yaml` overlay |

## Rebuilding What Drifted (rebuild)

`rebuild` makes both the database and the history agree with your task files: it rolls back every
migration whose recorded content no longer matches its definition — plus everything standing on those
— and then applies the whole graph again.

```bash
migraphe rebuild              # asks for confirmation first
migraphe rebuild --preview    # show the plan, change nothing
migraphe rebuild -y           # skip the confirmation
```

**It takes no migration argument.** Naming one would be `migraphe down <id>` followed by
`migraphe up`, which you can already do — and it is a way to leave the job half done, so it is
rejected rather than ignored.

This is a **development-time command**. It drops and re-creates real objects, so the data in them does
not survive.

### It is partly a permanent removal

A migration the history holds and your task files no longer declare comes down like anything else —
but nothing puts it back, because there is no definition left to apply. The confirmation names those
separately, before anything runs:

```
Migrations to rebuild:

  [!] db1/002_add_email

Permanently removed — no task file declares these any more, so they come out and do not go back:

  [-] db1/004_experiment

Rolling back 2 migration(s) — everything standing on them comes down too — then applying the whole graph.
```

### When it refuses

`rebuild` checks everything that could stop it **before** the destructive phase, and before `--preview`
returns — a preview that exits zero on a plan the real run would fail is not a rehearsal. It stops when:

- an applied row names a target this project no longer configures (there is no connection to take that
  migration out through)
- a plugin cannot report what a migration applied — a fault to fix in the plugin, not a state `amend`
  repairs
- something that has to come down declares `no_way_back:` — there is no version of the run that
  finishes, so nothing is torn down
- the history cannot say whether a migration matches its definition — run `migraphe upgrade-history`
  first

### Exit Codes

| Exit Code | Meaning |
|-----------|---------|
| 0 | The rebuild completed, or there was nothing to rebuild, or the run was a preview, or you answered `N` |
| 1 | Something refused the run, the rollback did not complete, or the re-apply failed |

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
- `name` (required): Identifier for this generator, selectable via `migraphe generate --name`. Markdown output plugins also use it as the documentation title, emitted as the `index.md` heading `# <name>`. It is never part of the output path.
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

Markdown output plugins (`jdbc-markdown`, `postgresql-markdown`, `mysql-markdown`) write a single database-wide `index.md` directly under `output-dir`, plus one directory per schema (`<output-dir>/<schema>/`) containing a `tables/` and a `views/` directory. Every table page lists column definitions (name, type, nullable, default), primary/unique keys, foreign keys with cross-links — both the **Foreign Keys** (imported keys) and **Referenced By** (exported keys) perspectives — and indexes. The exact directory layout and the imported-vs-exported foreign-key rendering are documented in the [`migraphe-plugin-jdbc` README](../migraphe-plugin-jdbc/README.md).

By default, `index.md` also embeds a single database-wide **ER diagram** in Mermaid `erDiagram` notation (a fenced ```mermaid block, rendered inline by GitHub and most Markdown viewers). Each table becomes an entity with its columns (type plus PK/FK markers; a column that is both is marked `PK, FK`), and foreign keys become relationships (`||--o{`). The diagram is schema-aware: tables from different schemas are distinct entities even when they share a name, cross-schema foreign keys are drawn, and cross-schema table links in the per-table pages resolve to the referenced schema's directory. Column types are shown by their base name (e.g. a PostgreSQL enum type is rendered as `user_account_status`, not its quoted schema-qualified form). It stays a single combined diagram — Mermaid `erDiagram` has no grouping construct, so tables are not boxed per schema. Set `er-diagram: false` on the generator to suppress this section, or `er-diagram-keys-only: true` to keep the diagram compact by showing only primary-key and foreign-key columns for each entity.

#### Per-Table ER Diagrams

By default (`er-diagram-per-table: true`) each table page also carries its own `## ER Diagram` section, placed right after the page header and before `## Columns`. Instead of the whole database it shows only the table's **neighborhood**: the table itself, every table transitively reachable by following its foreign keys towards the referenced side (its ancestors, and their ancestors, and so on), and every table that transitively references it (its descendants, and their descendants). Relationships are drawn for every foreign key with both ends inside that set.

The neighborhood is deliberately *not* the whole undirected connected component: sibling branches — "another descendant of an ancestor", or "another ancestor of a descendant" — are not pulled in, which keeps the diagram focused even in densely linked schemas. Circular references, self-references, and cross-schema foreign keys are all handled, and tables removed by `excludes` cut the traversal, so a neighborhood never reaches through an excluded table.

When a neighborhood grows past `er-diagram-per-table-max-entities` (default `60`), the diagram is replaced by a note and a link to the full diagram:

```markdown
## ER Diagram

ER diagram omitted: this table's neighborhood includes 82 entities, exceeding the configured limit of 60. See the full [ER diagram](../../index.md) in the database index instead.
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
- `id`: Unique execution ID (a time-ordered UUIDv7, so records sort by creation order)
- `node_id`: Task ID
- `target_id`: Target name, as declared by the task's `target:` and defined under `targets/`.
  Versions before 0.6.0 called this column `environment_id`; `initialize()` renames it in place.
  It has never held the `--env` overlay name, which selects configuration values only.
- `direction`: UP or DOWN
- `status`: SUCCESS, FAILURE, or SKIPPED
- `description`: Task name
- `executed_at`: Execution timestamp
- `duration_ms`: Execution duration
- `serialized_down_task`: Rollback SQL (UP migrations only)
- `error_message`: Error details (FAILURE status only)
- `fingerprint`: Fingerprint of the definition that was applied, recorded on UP success only. The
  JDBC, PostgreSQL and MySQL plugins hash four things together: the `up:` SQL, the `down:` SQL,
  both `autocommit` flags, and every migration this one depends on directly or indirectly. Each SQL text
  has its surrounding whitespace stripped and is otherwise hashed as written. Empty on DOWN rows, on
  rows written before 0.7.0, and when a plugin does not supply one — in every such case it means
  "unknown", not "unchanged".

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
    id("io.github.kakusuke.migraphe") version "v0.6.0"
}

migraphe {
    baseDir.set(layout.projectDirectory.dir("db")) // default: project directory
    // env = "production"                          // default: no overlay
}

dependencies {
    // Choose the plugin(s) for your database:
    migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.6.0")
    // migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-mysql:v0.6.0")
    // migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-jdbc:v0.6.0")
}
```

### Available Tasks

| Task | Description |
|------|-------------|
| `migrapheInit` | Create the migration history |
| `migrapheValidate` | Validate configuration files (offline, no DB connection) |
| `migrapheStatus` | Show migration execution status |
| `migrapheUp` | Execute forward (UP) migrations |
| `migrapheDown` | Execute rollback (DOWN) migrations |
| `migrapheAmend` | Record the current definitions as applied, one named migration (history only) |
| `migrapheRebuild` | Roll back what drifted and apply everything again |
| `migrapheUpgradeHistory` | Bring the history table to the shape this version writes |
| `migrapheGenerate` | Generate schema documentation |

### Task Options

**All tasks**:
- `--env=<name>` — Apply the `environments/<name>.yaml` overlay (see [Environment-Specific Execution](#environment-specific-execution))

**migrapheUp**:
- `--target=<nodeId>` — Migrate up to a specific node
- `--preview` — Preview without executing

**migrapheDown**:
- `--target=<nodeId>` — Rollback to a specific node
- `--all` — Rollback all executed migrations
- `--preview` — Preview without executing

**migrapheAmend**:
- `--migration=<nodeId>` — The migration to record the current definition of (required)
- `--preview` — Show the plan without recording anything

**migrapheInit**: no options of its own — it creates the history, or says it is already there

**migrapheUpgradeHistory**: no options of its own — every step is guarded, so running it against a history
that is already current writes nothing

**migrapheGenerate**:
- `--name=<name>` — Generate for a specific generator only

Options can also be specified via project properties (`-P`):

```bash
./gradlew migrapheUp -Pmigraphe.up.target=db1/create_users
./gradlew migrapheDown -Pmigraphe.down.all=true
./gradlew migrapheStatus -Pmigraphe.env=production
./gradlew migrapheAmend -Pmigraphe.amend.dryRun=true
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
- With the Gradle plugin, add the coordinate to the `migraphePlugin` configuration instead
- See [Installing Plugins](#installing-plugins) section

#### 1b. "Failed to resolve plugin" Error

**Problem:**
```
Failed to resolve plugin: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.6.0
```

**Solution:**
- Check that the Maven coordinate and `repository:` reference in `migraphe.yaml` are correct
- Confirm the JitPack build of `v0.6.0` finished successfully at <https://jitpack.io/#kakusuke/migraphe>
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

Plugin JARs and the Gradle plugin are currently distributed via JitPack at `com.github.kakusuke.migraphe:<module>:v0.6.0`. Maven Central publication is planned; the groupId will switch to `io.github.kakusuke.migraphe` at that point.

## Next Steps

- Explore the [Architecture Documentation](../CLAUDE.md) for design details
- Check the [Japanese User Guide](USER_GUIDE.ja.md) for translations
- Review example projects in `examples/` directory (if available)

## Support

For issues and questions:
- GitHub Issues: https://github.com/kakusuke/migraphe/issues
- Documentation: https://github.com/kakusuke/migraphe/tree/main/docs
