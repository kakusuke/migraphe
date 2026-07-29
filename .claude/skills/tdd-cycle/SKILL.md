---
name: tdd-cycle
description: Runs one strict t-wada style TDD cycle (plan → red → green → tidy → independent verify) for an incremental change to the Migraphe codebase. Use this for every production code change in this repo — bug fixes, small feature additions, incremental refactor steps — even when the user just says "fix X" or "add Y" without mentioning tests or TDD. Not for net-new classes ≥200 lines, multi-file scaffolding, surveys, or architecture decisions.
---

# One TDD cycle

Four phases run **in this context** — you write the plan, the test, the fix, and the tidy yourself.
The fifth phase is an **independent subagent** that audits the finished cycle.

```
[here]  1. Plan  →  2. Red  →  3. Green  →  4. Tidy
[sub ]  5. Verify (fresh context, Opus)
```

The first four phases touch the same two files (production + test class), so splitting them across
subagents just means reading those files four times.

Be clear-eyed about what this gives up. The old design's Red phase ran in an agent that had never seen
a proposed fix, so its test could only describe behavior. You have seen everything. The Red gate below
recovers the *ordering* guarantee — test demonstrably failing before production changes — but nothing
recovers the ignorance. That part you have to supply deliberately, in Plan.

## Scope

Right shape for this skill:
- Bug fixes (typically one file, well under 50 lines of diff)
- Small feature additions on top of existing code
- Incremental refactor steps with a single observable behavior change

Wrong shape — stop and switch rather than forcing the pipeline:
- Net-new classes ≥200 lines, multi-file porting, class extraction → `general-purpose` agent
- Cross-cutting surveys → `Explore` agent
- Architecture / design decisions → `Plan` agent

If you realise mid-cycle that the task is the wrong shape, abort and say so. What you do with the work
in progress depends on where you stopped:

- **Green already reached** — keep it. A passing test plus its fix is a complete step; just don't grow it.
- **Still red** (the common case: you write the test, then discover the fix is a 300-line port) — do not
  leave a failing test in the tree. It breaks the repo's 100%-green rule and the agent you hand off to
  has no way to tell an intentional red from a broken one. Either delete it, or keep it with
  `@Disabled("<why, and what will re-enable it>")` and name it explicitly in your handoff.

Either way, say which you did, and skip Verify — there is no cycle to audit.

## The Red gate

**Before editing any production file, you must have observed a failing `run_test` for the new test.**

Not "expect it to fail" — an actual non-zero `exit_code` in this transcript. This is the one rule that
distinguishes TDD from test-after, and since no separate process is enforcing it, it has to be
verifiable evidence rather than a promise. Each `run_test` returns a `log_path`; record it, because
Verify reads those logs directly rather than taking your account of them.

Two things the gate deliberately does *not* claim:

- It is a **subsequence** rule, not a three-event script. Real cycles iterate — red, edit, still red for
  a different reason, edit again, green. That is fine. What must never appear is a production edit with
  no preceding failing run for this test.
- It constrains *when you edit*, not *when you decide*. It cannot tell whether you wrote the assertion
  from the specification or from a fix you had already worked out. Only Plan can protect that.

A red run that fails to *compile* counts, as long as the compile error is the one your test's expected
production change would resolve. A red run that fails for an unrelated reason — a typo in your test, a
pre-existing failure, the wrong module — does **not** count; you have not shown the test measures
anything. Read the failure before moving on, and fix the test if that is what's broken.

**A gate violation cannot be repaired.** If you edited production first, that happened; reverting and
replaying a red run afterwards produces a clean-looking record of a cycle that wasn't test-driven, which
is worse than an honest one. Say so in the record, finish the cycle, and let Verify report it. The point
of the gate is to measure the discipline, not the paperwork.

If the test passes on its first run, that is a finding, not a fork in the road. Either the test doesn't
exercise the new behavior — much the more common case — or you are running a **characterization cycle**,
which is only valid if you declared it in Plan, before writing the test. A characterization claim made
*after* seeing green is indistinguishable from "my test was wrong and I'd rather not redo it"; treat it
as a broken test and fix it.

## Cycle record

Accumulate this as you go — Verify receives it in phase 5 and cannot audit the gate without it.

```
1. run_test(module=migraphe-plugin-jdbc, test_filter=*JdbcSchemaInfoProviderTest*) → exit 1
   "expected 2 rows but was 1"   log: /tmp/migraphe-build-mcp/20260729T130328-....log
2. edit JdbcSchemaInfoProvider.java           ← first production edit
3. run_test(same)                             → exit 0   log: ...130350-....log
4. tidy: extract buildKey(); run_spotless → exit 0; run_test(module) → exit 0   log: ...131112-....log
```

