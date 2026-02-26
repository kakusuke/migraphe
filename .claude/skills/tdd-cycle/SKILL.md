This skill runs one strict TDD cycle following t-wada style.

Precondition:
- A clear feature goal or bug symptom is defined by the parent.

This skill performs exactly ONE micro TDD loop.

Pipeline:

1) micro-plan
   - Define the smallest next observable behavior.
   - Do not design ahead.
   - Do not modify code.

2) test-writer
   - Write the smallest failing test for that behavior.
   - Do not modify production code.

Parent must run tests and confirm failure before proceeding.

3) minimal-fix
   - Modify production code minimally so all tests pass.
   - No refactor.
   - No improvement.
   - No structural cleanup.

Parent must run tests and confirm success.

4) regression-guard
   - Identify possible side effects.
   - Suggest missing edge case tests if necessary.
   - Do not modify production code.

5) tidy-after-green
   - Perform behavior-preserving tidy only.
   - Produce minimal diff.
   - No redesign.
   - Skip if no meaningful tidy exists.

Parent must run tests again after tidy.

Parent agent role:
- Run Bash (tests, build) between phases and review subagent results.
- Do NOT read source files, grep code, or write fixes in main context.
- All file reading, code analysis, and code changes are delegated to subagents.

Strict rules:
- Never merge phases.
- Never skip test execution between phases.
- Abort if behavior might change.
- Keep all steps minimal.
- This skill performs only one small cycle.
- The parent may invoke it repeatedly for incremental progress.
