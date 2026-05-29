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

## Session End Procedure

1. Update `CLAUDE.md` (English only) with progress. Route detail to the split docs: append the session record to [docs/CHANGELOG.md](../../../docs/CHANGELOG.md) (keep only the latest summary in CLAUDE.md), and record new design-decision detail in [docs/ARCHITECTURE.md](../../../docs/ARCHITECTURE.md) (keep a one-line summary + link in CLAUDE.md).
2. Update user-facing docs (`README*.md`, `USER_GUIDE*.md`).
3. Ensure all tests pass (100%).
4. Run `./gradlew spotlessApply`.
5. Commit if working on a feature.

**Version bumps**: when changing the version in docs/samples, **always bump `gradle.properties` in the same change** — it is the canonical version that drives the built artifacts (the doc/sample/JitPack-tag edits are cosmetic without it). See the full Release procedure in [CONTRIBUTING.md](../../../CONTRIBUTING.md#release-procedure).

## ErrorProne / NullAway

For the warning-fix table and `@SuppressWarnings` rules, see the `migraphe-errorprone` skill.
