# Public API policy

## Definition

Public API includes public Kotlin/Java types, constructors, functions, properties, Gradle extensions, task types, plugin IDs, configuration keys, and documented command behavior that consumers can depend on. Package-private or `internal` implementation details are not public API, but they must still preserve module boundaries.

## 0.x expectations

The project is early-stage and does not promise full binary or source compatibility across 0.x releases. That flexibility is not permission to expose types casually: every public API must have a clear consumer, deliberate naming, tests, and documentation.

Review public API changes for ownership, nullability, defaults, error behavior, compatibility impact, and future evolution. Prefer internal types until a stable boundary is understood. Do not expose Spring or Gradle implementation types from core or domain APIs.

Public API changes require an Issue-backed PR and maintainer review. Update compatibility and user documentation as applicable, and add regression coverage for the contract being exposed.
