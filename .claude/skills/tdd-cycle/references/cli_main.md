# Main (CLI entry point)

One reporting invariant that looks like a layering violation and is not.

## `createCommand` prints "Unknown command" from its `default` arm — do not hoist it

`createCommand(String, String[], ExecutionContext)` returns `@Nullable Command`, and that `null`
carries two different meanings:

- the command word matched nothing (`default` arm)
- the command word matched, but that command rejected its arguments (`createDownCommand`'s early
  `return null` after printing `Error: Version argument or --all required …`)

Only the arm that produced the `null` knows which case it is. `run`'s `if (command == null)` branch
cannot tell them apart, which is exactly why the `Unknown command: <name>` line used to be printed
there for both — so `migraphe down` emitted a correct diagnosis followed by a false one.

A factory method that writes to `System.err` reads like a smell, and the tempting tidy is to move the
print back up to the caller "where the reporting belongs". That reinstates the bug silently: every
test still passes, because the assertion that catches it lives in `MainTest`'s
`unknownCommandShouldBeReportedOnlyForUnrecognisedCommandWord`, which goes through `Main.run` — check
it is still there before touching this.

Printing from this layer is the file's convention, not an exception to it: `createDownCommand` already
reports its own rejection. `printUsage()` sits in the same arm for the same reason — the full help is
useful to someone who mistyped a command name and is noise after a targeted "you need --all or a
version" line.

The consequence is that `run`'s `if (command == null)` branch is now **silent by design**. Any new
`case` whose factory can return `null` must report the reason itself, or the user gets a bare exit
code 1 with no output at all.

If the two `null` reasons ever need to be distinguished by *callers* rather than just reported, that is
a return-type change across every null-returning helper here (plus their NullAway annotations) — a
design step with its own cycle, not a tidy.

## A new boolean flag must be registered in `firstPositionalArg`

`firstPositionalArg` walks the arguments skipping known flags and returns the first token that is not
one. Its `boolFlags` set is the *only* place that knows a bare flag takes no value. A flag added to a
command but not to that set is returned as the positional argument — so `migraphe amend --whatever`
is read as "amend the migration named `--whatever`", and the command reports that no such migration
exists rather than that the flag was unknown. `valueFlags` is the same hazard for a flag that
takes a value: omit it and the flag's *value* becomes the positional argument.

Nothing catches this at compile time and a command's own tests usually pass, because they construct
the command directly rather than going through the parser.

## `--check` is read from the raw arguments, not through `firstPositionalArg`

`case "status"` reads `List.of(args).contains("--check")` directly, the way `pin` already does. That
is deliberate for a flag with no value and no positional argument beside it, but it still has to be
in `boolFlags` — `status` takes no positional argument today, and the day it does, an unregistered
flag becomes that argument.

## `run` reads `user.dir` per call

`baseDir` is `Paths.get(System.getProperty("user.dir"))`, read fresh on every `run` and never cached.
Tests reach the real `run` path by setting that property and restoring it in a `finally`. This holds
only while `migraphe-cli` has no in-JVM parallelism (`build.gradle.kts` sets no `maxParallelForks` /
`forkEvery`, and the repo has no `junit-platform.properties`). Enabling either makes those tests
unsafe rather than merely unusual.
