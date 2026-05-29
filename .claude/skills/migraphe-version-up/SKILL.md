---
name: migraphe-version-up
description: Bump the Migraphe project version and drive its release. Use whenever the user wants to cut a release, bump the version number, or says things like "version up to 0.4.0", "release 0.4.0", "新しいバージョンを出して", "リリースして", or "バージョンを上げて" — even if they don't name every file that needs touching. Works in two phases: (1) inspect the changes since the last release, propose major/minor/patch, update every version reference, and open a PR; (2) after the PR is merged, tag and push to trigger the release workflow.
---

# Migraphe Version Up / Release

## Why this skill exists

Migraphe's version is declared canonically in **`gradle.properties`** — that single line is what actually changes the built artifacts. Around 50 *cosmetic* copies of the version string are scattered across docs, samples, and plugin READMEs (both bare `0.3.0` and `v`-prefixed `v0.3.0` JitPack tag forms). They must all move together, because a doc advertising `v0.4.0` while `gradle.properties` still says `0.3.0` ships the wrong artifact. The easy mistake is editing the visible docs and forgetting the one file that matters.

A `vX.Y.Z` tag push triggers `.github/workflows/release.yml`, which builds and publishes. So a release is really "bump everything → land it on main → tag it". This skill keeps a human merge gate in the middle so the tag (an irreversible, public action) only happens after review.

## Choosing the next version

Don't guess the bump level — derive it from what actually changed since the last release, then propose it to the user for confirmation.

1. Find the last released tag: `git tag --sort=-v:refname | head -1` (e.g. `v0.3.0`).
2. Inspect the changes since then: `git log <tag>..HEAD --oneline` (and skim `git diff <tag>..HEAD --stat` if commit messages are thin). Look at the Conventional-Commits prefixes and, more importantly, whether anything is **breaking** — a removed/renamed public API, a config-schema change, a CLI flag rename, a behavior change users depend on.
3. Classify and map to a bump. **While on `0.x`** (current state), the Cargo/npm `^`-compatibility boundary lives at the MINOR position, so:
   - **Breaking change** (incompatible API/config/CLI/behavior) → **MINOR** (`0.3.0` → `0.4.0`)
   - **New feature, backward-compatible** → **PATCH** (`0.3.0` → `0.3.1`)
   - **Bug fix / docs / internal only** → **PATCH** (`0.3.0` → `0.3.1`)

   Once the project reaches `1.0.0`, switch to standard SemVer (breaking → MAJOR, feature → MINOR, fix → PATCH).
4. **Propose the level and the resulting version to the user with a one-line justification** citing the deciding change — e.g. "Since v0.3.0 there's a breaking config rename (`scan-root`), so I propose a MINOR bump → **0.4.0**. OK, or override?" Let the user confirm or override before touching any files.

## Phase 1 — Bump every version reference and open a PR

1. Settle on the target `X.Y.Z` (see "Choosing the next version" above). Read the current version from `gradle.properties`.
2. **Branch:** `git checkout -b chore/bump-version-X.Y.Z`.
3. **Canonical bump (never skip):** set `version=X.Y.Z` in `gradle.properties`.
4. **Blanket replace** the old version → new, covering *both* bare (`0.3.0`) and `v`-prefixed (`v0.3.0`) forms, across:
   - `README.md`, `README.ja.md`
   - `docs/USER_GUIDE.md`, `docs/USER_GUIDE.ja.md`, `docs/PLUGIN_DEVELOPMENT.md`, `docs/PLUGIN_DEVELOPMENT.ja.md`
   - `CONTRIBUTING.md`
   - each plugin's `README.md` / `README.ja.md`
   - `sample/cli/*`, `sample/gradle/*` (incl. `build.gradle.kts`, `settings.gradle.kts`)
5. **Do NOT touch these — they record history:**
   - `docs/CHANGELOG.md` and `docs/ARCHITECTURE.md` — historical entries like "Version bump 0.2.1 → 0.3.0" are records; rewriting them corrupts the changelog.
   - `CLAUDE.md` — only current-state references may change; never rewrite its historical changelog summary.
   - **Sample lockfiles (`sample/**/migraphe.lock.yaml`) are gitignored** — out of git, so they never appear in the bump. Their SHA-256 hashes are tied to released JARs; sample users regenerate them locally with `migraphe pin`. There's nothing to do here for them.
6. **Verify before committing:** grep for the OLD version string across the repo and confirm the only remaining hits are the intentional history exclusions above. Show the user a short diff summary (files changed, count).
7. **Commit, push, open PR:**
   - Commit message: `chore: bump version to X.Y.Z` (conventional-commits style; end the body with the `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>` line per repo convention).
   - `git push -u origin chore/bump-version-X.Y.Z`
   - `gh pr create` with a body summarizing the bump and the change set that justified the level.
8. **Report the PR URL and STOP.** The tag must wait for the human merge gate — do not tag yet.

## Phase 2 — After the PR is merged: tag and release

Trigger this phase when the user reports the bump PR has been merged (e.g. "merged", "マージしたよ", "PR入った").

1. `git checkout main && git pull`.
2. Confirm `gradle.properties` on `main` shows `version=X.Y.Z` (guards against tagging the wrong commit).
3. **Tag and push:** `git tag vX.Y.Z && git push origin vX.Y.Z` — this triggers `.github/workflows/release.yml`.
4. Report that the release workflow is running and point the user at the Actions run; JitPack will build the new tag on first request.
