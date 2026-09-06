# Spring module instructions

## Responsibility

Own Spring-specific interpretation and extraction. Spring Boot 3.x is the initial target; this module adapts Spring semantics to the framework-independent core model.

## Boundaries

- Allowed: `config-contract-core` and Spring dependencies when explicitly justified.
- Forbidden: deployment-format parsing, `.env`/Compose ownership, and Gradle APIs.
- Isolate framework-version compatibility at this boundary.
- Do not guess Spring semantics when reliable determination is impossible; make unsupported behavior explicit.

## Tests and common mistakes

Use focused integration tests for supported Spring behavior and test relevant Java and Kotlin usage where applicable. Keep fixtures minimal. Do not duplicate core rules, parse deployment files here, or leak Spring implementation types into core APIs.

## Definition of done

The change has compatibility review, focused tests, documentation updates when behavior changes, passes ktlint and `./gradlew :config-contract-spring:check`, and keeps deployment parsing outside this module.
