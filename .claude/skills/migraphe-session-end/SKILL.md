---
name: migraphe-session-end
description: Migraphe session-end / pre-commit checklist. Use before a commit or when wrapping up a work session.
---

# Session End / Pre-commit Checklist

## Build Commands

```bash
./gradlew build          # Build
./gradlew test           # Run tests
./gradlew spotlessApply  # Format (MANDATORY before commit)
./gradlew clean build --warning-mode all 2>&1 | grep 警告  # ErrorProne check (MANDATORY before commit)
```

## Documentation — MANDATORY

Update when code changes:
- `README.md`, `README.ja.md` — Project overview
- `docs/USER_GUIDE.md`, `docs/USER_GUIDE.ja.md` — Detailed usage

### Japanese doc link consistency — MANDATORY

In any `.ja.md` file, content cross-reference links must point to the `.ja.md`
version of a doc when one exists — never the English `.md`. Verify with:

```bash
grep -rn --include='*.ja.md' -oE '\[[^]]*\]\([^)]*\.md[^)]*\)' . | grep -vE '\.ja\.md(#[^)]*)?\)'
```

Every hit must be one of these legitimate exceptions; anything else is a bug to fix:
- **Language-switcher links** whose label is `English` / `English version` / `英語版…` — these intentionally point to the English `.md`.
- Links to docs with **no `.ja.md` counterpart** (`CONTRIBUTING.md`, `CLAUDE.md`, `CHANGELOG.md`, `ARCHITECTURE.md`).

## Session End Procedure

1. Update `CLAUDE.md` (English only) with progress. Route detail to the split docs: append the session record to [docs/CHANGELOG.md](../../../docs/CHANGELOG.md) (keep only the latest summary in CLAUDE.md), and record new design-decision detail in [docs/ARCHITECTURE.md](../../../docs/ARCHITECTURE.md) (keep a one-line summary + link in CLAUDE.md).
2. Update user-facing docs (`README*.md`, `USER_GUIDE*.md`).
3. Ensure all tests pass (100%).
4. Run `./gradlew spotlessApply`.
5. Commit if working on a feature.

## Releases & version bumps

Version bumps and releases are explicit — never part of session end. Use the `migraphe-version-up` skill.

## ErrorProne / NullAway

For the warning-fix table and `@SuppressWarnings` rules, see the `migraphe-errorprone` skill.
