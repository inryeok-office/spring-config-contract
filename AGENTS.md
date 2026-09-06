# Repository instructions

## Purpose

Spring Config Contract is an early-stage developer tool intended to verify whether configuration required by a Spring Boot application matches configuration supplied by deployment environments. Product functionality is not yet implemented; keep infrastructure work separate from future feature work.

## Repository map

- `config-contract-core`: framework-independent domain models and comparison rules.
- `config-contract-spring`: Spring-specific interpretation and extraction.
- `config-contract-deployment`: deployment-format parsers and adapters.
- `config-contract-gradle-plugin`: Gradle orchestration and integration.
- `config-contract-test`: reusable test fixtures and utilities only.
- `samples`: future example applications; not a production module.
- `docs`: canonical engineering policies and architecture decisions.

Read the relevant module `AGENTS.md` before changing that module.

## Sources of truth

- Architecture and boundaries: [docs/architecture.md](docs/architecture.md)
- Development workflow and conventions: [docs/development.md](docs/development.md)
- Testing strategy: [docs/testing.md](docs/testing.md)
- Compatibility: [docs/compatibility.md](docs/compatibility.md)
- Public API: [docs/public-api-policy.md](docs/public-api-policy.md)
- Dependencies: [docs/dependency-policy.md](docs/dependency-policy.md)
- AI-assisted development: [docs/ai-development.md](docs/ai-development.md)
- Contribution workflow: [docs/contribution-workflow.md](docs/contribution-workflow.md)
- Architecture decisions: [docs/decisions/README.md](docs/decisions/README.md)

These documents are canonical. Link to them instead of duplicating detailed policy.

## Global invariants

- `config-contract-core` must remain independent of Spring and Gradle APIs.
- Product rules belong in core, deployment-specific parsing in deployment, Spring behavior in spring, and orchestration in the Gradle plugin.
- Tests must be deterministic and independent of developer-specific local state.
- Never commit secrets, credentials, tokens, private keys, webhook URLs, or secret-bearing environment files.
- Repository-facing communication is English.

## Work protocol

Before implementation, inspect the relevant instructions, implementation, tests, architecture/policy documentation, Issue scope, public API impact, and compatibility concerns. During implementation, keep the patch scoped, preserve boundaries and compatibility, add behavior tests, and update user-facing documentation.

Before completion, inspect `git diff`, run targeted tests and repository checks, verify formatting, documentation consistency, and secret hygiene, and report any incomplete verification. Work through an Issue-backed branch and PR; never push directly to `main`.

## Failure and change discipline

Never delete or weaken tests to make CI green, silence an error without understanding it, silently skip verification, fabricate command results, or guess unsupported Spring behavior. Prefer explicit unsupported/unknown behavior over incorrect assumptions. Use Conventional Commits, avoid drive-by dependency upgrades and unrelated formatting rewrites, and document architectural changes with an ADR when appropriate.

## Required validation

```bash
./gradlew check
./gradlew build
git diff --check
```

Run relevant targeted tests as described in [docs/testing.md](docs/testing.md).
