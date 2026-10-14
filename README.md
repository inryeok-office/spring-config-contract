# Spring Config Contract

> Catch Spring Boot configuration drift before deployment.

[![CI](https://github.com/inryeok-office/spring-config-contract/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/inryeok-office/spring-config-contract/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-21-blue.svg)](docs/compatibility.md)

Spring Config Contract checks whether configuration required by a Spring Boot application is supplied by its deployment configuration. It reports missing required keys and deployment keys that the application does not recognize.

## Release status

The v0.1.0 runtime modules are published to Maven Central. The initial submission of the Gradle plugin
`io.github.inryeok-office.config-contract` has completed, but Gradle Plugin Portal first-publication approval and public consumer verification are still pending. Until both complete, public Plugin Portal installation is not a verified workflow.

Use a [source checkout](docs/usage-guide.md#quick-start) for development today. The [publication guide](docs/publication.md) records the release state and remaining gates.

## What v0.1 provides

- Spring requirements from the supported `@ConfigurationProperties`, `@Value`, and application configuration subset.
- Deployment inputs from `.env.example` and Docker Compose `services.<name>.environment` entries.
- Deterministic `MISSING` and `UNUSED` findings, with required, optional, and default-aware behavior.
- The `configContractCheck` Gradle task, which fails non-zero when it finds either category.

The task compares key presence; it does not compare values. See the [compatibility guide](docs/compatibility.md) for the tested JDK and Spring Boot line, and the [usage guide](docs/usage-guide.md#known-limitations-and-unsupported-inputs) for v0.1 limitations.

## Installation after Plugin Portal approval

Once the plugin is publicly visible and the published-consumer checks pass, an external project will use the versioned Plugin Portal declaration below:

```kotlin
plugins {
    java
    id("io.github.inryeok-office.config-contract") version "0.1.0"
}

configContract {
    dotenvExampleFiles.from("deploy/.env.example")
    composeFiles.from("deploy/compose.yml")
}
```

Then run the task explicitly:

```bash
./gradlew configContractCheck
```

This is the intended published installation path, not a currently verified public-installation instruction. For the current source-checkout workflow, see the [usage guide](docs/usage-guide.md#quick-start).

## How it works

```text
Spring Boot requirements                    Deployment-provided keys
@ConfigurationProperties / @Value           .env.example / Docker Compose
application.properties / yml                 services.<name>.environment
                    │                                  │
                    └────────── Contract comparison ───┘
                                      │
                            MISSING / UNUSED findings
```

The Gradle plugin registers the `configContract` extension and `configContractCheck` task. The task analyzes the compiled `main` source set. It is deliberately not attached to `check`, so CI must invoke it explicitly. Deployment and application configuration paths must resolve inside the root project, and reported paths are relative to that root. Findings are ordered by key; diagnostics for unsupported Spring discovery cases are reported but do not themselves fail the build.

For a passing configuration, the task reports:

```text
Configuration contract check passed: no findings
```

For a missing required key, it fails non-zero with a finding such as:

```text
MISSING app.api-key (com.example.AppSettings.apiKey)
```

`UNUSED` deployment keys fail the task in the same way. The [usage guide](docs/usage-guide.md#findings) defines the exact behavior and supported discovery subset.

## Documentation

- [Usage guide](docs/usage-guide.md): installation states, DSL configuration, diagnostics, and limitations.
- [Compatibility](docs/compatibility.md): supported JDK, Gradle Wrapper, Kotlin, and Spring Boot claims.
- [Architecture](docs/architecture.md): module boundaries and dependency direction.
- [Publication readiness](docs/publication.md): artifact topology, release gates, and recovery policy.
- [Samples](samples/README.md): the offline end-to-end scenarios used by the build.

## Development and contributing

JDK 21 and the checked-in Gradle Wrapper are required:

```bash
./gradlew check
./gradlew build
```

On Windows, use `gradlew.bat`. Start with [CONTRIBUTING.md](CONTRIBUTING.md), then read the [development guide](docs/development.md), [testing guide](docs/testing.md), and [governance policy](docs/governance.md).

## License

Spring Config Contract is licensed under the [Apache License 2.0](LICENSE).
