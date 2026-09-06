# Development

## Toolchain

Use the Gradle Wrapper, Kotlin JVM, JDK 21 toolchain, Gradle Kotlin DSL, JUnit Platform/Jupiter, and ktlint. The root build files and version catalog are the source of truth for versions and repositories.

Run:

```bash
./gradlew check
./gradlew build
```

`verifyRepositoryRules` runs as part of `check`. It reports forbidden framework references in framework-independent modules and secret-sensitive filenames that should never enter the repository.

On Windows, use `gradlew.bat`. Do not commit generated `build/` output or local Gradle state.

## Local workflow

For a small contribution, create a branch, make the focused change, run relevant validation, and open a PR. For normal features and bugs, start with an Issue, use the branch convention, implement with tests, and submit a PR for review and CI. Architecture or public API changes need design discussion and an ADR when the decision is durable.

Branch names are `feat/<issue>-description`, `fix/<issue>-description`, `docs/<issue>-description`, `refactor/<issue>-description`, or `test/<issue>-description`. Commits use Conventional Commits.

## Engineering expectations

Keep patches scoped and avoid unrelated cleanup, drive-by dependency upgrades, and broad formatting rewrites. All repository-facing communication is English. Update documentation for user-visible behavior, public APIs, compatibility changes, and architectural decisions. See [the dependency policy](dependency-policy.md) before adding libraries and [the public API policy](public-api-policy.md) before exposing types.
