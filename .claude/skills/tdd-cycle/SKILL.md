This skill runs **one strict t-wada style TDD cycle**, scoped to **incremental changes**.

## Scope and Non-Goals

Use this skill for:
- Small bug fixes (1 file, <50 lines diff)
- Small feature additions on top of existing code
- Incremental refactor steps that have a single observable behavior change

Do **not** use this skill for:
- Net-new classes ≥200 lines, multi-file porting, or class extraction refactors → use `general-purpose` agent instead
- Pure surveys / cross-cutting investigations → use `Explore` agent
- Architecture / design decisions → use `Plan` agent

If the parent realises mid-cycle the task is the wrong shape for this skill, abort and switch tools rather than forcing the pipeline through.

## Pipeline

The parent agent orchestrates the cycle by invoking specialised subagents in order. Each subagent now runs its own test verification via the `mcp__migraphe-build__*` tools — **the parent does not run `./gradlew` directly**.

```
micro-plan → test-writer (red) → minimal-fix (green) → regression-guard → tidy-after-green
                            └── characterization shortcut ──→ regression-guard (skip green)
```

### 1) micro-plan
Define the **smallest *valuable* observable behavior** for one cycle.

- One cycle = one cohesive user-facing behavior; multiple `assertThat` lines in a single test are fine if they describe the same behavior
- Avoid value-zero proposals like "just assertNotNull on the constructor" — the cycle output should be something that could ship on its own
- Do not design ahead, do not modify code

### 2) test-writer (Red)
Write the smallest failing test for the planned behavior, then verify it fails via `mcp__migraphe-build__run_test`.

- Must report the actual run result (exit code, failure marker, summary)
- Do not modify production code
- **Characterization mode**: If production *already implements* the behavior (e.g., locking in pre-existing logic before a refactor), say so explicitly in the report. The test should pass green immediately, and the pipeline shortcuts straight to `regression-guard` (skipping `minimal-fix` and `tidy-after-green`).

### 3) minimal-fix (Green)
Modify production code minimally so all tests pass. Verify via `mcp__migraphe-build__run_test`.

- No refactor, no improvement, no structural cleanup beyond what the fix demands
- **Porting allowed**: If existing logic in another class already implements the target behavior, mechanically porting it verbatim (with the minimum syntactic adaptations to compile) counts as minimal. Flag this in the report so the parent knows it was a port, not a fresh implementation.
- Must report the run result (exit code, summary) and confirm green

### 4) regression-guard
Identify possible side effects of the diff and suggest missing edge case tests if any.

- Each finding must carry a `severity:` tag — one of `blocker` / `important` / `nice-to-have` — so the parent can decide whether to address now or defer
- Do not modify production code

### 5) tidy-after-green
Perform behavior-preserving tidy only. Verify via `mcp__migraphe-build__run_test` after edits, then run `mcp__migraphe-build__run_spotless`.

- Produce minimal diff. No redesign.
- If no meaningful tidy exists, the agent **must still report explicitly** with `tidy_status: skipped` and a one-line reason (e.g., "no duplication; names already clear"). Silent skips are not allowed — the parent needs visibility.

## Parent agent role

- Invoke each subagent in order via the `Agent` tool.
- Read each subagent's report and confirm:
  - test-writer: red confirmed (or characterization mode declared)
  - minimal-fix: green confirmed (or skipped because of characterization)
  - regression-guard: severities reviewed; address `blocker` immediately, queue `important` for follow-up, ignore `nice-to-have` unless trivial
  - tidy-after-green: tidy applied **or** `tidy_status: skipped` with reason present
- Do **not** read source files, grep code, or write fixes in the main context. All file reading, code analysis, code changes, and test execution are delegated to subagents.
- Do **not** invoke `./gradlew` from the parent. Subagents run tests through `mcp__migraphe-build__run_test`.

## Strict rules

- Never merge phases. Each phase produces an independent report.
- Never skip a subagent's test verification. If a phase report lacks a test result, re-invoke it.
- Abort if behavior might change unexpectedly.
- Keep all steps minimal.
- This skill performs only one small cycle. The parent may invoke it repeatedly for incremental progress.
