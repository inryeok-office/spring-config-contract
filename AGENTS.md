# AGENTS.md

## Purpose

Spring Config Contract is an early-stage developer tool for checking whether configuration required by a Spring Boot application matches configuration supplied by deployment environments.

## Architecture and boundaries

- `config-contract-core` contains future domain models and comparison rules. It must remain independent of Spring, Gradle APIs, and deployment formats.
- `config-contract-spring` contains Spring-specific extraction and may depend on core.
- `config-contract-deployment` contains deployment parsers and may depend on core. Planned inputs include `.env` and Docker Compose.
- `config-contract-gradle-plugin` is the integration layer and should compose the other modules; business rules do not belong here.
- `config-contract-test` contains shared test support and fixtures.
- `samples` contains future example Spring Boot projects.

Never modify these boundaries silently. Architecture changes require documentation and review.

## Repository policy

- Repository-facing code, issues, pull requests, commits, and documentation must be in English.
- Use Kotlin, JDK 21, Gradle Kotlin DSL, JUnit 5, and Spring Boot 3.x compatibility as the initial target.
- Centralize dependency versions in the version catalog where practical; avoid unnecessary dependencies and keep versions stable.
- No public API may be introduced casually. Review naming, compatibility, and documentation before exposing types or behavior.
- New functionality must have focused tests. AI-generated code receives exactly the same review and test requirements as human-written code.
- Inspect existing files and git status before modifying anything.

## Documentation and collaboration

Update relevant documentation for user-visible behavior, public APIs, and architectural decisions. Use Conventional Commits and the branch conventions in `CONTRIBUTING.md`. PRs should describe scope, testing, breaking changes, and related issues.

## AI-generated code

Agents must keep changes within the requested scope, explain meaningful assumptions, preserve existing user changes, and avoid speculative features. Agents must inspect their diff and run the required checks before finishing.

## Prohibited changes

Do not implement product functionality during scaffolding-only work. Never commit credentials, tokens, secret-bearing environment files, webhook URLs, private keys, or generated local state. Do not change visibility, collaborators, branch protection, rulesets, secrets, publishing, releases, or deployment configuration without explicit authorization.

## Required finishing commands

Before finishing a change, run formatting/lint verification, all tests, `./gradlew check`, and `./gradlew build`. Inspect the generated structure and `git diff`, validate workflow YAML and local documentation links where possible, and search for obvious secrets.
