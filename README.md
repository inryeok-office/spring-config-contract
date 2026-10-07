# Spring Config Contract

> Catch Spring Boot configuration drift before deployment.

[![CI](https://github.com/inryeok-office/spring-config-contract/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/inryeok-office/spring-config-contract/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-21-blue.svg)](docs/compatibility.md)

Spring Config Contract is an open-source developer tool that checks whether configuration required by a Spring Boot application matches the configuration provided by its deployment environment.

## Why?

Configuration drift is easy to miss: an application can require a property that is absent from deployment, while deployment files can retain values that the application no longer uses. This project is about comparing two different sides of that contract—not merely comparing Spring configuration files with each other.

For supported inputs, the v0.1 workflow reports required keys that deployment omits and deployment keys the application does not recognize. See the [usage guide](docs/usage-guide.md) for the supported input subset and examples.

## Status

**Early development.** The v0.1 `configContractCheck` Gradle task is implemented and under release-candidate review, but not yet published; see the [usage guide](docs/usage-guide.md) to try it from a source checkout. The release scope is tracked in the [v0.1.0 milestone](https://github.com/inryeok-office/spring-config-contract/milestone/1).

Follow the [v0.1.0 release tracker](https://github.com/inryeok-office/spring-config-contract/issues/26) for scope status.

## v0.1.0 scope

The first release implements:

- Spring-side inputs: supported `@ConfigurationProperties`, `@Value`, and application configuration cases, built and tested against Spring Boot 3.5.x.
- Deployment-side inputs: `.env.example` and Docker Compose.
- Contract findings: missing and unused configuration, required/optional behavior, and default-value-aware comparison.
- Developer integration: a Gradle integration with the `configContractCheck` task and CI-friendly results.

The plugin is not published. It can be used from a source checkout as described in the [usage guide](docs/usage-guide.md).

## How it is intended to work

```text
Spring Boot
@ConfigurationProperties / @Value / application.yml
                    │
                    ▼
             Requirements
                    │
                    ▼
             Contract Engine
                    ▲
                    │
           Provided Config
                    ▲
                    │
      .env.example / Docker Compose
```

This is the v0.1 data flow for the supported inputs.

## The `configContractCheck` task

The Gradle plugin `io.github.inryeok-office.config-contract` is implemented on `main` but not yet published, so it can only be used from a source build. It adds a `configContract` extension and a `configContractCheck` task:

```kotlin
plugins {
    java
    id("io.github.inryeok-office.config-contract")
}

configContract {
    activeProfiles.set(listOf("prod"))        // optional; empty analyzes the default profile
    dotenvExampleFiles.from(".env.example")   // optional
    composeFiles.from("compose.yml")          // optional
    // applicationConfigurationFiles defaults to top-level application*.properties|yml|yaml in main resources
}
```

`./gradlew configContractCheck` compiles the `main` source set, discovers its requirements, reads the deployment files, and prints one line per finding, ordered by key. The task fails when any `MISSING` or `UNUSED` finding exists; Spring discovery diagnostics are listed but do not fail the build. Every path in the output is relative to the root project, and deployment files must live inside it. The task is not attached to `check`.

## Architecture

- `config-contract-core`: framework-independent domain models and comparison rules.
- `config-contract-spring`: Spring-specific interpretation and discovery.
- `config-contract-deployment`: deployment-format parsers and adapters.
- `config-contract-gradle-plugin`: Gradle orchestration and integration.
- `config-contract-test`: shared fixtures and test utilities.

See the [architecture guide](docs/architecture.md) for dependency boundaries and invariants.

## Roadmap

See the [roadmap](docs/roadmap.md), [v0.1.0 milestone](https://github.com/inryeok-office/spring-config-contract/milestone/1), and [release tracker](https://github.com/inryeok-office/spring-config-contract/issues/26).

## Development

JDK 21 is required. The implemented workflow and repository verification can be run with the Gradle Wrapper:

```bash
./gradlew check
./gradlew build
```

On Windows, use `gradlew.bat`. See the [development guide](docs/development.md) and [testing guide](docs/testing.md) for contributor commands and expectations.

## Contributing

The project is early-stage, and focused contributions and design discussions are welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md), then review the [contribution workflow](docs/contribution-workflow.md) and [governance policy](docs/governance.md).

## License

Spring Config Contract is licensed under the [Apache License 2.0](LICENSE).
