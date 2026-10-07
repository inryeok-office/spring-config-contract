# ADR-0003: Publish the plugin through the Plugin Portal and runtime modules through Maven Central

- Status: Accepted
- Date: 2026-10-07

## Context

The Gradle plugin directly depends on `config-contract-core`, `config-contract-spring`, and
`config-contract-deployment`. Spring and deployment also depend on core, and their runtime graphs include libraries
from Maven Central. An included source build can resolve project dependencies, but an external consumer cannot.

Gradle resolves a `plugins` DSL request by looking for a marker at
`plugin.id:plugin.id.gradle.plugin:plugin.version`. That marker depends on the plugin implementation artifact, whose
published metadata must then expose resolvable runtime dependencies. A marker by itself therefore cannot make the
current plugin usable outside the source build.

The publication design also needs stable coordinates and a versioning rule before publishing automation or credentials
are introduced.

## Decision

- Publish the plugin marker and `config-contract-gradle-plugin` implementation artifact to the Gradle Plugin Portal.
- Publish `config-contract-core`, `config-contract-spring`, and `config-contract-deployment` to Maven Central.
- Use the existing `io.github.inryeok-office` group and Gradle project names for implementation and runtime module
  coordinates. Keep the existing `io.github.inryeok-office.config-contract` plugin ID.
- Use one aligned project version for the marker, implementation, and all runtime modules. v0.1.0 is `0.1.0` across
  every published coordinate.
- Require plugin consumers and validation fixtures to make the Plugin Portal and Maven Central available to plugin
  resolution. This also supports environments that mirror the two repositories separately.
- Publish Maven POMs and Gradle Module Metadata with complete runtime dependency edges. Central-bound artifacts also
  carry the required source/documentation artifacts, POM metadata, and signatures.
- Keep credential-free local publication and isolated consumer resolution in normal CI. Any external publication is a
  separate, manually approved maintainer action.

The complete coordinates, metadata, secret boundary, and validation ladder are defined in
[the publication readiness guide](../publication.md).

## Consequences

- External plugin resolution has a complete path from marker to implementation to runtime modules; project dependencies
  do not leak into published metadata.
- Library artifacts must be available on Maven Central before the corresponding plugin version is approved on the
  Plugin Portal.
- Releases must coordinate two services and keep one version aligned across them.
- Maven Central namespace verification, signing, required POM metadata, and source/documentation artifacts become
  release prerequisites.
- Repository mirrors must expose both the Plugin Portal and Maven Central in plugin resolution.
- Publication implementation requires focused build and CI work, tracked by Issue #55, but this decision performs no
  publication and introduces no credentials.

## Alternatives considered

### Gradle Plugin Portal only

Rejected. The Portal is the correct discovery and implementation target for Gradle plugins, but not the selected
distribution target for the three general runtime modules. Publishing only a marker also leaves its implementation
dependencies unresolved.

### Maven Central only

Rejected. A custom marker in a Maven repository can support the `plugins` DSL when consumers configure that repository,
but it gives up the standard Plugin Portal discovery path without reducing the need to publish the runtime modules.

### Shade all runtime modules into the plugin implementation

Rejected. Shading would duplicate artifacts, complicate license and dependency tracking, and change packaging solely to
avoid publishing the existing module graph. The normal metadata-based dependency model is clearer and testable.
