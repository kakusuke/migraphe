# migraphe-plugin-mysql

MySQL plugin for Migraphe migration orchestration tool.

[Japanese version](README.ja.md)

## Features

- MySQL database connection management
- SQL-based migration execution with transaction support
- Migration history tracking in MySQL (InnoDB, `utf8mb4`)
- Autocommit mode for DDL statements that cannot run in transactions
- Schema documentation generators (`mysql-schema` source / `mysql-markdown` output) covering MySQL-specific objects (storage engines, table metadata, triggers, routines, events, partitions)

## Installation

### Via JitPack (recommended)

Declare the plugin in `migraphe.yaml`:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-mysql:v0.4.1
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

### Autocommit Mode

For DDL statements that cannot run in transactions (note: in MySQL most DDL triggers an implicit commit regardless):

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

## Configuration Fields

| Field | Required | Description |
|-------|----------|-------------|
| `type` | Yes | Must be `mysql` |
| `jdbc_url` | Yes | JDBC connection URL |
| `username` | Yes | Database username |
| `password` | Yes | Database password |

## Requirements

- Java 21 or later
- MySQL 8.0 or later (recommended)

## License

Same as Migraphe project license.
