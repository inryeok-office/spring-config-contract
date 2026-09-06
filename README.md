# Spring Config Contract

> Catch Spring Boot configuration drift before deployment.

[![CI](https://github.com/inryeok-office/spring-config-contract/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/inryeok-office/spring-config-contract/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-21-blue.svg)](docs/compatibility.md)

Spring Config Contract is an open-source developer tool being built to check whether configuration required by a Spring Boot application matches the configuration provided by its deployment environment.

## Why?

Configuration drift is easy to miss: an application can require a property that is absent from deployment, while deployment files can retain values that the application no longer uses. This project is about comparing two different sides of that contract—not merely comparing Spring configuration files with each other.

Conceptually, an application might require `app.jwt.secret`, `DB_HOST`, and `REDIS_HOST`, while deployment provides `DB_HOST`, `REDIS_HOST`, and `LEGACY_API_KEY`. A future contract check should be able to report the missing and unused keys. This is an illustrative target, not an executable feature today.

## Status

**Early development.** Product functionality is not yet generally available. The repository currently contains the development infrastructure, module scaffolding, and project documentation. Development is targeting [v0.1.0](https://github.com/inryeok-office/spring-config-contract/milestone/1).

Follow the [v0.1.0 release tracker](https://github.com/inryeok-office/spring-config-contract/issues/26) to see the planned work.

## Planned v0.1.0 scope

The first release is planned to cover:

- Spring-side inputs: `@ConfigurationProperties`, `@Value`, and `application.yml` / supported Spring application configuration.
- Deployment-side inputs: `.env.example` and Docker Compose.
- Contract findings: missing and unused configuration, required/optional behavior, and default-value-aware comparison.
- Developer integration: a Gradle integration with a planned `configContractCheck` task and CI-friendly results.

These capabilities are planned and are not available in the current repository.

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

This is a conceptual data flow for the planned product.

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

JDK 21 is required. The product is not implemented yet, but the repository build and verification infrastructure can be run with the Gradle Wrapper:

```bash
./gradlew check
./gradlew build
```

On Windows, use `gradlew.bat`. See the [development guide](docs/development.md) and [testing guide](docs/testing.md) for contributor commands and expectations.

## Contributing

The project is early-stage, and focused contributions and design discussions are welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md), then review the [contribution workflow](docs/contribution-workflow.md) and [governance policy](docs/governance.md).

## License

Spring Config Contract is licensed under the [Apache License 2.0](LICENSE).
