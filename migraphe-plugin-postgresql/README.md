# migraphe-plugin-postgresql

PostgreSQL plugin for Migraphe migration orchestration tool.

[Japanese version](README.ja.md)

## Features

- PostgreSQL database connection management
- SQL-based migration execution with transaction support
- Migration history tracking in PostgreSQL
- Autocommit mode for DDL statements that cannot run in transactions
- Dollar-quoted statement splitting (`$$ ... $tag$`) for function/procedure bodies
- Schema documentation generators (`postgresql-schema` source / `postgresql-markdown` output) covering PostgreSQL-specific objects (extensions, enums, sequences, functions, triggers, materialized views, partitions, policies)

## Installation

### Via JitPack (recommended)

Declare the plugin in `migraphe.yaml`:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.6.0
    repository: jitpack
```

### Via plugins directory

Build the fat JAR and place it in your project's `plugins/` directory:

```bash
./gradlew :migraphe-plugin-postgresql:fatJar
mkdir -p your-project/plugins
cp migraphe-plugin-postgresql/build/libs/migraphe-plugin-postgresql-*-all.jar your-project/plugins/
```

## Configuration

### Target Configuration

Create a target file in `targets/` directory:

```yaml
# targets/mydb.yaml
type: postgresql
jdbc_url: jdbc:postgresql://localhost:5432/mydb
username: myuser
password: mypassword
```

The driver class and DB label are fixed by this plugin, so they are not configurable.

#### Target Fields

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `type` | Yes | — | Must be `postgresql` |
| `jdbc_url` | Yes | — | JDBC connection URL |
| `username` | Yes | — | Database username |
| `password` | No | — | Database password (omit for password-less / externally-authenticated connections) |

### Task Configuration

Create migration tasks in `tasks/` directory:

```yaml
# tasks/mydb/001_create_users.yaml
name: Create users table
target: mydb
up: |
  CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL
  );
down: |
  DROP TABLE IF EXISTS users;
```

#### Task Fields

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `name` | Yes | — | Human-readable task name |
| `description` | No | — | Optional description |
| `target` | Yes | — | Target name this task runs against (matches a file in `targets/`) |
| `dependencies` | No | `[]` | List of task IDs that must run before this task |
| `up` | Yes | — | SQL executed on migrate up |
| `down` | No | — | SQL executed on rollback (down). Omit for irreversible migrations |
| `autocommit` | No | `false` | Run outside a transaction (see [Autocommit Mode](#autocommit-mode)) |

### Multi-Statement SQL and Dollar-Quoting

`up` / `down` may contain multiple statements separated by `;`. The PostgreSQL plugin understands **dollar-quoted strings** (`$$ ... $$` and tagged `$tag$ ... $tag$`), so semicolons inside function or procedure bodies are not treated as statement separators:

```yaml
# tasks/mydb/002_add_function.yaml
name: Add trigger function
target: mydb
up: |
  CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
  BEGIN
    NEW.updated_at = now();
    RETURN NEW;
  END;
  $$ LANGUAGE plpgsql;

  CREATE TRIGGER users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
down: |
  DROP TRIGGER users_updated_at ON users;
  DROP FUNCTION set_updated_at();
```

Unlike MySQL, PostgreSQL uses dollar-quoting (not `BEGIN ... END` keyword blocks or a `DELIMITER` directive) to delimit bodies, so no extra directive is needed.

### Autocommit Mode

Set `autocommit: true` for DDL statements that cannot run inside a transaction:

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

**Use cases:**
- `CREATE DATABASE` / `DROP DATABASE`
- `CREATE INDEX CONCURRENTLY`
- `VACUUM`
- `CLUSTER`

## Generator Types

This plugin provides a source/output generator pair for PostgreSQL schema documentation.

| Kind | Type | Description |
|------|------|-------------|
| Source | `postgresql-schema` | Extracts JDBC base schema plus PostgreSQL-specific metadata (extensions, enums, sequences, functions, triggers, materialized views, partitions, policies) from `pg_catalog` |
| Output | `postgresql-markdown` | Renders Markdown documentation including the PostgreSQL-specific objects above |

### Generators Configuration

Add a `generators` section to `migraphe.yaml`:

```yaml
generators:
  - name: pg-schema-docs
    type: postgresql-markdown
    source:
      type: postgresql-schema
      target: mydb
    output-dir: docs/postgresql
    excludes:
      - schema: "information_schema"
      - schema: "public"
        table: "tmp_.*"
```

Run with:

```bash
migraphe generate --name pg-schema-docs
```

#### Generator Fields

For the `postgresql-markdown` output type:

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `name` | Yes | — | Generator identifier (used by `--name`) |
| `type` | Yes | — | Must be `postgresql-markdown` |
| `source.type` | Yes | — | Source plugin type; `postgresql-schema` for full PostgreSQL coverage |
| `source.target` | Yes | — | Target name the source reads schema metadata from |
| `output-dir` | No | `docs/schema` | Directory where generated Markdown files are written |
| `excludes` | No | — | List of exclusion filters applied to extracted schemas/tables |
| `excludes[].schema` | No | — | Regex matching schema names to exclude |
| `excludes[].table` | No | — | Regex matching table names to exclude (used together with `schema`) |

The `postgresql-schema` source accepts a single `target` field (the target whose schema is extracted).

### PostgreSQL-Specific Documentation

On top of the standard JDBC schema (tables, views, columns, keys, indexes), the `postgresql-schema` / `postgresql-markdown` pair documents PostgreSQL-specific objects extracted from `pg_catalog`:

- **Extensions** — installed extensions and their versions
- **Enums** — user-defined enum types and their labels
- **Sequences** — standalone and owned sequences
- **Functions / Procedures** — including language, definer/owner attribution, and the routine body (`pg_proc.prosrc`) as a fenced SQL block. Arguments stay as the single formatted line produced by `pg_get_function_arguments()`
- **Triggers** — table triggers and their timing/events
- **Materialized Views** — definitions in addition to regular views
- **Partitions** — partitioned tables and their partition hierarchy
- **Policies** — row-level security (RLS) policies

## Configuration Fields

All option tables are documented inline above:

- [Target Fields](#target-fields)
- [Task Fields](#task-fields)
- [Generator Fields](#generator-fields)

## Requirements

- Java 21 or later
- PostgreSQL 12 or later (recommended)

## License

Same as Migraphe project license.
