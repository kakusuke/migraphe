# Migraphe

[![CI](https://github.com/kakusuke/migraphe/actions/workflows/ci.yml/badge.svg)](https://github.com/kakusuke/migraphe/actions/workflows/ci.yml)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

A migration orchestration tool that manages database and infrastructure migrations across multiple environments using a directed acyclic graph (DAG).

[日本語版 README](README.ja.md)

## Features

- **DAG-based migration** with explicit dependencies between tasks
- **Multi-environment** support (development, staging, production)
- **Pluggable database support**: PostgreSQL, MySQL, and any JDBC database
- **Dialect-aware SQL statement splitting**: multi-statement migrations, stored procedures, PostgreSQL `DO $$...$$`, and MySQL `DELIMITER`
- **Automatic plugin resolution** from Maven coordinates in `migraphe.yaml` (Maven Central, JitPack, or any HTTPS Maven repository)
- **Reproducible builds** via SHA-256 lockfile (`migraphe.lock.yaml`)
- **Gradle plugin** with `migrapheUp`/`Down`/`Status`/`Validate`/`Generate` tasks
- **Schema documentation generation** (Markdown / JSON) for JDBC, PostgreSQL, and MySQL
- **Parallel execution** via Virtual Threads (opt-in)
- **Configurable layout** via `project.scan-root` to relocate `tasks/`, `targets/`, `environments/`, and `plugins/` under a subdirectory (CLI / Gradle share the same field)
- **Type-safe**: built with Java 21, jspecify + NullAway

## Requirements

- Java 21 or later
- A database reachable over JDBC. Verified against PostgreSQL, MySQL 8.0 and MariaDB 10.1
  - **MySQL**: 5.6.4 or later. The history table stores `TIMESTAMP(6)`, which Oracle MySQL 5.5 cannot parse, so 5.5 is not supported
  - **MariaDB**: the 5.5 generation onwards. The history table's index key lengths stay within InnoDB's 767-byte limit, so it can be created on servers without `innodb_large_prefix`

## Install

```bash
# mise (recommended) — the release tarball ships bin/ and lib/ at its root
mise use github:kakusuke/migraphe

# Or manual install
mkdir -p ~/.local/migraphe
curl -L https://github.com/kakusuke/migraphe/releases/download/v0.6.0/migraphe-0.6.0.tar.gz | tar xz -C ~/.local/migraphe
export PATH="$HOME/.local/migraphe/bin:$PATH"
migraphe --help
```

Zip / fat-jar / build-from-source alternatives are listed in [User Guide → Installation](docs/USER_GUIDE.md#installation).

## Hello World

A 5-minute walkthrough — create a project, declare a PostgreSQL plugin, write one migration, and run it — lives in the User Guide:

1. [Install plugins via Maven coordinates](docs/USER_GUIDE.md#installing-plugins)
2. [Project setup](docs/USER_GUIDE.md#project-setup)
3. [Write your first migration](docs/USER_GUIDE.md#writing-migrations)
4. [Run migrations](docs/USER_GUIDE.md#running-migrations)

For Gradle integration instead of the CLI, see [User Guide → Gradle Plugin](docs/USER_GUIDE.md#gradle-plugin).

## Documentation

- [User Guide](docs/USER_GUIDE.md) ([日本語](docs/USER_GUIDE.ja.md)) — installation, configuration, running, rollback, generate, troubleshooting
- [Plugin Development](docs/PLUGIN_DEVELOPMENT.md) ([日本語](docs/PLUGIN_DEVELOPMENT.ja.md)) — write your own plugin
- [Contributing](CONTRIBUTING.md) — build from source, coding standards, PR workflow
- [Architecture notes](CLAUDE.md) — design decisions and module layout

## License

Apache License 2.0
