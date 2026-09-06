# Architecture

Spring Config Contract is structured as a small dependency-directed multi-module Gradle project.

```text
core  ←  spring
  ↑       ↑
deployment ← gradle-plugin
      test-support (shared test infrastructure)
```

`config-contract-core` is the center of the design. It will contain domain models and comparison rules and must remain independent of Spring, Gradle APIs, and deployment formats. `config-contract-spring` may depend on core for Spring Boot configuration extraction. `config-contract-deployment` may depend on core for parsers such as `.env` and Docker Compose. `config-contract-gradle-plugin` is an integration layer that composes these modules; business rules must not be placed there.

`config-contract-test` is reserved for reusable fixtures and test utilities. The `samples` directory will eventually hold example Spring Boot applications and is not a production module.
