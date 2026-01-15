# migraphe-plugin-postgresql

PostgreSQL plugin for Migraphe migration orchestration tool.

[Japanese version](README.ja.md)

## Features

- PostgreSQL database connection management
- SQL-based migration execution with transaction support
- Migration history tracking in PostgreSQL
- Autocommit mode for DDL statements that cannot run in transactions

## Installation

### Build Fat JAR

```bash
./gradlew :migraphe-plugin-postgresql:fatJar
```

### Place in plugins directory

```bash
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
