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

## Pre-release builds via JitPack (beta channel)

> ⚠️ **Beta channel — temporary distribution**
>
> JitPack 経由の配布は Maven Central 対応までの暫定措置です。
> 将来座標は `io.github.kakusuke.migraphe:<module>:X.Y.Z`（Maven Central）に
> 置き換わります。現時点の `com.github.kakusuke.migraphe:<module>:main-SNAPSHOT`
> 座標を **本番運用に組み込まないでください**。
>
> このセクションは Migraphe 本体コントリビューターおよびプラグイン開発者が、
> push されたばかりの `main` を `git clone` なしで動作確認するための手順です。

### CLI から利用する場合

任意のディレクトリに以下の `migraphe.yaml` を配置（座標は将来の Maven Central 移行で変わります）:

```yaml
repositories:
  - id: jitpack
    url: https://jitpack.io

plugins:
  - { coordinate: com.github.kakusuke.migraphe:migraphe-plugin-postgresql:main-SNAPSHOT, repository: jitpack }
  - { coordinate: com.github.kakusuke.migraphe:migraphe-plugin-mysql:main-SNAPSHOT, repository: jitpack }
  - { coordinate: com.github.kakusuke.migraphe:migraphe-plugin-generator-json:main-SNAPSHOT, repository: jitpack }

# ... project / history / generators / targets / tasks は通常どおり
```

> ℹ️ Maven Central 対応後は `io.github.kakusuke.migraphe:<module>:X.Y.Z` 座標 + `repositories` ブロック削除（mavenCentral 自動）に書き換えてください。

その後 `migraphe pin` で `migraphe.lock.yaml` を生成、`migraphe validate` でロックファイル整合を確認、`migraphe status` で動作検証します。

### Gradle から利用する場合

```kotlin
plugins {
    id("io.github.kakusuke.migraphe") version "0.1.0-SNAPSHOT"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-postgresql:main-SNAPSHOT")
    migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-mysql:main-SNAPSHOT")
    migraphePlugin("com.github.kakusuke.migraphe:migraphe-plugin-generator-json:main-SNAPSHOT")
}
```

> ℹ️ Maven Central 対応後は `migraphePlugin("io.github.kakusuke.migraphe:<module>:X.Y.Z")` + JitPack リポジトリ削除に書き換えてください。

### 注意事項

- **`main-SNAPSHOT` の SHA は不安定**: JitPack は `main` ブランチへのコミット毎に再ビルドを行い、その都度 JAR の SHA-256 が変わります。`migraphe pin` で記録したロック値が `ChecksumMismatchException` を起こした場合は、`migraphe pin` を再実行してコミットし直してください。
- **JitPack ビルドキャッシュのリフレッシュ**: 古いビルドが返る場合は <https://jitpack.io/#kakusuke/migraphe> で対象バージョンを "Look up" → "Get it" して再ビルドを促せます。
- **ローカルの maven-publish との切り替え**: デフォルトの `./gradlew publishToMavenLocal` は `io.github.kakusuke.migraphe` 配下に発行されます。JitPack 互換 artefact（`com.github.kakusuke.migraphe`）をローカルに置きたい場合は `./gradlew -PpublishGroup=com.github.kakusuke.migraphe publishToMavenLocal` を使ってください。両 groupId は `~/.m2/` で共存します。

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
