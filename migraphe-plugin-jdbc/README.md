# migraphe-plugin-jdbc

Generic JDBC plugin for Migraphe migration orchestration tool.

[Japanese version](README.ja.md)

## Features

- Works standalone with **any JDBC-compliant database** by specifying the driver class explicitly
- Serves as the base implementation for the PostgreSQL and MySQL plugins (which fix the driver/label and add DB-specific DDL and metadata)
- SQL-based migration execution with transaction support
- Migration history tracking via JDBC
- Autocommit mode for DDL statements that cannot run in transactions
- Schema documentation generators (`jdbc-schema` source / `jdbc-markdown` output) driven by `DatabaseMetaData`

## Installation

### Via JitPack (recommended)

Declare the plugin in `migraphe.yaml`:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-jdbc:v0.4.2
    repository: jitpack
```

> Note: this plugin does not bundle a JDBC driver. Ensure the driver for your target database is on the classpath.

### Via plugins directory

Build the fat JAR and place it in your project's `plugins/` directory:

```bash
./gradlew :migraphe-plugin-jdbc:fatJar
mkdir -p your-project/plugins
cp migraphe-plugin-jdbc/build/libs/migraphe-plugin-jdbc-*-all.jar your-project/plugins/
```

## Configuration

### Target Configuration

Create a target file in `targets/` directory. Because this is a generic plugin, you must supply the JDBC `driver_class`:

```yaml
# targets/mydb.yaml
type: jdbc
driver_class: org.mariadb.jdbc.Driver
db_label: MariaDB
jdbc_url: jdbc:mariadb://localhost:3306/myapp
username: myuser
password: mypassword
```

#### Target Fields

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `type` | Yes | — | Must be `jdbc` |
| `jdbc_url` | Yes | — | JDBC connection URL |
| `username` | Yes | — | Database username |
| `password` | No | — | Database password |
| `driver_class` | Yes | — | Fully-qualified JDBC driver class name (e.g., `org.mariadb.jdbc.Driver`) |
| `db_label` | No | — | Human-readable database label used in output/logs |

### Task Configuration

Create migration tasks in `tasks/` directory:

```yaml
# tasks/mydb/001_create_users.yaml
name: Create users table
target: mydb
up: |
  CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE
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

### Multi-Statement SQL

`up` / `down` may contain multiple statements. The generic JDBC plugin uses the **standard statement splitter**: it splits on `;` while respecting string literals (`'...'`, `"..."`) and standard SQL comments (`--`, `/* ... */`). It does **not** apply any dialect-specific block parsing (such as PostgreSQL dollar-quoting or MySQL `BEGIN ... END`); for stored procedures or function bodies that contain `;`, use the PostgreSQL or MySQL plugin, which override the grammar accordingly.

```yaml
# tasks/mydb/002_seed.yaml
name: Seed reference data
target: mydb
up: |
  INSERT INTO roles (name) VALUES ('admin');
  INSERT INTO roles (name) VALUES ('user');
down: |
  DELETE FROM roles WHERE name IN ('admin', 'user');
```

### Autocommit Mode

Set `autocommit: true` for DDL statements that cannot run inside a transaction. Each statement is then committed immediately rather than wrapped in a single transaction:

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

Use cases depend on the target database; statements like `CREATE DATABASE` / `DROP DATABASE` typically require autocommit. See the PostgreSQL and MySQL plugin READMEs for dialect-specific cases.

## Generator Types

This plugin provides a source/output generator pair for generic JDBC schema documentation.

| Kind | Type | Description |
|------|------|-------------|
| Source | `jdbc-schema` | Extracts database schema metadata (tables, views, columns, keys, indexes) via JDBC `DatabaseMetaData` |
| Output | `jdbc-markdown` | Renders Markdown documentation from the extracted schema, with directory structure and cross-references |

### Generators Configuration

Add a `generators` section to `migraphe.yaml`:

```yaml
generators:
  - name: schema-docs
    type: jdbc-markdown
    source:
      type: jdbc-schema
      target: mydb
    output-dir: docs/schema
    excludes:
      - schema: "information_schema"
```

Run with:

```bash
migraphe generate --name schema-docs
```

#### Generator Fields

For the `jdbc-markdown` output type:

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `name` | Yes | — | Generator identifier (used by `--name`) |
| `type` | Yes | — | Must be `jdbc-markdown` |
| `source.type` | Yes | — | Source plugin type; `jdbc-schema` for this plugin |
| `source.target` | Yes | — | Target name the source reads schema metadata from |
| `output-dir` | No | `docs/schema` | Directory where generated Markdown files are written |
| `excludes` | No | — | List of exclusion filters applied to extracted schemas/tables |
| `excludes[].schema` | No | — | Regex matching schema names to exclude |
| `excludes[].table` | No | — | Regex matching table names to exclude (used together with `schema`) |

The `jdbc-schema` source accepts a single `target` field (the target whose schema is extracted).

### Output Structure

The `jdbc-markdown` generator produces:

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

Each table page includes column definitions (name, type, nullable, default), primary/unique keys, foreign keys with cross-links (both **Foreign Keys** via imported keys and **Referenced By** via exported keys), and indexes.

## Configuration Fields

All option tables are documented inline above:

- [Target Fields](#target-fields)
- [Task Fields](#task-fields)
- [Generator Fields](#generator-fields)

## Requirements

- Java 21 or later
- A JDBC driver for your target database on the classpath

## License

Same as Migraphe project license.
