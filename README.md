# Spring Config Contract

Spring Config Contract is an open-source developer tool intended to check whether the configuration required by a Spring Boot application matches the configuration provided by its deployment environments.

## Status

This project is in early development. The repository currently contains development infrastructure and module scaffolding; product functionality has not been implemented yet.

## Planned capabilities

The project is planned to support configuration requirement discovery, deployment configuration parsing, missing and unused configuration detection, required/optional/default handling, and Gradle integration. Initial targets include Spring Boot 3.x applications, `application.yml`, `.env.example`, and Docker Compose.

## Architecture

The project is organized into independent layers:

- `config-contract-core`: framework-independent domain models and comparison rules.
- `config-contract-spring`: Spring-specific configuration extraction.
- `config-contract-deployment`: deployment configuration parsers.
- `config-contract-gradle-plugin`: Gradle integration that composes the other layers.
- `config-contract-test`: shared test support and fixtures.

See [the architecture guide](docs/architecture.md) for dependency boundaries.

## Build

JDK 21 is required. Run the checks with the Gradle Wrapper:

```bash
./gradlew check
./gradlew build
```

On Windows, use `gradlew.bat`.

See [development](docs/development.md), [architecture](docs/architecture.md), and [testing](docs/testing.md) for project conventions.

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening an issue or pull request.
