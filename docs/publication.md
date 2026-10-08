# Publication readiness

Spring Config Contract is not published. The current supported workflow uses a source checkout and
`pluginManagement.includeBuild`, as described in the [usage guide](usage-guide.md). The credential-free publication
pipeline is implemented, but none of the coordinates below are available to consumers until a maintainer explicitly
approves and performs a release.

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
ordinary project dependencies. For a standard public consumer, the intended path is the Gradle default: no explicit
`pluginManagement.repositories` block and resolution through the Plugin Portal. That path cannot be fully exercised
until a version is visible on the Portal, so every external release requires a clean post-publication smoke test.

Repository mirrors and local publication fixtures must expose both the Plugin Portal and Maven Central to plugin
resolution:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

The explicit declaration is especially important for repository mirrors: the Plugin Portal's
[mirroring guidance](https://plugins.gradle.org/docs/mirroring) explicitly recommends making Maven Central available
when plugins depend on libraries published there. Validation must cover both the default public path and the explicit
two-repository mirror model rather than assuming that a Portal mirror proxies Central.

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

The root build remains the single version source. It reads a dedicated `releaseVersion` Gradle property, defaulting to
`0.1.0-SNAPSHOT` for development. Local publication fixtures pass an explicit test version;
external publication tasks require `-PreleaseVersion=<version>` and reject blank or `-SNAPSHOT` values. Passing
`-Pversion` is not the contract because the current root build assignment overrides it. For the 0.x line, patch
versions contain compatible fixes and minor versions may change public APIs according to the
[public API policy](public-api-policy.md). A release must never mix plugin and runtime-module versions.

Before any irreversible release, maintainers must verify control of the `io.github.inryeok-office` Maven Central
namespace and verify that the Plugin Portal account behind `GRADLE_PUBLISH_KEY` can establish ownership of the
`inryeok-office` GitHub organization. A first Portal publication and a later group or plugin ID change require manual
Portal review. Failure of either ownership prerequisite blocks publication; it does not justify silently choosing
different coordinates.

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

The `gradlePlugin` declaration configures the ID, display name, description, website, VCS URL, tags, feature
compatibility, and publication tooling. Configuration Cache support is backed by
`ConfigContractPluginFunctionalTest`, which runs the task twice and asserts cache reuse. The implementation pins
`com.gradle.plugin-publish` 2.2.1 for the checked-in Gradle 9.8.0 Wrapper; the
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
implementation metadata. Spring and deployment expose core types in public signatures, so their core project dependency
is `api`; their POM and `apiElements` metadata allow an external
consumer to compile those signatures. No project dependency may remain as an unresolved local-project reference.

Publishing these modules at stable Central coordinates makes their non-`internal` types public API under the
[public API policy](public-api-policy.md). They remain supporting components of the Gradle plugin rather than separate
v0.1 user entry points, but publication metadata must still describe their existing API accurately.

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

The metadata-based model places Spring, Kotlin, and parser dependencies on the consumer's plugin/buildscript
classpath. `ConfigContractCheckTask` also uses a parent-first application class loader. Gradle may therefore resolve
versions shared with other applied plugins before discovery runs. v0.1 claims only the versions in the compatibility
guide, and the isolated consumer test applies this plugin alongside the matching Spring Boot and Kotlin Gradle plugins.
Broader combinations are not claimed. Worker/process isolation or dependency relocation is deferred;
if the tested combination conflicts, external publication is blocked and isolation requires a separate design change.

## Publication metadata and tooling

The publication pipeline adds the following mechanics without changing product behavior:

- Apply `maven-publish` to the three Central-bound runtime modules and publish their Java components, source artifacts,
  Dokka-generated API documentation artifacts, Maven POMs, and Gradle Module Metadata. The implementation must use the
  dependency-reviewed Dokka v2 Gradle plugin and package its HTML output as the `javadoc` classifier instead of
  publishing an empty Javadoc JAR. Dokka 2.2.0 is pinned under the dependency policy.
- Publish the core edge from spring and deployment as `api`, matching their public signatures.
- Apply `signing` to every Central-bound artifact and metadata file only when signing inputs are present. Release jobs
  use protected secrets; credential-free CI generates an ephemeral throwaway key in a temporary directory, signs the
  local publications, verifies every `.asc`, and then removes the key material.
- Keep `java-gradle-plugin` for descriptors and marker generation, and apply `com.gradle.plugin-publish` to the plugin
  project for Portal publication.
- Use a dependency-reviewed client for the Maven Central Publisher Portal. Gradle 9.8 documents that the legacy Maven
  deployment protocol is no longer accepted by Central, so `maven-publish` alone is not the remote upload mechanism.

Switching modules to `java-library`, shading dependencies into the plugin, or publishing a new platform/BOM is not
required for the current dependency model and is outside the implementation scope unless separate evidence justifies
it.

Every Central POM must include:

- a module-specific name and description;
- project URL `https://github.com/inryeok-office/spring-config-contract`;
- Apache License 2.0 name and URL;
- SCM browse URL and Git connection for this repository;
- organization-level developer metadata for `inryeok-office`, without an invented personal email;
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

The implemented pipeline follows the current [Maven Central requirements](https://central.sonatype.org/publish/requirements/)
for signatures, source/documentation artifacts, and POM metadata; maintainers must recheck them before an external
release.

## Credentials and trust boundary

Normal builds, pull requests, local publication tests, and consumer fixtures require no credentials. Real publishing is
allowed only from a maintainer-approved CI environment. Expected secret categories are:

- Gradle Plugin Portal API key and secret (`GRADLE_PUBLISH_KEY`, `GRADLE_PUBLISH_SECRET`);
- Maven Central Publisher Portal username/token pair (`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`);
- an ASCII-armored in-memory signing key and its passphrase (`SIGNING_KEY`, `SIGNING_PASSWORD`).

They must be scoped CI secrets rather than repository files, command-line output, checked-in `gradle.properties`, or
long-lived workspace files. Logs must not print secret values. Pull-request workflows must never receive publishing
secrets, and the release environment must require human approval.

## Implemented local and remote-preflight boundaries

`preparePublicationVerification` creates disposable Central-like, Portal-like, and third-party Maven repositories.
`PublicationPipelineFunctionalTest` runs that task with a non-SNAPSHOT test version, verifies generated POM and Gradle
Module Metadata, signs every Central-like JAR/POM/module with a generated ephemeral key, and resolves isolated offline
consumers without `includeBuild`, `mavenLocal()`, or a source-tree classpath. One consumer proves the deterministic
passing and failing `configContractCheck` paths; another applies the supported Spring Boot and Kotlin Gradle plugins
beside Spring Config Contract and executes the task.

External publication remains opt-in. `validateExternalPublicationRelease` rejects a missing, blank, or `-SNAPSHOT`
`releaseVersion`. `validateRemotePublicationPreflightInputs` additionally requires
`-PallowRemotePreflight=true` and protected Central/signing inputs, but deliberately makes no network request. It is
the safe hand-off before a maintainer performs the documented user-managed Central validation deployment. The plugin
project exposes the Plugin Portal's supported manual validation path:

```text
./gradlew :config-contract-gradle-plugin:publishPlugins --validate-only -PreleaseVersion=<release-version>
```

That command is never run by pull-request CI and requires protected Portal credentials. A successful Central validation
deployment must still be stopped before release until separate human approval is given.

## Release sequence and failure recovery

Maven Central and the Plugin Portal do not provide one atomic transaction. A maintainer-approved release follows this
order:

1. Complete the credential-free validation ladder below and confirm both namespace/ownership prerequisites.
2. Upload the signed Central bundle as a user-managed deployment, let Central validate it, and stop before release.
3. Run `publishPlugins --validate-only` with the protected Portal credentials; this performs server-side validation
   without uploading the plugin.
4. After human approval, release the validated Central deployment and wait until every runtime module resolves from the
   public repository.
5. Publish the identical version to the Plugin Portal, wait for any required manual approval, and confirm visibility.
6. Run the default and mirrored post-publication consumer smoke tests.

If a failure before Central release cannot be corrected without changing artifacts or metadata, discard the staged
deployment and rebuild the same version from the corrected commit. After Central release, published files are
immutable: if the exact Portal publication cannot complete, do not overwrite or reuse that version. Record the orphaned
Central modules, increment the patch version for the entire aligned set, and restart the full sequence.

## Validation ladder

Publication implementation is not ready for an external release until all stages pass:

1. Run `./gradlew check`, `./gradlew build`, and `git diff --check` with the checked-in Wrapper and JDK 21.
2. Generate every POM, Gradle Module Metadata file, Dokka artifact, plugin descriptor, and marker locally without
   contacting a remote publication endpoint.
3. Publish core, spring, and deployment to a temporary Central-like Maven repository, publish the marker and plugin
   implementation to a separate temporary Portal-like Maven repository, and seed a third read-only repository with the
   already-resolved third-party runtime graph and its Maven/Gradle metadata.
4. Create a fresh consumer fixture outside the repository build. It must not use `includeBuild`, a project dependency,
   `mavenLocal()`, or a repository-specific absolute path.
5. Give the consumer the two project-artifact repositories and the read-only third-party repository in
   `pluginManagement.repositories`; run offline, resolve the explicit test version, and prove the complete third-party
   dependency hop without a repository-local classpath.
6. Verify task registration and run `configContractCheck` for a passing contract and a deterministic failing contract.
   Repeat with the tested Spring Boot and Kotlin Gradle plugins applied in the consumer.
7. Generate an ephemeral test signing key, exercise conditional signing, and cryptographically verify one `.asc` for
   every Central-bound JAR, POM, and `.module` file. No key survives the test lifecycle.
8. Inspect coordinates, aligned versions, `api` and runtime edges, URLs, license, developer and SCM metadata, marker
   dependency, checksums, signatures, and the complete transitive dependency chain.
9. Only after stages 1-8 pass may a protected environment perform Central validation and
   `publishPlugins --validate-only` as the remote preflight.
10. A separately approved release follows the sequence above. Once visible, a clean Gradle user home must resolve the
    plugin with the default Portal-only configuration, and a second smoke test must exercise the explicit mirrored
    Portal-plus-Central model.

The local repositories, third-party mirror, consumer, and signing home must be created under disposable build or
temporary directories and removed by the test lifecycle. Stages 1-8 run offline after the repository build has resolved
its declared dependencies; they must not require a Docker daemon, mutable host configuration, or developer-specific
paths.

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
- [Dokka Gradle plugin](https://kotlinlang.org/docs/dokka-gradle.html)
- [Maven Central publication requirements](https://central.sonatype.org/publish/requirements/)
