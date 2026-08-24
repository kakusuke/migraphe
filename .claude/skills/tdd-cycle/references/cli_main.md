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
reports its own rejection.

If the two `null` reasons ever need to be distinguished by *callers* rather than just reported, that is
a return-type change across every null-returning helper here (plus their NullAway annotations) — a
design step with its own cycle, not a tidy.

## `run` reads `user.dir` per call

`baseDir` is `Paths.get(System.getProperty("user.dir"))`, read fresh on every `run` and never cached.
Tests reach the real `run` path by setting that property and restoring it in a `finally`. This holds
only while `migraphe-cli` has no in-JVM parallelism (`build.gradle.kts` sets no `maxParallelForks` /
`forkEvery`, and the repo has no `junit-platform.properties`). Enabling either makes those tests
unsafe rather than merely unusual.