One line per event, plus the failure summary and `log_path` for every non-zero exit — Verify cannot tell
a real red from a typo without them. It is evidence, not prose. Record what actually happened, including
retries and false starts; a tidied-up record is the one failure mode this whole mechanism exists to
prevent.

## Phases

### 1. Plan

State, in one or two lines before writing anything:
- the single observable behavior this cycle delivers
- concrete input values and concrete expected output

You have the full conversation here, so use it — prior cycles, the user's actual words, decisions
already made. Don't re-derive what's already settled.

Size the step to be **the smallest thing that is still worth shipping on its own**. A cycle whose test
could be deleted tomorrow without anyone noticing the system got dumber was too small; merge it into
the behavior that justifies it. Multiple `assertThat` lines in one test are fine when they describe one
cohesive behavior — splitting one behavior across artificial cycles just multiplies overhead.

Derive the expected values from what the system *should* do — the specification, the user's report, the
contract of the method. Do not work out the fix first and then write down what it would produce. That
inverts the cycle: the test stops being a statement about behavior and becomes a restatement of the
implementation, so it can no longer fail for the right reason. This is the guarantee the old
multi-agent design got for free, and here it is yours to keep. If you already know the fix — common
when the user hands you a diagnosed bug — write the expected values down *before* you look at how the
fix would be written, and keep them.

When the user has already specified the behavior and the fix, don't restate it at length. One line
naming the behavior and the expected values is enough, and then move on.

**Declare a characterization cycle here or not at all.** If production already implements the behavior
and you are locking it in before a refactor, say so now, before writing the test. The declaration is
only meaningful in advance.

### 2. Red

Write **one** `@Test` method in the existing test class for that component. Read that class first and
mirror its conventions exactly — imports, `@BeforeEach` patterns, naming, assertion style, how it
constructs domain objects. Don't introduce helpers or abstractions the class doesn't already use, and
don't refactor anything while you're in there.

Name the test after the expected behavior, not the bug. No explanatory comments inside the body —
variable names carry it.

Then `run_test`, scoped as narrowly as it will go (`module` + `test_filter` for the single class).
Record the exit code. **Gate: non-zero, or characterization declared.**

### 3. Green

Change only what is causally necessary to make the failing test pass. No renaming, no restructuring,
no style improvements, no comments, no whitespace changes outside the changed lines. Those belong in
Tidy, one phase later, where the tests already protect them.

**Porting counts as minimal.** If a refactor is moving logic that already exists in another class,
copying it verbatim with only the mechanical adaptations needed to compile is fine — no new logic is
being authored, and the test is verifying the move preserved behavior. Note it in the cycle record as a
port so Verify doesn't read it as over-implementation.

`run_test` → exit 0. If it isn't green, say so and investigate; don't paper over it.

### 4. Tidy

Behavior must be **identical** before and after. Skip a change when you can point at a concrete way it
could differ — a caller, a subclass override, an input class — not merely when total certainty is
unavailable. "Some story exists where this matters" is true of every edit ever made, and treating it as
the bar turns this phase into a rubber stamp.

In scope: rename unclear locals/params/private methods, extract small pure helpers, guard clauses and
early returns, simplify conditionals, remove dead code and duplication, reorder private methods for
narrative flow.

Out of scope: anything observable. Public API signatures, test assertions (except exact duplication
between tests), new features, redesigns, performance tuning, new dependencies, moving classes between
packages. `migraphe-api` is public surface — be conservative there.

`references/tidy-notes.md` holds the repo's accumulated tidy rules. Read it every cycle — it is short
and the rules in it are general.

It ends with a gated table of per-class topic files. **Open a topic file only when the diff touches one
of the paths listed for it.** If the diff touches none of them, you are finished with references; don't
browse them for inspiration. These files exist to stop a specific dangerous tidy on a specific class,
and reading one you don't need costs more than it can possibly return.

Add to these notes when a cycle teaches you something durable — a trap that will bite again, an
invariant a tidier could break without noticing. Never record what you did or when; that is git's job,
and narrative is what made these files unreadable before.

Then `run_spotless` **first**, and `run_test` (module scope) after it. Spotless rewrites files, so
running it last would leave the committed tree in a state no test ever saw. Its rewrapping — including
of pre-existing violations on lines you touched — is expected noise, not a tidy action; never hand-format
in anticipation of it.

Take the diff you hand to Verify **after** spotless, so the audit sees the tree as it will be committed.

