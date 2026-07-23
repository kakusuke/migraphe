# migraphe-plugin-generator-json

JSON output generator plugin for Migraphe migration orchestration tool.

[Japanese version](README.ja.md)

## Features

- Generator **output** plugin only — it does not provide a database/environment type
- Renders any data produced by a generator **source** plugin as pretty-printed JSON to **stdout**
- Pairs with any source (e.g., `migration-tree`, `jdbc-schema`) since output plugins are source-agnostic
- Useful for piping migration/schema metadata into other tooling

## Installation

### Via JitPack (recommended)

Declare the plugin in `migraphe.yaml`:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-generator-json:v0.4.3
    repository: jitpack
```

### Via plugins directory

Build the fat JAR and place it in your project's `plugins/` directory:

```bash
./gradlew :migraphe-plugin-generator-json:fatJar
mkdir -p your-project/plugins
cp migraphe-plugin-generator-json/build/libs/migraphe-plugin-generator-json-*-all.jar your-project/plugins/
```

## Generator Types

This plugin provides a single output type. It does **not** provide an environment (`target`) type.

| Kind | Type | Description |
|------|------|-------------|
| Output | `output-json` | Serializes any source data as pretty-printed JSON to stdout |

### Generators Configuration

Add a `generators` section to `migraphe.yaml`. Combine `output-json` with any source plugin:

```yaml
generators:
  # Migration tree as JSON to stdout
  - name: tree
    type: output-json
    source:
      type: migration-tree
    output-dir: docs

  # JDBC schema as JSON to stdout
  - name: schema-json
    type: output-json
    source:
      type: jdbc-schema
      target: mydb
    output-dir: docs
```

Run with:

```bash
migraphe generate --name tree
```

The serialized JSON is written to stdout, so you can redirect or pipe it:

```bash
migraphe generate --name tree > tree.json
```

### Generator Fields

For the `output-json` output type:

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `name` | Yes | — | Generator identifier (used by `--name`) |
| `type` | Yes | — | Must be `output-json` |
| `source.type` | Yes | — | Source plugin type; any source works (e.g., `migration-tree`, `jdbc-schema`) |
| `source.target` | Depends on source | — | Target name, required only by sources that need a database connection (e.g., `jdbc-schema`); omit for `migration-tree` |
| `output-dir` | No | — | Accepted for consistency with other output plugins, but ignored — JSON is always written to stdout |

This output plugin has **no user-configurable formatting options**: it always renders pretty-printed JSON to stdout. Any source-specific fields are defined by the chosen source plugin.

## Requirements

- Java 21 or later

## License

Same as Migraphe project license.
