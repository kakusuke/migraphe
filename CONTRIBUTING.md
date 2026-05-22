# Contributing to Migraphe

Thank you for considering contributing to Migraphe! This document provides guidelines for contributing.

## Development Workflow (GitHub Flow)

We use [GitHub Flow](https://docs.github.com/en/get-started/quickstart/github-flow):

1. **Create a branch** from `main`
   ```bash
   git checkout main
   git pull origin main
   git checkout -b feature/your-feature-name
   ```

2. **Make changes** following our coding standards

3. **Commit** with clear messages
   ```bash
   git commit -m "Add feature X for Y"
   ```

4. **Push** your branch
   ```bash
   git push -u origin feature/your-feature-name
   ```

5. **Create a Pull Request** on GitHub

6. After review and CI passing, the PR will be **merged**

### Branch Naming

- `feature/` - New features
- `fix/` - Bug fixes
- `docs/` - Documentation updates
- `refactor/` - Code refactoring

## Semantic Versioning

We follow [Semantic Versioning 2.0.0](https://semver.org/):

- **MAJOR** (X.0.0): Breaking changes
- **MINOR** (0.X.0): New features (backward compatible)
- **PATCH** (0.0.X): Bug fixes (backward compatible)

Version is managed in `gradle.properties`.

## Development Setup

### Prerequisites

- Java 21+
- Gradle 8.5+

### Build

```bash
./gradlew build
```

### Run Tests

```bash
./gradlew test
```

### Code Formatting

We use [Spotless](https://github.com/diffplug/spotless) with Google Java Format.

```bash
# Check formatting
./gradlew spotlessCheck

# Apply formatting (required before commit)
./gradlew spotlessApply
```

## Coding Standards

### General

- Keep code simple and focused (KISS principle)
- Follow existing patterns in the codebase
- Write tests for new functionality (TDD preferred)

### Java Style

- Use records for immutable data
- Prefer interfaces over abstract classes
- Use `@Nullable` (jspecify) for nullable fields
- Avoid `Optional` except for SmallRye `@ConfigMapping`

### Null Safety

- NullAway is enabled for compile-time null checks
- Annotate nullable parameters/fields with `@Nullable`
- Never pass `null` where not explicitly allowed

## Testing

- Use JUnit 5 + AssertJ
- Integration tests use Testcontainers
- All tests must pass before merging

```bash
./gradlew test
```

## Pull Request Guidelines

1. Keep PRs focused on a single concern
2. Update documentation if applicable
3. Ensure all tests pass
4. Run `./gradlew spotlessApply` before committing
5. Link related issues in the PR description

## Reporting Issues

- Use GitHub Issues
- Provide steps to reproduce
- Include environment details (Java version, OS)

## Distribution & JitPack operational notes

エンドユーザー向けの導入手順は [README](README.md) と [USER_GUIDE](docs/USER_GUIDE.md) を参照してください。Migraphe のプラグイン JAR と Gradle プラグインは現在 JitPack (`com.github.kakusuke.migraphe:<module>:<git-tag>`、現行は `v0.2.1`) 経由で配布されています。Maven Central 公開後は groupId が `io.github.kakusuke.migraphe` に切り替わる予定です。

コントリビューター向けの運用上の留意点:

- **タグ vs `main-SNAPSHOT`**: エンドユーザー向けドキュメントは安定 Git タグ (`v0.2.1` 等) を案内しています。最新 `main` を動作確認したい場合は `main-SNAPSHOT` を使えますが、JitPack は `main-SNAPSHOT` を `main-<tag>-<commit>-<n>` のような具体バージョンに解決するため、現状の `LockSyncChecker` は yaml と lock のバージョン文字列ミスマッチで失敗します（既知の不具合）。`main-SNAPSHOT` を試すときは、毎回 `migraphe.lock.yaml` を削除して `migraphe pin` で再生成する運用が必要です。
- **`main-SNAPSHOT` の SHA は不安定**: JitPack は `main` ブランチへのコミット毎に再ビルドを行い、その都度 JAR の SHA-256 が変わります。`migraphe pin` で記録したロック値が `ChecksumMismatchException` を起こした場合は、`migraphe pin` を再実行してコミットし直してください。
- **JitPack ビルドキャッシュのリフレッシュ**: 古いビルドが返る場合は <https://jitpack.io/#kakusuke/migraphe> で対象バージョンを "Look up" → "Get it" して再ビルドを促せます。
- **ローカル publish の groupId 切り替え**: デフォルトの `./gradlew publishToMavenLocal` は `io.github.kakusuke.migraphe` 配下に発行されます。JitPack 互換 artefact（`com.github.kakusuke.migraphe`）をローカルに置きたい場合は `./gradlew -PpublishGroup=com.github.kakusuke.migraphe publishToMavenLocal` を使ってください。両 groupId は `~/.m2/` で共存します。
- **JitPack ビルド設定**: `jitpack.yml` (`-PpublishGroup=com.github.kakusuke.migraphe publishToMavenLocal`) が JitPack のビルド手順を定義しています。`build.gradle.kts` の `allprojects.group` は `providers.gradleProperty("publishGroup").getOrElse("io.github.kakusuke.migraphe")` で、JitPack ビルド時のみ `com.github.kakusuke.migraphe` に切り替わります。

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
