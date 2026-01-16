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

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
