# Deferred Issues

No outstanding deferred issues.

Issue 1 (Generator top-level `target` removal) was resolved in Session 41. Issue 2 (cross-classloader `ClassCastException` in Markdown plugins) was resolved in Session 42 via the `DefinitionResolver` / `OutputContext.definitionAs(Class<T>)` API backed by a `PropertiesDefinitionResolver` that re-materialises `@ConfigMapping` interfaces in the plugin's own classloader.
