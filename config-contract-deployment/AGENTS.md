# Deployment module instructions

## Responsibility

Own parsers and adapters for deployment configuration formats, including planned `.env` and Docker Compose inputs.

## Boundaries

- Allowed: `config-contract-core` and narrowly justified format libraries.
- Forbidden: Spring dependencies, Gradle APIs, and Spring-specific interpretation.
- Parsing must be deterministic and must not depend on ambient machine state.
- Malformed, ambiguous, and unsupported input must produce explicit, tested behavior.

## Tests and common mistakes

Cover valid, empty, malformed, and boundary inputs with minimal fixture files. Filesystem-sensitive tests must use fixtures or temporary directories, never developer-specific absolute paths. Do not embed comparison rules or Spring semantics in parsers.

## Definition of done

The parser behavior and failure modes are tested, dependencies are justified, documentation is updated when needed, `./gradlew :config-contract-deployment:check` passes, and no Spring or Gradle dependency has been introduced.
