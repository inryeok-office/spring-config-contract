# Gradle plugin module instructions

## Responsibility

Own Gradle plugin IDs, extensions, task wiring, input/output integration, and composition of the other modules.

## Boundaries

- Allowed: Gradle APIs and project integration modules.
- Forbidden: product business rules, parser implementations, and core APIs that expose Gradle types.
- Keep business decisions in core or the owning integration module.
- Tasks must produce deterministic, CI-friendly outcomes.

## Tests and common mistakes

Use Gradle TestKit for meaningful plugin behavior once implemented, including task outcomes and representative sample builds. Do not hide domain logic in task actions, rely on local Gradle state, or make network access part of tests.

## Definition of done

The orchestration change has integration coverage where applicable, compatibility and public API impact are reviewed, documentation is updated, `./gradlew :config-contract-gradle-plugin:check` passes, and no product logic has been added to the plugin.
