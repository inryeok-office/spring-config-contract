# Core module instructions

## Responsibility

Own framework-independent contract models, domain concepts, and comparison rules. Keep this module usable without Spring, Gradle, deployment formats, filesystem access, or environment access.

## Boundaries

- Allowed: the JDK, Kotlin standard library, and deliberately minimal dependencies.
- Forbidden: Spring libraries, Gradle APIs, deployment parser libraries, direct filesystem/environment access, and integration-specific types.
- Models and rules must be deterministic and independent of machine state.
- If external input is needed, model it through an explicit abstraction supplied by an outer layer.

## Tests and common mistakes

Add focused unit and regression tests for every behavior change, including edge and unsupported cases. Do not put parsing, Spring interpretation, or Gradle orchestration here. Do not expose integration types through core APIs, or use time, random state, environment variables, or the filesystem implicitly.

## Definition of done

The change has deliberate API review, tests, documentation/ADR updates when needed, passes ktlint and `./gradlew :config-contract-core:check`, and preserves the framework-independent dependency boundary.