If nothing is worth tidying, say so with a one-line reason — "no duplication, names already clear" — so
it's visible that the phase ran and found nothing rather than silently vanishing. That exit is there for
honesty, not convenience: reaching for it every cycle is how the Refactor phase dies without anyone
noticing. Don't manufacture changes to avoid an empty phase either.

### 5. Verify

Spawn the `cycle-verifier` agent (`Agent` tool, `subagent_type: "cycle-verifier"`). Its whole value is
that it hasn't watched you make the decisions, so give it evidence rather than narration:

- the full post-spotless cycle diff — `git diff` plus any new files, pasted into the prompt
- the cycle record verbatim, including every `log_path` — it reads those logs itself
- the one-line behavior statement from Plan, and whether you declared characterization

It has `Read`/`Grep`/`Glob` to check surrounding code and to open the build logs, but it will not go
hunting for the diff. If you don't paste it, the audit is worthless.

## Acting on the verdict

Verify reports findings in five categories.

| Finding | Action |
|---|---|
| `gate` — Red gate not observed, or out of order | **Unfixable.** Report it and close. Do not replay a red run to produce a clean record. |
| `test-validity` — test tracks the implementation instead of the behavior | Fix the test, re-verify once. |
| `over-implementation` — Green wrote more than the test demanded | Trim, re-verify once. |
| `scope: too-large` | Note where the seam was; size the next cycle to it. Don't retro-split a green cycle. |
| `scope: too-small` | Don't merge it backwards — it's already green. Fold the follow-on behavior into the next cycle. |
| `regression: blocker` | Surface to the user, and make it the next cycle's input. Start that cycle from a failing test. |
| `regression: important` | Record as a candidate for a following cycle. |
| `regression: nice-to-have` | Ignore unless the fix is trivial and obviously safe. |

`test-validity` and `over-implementation` are worth fixing now because the fix is real: a better
assertion, a smaller diff. `gate` is different — it reports a fact about the past. The only "fix"
available is to revert, replay a red run you already know the answer to, and reapply, which manufactures
a record indistinguishable from an honest cycle. Take the finding.

A regression blocker means a *different* bug exists. Patching it here would be a production change with
no failing test behind it — exactly what this skill prevents. It needs its own cycle, and it needs the
user to know about it, because nothing guarantees that cycle ever starts.

**Re-verify at most once.** Spawn a fresh `cycle-verifier` rather than messaging the old one; its value
is the independent read. If the second audit repeats a finding you believe is wrong, don't loop — close
the cycle, state your disagreement and why, and let the user settle it. An audit that costs more than
the work it audits has stopped paying for itself.

**When the cycle closes**, report to the user: what behavior shipped, the verdict, and any blocker or
follow-up Verify named — including its `next cycle` line. Findings that reach nobody are the same as
findings that were never made.

## Running tests

Always go through the MCP server (`mcp__migraphe-build__run_test` / `run_spotless`). Don't invoke
`./gradlew` directly — the server caps output, extracts failure markers, and writes full logs to
`/tmp/migraphe-build-mcp/`.

Scope every run: `module` is the Gradle project name without the leading colon, `test_filter` is a
`--tests` pattern. Unscoped runs build and test all eight modules and are never needed inside a cycle.

Cost varies by an order of magnitude, so plan the cycle accordingly: modules testing against H2 or plain
JUnit finish in 3–15s, but `migraphe-plugin-mysql` and `migraphe-plugin-postgresql` drive real databases
through Testcontainers and take 55–85s per run even with a narrow filter. In those modules a four-run
cycle is five minutes of waiting — get the test right before running it rather than iterating through the
build. Modules:

```
migraphe-api  migraphe-core  migraphe-cli  migraphe-gradle-plugin
migraphe-plugin-jdbc  migraphe-plugin-postgresql  migraphe-plugin-mysql
migraphe-plugin-generator-json
```

Run the *narrow* filter during Red and Green, and the *module* scope once in Tidy to catch anything the
tidy disturbed. `run_build` and `run_errorprone_check` belong to the session-end checklist, not here —
`run_errorprone_check` does a clean build and takes over 12 minutes.

Every result carries a `log_path` under `/tmp/migraphe-build-mcp/`. Keep those paths in the cycle record —
they are what makes the gate auditable. Each log lists the Gradle tasks it ran, and `:<module>:compileJava`
being `UP-TO-DATE` versus executing tells Verify whether production sources had changed by that run. That
is independent of anything you write down, which is the point.

## One cycle only

This skill delivers exactly one cycle. Invoke it again for the next one. Resist the pull to keep going
"while you're in there" — the follow-up items Verify surfaced are the input to the *next* cycle, and
running them as separate cycles is what keeps each step small enough to reason about.
