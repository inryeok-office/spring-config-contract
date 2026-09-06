# Test-support module instructions

## Responsibility

Provide reusable fixtures, builders, and test utilities shared by module tests.

## Boundaries

- Allowed: narrowly scoped test-support dependencies and modules needed by tests.
- Forbidden: product logic disguised as helpers, production behavior, and hidden architecture decisions.
- Fixtures must be minimal, purpose-specific, readable, and deterministic.
- Avoid absolute paths, wall-clock assumptions, network access, credentials, and mutable global machine state.

## Tests and common mistakes

Test helpers through the consuming tests. Keep fixture setup explicit and avoid helpers that make assertions or conceal important behavior. Do not add a utility here merely to shorten trivial test code.

## Definition of done

The support API is justified, consumers remain clear, tests are deterministic, documentation is updated when needed, and `./gradlew :config-contract-test:check` passes.
