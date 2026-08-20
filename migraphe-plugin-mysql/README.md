# migraphe-plugin-mysql

MySQL plugin for Migraphe migration orchestration tool.

[Japanese version](README.ja.md)

## Features

- MySQL database connection management
- SQL-based migration execution with transaction support
- Migration history tracking in MySQL (InnoDB, `utf8mb4`)
- Autocommit mode for DDL statements that cannot run in transactions
- Recursive `BEGIN ... END` block handling and `DELIMITER` directive support for stored routines
- Schema documentation generators (`mysql-schema` source / `mysql-markdown` output) covering MySQL-specific objects (storage engines, table metadata, triggers, routines, events, partitions)

## Installation

### Via JitPack (recommended)

Declare the plugin in `migraphe.yaml`:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-mysql:v0.5.0
    repository: jitpack
```

### Via plugins directory

Build the fat JAR and place it in your project's `plugins/` directory:

```bash
./gradlew :migraphe-plugin-mysql:fatJar
mkdir -p your-project/plugins
cp migraphe-plugin-mysql/build/libs/migraphe-plugin-mysql-*-all.jar your-project/plugins/
```

## Configuration

### Target Configuration

Create a target file in `targets/` directory:

```yaml
# targets/mydb.yaml
type: mysql
jdbc_url: jdbc:mysql://localhost:3306/mydb
username: myuser
password: mypassword
```

The driver class and DB label are fixed by this plugin, so they are not configurable.

#### Target Fields

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `type` | Yes | — | Must be `mysql` |
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
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
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

### Multi-Statement SQL, BEGIN ... END, and DELIMITER

`up` / `down` may contain multiple statements separated by `;`. The MySQL plugin handles **recursive `BEGIN ... END` blocks**, so semicolons inside a stored routine body are not mistaken for statement separators. It also supports the **`DELIMITER` directive** to switch the statement terminator when defining routines that themselves contain `;`:

```yaml
# tasks/mydb/002_add_trigger.yaml
name: Add audit trigger
target: mydb
up: |
  DELIMITER //
  CREATE TRIGGER users_audit AFTER INSERT ON users
  FOR EACH ROW
  BEGIN
    INSERT INTO audit_log (entity, entity_id) VALUES ('users', NEW.id);
  END //
  DELIMITER ;
down: |
  DROP TRIGGER users_audit;
```

Unlike PostgreSQL (which uses dollar-quoting), MySQL relies on `BEGIN ... END` blocks and the `DELIMITER` directive to delimit routine bodies.

### Autocommit Mode

Set `autocommit: true` for DDL statements that cannot run inside a transaction (note: in MySQL most DDL triggers an implicit commit regardless):

```yaml
# tasks/admin/001_create_database.yaml
name: Create application database
target: admin
autocommit: true
up: |
  CREATE DATABASE myapp CHARACTER SET utf8mb4;
down: |
  DROP DATABASE myapp;
```

**Use cases:**
- `CREATE DATABASE` / `DROP DATABASE`
- Statements run against the server outside of an application transaction

## Generator Types

This plugin provides a source/output generator pair for MySQL schema documentation.

| Kind | Type | Description |
|------|------|-------------|
| Source | `mysql-schema` | Extracts JDBC base schema plus MySQL-specific metadata (storage engines, table metadata, triggers, routines, events, partitions) from `information_schema`. Uses catalog-based discovery since MySQL exposes databases as JDBC catalogs |
| Output | `mysql-markdown` | Renders Markdown documentation including the MySQL-specific objects above |

### Generators Configuration

Add a `generators` section to `migraphe.yaml`:

```yaml
generators:
  - name: mysql-schema-docs
    type: mysql-markdown
    source:
      type: mysql-schema
      target: mydb
    output-dir: docs/mysql
    excludes:
      - schema: "information_schema"
      - schema: "mydb"
        table: "tmp_.*"
```

Run with:

```bash
migraphe generate --name mysql-schema-docs
```

#### Generator Fields

For the `mysql-markdown` output type:

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `name` | Yes | — | Generator identifier (used by `--name`) |
| `type` | Yes | — | Must be `mysql-markdown` |
| `source.type` | Yes | — | Source plugin type; `mysql-schema` for full MySQL coverage |
| `source.target` | Yes | — | Target name the source reads schema metadata from |
| `output-dir` | No | `docs/schema` | Directory where generated Markdown files are written |
| `excludes` | No | — | List of exclusion filters applied to extracted schemas/tables |
| `excludes[].schema` | No | — | Regex matching schema (database) names to exclude |
| `excludes[].table` | No | — | Regex matching table names to exclude (used together with `schema`) |

The `mysql-schema` source accepts a single `target` field (the target whose schema is extracted).

### MySQL-Specific Documentation

On top of the standard JDBC schema (tables, views, columns, keys, indexes), the `mysql-schema` / `mysql-markdown` pair documents MySQL-specific objects extracted from `information_schema`:

- **Storage Engines** — per-table engine (e.g., InnoDB) and table options
- **Table Metadata** — collation, row format, auto-increment, comments
- **Triggers** — table triggers with timing/events
- **Routines** — stored procedures and functions, including definer attribution, a parameter table (position / mode / name / type) and the routine body as a fenced SQL block. The body is omitted when the connected account lacks the privilege to read `ROUTINE_DEFINITION`
- **Events** — scheduled events
- **Partitions** — partitioning method and partition list

## Configuration Fields

All option tables are documented inline above:

- [Target Fields](#target-fields)
- [Task Fields](#task-fields)
- [Generator Fields](#generator-fields)

## Requirements

- Java 21 or later
- MySQL 8.0 or later (recommended)

## License

Same as Migraphe project license.
