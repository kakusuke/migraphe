# Migraphe - Project Documentation for Claude

> **IMPORTANT**: This file MUST always be written in English. Never translate it to Japanese or any other language, even partially. This rule is permanent.

## Overview

DAG-based migration orchestration tool for database/infrastructure migrations across multiple targets.

## Instructions for Claude

0. **Before touching drift, fingerprints, `amend`, `rebuild`, orphans, or the history's `dependencies` / `plugin_metadata` columns, read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — "Drift and its repair" in full.** It states what is built and why, not how it got there.
0a. **When a discussion settles something, edit the record in place — never append a correction.** A "superseded" note leaves the old text implementable by whoever reads that passage next. Rewrite the passage itself, then grep the retired claim's own words across `docs/`, `CLAUDE.md`, the memory directory and `.claude/skills/` — a phrase that reached one file usually reached three. **A limit a change removes must be retracted as deliberately as a decision it reverses.**
0a1. **A decision is not recorded until it is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).** Commit
messages carried the full reasoning for several decisions in this repo — why `no_way_back:` exists, why
`history.target` has no fallback, why an unresolved dependency loads while a cycle does not — and none
of it was in the design file. An audit reported all three as "no design record
anywhere", which was false and cost a correction. **Before calling anything unjustified, run
`git log -S '<the identifier>' --reverse` and read the message.** When you settle something, write it
into the design file in the same change — a message nobody greps is not a record.
0b. **Check a design claim against the code before acting on it.** When a passage enumerates parts, verify each separately: a partial implementation reads as complete unless counted. When it depends on a data shape, check nothing else changed that shape. Two audits and I compared the code against the *columns* rather than against the *commands' stated meaning*, and missed defects for months.
1. **When in doubt, ask — `AskUserQuestion`, not a guess.** If two readings would lead to materially different work — which design was already settled, what is in scope, which of two remedies applies — ask. Guessing has repeatedly re-proposed rejected designs and cost a correction. Do not ask what the code, [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), or this file already answers.
2. **CLAUDE.md language**: This file must always be in English — no exceptions, ever.
3. **Keep CLAUDE.md compact — and do not mirror other files into it.** Design decisions go to [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and stay there; do not keep a summary line here for each one. The summaries used to live here, grew to 28 entries, and had to be read every session to learn what a link would have told you. Anything a session can reconstruct by reading the code — module lists, package trees, interface inventories, the tech stack — does not belong here either.
4. **Respond in Japanese**: All user-facing output must be in Japanese. Internal reasoning may be in English.
5. **Changelog maintenance**: Append every new session record to [docs/CHANGELOG.md](docs/CHANGELOG.md). Nothing about a past session goes in this file — not the latest one either.
6. **Subagent delegation**: Delegate broad exploration to `Explore`, and independent judgment (e.g. `cycle-verifier`) to a fresh context. Do *not* delegate work that reads the same few files repeatedly — TDD cycles run in the main context (see `/tdd-cycle`). Do not duplicate subagent research.
7. **LSP first**: For Java symbol lookup (definitions, references, hover), prefer the `LSP` tool over `Read`/`Grep`. Note that subagents do not have it — another reason to keep file-level work in the main context.
8. **Large output**: Commands producing many lines — always limit with `sed -n 'X,Yp'`, `grep -n pattern | head -N`, or `wc -l`. Never consume full large output in main context.

## Development Process

### TDD — MANDATORY

**Every code change MUST go through the `/tdd-cycle` skill, one cycle at a time.**

The `/tdd-cycle` skill runs: `Plan → Red → Green → Tidy` **in the main context**, then `cycle-verifier` (Opus, fresh context) audits the finished cycle.

Call it repeatedly to advance implementation incrementally. Never write production code outside this cycle.

| Phase | Rule |
|-------|------|
| **Plan** | Name one observable behavior + concrete expected values. |
| **Red** | Write a failing test first. **Gate**: a non-zero `run_test` exit must be observed *before* the first production edit. |
| **Green** | Write the minimum code to make the test pass. |
| **Tidy** | Remove duplication, improve readability, behavior identical. Tests stay green. Never skip — report explicitly if nothing to do. |
| **Verify** | `cycle-verifier` subagent audits: gate / test-validity / over-implementation / scope / regression. |

Tests run through the `migraphe-build` MCP server (`run_test` / `run_spotless`) — never `./gradlew` directly. Scope every run with `module` + `test_filter`. `run_errorprone_check` is session-end only (12+ min clean build).

The first four phases share one context because they touch the same two files; splitting them across subagents only re-reads those files. What that gives up in structural enforcement is recovered by the Red gate (verifiable evidence, not a promise) and the independent Verify audit. The skill is scoped to incremental changes; net-new classes ≥200 lines or multi-file scaffolding route to `general-purpose` instead.

All tests MUST pass at 100% before committing.

### Build / Pre-commit / Session End

Build/test/spotless/ErrorProne commands and the pre-commit / session-end checklist (incl. doc updates, version-bump rules) live in the `migraphe-session-end` skill.

ErrorProne/NullAway warning fixes: see the `migraphe-errorprone` skill.

## Design Principles

Only the ones that differ from what the language and the tooling already push you toward:

- **Null Safety**: `@Nullable` (jspecify) + NullAway. `Optional` is for SmallRye `@ConfigMapping` and
  nowhere else — not as a return type, not as a field.

## Session End Procedure

Pre-commit / session-end steps (incl. CLAUDE.md / CHANGELOG.md / ARCHITECTURE.md routing, user-doc updates, and version-bump rules): run the `migraphe-session-end` skill.


---
## Where things are written down

| what | where |
|---|---|
| design decisions, including drift, fingerprints, `amend`, `rebuild`, orphans | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — the numbered entries, then "Drift and its repair" |
| what a plugin author implements | [docs/PLUGIN_DEVELOPMENT.md](docs/PLUGIN_DEVELOPMENT.md) + `.ja.md` |
| session records, what changed when | [docs/CHANGELOG.md](docs/CHANGELOG.md) |
| what the user runs | [docs/USER_GUIDE.md](docs/USER_GUIDE.md) + `.ja.md` |

This file holds **norms only** — how to work in this repo. It does not track progress, list defects,
or record what a past session did. Those go in the files above, and keeping them out of here is what
stops the norms from being buried.
