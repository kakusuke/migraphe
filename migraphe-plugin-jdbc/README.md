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
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-jdbc:v0.4.1
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

### Autocommit Mode

For DDL statements that cannot run in transactions:

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

## Configuration Fields

| Field | Required | Description |
|-------|----------|-------------|
| `type` | Yes | Must be `jdbc` |
| `jdbc_url` | Yes | JDBC connection URL |
| `username` | Yes | Database username |
| `password` | No | Database password |
| `driver_class` | Yes | Fully-qualified JDBC driver class name (e.g., `org.mariadb.jdbc.Driver`) |
| `db_label` | No | Human-readable database label used in output/logs |

## Requirements

- Java 21 or later
- A JDBC driver for your target database on the classpath

## License

Same as Migraphe project license.
