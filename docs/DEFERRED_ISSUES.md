# Deferred Issues

Known issues identified during Session 39 sample verification but deferred to separate TDD cycles. Each entry links a location in the code and outlines the intended fix direction.

---

## Issue 2 — `JdbcMarkdownPlugin` family: cross-classloader `ClassCastException`

### Symptom

Running `migraphe generate` for any markdown generator (`postgresql-markdown` / `mysql-markdown` / `jdbc-markdown`) — in either CLI or Gradle — fails with a `ClassCastException` on this line:

```java
var definition = (JdbcMarkdownDefinition) context.definition();
```

because `context.definition()` is a `GeneratorExecutor.GeneratorSectionAdapter` loaded by core's AppClassLoader, while `JdbcMarkdownDefinition` is loaded by the plugin's URLClassLoader. Their `Class` identities differ even if the interface fully qualified name matches.

Furthermore, `GeneratorSectionAdapter` only implements the bare `GeneratorDefinition` contract (`type()`). It has no way to supply the plugin-specific fields that `JdbcMarkdownDefinition` requires: `name()`, `outputDir()`, `excludes()`. Even if the cross-classloader issue were solved, the adapter would not satisfy the interface.

### Locations

- `migraphe-core/src/main/java/io/github/kakusuke/migraphe/core/generator/GeneratorExecutor.java` — `GeneratorSectionAdapter`
- `migraphe-plugin-jdbc/src/main/java/io/github/kakusuke/migraphe/jdbc/markdown/JdbcMarkdownPlugin.java` — failing cast
- `migraphe-plugin-postgresql/.../PostgreSQLMarkdownPlugin` — same pattern
- `migraphe-plugin-mysql/.../MySQLMarkdownPlugin` — same pattern

### Candidate approaches

1. **(A) Add `<T> T definitionAs(Class<T>)` to `OutputContext`.** The method internally materialises the plugin's `@ConfigMapping` interface via SmallRye Config in the plugin's own classloader. Cleanest alignment with existing SmallRye architecture. Plugins keep their typed definition interfaces unchanged.

2. **(B) Replace `GeneratorSectionAdapter` with a dynamic `Proxy.newProxyInstance`.** Use the plugin's `definitionClass().getClassLoader()` as the proxy's classloader. Metadata is stored in a `Map` behind the proxy. Works but leaks knowledge about SmallRye's reflection model outside of SmallRye.

3. **(C) Add `Map<String, String> properties()` to `GeneratorSection`.** Plugins retrieve their fields by key. Easiest to implement, but loses compile-time type safety and forces manual parsing of collections (e.g. `excludes`).

Preferred: **(A)** — it extends the existing SmallRye Config integration rather than side-stepping it.

### Scope of change

- Medium. Touches `OutputContext` public API (likely additive), `GeneratorExecutor`, and every markdown plugin (`jdbc`, `postgresql`, `mysql`).
- Requires test coverage for cross-classloader behaviour, which typically means loading a plugin JAR at test time via `URLClassLoader`.

---

## Status

This issue does not block migration execution (`up` / `down` / `status` / `validate`). It only affects the `generate` subcommand. It is tracked here so that future TDD cycles can pick it up without re-discovery.
