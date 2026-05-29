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
  - coordinate: com.github.kakusuke.migraphe:migraphe-plugin-generator-json:v0.3.0
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

## Requirements

- Java 21 or later

## License

Same as Migraphe project license.
