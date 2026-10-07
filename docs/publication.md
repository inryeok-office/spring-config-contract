# Publication readiness

Spring Config Contract is not published. The current supported workflow uses a source checkout and
`pluginManagement.includeBuild`, as described in the [usage guide](usage-guide.md). This document defines the future
publication contract; none of the coordinates below are available to consumers until the implementation in
[Issue #55](https://github.com/inryeok-office/spring-config-contract/issues/55) is completed and a release is explicitly
approved.

The publication topology is an architectural decision recorded in
[ADR-0003](decisions/0003-plugin-portal-and-maven-central-publication.md).

## Publication targets

Two public repositories have distinct responsibilities:

- The Gradle Plugin Portal publishes the plugin marker and the `config-contract-gradle-plugin` implementation
  artifact. It provides discovery through the `plugins` DSL.
- Maven Central publishes the runtime library modules `config-contract-core`, `config-contract-spring`, and
  `config-contract-deployment`.

The Plugin Portal is not the distribution target for arbitrary library modules. Publishing only the marker would also
be insufficient: the marker points to the implementation artifact, whose runtime metadata refers to the three project
modules. Those modules must exist at stable external coordinates.

Consumers resolving plugins use the repositories in `pluginManagement.repositories`, not the repositories used for
ordinary project dependencies. The supported future repository model therefore includes both repositories:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

This declaration is especially important for repository mirrors: the Plugin Portal's
[mirroring guidance](https://plugins.gradle.org/docs/mirroring) explicitly recommends making Maven Central available
when plugins depend on libraries published there. The implementation must test the two-repository model rather than
assuming that a Portal mirror proxies Central.

## Coordinates and versions

All artifacts in one release use the same version. The first release maps `0.1.0` to the plugin, marker, and every
runtime module; there are no independently versioned modules.

| Purpose | Repository | Coordinate at v0.1.0 |
| --- | --- | --- |
| Core runtime | Maven Central | `io.github.inryeok-office:config-contract-core:0.1.0` |
| Spring runtime | Maven Central | `io.github.inryeok-office:config-contract-spring:0.1.0` |
| Deployment runtime | Maven Central | `io.github.inryeok-office:config-contract-deployment:0.1.0` |
| Plugin implementation | Gradle Plugin Portal | `io.github.inryeok-office:config-contract-gradle-plugin:0.1.0` |
| Plugin marker | Gradle Plugin Portal | `io.github.inryeok-office.config-contract:io.github.inryeok-office.config-contract.gradle.plugin:0.1.0` |

The implementation artifact uses the existing root group and Gradle project name. The marker coordinate follows
Gradle's `plugin.id:plugin.id.gradle.plugin:plugin.version` convention and is generated from the existing plugin
declaration.

The root build remains the single version source. Development builds use a `-SNAPSHOT` version; an approved release
supplies one validated, non-snapshot version to every project. For the 0.x line, patch versions contain compatible
fixes and minor versions may change public APIs according to the [public API policy](public-api-policy.md). A release
must never mix plugin and runtime-module versions.

Before Maven Central publication, maintainers must verify control of the `io.github.inryeok-office` namespace. A
namespace verification failure blocks publication; it does not justify silently choosing different coordinates.

## Plugin Portal metadata

The future Portal publication uses these concrete values:

| Field | Value |
| --- | --- |
| Plugin ID | `io.github.inryeok-office.config-contract` |
| Display name | `Spring Config Contract` |
| Description | `Checks Spring Boot configuration requirements against deployment-provided configuration.` |
| Website | `https://github.com/inryeok-office/spring-config-contract` |
| VCS URL | `https://github.com/inryeok-office/spring-config-contract` |
| Tags | `spring`, `spring-boot`, `configuration`, `verification` |
| Gradle feature compatibility | Configuration Cache supported |

The ID, display name, description, and implementation class already exist in the `gradlePlugin` declaration. Issue
#55 adds the website, VCS URL, tags, feature compatibility, and publication tooling. Configuration Cache support is
backed by `ConfigContractPluginFunctionalTest`, which runs the task twice and asserts cache reuse. The implementation
must use a pinned version of
`com.gradle.plugin-publish` compatible with the checked-in Gradle Wrapper; version 2.2.1 is the current candidate for
Gradle 9.8.0 and must be rechecked when the implementation is reviewed. The
[official Plugin Portal guide](https://plugins.gradle.org/docs/publish-plugin) is the source of truth for its required
metadata and tasks.

## Runtime dependency model

The current `runtimeClasspath` reports establish these publication edges:

```text
config-contract-gradle-plugin
├── config-contract-core
├── config-contract-spring
│   ├── config-contract-core
│   ├── spring-boot
│   ├── kotlin-reflect
│   └── snakeyaml
└── config-contract-deployment
    ├── config-contract-core
    └── snakeyaml-engine
```

Kotlin standard-library and Spring Boot transitive dependencies are also present and are already available from Maven
Central. The plugin directly uses all three project modules, so all three direct edges must remain in the plugin
implementation metadata. Spring and deployment must each publish their edge to core. No project dependency may remain
as an unresolved local-project reference.

Future consumer resolution is:

```text
plugins DSL request
  -> Plugin Portal marker
  -> Plugin Portal implementation artifact
  -> Maven Central core + spring + deployment modules
  -> Maven Central third-party runtime dependencies
  -> plugin application and configContractCheck registration
```

Gradle Module Metadata and Maven POMs must agree on the group, artifact, aligned version, and runtime dependencies.
Gradle publishes Module Metadata alongside Maven metadata when `maven-publish` is used; both formats are required so
Gradle consumers retain variant information while Maven-compatible tooling can still inspect the dependency graph.

## Publication metadata and tooling

Issue #55 must add publication mechanics without changing product behavior:

- Apply `maven-publish` to the three Central-bound runtime modules and publish their Java components, source artifacts,
  documentation artifacts, Maven POMs, and Gradle Module Metadata.
- Apply `signing` to every Central-bound artifact and metadata file. Signing material must be provided only at the
  release boundary.
- Keep `java-gradle-plugin` for descriptors and marker generation, and apply `com.gradle.plugin-publish` to the plugin
  project for Portal publication.
- Use a dependency-reviewed client for the Maven Central Publisher Portal. Gradle 9.8 documents that the legacy Maven
  deployment protocol is no longer accepted by Central, so `maven-publish` alone is not the remote upload mechanism.
- Publish and verify the Central runtime modules before the Portal plugin version is approved, so a visible marker can
  never point at unavailable runtime artifacts.

Switching modules to `java-library`, shading dependencies into the plugin, or publishing a new platform/BOM is not
required for the current dependency model and is outside the implementation scope unless separate evidence justifies
it.

Every Central POM must include:

- a module-specific name and description;
- project URL `https://github.com/inryeok-office/spring-config-contract`;
- Apache License 2.0 name and URL;
- SCM browse URL and Git connection for this repository;
- organization-level contributor metadata for `inryeok-office`, without an invented personal email;
- the correct runtime dependency coordinates and aligned version.

Use these module-specific values:

| Artifact | POM name | POM description |
| --- | --- | --- |
| `config-contract-core` | `Spring Config Contract Core` | `Framework-independent configuration contract models and comparison rules.` |
| `config-contract-spring` | `Spring Config Contract Spring` | `Spring Boot configuration requirement discovery for Spring Config Contract.` |
| `config-contract-deployment` | `Spring Config Contract Deployment` | `Deployment configuration parsers for Spring Config Contract.` |

All three POMs use `inryeok-office` as the organization-level developer ID and name, with
`https://github.com/inryeok-office` as its URL. SCM uses
`https://github.com/inryeok-office/spring-config-contract` for browsing and
`scm:git:https://github.com/inryeok-office/spring-config-contract.git` for the connection. No personal email is
required or invented.

The implementation must follow the current [Maven Central requirements](https://central.sonatype.org/publish/requirements/)
for signatures, source/documentation artifacts, and POM metadata.

## Credentials and trust boundary

Normal builds, pull requests, local publication tests, and consumer fixtures require no credentials. Real publishing is
allowed only from a maintainer-approved CI environment. Expected secret categories are:

- Gradle Plugin Portal API key and secret (`GRADLE_PUBLISH_KEY`, `GRADLE_PUBLISH_SECRET`);
- Maven Central Publisher Portal username/token pair (`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`);
- an ASCII-armored in-memory signing key and its passphrase (`SIGNING_KEY`, `SIGNING_PASSWORD`).

They must be scoped CI secrets rather than repository files, command-line output, checked-in `gradle.properties`, or
long-lived workspace files. Logs must not print secret values. Pull-request workflows must never receive publishing
secrets, and the release environment must require human approval.

## Validation ladder

Publication implementation is not ready for an external release until all stages pass:

1. Run `./gradlew check`, `./gradlew build`, and `git diff --check` with the checked-in Wrapper and JDK 21.
2. Generate every POM, Gradle Module Metadata file, plugin descriptor, and marker locally without contacting a remote
   publication endpoint.
3. Publish core, spring, and deployment to a temporary Central-like Maven repository, and publish the marker and plugin
   implementation to a separate temporary Portal-like Maven repository.
4. Create a fresh consumer fixture outside the repository build. It must not use `includeBuild`, a project dependency,
   `mavenLocal()`, or a repository-specific absolute path.
5. Give the consumer only the two temporary repositories in `pluginManagement.repositories`; resolve and apply
   `io.github.inryeok-office.config-contract` with an explicit test version.
6. Verify task registration and run `configContractCheck` for a passing contract and a deterministic failing contract.
7. Inspect the generated POMs, Module Metadata, marker dependency, checksums/signatures where applicable, coordinates,
   versions, URLs, license, SCM, and the complete transitive dependency chain.
8. Only after the credential-free ladder passes in CI may a separately approved workflow receive secrets and offer a
   manually gated external publication action.

The local repositories and consumer must be created under disposable build or temporary directories and removed by
the test lifecycle. Tests must work without a Docker daemon, mutable host configuration, or developer-specific paths.

## Implementation boundary

[Issue #55](https://github.com/inryeok-office/spring-config-contract/issues/55) owns the build configuration, metadata
generation, isolated consumer fixture, and credential-free CI validation. A production publish, Git tag, and GitHub
Release remain separate maintainer actions. Completing that Issue must not by itself execute any of them.

## References

- [Gradle plugin marker artifacts](https://docs.gradle.org/current/userguide/plugins_intermediate.html#sec:plugin_markers)
- [Gradle Plugin Portal publishing](https://plugins.gradle.org/docs/publish-plugin)
- [Gradle Plugin Portal mirroring](https://plugins.gradle.org/docs/mirroring)
- [Maven Publish Plugin](https://docs.gradle.org/current/userguide/publishing_maven.html)
- [Gradle Module Metadata](https://docs.gradle.org/current/userguide/publishing_gradle_module_metadata.html)
- [Maven Central publication requirements](https://central.sonatype.org/publish/requirements/)
