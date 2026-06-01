# migraphe-plugin-postgresql

PostgreSQL plugin for Migraphe migration orchestration tool.

[Japanese version](README.ja.md)

## Features

- PostgreSQL database connection management
- SQL-based migration execution with transaction support
- Migration history tracking in PostgreSQL
- Autocommit mode for DDL statements that cannot run in transactions
- Schema documentation generators (`postgresql-schema` source / `postgresql-markdown` output) covering PostgreSQL-specific objects (extensions, enums, sequences, functions, triggers, materialized views, partitions, policies)

## Installation

### Via JitPack (recommended)

Declare the plugin in `migraphe.yaml`:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:v0.4.1
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

## Configuration Fields

| Field | Required | Description |
|-------|----------|-------------|
| `type` | Yes | Must be `postgresql` |
| `jdbc_url` | Yes | JDBC connection URL |
| `username` | Yes | Database username |
| `password` | Yes | Database password |

## Requirements

- Java 21 or later
- PostgreSQL 12 or later (recommended)

## License

Same as Migraphe project license.
