# Architecture

Spring Config Contract is a dependency-directed multi-module Gradle project. The current implementation is scaffolding only; the boundaries below describe ownership for future work.

```text
                 +--------------------------+
                 | config-contract-gradle   |
                 |       -plugin            |
                 +------------+-------------+
                              | orchestrates
              +---------------+----------------+
              v                                v
        +-----------+                    +-------------+
        |  spring   |                    | deployment  |
        +-----+-----+                    +------+------+
              +---------------+----------------+
                              v
                       +--------------+
                       |     core     |
                       | domain/rules |
                       +--------------+

        config-contract-test supports tests; it is not production logic.
```

## Module ownership

| Module | Owns | May depend on | Must not own |
| --- | --- | --- | --- |
| `config-contract-core` | Framework-independent contract models and comparison rules | Minimal JDK/Kotlin functionality | Spring, Gradle APIs, filesystem access, environment access, or format-specific parsing |
| `config-contract-spring` | Spring-specific interpretation and extraction | `core`, Spring libraries when introduced | `.env`/Compose parsing or Gradle orchestration |
| `config-contract-deployment` | Deployment input parsers and adapters | `core`, format libraries when justified | Spring behavior or Gradle APIs |
| `config-contract-gradle-plugin` | Gradle extension/task wiring and composition | Other project modules and Gradle APIs | Product business rules or Gradle types in core |
| `config-contract-test` | Reusable fixtures, builders, and test utilities | Modules needed to support tests | Hidden product logic or production dependencies |

## Dependency direction

Core is the ownership center. Spring and deployment depend inward on core. The Gradle plugin may compose the integration modules. Core must never depend outward on Spring, deployment formats, or Gradle. Deployment-specific concepts must not leak into core models, and Gradle APIs must not leak into core or domain APIs.

New edges require an explicit architectural review and, when durable, an ADR. See the scoped `AGENTS.md` files for change-level rules.
