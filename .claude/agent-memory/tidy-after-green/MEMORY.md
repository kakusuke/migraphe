# Tidy-After-Green Agent Memory

## Project Conventions (confirmed)
- Java 21, Gradle 8.5 Kotlin DSL
- Records and immutable collections are idiomatic
- Sealed interfaces + pattern matching used in `RenderRow` hierarchy
- `@Nullable` (jspecify) + NullAway; avoid `Optional` except in SmallRye `@ConfigMapping`
- Package structure is fixed — do not move classes
- Public API lives in `migraphe-api`; be extra conservative there

## Safe Rename Scope
- Only rename private-method-local variables and parameters
- Do NOT rename `Row` record fields (e.g., `column()`, `isBranch()`) — referenced widely

## Java Type Inference Gotcha
- `Comparator.comparingInt(T::field).reversed()` inside `.thenComparing(...)` can lose type parameter
  — always use `Comparator.<T>comparingInt(T::field).reversed()` (explicit type witness) to be safe

## Topic Files
- [graphcanvas.md](graphcanvas.md) — GraphCanvas/ExecutionGraphView naming, dead-code removal history, Row/Cell architecture notes
- [jdbc_markdown_generator.md](jdbc_markdown_generator.md) — JdbcMarkdownGenerator/Definition Template Method pattern, ER diagram feature tidy history (Sessions 2026-07-22/23), regex precompilation, hex-formatting gotcha
